package com.love.archive.course.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.testsupport.ApiIntegrationTest;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.CompleteMultipartUploadResult;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.UploadPartResult;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.Charset;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 课程模块的接口行为，重点是付费门禁。
 *
 * <p>核心不变量：<b>访客列表接口在任何情况下都不能带出正文与视频播放地址</b>。
 * 这一条有专门的用例，而且不是只断言状态码 200——是断言字段不存在、且整个响应体里
 * 搜不到正文原文。</p>
 *
 * <p>只设 {@code app.storage.cos.bucket} 而不设 secret / region：这样
 * {@code CosStorageConfiguration} 的条件不成立，真实 COSClient 不会装配，
 * 下面那个 mock 就是唯一的 COSClient。</p>
 */
@Import(CourseApiTest.StubCosConfiguration.class)
@TestPropertySource(properties = "app.storage.cos.bucket=course-test-bucket")
class CourseApiTest extends ApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "https://h5.example.test";
    private static final String ADMIN_USERNAME = "course-admin";
    private static final String ADMIN_PASSWORD = "course-admin-2026";
    private static final String OTHER_ADMIN_USERNAME = "course-admin-other";
    private static final String FREE_PHONE = "13800138001";
    private static final String VIP_PHONE = "13800138002";
    private static final String GUEST_PASSWORD = "Guest-course-2026";

    /** 正文原文。用于在列表响应里全文搜索，确认它一个字都没漏出去。 */
    private static final String SECRET_BODY = "# 只有会员看得到的正文-a7f3c9";

    /** 迁移与测试基类都种了这四个合集，RESTART IDENTITY 之后第一个的 id 稳定是 1。 */
    private static final long SEEDED_COLLECTION_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private StringRedisTemplate redis;

    private Cookie adminCookie;

    @BeforeEach
    void prepare() throws Exception {
        resetDatabase();
        resetRateLimits(redis);
        insertAdmin(ADMIN_USERNAME);
        adminCookie = adminLogin(ADMIN_USERNAME);
    }

    // ==================== 鉴权 ====================

    @Test
    void adminEndpointsRequireAnAdminSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/course-collections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestEndpointsRequireAGuestToken() throws Exception {
        mockMvc.perform(get("/api/v1/guest/courses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/guest/courses/collections"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/guest/courses/1")).andExpect(status().isUnauthorized());
    }

    // ==================== 合集 ====================

    @Test
    void migrationSeedsTheFourProductCollections() throws Exception {
        mockMvc.perform(get("/api/v1/admin/course-collections").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("情绪与认知"))
                .andExpect(jsonPath("$.data[1].name").value("择偶与筛选"))
                .andExpect(jsonPath("$.data[2].name").value("恋爱关系"))
                .andExpect(jsonPath("$.data[3].name").value("形象与状态"));
    }

    @Test
    void rejectsADuplicateCollectionName() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/course-collections")
                        .content("{\"name\":\"情绪与认知\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_COLLECTION_NAME_DUPLICATE"));
    }

    @Test
    void hidingACollectionRemovesItsCoursesFromTheGuestSide() throws Exception {
        long courseId = publishTextCourse("会被隐藏的课");
        String vipToken = vipGuest();
        assertThat(guestListBody(vipToken)).contains("会被隐藏的课");

        mockMvc.perform(adminPatch("/api/v1/admin/course-collections/" + SEEDED_COLLECTION_ID)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        assertThat(guestListBody(vipToken)).doesNotContain("会被隐藏的课");
        // 合集下线，课程详情也不该还能打开。
        mockMvc.perform(guestGet("/api/v1/guest/courses/" + courseId, vipToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    // ==================== 内容校验 ====================

    @Test
    void articleAndTextCoursesMustCarryABody() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/courses").content(courseJson("ARTICLE", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_CONTENT_REQUIRED"));
        // 只有空白不算正文。
        mockMvc.perform(adminPost("/api/v1/admin/courses")
                        .content(courseJson("TEXT", "   \\n  ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_CONTENT_REQUIRED"));
    }

    @Test
    void videoCoursesMustCarryAnObjectKey() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/courses")
                        .content(courseJson("VIDEO", "有简介但没视频", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_VIDEO_REQUIRED"));
    }

    /**
     * 产品口径：时长只登记，不做任何校验、不做任何提示。
     * 这个用例把「没有 5–15 分钟区间」钉住——1 秒与 10 小时都必须收下。
     */
    @Test
    void videoDurationIsRecordedWithoutAnyRangeValidation() throws Exception {
        assertThat(createVideoCourse("一秒课", 1)).isPositive();
        assertThat(createVideoCourse("十小时课", 36_000)).isPositive();
        assertThat(createVideoCourse("没时长的课", null)).isPositive();
    }

    // ==================== 乐观锁与状态流转 ====================

    @Test
    void concurrentEditsCollideOnTheVersion() throws Exception {
        long courseId = createTextCourse("会被并发改的课");

        mockMvc.perform(adminPut("/api/v1/admin/courses/" + courseId)
                        .content(courseJson("TEXT", "第一次改", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        // 第二个人还拿着 version 0。
        mockMvc.perform(adminPut("/api/v1/admin/courses/" + courseId)
                        .content(courseJson("TEXT", "第二次改", 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_VERSION_CONFLICT"));
    }

    @Test
    void republishingKeepsTheOriginalPublishTime() throws Exception {
        long courseId = publishTextCourse("上下架的课");
        String firstPublishedAt = jsonOf(
                mockMvc.perform(get("/api/v1/admin/courses/" + courseId).cookie(adminCookie))
                        .andReturn(),
                "publishedAt");

        mockMvc.perform(adminPost("/api/v1/admin/courses/" + courseId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(adminPost("/api/v1/admin/courses/" + courseId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                // 下架再上架不该把「最新上架」的排序刷到最前。
                .andExpect(jsonPath("$.data.publishedAt").value(firstPublishedAt));
    }

    @Test
    void deletingACourseLeavesAnAuditTrail() throws Exception {
        long courseId = createTextCourse("待删除的课");
        mockMvc.perform(adminDelete("/api/v1/admin/courses/" + courseId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/courses/" + courseId).cookie(adminCookie))
                .andExpect(status().isNotFound());
        assertThat(auditCount("COURSE_DELETED")).isEqualTo(1);
    }

    @Test
    void writesAuditEntriesForCreateAndPublish() throws Exception {
        publishTextCourse("留痕的课");
        assertThat(auditCount("COURSE_CREATED")).isEqualTo(1);
        assertThat(auditCount("COURSE_PUBLISHED")).isEqualTo(1);
    }

    // ==================== 付费门禁（核心） ====================

    @Test
    void freeGuestsSeeTheListWithEverythingLocked() throws Exception {
        publishTextCourse("免费能看到标题的课");
        String freeToken = freeGuest();

        mockMvc.perform(guestGet("/api/v1/guest/courses", freeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("免费能看到标题的课"))
                .andExpect(jsonPath("$.data.items[0].locked").value(true));
    }

    @Test
    void paidGuestsSeeTheSameListUnlocked() throws Exception {
        publishTextCourse("会员看到的课");

        mockMvc.perform(guestGet("/api/v1/guest/courses", vipGuest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].locked").value(false));
    }

    /**
     * <b>本次最重要的用例。</b>
     *
     * <p>列表对免费用户开放，所以正文与播放地址一旦出现在列表响应里就是付费内容泄露。
     * 这里不只检查已知字段路径，还在整个响应体里搜正文原文——
     * 将来有人往列表 DTO 上加字段，也会被这一条挡住。</p>
     */
    @Test
    void theListNeverCarriesBodyOrPlaybackUrlForAnyone() throws Exception {
        publishTextCourse("正文不能出现在列表里的课");
        createVideoCourse("视频课", 600);
        publishLatestVideoCourse();

        for (String token : new String[] {freeGuest(), vipGuest()}) {
            String body = guestListBody(token);

            assertThat(body)
                    .withFailMessage("列表响应里出现了正文原文，付费内容泄露：%s", body)
                    .doesNotContain(SECRET_BODY);
            assertThat(body)
                    .withFailMessage("列表响应里出现了签名地址，付费内容泄露：%s", body)
                    .doesNotContain("cos.test");

            mockMvc.perform(guestGet("/api/v1/guest/courses", token))
                    .andExpect(jsonPath("$.data.items[0].contentMarkdown").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].videoUrl").doesNotExist())
                    .andExpect(jsonPath("$.data.items[1].contentMarkdown").doesNotExist())
                    .andExpect(jsonPath("$.data.items[1].videoUrl").doesNotExist());
        }
    }

    @Test
    void freeGuestsCannotOpenACourse() throws Exception {
        long courseId = publishTextCourse("会员专属的课");

        mockMvc.perform(guestGet("/api/v1/guest/courses/" + courseId, freeGuest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_VIP_REQUIRED"));
    }

    @Test
    void paidGuestsGetTheBodyAndPlaybackUrl() throws Exception {
        long textCourseId = publishTextCourse("会员能读的图文课");
        String vipToken = vipGuest();

        mockMvc.perform(guestGet("/api/v1/guest/courses/" + textCourseId, vipToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMarkdown").value(SECRET_BODY))
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());

        createVideoCourse("会员能看的视频课", 600);
        long videoCourseId = publishLatestVideoCourse();
        mockMvc.perform(guestGet("/api/v1/guest/courses/" + videoCourseId, vipToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.containsString("cos.test")));
    }

    /**
     * 免费用户连「这个 id 存不存在」都不该探测到，所以门禁先判、再查库：
     * 一个不存在的 id 对免费用户也是 403 而不是 404。
     */
    @Test
    void theGateIsCheckedBeforeExistenceSoFreeGuestsCannotProbeIds() throws Exception {
        mockMvc.perform(guestGet("/api/v1/guest/courses/999999", freeGuest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_VIP_REQUIRED"));
    }

    @Test
    void unpublishedCoursesAreInvisibleEvenToPaidGuests() throws Exception {
        long draftId = createTextCourse("还没上架的课");
        String vipToken = vipGuest();

        mockMvc.perform(guestGet("/api/v1/guest/courses/" + draftId, vipToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        mockMvc.perform(guestGet("/api/v1/guest/courses", vipToken))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ==================== Markdown 上传 ====================

    @Test
    void readsAnUploadedMarkdownFileWithoutStoringIt() throws Exception {
        mockMvc.perform(adminMultipart("/api/v1/admin/course-assets/markdown")
                        .file(new MockMultipartFile(
                                "file", "lesson.md", "text/markdown",
                                "# 标题\n正文".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# 标题\n正文"));
    }

    @Test
    void rejectsMarkdownThatIsNotUtf8() throws Exception {
        // GBK 的「中文」是 D6 D0 CE C4，不是合法 UTF-8。严格解码会拒绝，
        // 而不是静默替换成一堆 U+FFFD 让管理员以为是编辑器坏了。
        mockMvc.perform(adminMultipart("/api/v1/admin/course-assets/markdown")
                        .file(new MockMultipartFile(
                                "file", "gbk.md", "text/markdown",
                                "中文".getBytes(Charset.forName("GBK")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_MARKDOWN_INVALID"));
    }

    @Test
    void rejectsMarkdownOverOneMebibyte() throws Exception {
        byte[] tooBig = new byte[1024 * 1024 + 1];
        Arrays.fill(tooBig, (byte) 'a');
        mockMvc.perform(adminMultipart("/api/v1/admin/course-assets/markdown")
                        .file(new MockMultipartFile("file", "big.md", "text/markdown", tooBig)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("COURSE_MARKDOWN_TOO_LARGE"));
    }

    // ==================== 视频分块上传 ====================

    @Test
    void uploadsAVideoInParts() throws Exception {
        String uploadId = initiateVideoUpload();

        mockMvc.perform(adminMultipart(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId + "/parts")
                        .file(new MockMultipartFile("file", new byte[1024]))
                        .param("partNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partNumber").value(1))
                .andExpect(jsonPath("$.data.etag").value("etag-1"));

        mockMvc.perform(adminPost(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId + "/complete")
                        .content("{\"parts\":[{\"partNumber\":1,\"etag\":\"etag-1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(12_582_912L))
                .andExpect(jsonPath("$.data.contentType").value("video/mp4"))
                // 合并完就给回放地址：管理员要在保存课程之前确认传对了文件，
                // 否则唯一的验证方式是先保存再重新打开抽屉，传错了得连课程一起返工。
                .andExpect(jsonPath("$.data.previewUrl")
                        .value(org.hamcrest.Matchers.containsString("cos.test")));
    }

    @Test
    void tellsTheBrowserWhatChunkSizeToUse() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/course-assets/videos/uploads")
                        .content("{\"filename\":\"a.mp4\",\"contentType\":\"video/mp4\",\"totalBytes\":1048576}"))
                .andExpect(status().isOk())
                // 8 MiB，刻意压在 spring.servlet.multipart.max-file-size（10MB）之下。
                .andExpect(jsonPath("$.data.partSizeBytes").value(8 * 1024 * 1024));
    }

    @Test
    void rejectsVideoTypesOutsideTheWhitelist() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/course-assets/videos/uploads")
                        .content("{\"filename\":\"a.avi\",\"contentType\":\"video/x-msvideo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_VIDEO_TYPE_UNSUPPORTED"));
    }

    @Test
    void rejectsPartNumbersOutsideTheCosRange() throws Exception {
        String uploadId = initiateVideoUpload();
        mockMvc.perform(adminMultipart(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId + "/parts")
                        .file(new MockMultipartFile("file", new byte[1024]))
                        .param("partNumber", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OBJECT_STORAGE_PART_NUMBER_INVALID"));
    }

    /**
     * uploadId 随初始化响应回到浏览器，所以只按它定位会话是个漏洞：
     * 任何登录管理员拿到别人的 uploadId 就能往那个上传里塞分块或提前合并。
     */
    @Test
    void anotherAdminCannotPushPartsIntoSomeoneElsesUpload() throws Exception {
        String uploadId = initiateVideoUpload();

        insertAdmin(OTHER_ADMIN_USERNAME);
        Cookie otherCookie = adminLogin(OTHER_ADMIN_USERNAME);

        mockMvc.perform(multipart(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId + "/parts")
                        .file(new MockMultipartFile("file", new byte[1024]))
                        .param("partNumber", "1")
                        .cookie(otherCookie)
                        .header("Origin", TRUSTED_ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_UPLOAD_NOT_FOUND"));
    }

    @Test
    void abortingAnUploadClosesTheSession() throws Exception {
        String uploadId = initiateVideoUpload();

        mockMvc.perform(adminDelete(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId))
                .andExpect(status().isOk());
        // 已结束的会话不能再收分块。
        mockMvc.perform(adminMultipart(
                        "/api/v1/admin/course-assets/videos/uploads/" + uploadId + "/parts")
                        .file(new MockMultipartFile("file", new byte[1024]))
                        .param("partNumber", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_UPLOAD_NOT_FOUND"));
    }

    // ==================== 辅助 ====================

    private String initiateVideoUpload() throws Exception {
        MvcResult result = mockMvc.perform(adminPost("/api/v1/admin/course-assets/videos/uploads")
                        .content("{\"filename\":\"lesson.mp4\",\"contentType\":\"video/mp4\","
                                + "\"totalBytes\":12582912}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonOf(result, "uploadId");
    }

    private long createTextCourse(String title) throws Exception {
        MvcResult result = mockMvc.perform(adminPost("/api/v1/admin/courses")
                        .content(courseJson("TEXT", SECRET_BODY, null, title)))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(jsonOf(result, "id"));
    }

    private long publishTextCourse(String title) throws Exception {
        long courseId = createTextCourse(title);
        mockMvc.perform(adminPost("/api/v1/admin/courses/" + courseId + "/publish"))
                .andExpect(status().isOk());
        return courseId;
    }

    private long createVideoCourse(String title, Integer durationSeconds) throws Exception {
        String duration = durationSeconds == null
                ? ""
                : ",\"videoDurationSeconds\":" + durationSeconds;
        MvcResult result = mockMvc.perform(adminPost("/api/v1/admin/courses")
                        .content("""
                                {"collectionId":%d,"title":"%s","contentType":"VIDEO",
                                 "videoObjectKey":"courses/videos/%s.mp4"%s}
                                """.formatted(
                                SEEDED_COLLECTION_ID,
                                title,
                                java.util.UUID.randomUUID(),
                                duration)))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(jsonOf(result, "id"));
    }

    /** 把最近建的视频课上架，供门禁用例用。 */
    private long publishLatestVideoCourse() throws Exception {
        MvcResult list = mockMvc.perform(get("/api/v1/admin/courses")
                        .param("contentType", "VIDEO")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();
        long courseId = new ObjectMapper()
                .readTree(list.getResponse().getContentAsString())
                .path("data").path("items").path(0).path("id").asLong();
        mockMvc.perform(adminPost("/api/v1/admin/courses/" + courseId + "/publish"))
                .andExpect(status().isOk());
        return courseId;
    }

    private String courseJson(String contentType, String body, Long expectedVersion) {
        return courseJson(contentType, body, expectedVersion, "教材名称");
    }

    private String courseJson(
            String contentType, String body, Long expectedVersion, String title) {
        StringBuilder json = new StringBuilder("{\"collectionId\":")
                .append(SEEDED_COLLECTION_ID)
                .append(",\"title\":\"").append(title).append('"')
                .append(",\"contentType\":\"").append(contentType).append('"');
        if (body != null) {
            json.append(",\"contentMarkdown\":\"").append(body).append('"');
        }
        if (expectedVersion != null) {
            json.append(",\"expectedVersion\":").append(expectedVersion);
        }
        return json.append('}').toString();
    }

    private String guestListBody(String token) throws Exception {
        return mockMvc.perform(guestGet("/api/v1/guest/courses", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String freeGuest() throws Exception {
        return registerGuest(mockMvc, FREE_PHONE, GUEST_PASSWORD);
    }

    /** 直接把等级写成 VIP：这里要验的是门禁，不是升级流程。 */
    private String vipGuest() throws Exception {
        String token = registerGuest(mockMvc, VIP_PHONE, GUEST_PASSWORD);
        execute("UPDATE user_account SET membership_tier = 'VIP' WHERE phone = '" + VIP_PHONE + "'");
        return token;
    }

    private MockHttpServletRequestBuilder guestGet(String url, String token) {
        return get(url).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder adminPost(String url) {
        return post(url)
                .cookie(adminCookie)
                .header("Origin", TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder adminPut(String url) {
        return put(url)
                .cookie(adminCookie)
                .header("Origin", TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder adminPatch(String url) {
        return patch(url)
                .cookie(adminCookie)
                .header("Origin", TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder adminDelete(String url) {
        return delete(url).cookie(adminCookie).header("Origin", TRUSTED_ORIGIN);
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            adminMultipart(String url) {
        var builder = multipart(url);
        builder.cookie(adminCookie);
        builder.header("Origin", TRUSTED_ORIGIN);
        return builder;
    }

    private static String jsonOf(MvcResult result, String field) throws Exception {
        return new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path(field)
                .asText();
    }

    private long auditCount(String action) {
        return Long.parseLong(queryString(
                "SELECT COUNT(*) FROM audit_log WHERE action = '" + action + "'"));
    }

    private String queryString(String sql) {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : "";
        } catch (SQLException exception) {
            throw new IllegalStateException("查询失败", exception);
        }
    }

    private void execute(String sql) {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("执行失败", exception);
        }
    }

    private void insertAdmin(String username) {
        char[] password = ADMIN_PASSWORD.toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(username);
        admin.setDisplayName("Course Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
    }

    private Cookie adminLogin(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .header("Origin", TRUSTED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("archive-token-admin");
    }

    /** 假 COS：只回放固定结果，不做网络往返。签名地址带 {@code cos.test} 便于断言。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubCosConfiguration {

        @Bean
        COSClient stubCosClient() throws Exception {
            COSClient client = mock(COSClient.class);
            AtomicInteger uploadIds = new AtomicInteger();

            when(client.generatePresignedUrl(anyString(), anyString(), any()))
                    .thenAnswer(invocation -> URI
                            .create("https://cos.test/" + invocation.getArgument(1))
                            .toURL());

            when(client.initiateMultipartUpload(any())).thenAnswer(invocation -> {
                InitiateMultipartUploadResult result = new InitiateMultipartUploadResult();
                // uploadId 在库里唯一，同一个用例里可能开多次会话，所以要递增。
                result.setUploadId("cos-upload-" + uploadIds.incrementAndGet());
                return result;
            });

            when(client.uploadPart(any())).thenAnswer(invocation -> {
                UploadPartResult result = new UploadPartResult();
                result.setPartNumber(1);
                result.setETag("etag-1");
                return result;
            });

            when(client.completeMultipartUpload(any()))
                    .thenReturn(new CompleteMultipartUploadResult());

            ObjectMetadata stored = new ObjectMetadata();
            stored.setContentLength(12_582_912L);
            stored.setContentType("video/mp4");
            when(client.getObjectMetadata(anyString(), anyString())).thenReturn(stored);

            return client;
        }
    }
}

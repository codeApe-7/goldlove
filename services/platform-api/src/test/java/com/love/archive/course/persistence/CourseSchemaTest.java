package com.love.archive.course.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V6 的库级不变量。课程正文与视频地址是付费内容，「有没有内容」不能只靠服务层校验——
 * 绕过应用直接写库也必须被拦住，否则 H5 端会拿到一节点开全是空白的课。
 *
 * <p>另一半是权限：archive_app 没有 DDL，新表如果忘了 GRANT，插入会直接权限报错。
 * 序列尤其容易漏——V1 那句 {@code GRANT ... ON ALL SEQUENCES} 只覆盖它执行时已存在的序列。</p>
 */
@Transactional
class CourseSchemaTest extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void migrationSeedsTheFourCollectionsInDisplayOrder() {
        assertThat(jdbc.sql("""
                        SELECT name FROM course_collection
                         WHERE name IN ('情绪与认知', '择偶与筛选', '恋爱关系', '形象与状态')
                         ORDER BY sort_order
                        """)
                .query(String.class)
                .list())
                .containsExactly("情绪与认知", "择偶与筛选", "恋爱关系", "形象与状态");
    }

    @Test
    void collectionNamesAreUnique() {
        assertRejected("uq_course_collection_name",
                () -> jdbc.sql("INSERT INTO course_collection (name) VALUES ('情绪与认知')")
                        .update());
    }

    @Test
    void collectionStatusIsLimitedToActiveOrHidden() {
        assertRejected("ck_course_collection_status", () -> jdbc.sql("""
                        INSERT INTO course_collection (name, status) VALUES ('测试合集', 'DELETED')
                        """).update());
    }

    @Test
    void articleAndTextCoursesMustCarryBody() {
        long collectionId = insertCollection("正文校验合集");

        assertRejected("ck_course_content",
                () -> insertCourse(collectionId, "ARTICLE", null, null));
        // 只有空白字符不算正文：编辑器清空后常留下一个换行。
        assertRejected("ck_course_content",
                () -> insertCourse(collectionId, "TEXT", "   \n ", null));

        assertThat(insertCourse(collectionId, "ARTICLE", "# 标题", null)).isPositive();
        assertThat(insertCourse(collectionId, "TEXT", "纯文本正文", null)).isPositive();
    }

    @Test
    void videoCoursesMustCarryAnObjectKey() {
        long collectionId = insertCollection("视频校验合集");

        assertRejected("ck_course_content",
                () -> insertCourse(collectionId, "VIDEO", "有正文但没视频", null));

        assertThat(insertCourse(collectionId, "VIDEO", null, "courses/videos/a.mp4")).isPositive();
    }

    @Test
    void publishedCoursesMustCarryAPublishTime() {
        long collectionId = insertCollection("发布时间合集");
        long courseId = insertCourse(collectionId, "TEXT", "正文", null);

        assertRejected("ck_course_published_at",
                () -> jdbc.sql("UPDATE course SET status = 'PUBLISHED' WHERE id = :id")
                        .param("id", courseId)
                        .update());

        assertThat(jdbc.sql("""
                        UPDATE course SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP
                         WHERE id = :id
                        """)
                .param("id", courseId)
                .update()).isOne();
    }

    /**
     * 时长只是展示用元数据：库里唯一那条约束是「不能是 0 或负数」的数据合理性兜底，
     * 不存在 5–15 分钟之类的业务区间。这个用例就是把「没有区间校验」钉住。
     */
    @Test
    void videoDurationOnlyRejectsNonPositiveValues() {
        long collectionId = insertCollection("时长合集");

        assertRejected("ck_course_duration", () -> insertVideoCourse(collectionId, 0, null));
        assertRejected("ck_course_duration", () -> insertVideoCourse(collectionId, -1, null));

        // 1 秒与 10 小时都必须收下——时长不参与任何业务校验，也允许为空。
        assertThat(insertVideoCourse(collectionId, 1, null)).isPositive();
        assertThat(insertVideoCourse(collectionId, 36_000, null)).isPositive();
        assertThat(insertVideoCourse(collectionId, null, null)).isPositive();
    }

    @Test
    void videoSizeMustBePositiveWhenPresent() {
        long collectionId = insertCollection("大小合集");

        assertRejected("ck_course_video_size", () -> insertVideoCourse(collectionId, 600, 0L));
        assertThat(insertVideoCourse(collectionId, 600, 1L)).isPositive();
    }

    @Test
    void theSameVideoObjectCannotBackTwoCourses() {
        long collectionId = insertCollection("对象唯一合集");
        insertCourse(collectionId, "VIDEO", null, "courses/videos/shared.mp4");

        assertRejected("uq_course_video_object_key",
                () -> insertCourse(collectionId, "VIDEO", null, "courses/videos/shared.mp4"));
    }

    @Test
    void uploadSessionsAreUniquePerUploadIdAndObjectKey() {
        long adminId = insertAdmin("course-schema-admin");
        insertUpload(adminId, "cos-upload-1", "courses/videos/one.mp4");

        assertRejected("uq_course_video_upload_id",
                () -> insertUpload(adminId, "cos-upload-1", "courses/videos/two.mp4"));
        assertRejected("uq_course_video_upload_object_key",
                () -> insertUpload(adminId, "cos-upload-2", "courses/videos/one.mp4"));
    }

    /**
     * 合集不给 DELETE：下面挂着课程，删了会留孤儿，下线应该走 status = 'HIDDEN'。
     * 课程与上传会话给 DELETE：草稿写错要能删，上传会话是临时数据。
     */
    @Test
    void runtimeRoleHasExactlyThePrivilegesTheModuleNeeds() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single())
                .isEqualTo("archive_app");

        for (String privilege : new String[] {"SELECT", "INSERT", "UPDATE"}) {
            assertThat(hasTablePrivilege("course_collection", privilege)).isTrue();
            assertThat(hasTablePrivilege("course", privilege)).isTrue();
            assertThat(hasTablePrivilege("course_video_upload", privilege)).isTrue();
        }
        assertThat(hasTablePrivilege("course_collection", "DELETE")).isFalse();
        assertThat(hasTablePrivilege("course", "DELETE")).isTrue();
        assertThat(hasTablePrivilege("course_video_upload", "DELETE")).isTrue();
    }

    /**
     * IDENTITY 列取值要用序列的 USAGE 权限。V1 的 {@code ON ALL SEQUENCES} 只覆盖当时已有的序列，
     * 后建的三个序列必须在 V6 里再授一次，否则每一次插入都会权限报错。
     */
    @Test
    void runtimeRoleCanDrawFromTheNewIdentitySequences() {
        for (String sequence : new String[] {
                "course_collection_id_seq", "course_id_seq", "course_video_upload_id_seq"}) {
            assertThat(jdbc.sql("SELECT has_sequence_privilege(current_user, :name, 'USAGE')")
                            .param("name", sequence)
                            .query(Boolean.class)
                            .single())
                    .withFailMessage("序列 %s 没有授权给 archive_app", sequence)
                    .isTrue();
        }
    }

    /**
     * Postgres 里一条语句失败就把整个事务标成 aborted，后续语句全部拒绝执行。
     * 想在同一个用例里既验证「被拦住」又验证「合法数据能进」，就得让失败的语句
     * 跑在自己的 SAVEPOINT 里——这就是 PROPAGATION_NESTED 做的事。
     */
    private void assertRejected(String expectedConstraint, Runnable statement) {
        TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(() -> savepoint.executeWithoutResult(ignored -> statement.run()))
                .hasStackTraceContaining(expectedConstraint);
    }

    private long insertCollection(String name) {
        return jdbc.sql("INSERT INTO course_collection (name) VALUES (:name) RETURNING id")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long insertCourse(
            long collectionId, String contentType, String markdown, String videoObjectKey) {
        return jdbc.sql("""
                        INSERT INTO course (
                            collection_id, title, content_type, content_markdown, video_object_key
                        ) VALUES (
                            :collectionId, '教材名称', :contentType, :markdown, :videoObjectKey
                        ) RETURNING id
                        """)
                .param("collectionId", collectionId)
                .param("contentType", contentType)
                .param("markdown", markdown)
                .param("videoObjectKey", videoObjectKey)
                .query(Long.class)
                .single();
    }

    private long insertVideoCourse(long collectionId, Integer durationSeconds, Long sizeBytes) {
        return jdbc.sql("""
                        INSERT INTO course (
                            collection_id, title, content_type, video_object_key,
                            video_duration_seconds, video_size_bytes
                        ) VALUES (
                            :collectionId, '教材名称', 'VIDEO',
                            'courses/videos/' || gen_random_uuid() || '.mp4',
                            :duration, :size
                        ) RETURNING id
                        """)
                .param("collectionId", collectionId)
                .param("duration", durationSeconds)
                .param("size", sizeBytes)
                .query(Long.class)
                .single();
    }

    private long insertAdmin(String username) {
        return jdbc.sql("""
                        INSERT INTO admin_user (username, display_name, password_hash, status)
                        VALUES (:username, '课程库级测试', 'not-a-real-hash', 'ACTIVE')
                        RETURNING id
                        """)
                .param("username", username)
                .query(Long.class)
                .single();
    }

    private void insertUpload(long adminId, String uploadId, String objectKey) {
        jdbc.sql("""
                        INSERT INTO course_video_upload (upload_id, object_key, admin_id, content_type)
                        VALUES (:uploadId, :objectKey, :adminId, 'video/mp4')
                        """)
                .param("uploadId", uploadId)
                .param("objectKey", objectKey)
                .param("adminId", adminId)
                .update();
    }

    private boolean hasTablePrivilege(String table, String privilege) {
        return jdbc.sql("SELECT has_table_privilege(current_user, :table, :privilege)")
                .param("table", table)
                .param("privilege", privilege)
                .query(Boolean.class)
                .single();
    }
}

# 照片上传闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让嘉宾上传头像与生活照：照片随草稿保存、随提交固化进不可变版本快照、管理员审核与嘉宾本人可见，并保证对象存储 Key 永不进入客户端。

**Architecture:** 在既有 `storage` 模块之上，`guest` 模块新增 `ProfilePhotoService`（上传校验、草稿照片列表、删除），`review` 模块在提交时把草稿照片快照复制进 `profile_revision_photo` 并在详情中返回照片元数据与 15 分钟签名 URL。上传走服务端中继：multipart → 服务端真实格式校验 → `ObjectStorageService.put` → 照片引用落库。`guest` 与 `review` 模块声明新增对 `storage::application` 的依赖。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Modulith、MyBatis-Plus、PostgreSQL 18 + Flyway、腾讯云 COS 官方 SDK、Lombok、JUnit 5、Testcontainers、Mockito。

## Global Constraints

- 照片类别仅 `AVATAR`（每人最多 1 张）与 `LIFE`（每人最多 6 张）。
- 单张 ≤ 10 MiB；仅 JPEG/PNG/WebP；服务端嗅探并校验真实格式与最小尺寸 64×64，不信任客户端 `Content-Type`。
- 提交建档时头像必填；缺失返回 `PROFILE_VALIDATION_FAILED`（missing 含 `avatar`）。
- 草稿删除立即删除 COS 对象；对象进入 `profile_revision_photo` 快照后保留。
- 对象键形如 `profiles/{accountId}/{category小写}/{uuid}.{扩展名}`，uuid 服务端生成。
- 展示 URL 一律 15 分钟短时签名，不落库、不持久化。
- 所有账号 ID 取自 Sa-Token；照片查询/删除带 `guest_profile.user_account_id = accountId` 条件，跨账号与不存在统一 `PHOTO_NOT_FOUND`。
- 实体不使用 Lombok `@Data`/类级 `@ToString`；密文字节数组防御性复制（本项目现有照片表无字节数组字段）。
- 常规测试套件不依赖真实云：`ObjectStorageService` 用 Mockito mock；真桶行为由 gated `CosLiveSmokeTest` 覆盖。
- 照片对象键、签名 URL 不进入日志；`SensitiveDataGuardTest` 的日志模式扩展至 `objectKey`/`object_key`。
- 每次生产改动先写失败测试（RED）→ 实现（GREEN）→ 聚焦测试 → 提交。

---

### Task 1: 照片持久化（V5 迁移、实体、Mapper）

**Files:**
- Create: `services/platform-api/src/main/resources/db/migration/V5__profile_photo_upload.sql`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/domain/PhotoCategory.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfilePhotoEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfilePhotoMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionPhotoEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionPhotoMapper.java`
- Modify: `services/platform-api/src/test/java/com/love/archive/testsupport/ApiIntegrationTest.java`（TRUNCATE 列表加入新表）
- Test: `services/platform-api/src/test/java/com/love/archive/profile/ProfilePhotoPersistenceTest.java`

**Interfaces:**
- Produces: `PhotoCategory`（枚举 `AVATAR`、`LIFE`）
- Produces: `ProfilePhotoEntity`（表 `profile_photo`）
- Produces: `ProfilePhotoMapper extends BaseMapper<ProfilePhotoEntity>`，含 `int countRevisionReferences(String objectKey)` 与 `int selectMaxSortOrder(long guestProfileId, PhotoCategory category)`
- Produces: `ProfileRevisionPhotoEntity`（表 `profile_revision_photo`）
- Produces: `ProfileRevisionPhotoMapper extends BaseMapper<ProfileRevisionPhotoEntity>`

- [ ] **Step 1: 写失败测试 `ProfilePhotoPersistenceTest`**

```java
package com.love.archive.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfilePhotoPersistenceTest extends ApiIntegrationTest {

    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;

    private long profileId;
    private long accountId;

    @BeforeEach
    void seedProfile() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("photo-persistence-admin");
        admin.setDisplayName("Photo Persistence Admin");
        admin.setPasswordHash("not-used");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminMapper.insert(admin);

        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext("photo-persistence".getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac("photo-persistence");
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedByAdminId(admin.getId());
        account.setActivatedAt(OffsetDateTime.now());
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        accountId = account.getId();

        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(accountId);
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        profileMapper.insert(profile);
        profileId = profile.getId();
    }

    @Test
    void enforcesOneAvatarAndLifeSortUniqueness() {
        insertPhoto("AVATAR", "profiles/1/avatar/a.jpg", 0);
        assertThatThrownBy(() -> insertPhoto("AVATAR", "profiles/1/avatar/b.jpg", 1))
                .hasMessageContaining("uq_profile_photo_avatar_one");

        insertPhoto("LIFE", "profiles/1/life/c.jpg", 0);
        assertThatThrownBy(() -> insertPhoto("LIFE", "profiles/1/life/d.jpg", 0))
                .hasMessageContaining("uq_profile_photo_profile_category_sort");
    }

    @Test
    void rejectsInvalidCategory() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO profile_photo (
                    guest_profile_id, category, object_key, sha256, size_bytes,
                    content_type, width, height, sort_order
                ) VALUES (?, 'VIDEO', 'profiles/1/video/v.mp4', ?, 1, 'video/mp4', 100, 100, 0)
                """, profileId, "a".repeat(64)))
                .hasMessageContaining("ck_profile_photo_category");
    }

    @Test
    void revisionPhotoRowsAreImmutable() throws SQLException {
        long revisionId = insertRevision();
        execute("""
                INSERT INTO profile_revision_photo (
                    profile_revision_id, category, object_key, sha256, size_bytes,
                    content_type, width, height, sort_order, created_at
                ) VALUES (?, 'AVATAR', 'profiles/1/avatar/snap.jpg', ?, 10,
                          'image/jpeg', 100, 100, 0, ?)
                """, revisionId, "b".repeat(64), OffsetDateTime.now());

        assertThatThrownBy(() -> execute("""
                UPDATE profile_revision_photo SET width = 50 WHERE profile_revision_id = ?
                """, revisionId))
                .hasMessageContaining("profile_revision_photo rows are immutable");
        assertThatThrownBy(() -> execute("""
                DELETE FROM profile_revision_photo WHERE profile_revision_id = ?
                """, revisionId))
                .hasMessageContaining("profile_revision_photo rows are immutable");
    }

    private long insertRevision() {
        OffsetDateTime now = OffsetDateTime.now();
        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(profileId);
        revision.setRevisionNumber(1);
        revision.setStatus(RevisionStatus.PENDING);
        revision.setSubmittedByAccountId(accountId);
        revision.setSubmittedAt(now);
        revision.setReviewDeadlineAt(now.plusHours(24));
        revision.setSubmissionKeyHmac(UUID.randomUUID().toString());
        revision.setRequestPayloadSha256("c".repeat(64));
        revision.setVersion(0L);
        revision.setCreatedAt(now);
        revisionMapper.insert(revision);
        return revision.getId();
    }

    private void insertPhoto(String category, String objectKey, int sortOrder) {
        execute("""
                INSERT INTO profile_photo (
                    guest_profile_id, category, object_key, sha256, size_bytes,
                    content_type, width, height, sort_order
                ) VALUES (?, ?, ?, ?, 10, 'image/jpeg', 100, 100, ?)
                """, profileId, category, objectKey, "d".repeat(64), sortOrder);
    }

    private static void execute(String sql, Object... args) {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
```

- [ ] **Step 2: 运行并见证失败**

```bash
export JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_USER_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/maven-home
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfilePhotoPersistenceTest
```

Expected: `relation "profile_photo" does not exist`。

- [ ] **Step 3: 添加 V5 迁移与实体/Mapper**

`V5__profile_photo_upload.sql`：

```sql
CREATE TABLE profile_photo (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    guest_profile_id BIGINT NOT NULL REFERENCES guest_profile(id),
    category VARCHAR(16) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_profile_photo_object_key UNIQUE (object_key),
    CONSTRAINT uq_profile_photo_profile_category_sort
        UNIQUE (guest_profile_id, category, sort_order),
    CONSTRAINT ck_profile_photo_category CHECK (category IN ('AVATAR', 'LIFE'))
);

CREATE UNIQUE INDEX uq_profile_photo_avatar_one
    ON profile_photo (guest_profile_id) WHERE category = 'AVATAR';
CREATE INDEX ix_profile_photo_profile ON profile_photo (guest_profile_id);

CREATE TABLE profile_revision_photo (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    profile_revision_id BIGINT NOT NULL REFERENCES profile_revision(id),
    category VARCHAR(16) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_profile_revision_photo_category_sort
        UNIQUE (profile_revision_id, category, sort_order),
    CONSTRAINT ck_profile_revision_photo_category
        CHECK (category IN ('AVATAR', 'LIFE'))
);

CREATE INDEX ix_profile_revision_photo_revision
    ON profile_revision_photo (profile_revision_id);

CREATE FUNCTION reject_profile_photo_immutable_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profile_revision_photo_immutable
    BEFORE UPDATE OR DELETE ON profile_revision_photo
    FOR EACH ROW EXECUTE FUNCTION reject_profile_photo_immutable_mutation();

GRANT SELECT, INSERT, UPDATE, DELETE ON profile_photo TO archive_app;
GRANT SELECT, INSERT ON profile_revision_photo TO archive_app;
GRANT USAGE, SELECT ON SEQUENCE profile_photo_id_seq TO archive_app;
GRANT USAGE, SELECT ON SEQUENCE profile_revision_photo_id_seq TO archive_app;
```

`PhotoCategory.java`：

```java
package com.love.archive.guest.domain;

public enum PhotoCategory {
    AVATAR,
    LIFE
}
```

`ProfilePhotoEntity.java`：

```java
package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.guest.domain.PhotoCategory;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_photo")
public class ProfilePhotoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("guest_profile_id")
    private Long guestProfileId;
    @TableField("category")
    private PhotoCategory category;
    @TableField("object_key")
    private String objectKey;
    @TableField("sha256")
    private String sha256;
    @TableField("size_bytes")
    private Long sizeBytes;
    @TableField("content_type")
    private String contentType;
    @TableField("width")
    private Integer width;
    @TableField("height")
    private Integer height;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
```

`ProfilePhotoMapper.java`：

```java
package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.love.archive.guest.domain.PhotoCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfilePhotoMapper extends BaseMapper<ProfilePhotoEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM profile_revision_photo
            WHERE object_key = #{objectKey}
            """)
    int countRevisionReferences(@Param("objectKey") String objectKey);

    @Select("""
            SELECT COALESCE(MAX(sort_order), -1) + 1
            FROM profile_photo
            WHERE guest_profile_id = #{guestProfileId}
              AND category = #{category}
            """)
    int selectMaxSortOrder(
            @Param("guestProfileId") long guestProfileId,
            @Param("category") PhotoCategory category);
}
```

`ProfileRevisionPhotoEntity.java`：

```java
package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.guest.domain.PhotoCategory;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_revision_photo")
public class ProfileRevisionPhotoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("profile_revision_id")
    private Long profileRevisionId;
    @TableField("category")
    private PhotoCategory category;
    @TableField("object_key")
    private String objectKey;
    @TableField("sha256")
    private String sha256;
    @TableField("size_bytes")
    private Long sizeBytes;
    @TableField("content_type")
    private String contentType;
    @TableField("width")
    private Integer width;
    @TableField("height")
    private Integer height;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
```

`ProfileRevisionPhotoMapper.java`：

```java
package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProfileRevisionPhotoMapper extends BaseMapper<ProfileRevisionPhotoEntity> {
}
```

`ApiIntegrationTest.resetDatabase()` 的 TRUNCATE 语句改为：

```java
statement.execute("""
        TRUNCATE TABLE audit_log, profile_review_record, profile_revision_photo,
            profile_revision_field_value, profile_revision, profile_photo,
            profile_field_value, guest_profile, authorization_record,
            activation_credential, payment_record, external_identity, user_account,
            admin_user RESTART IDENTITY CASCADE
        """);
```

- [ ] **Step 4: 运行并见证通过**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfilePhotoPersistenceTest
```

Expected: 3 tests, 0 failures。

- [ ] **Step 5: 提交**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
git add services/platform-api/src
git commit -m "feat: add profile photo persistence"
```

---

### Task 2: 服务端真实图片校验

**Files:**
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/PhotoFileValidator.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/PhotoFileValidatorTest.java`

**Interfaces:**
- Consumes: `ApiException`（`com.love.archive.common.web`）
- Produces: `PhotoFileValidator.validate(byte[] content)` → `ImageInfo`
- Produces: `record ImageInfo(String contentType, int width, int height)`（嵌套于 `PhotoFileValidator`）

- [ ] **Step 1: 写失败测试**

```java
package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.common.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoFileValidatorTest {

    private final PhotoFileValidator validator = new PhotoFileValidator();

    @Test
    void validatesJpegPngAndWebpDimensions() throws Exception {
        PhotoFileValidator.ImageInfo jpeg = validator.validate(jpeg(64, 64));
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.width()).isEqualTo(64);
        assertThat(jpeg.height()).isEqualTo(64);

        PhotoFileValidator.ImageInfo png = validator.validate(png(80, 100));
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.width()).isEqualTo(80);
        assertThat(png.height()).isEqualTo(100);

        PhotoFileValidator.ImageInfo webp = validator.validate(webpVp8l(64, 64));
        assertThat(webp.contentType()).isEqualTo("image/webp");
        assertThat(webp.width()).isEqualTo(64);
        assertThat(webp.height()).isEqualTo(64);
    }

    @Test
    void rejectsEmptyOversizedAndFakeFiles() {
        assertCode(() -> validator.validate(new byte[0]), "PHOTO_CONTENT_INVALID");
        assertCode(() -> validator.validate(null), "PHOTO_CONTENT_INVALID");
        assertCode(() -> validator.validate(new byte[10 * 1024 * 1024 + 1]),
                "PHOTO_TOO_LARGE");
        assertCode(() -> validator.validate("not-an-image".getBytes()),
                "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}),
                "PHOTO_FORMAT_UNSUPPORTED");
    }

    @Test
    void rejectsImagesBelowMinimumDimension() throws Exception {
        assertCode(() -> validator.validate(jpeg(32, 32)), "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(png(63, 100)), "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(webpVp8l(1, 1)), "PHOTO_FORMAT_UNSUPPORTED");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] webpVp8l(int width, int height) {
        byte[] data = new byte[30];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        data[8] = 'W'; data[9] = 'E'; data[10] = 'B'; data[11] = 'P';
        data[12] = 'V'; data[13] = 'P'; data[14] = '8'; data[15] = 'L';
        data[20] = 0x2F;
        int bits = (width - 1) | ((height - 1) << 14);
        data[21] = (byte) (bits & 0xFF);
        data[22] = (byte) ((bits >>> 8) & 0xFF);
        data[23] = (byte) ((bits >>> 16) & 0xFF);
        data[24] = (byte) ((bits >>> 24) & 0xFF);
        return data;
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}
```

- [ ] **Step 2: 运行并见证失败**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=PhotoFileValidatorTest
```

Expected: 编译失败，`cannot find symbol: class PhotoFileValidator`。

- [ ] **Step 3: 实现 `PhotoFileValidator`**

```java
package com.love.archive.guest.application;

import com.love.archive.common.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class PhotoFileValidator {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MIN_DIMENSION = 64;
    private static final int JPEG = 0;
    private static final int PNG = 1;
    private static final int WEBP = 2;

    public ImageInfo validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("PHOTO_CONTENT_INVALID", "文件内容不能为空");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PHOTO_TOO_LARGE",
                    "单张照片不能超过 10 MiB");
        }
        int format = sniff(content);
        if (format < 0) {
            throw invalid("PHOTO_FORMAT_UNSUPPORTED", "仅支持 JPEG、PNG、WebP 图片");
        }
        int[] size = decodeSize(format, content);
        if (size == null || size[0] < MIN_DIMENSION || size[1] < MIN_DIMENSION) {
            throw invalid("PHOTO_FORMAT_UNSUPPORTED", "图片无法识别或尺寸过小（至少 64×64）");
        }
        String contentType = switch (format) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            default -> "image/webp";
        };
        return new ImageInfo(contentType, size[0], size[1]);
    }

    private static int sniff(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return WEBP;
        }
        return -1;
    }

    private static int[] decodeSize(int format, byte[] content) {
        if (format == JPEG || format == PNG) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                return image == null
                        ? null
                        : new int[] {image.getWidth(), image.getHeight()};
            } catch (IOException exception) {
                return null;
            }
        }
        return decodeWebpSize(content);
    }

    private static int[] decodeWebpSize(byte[] content) {
        if (content.length < 30) {
            return null;
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == 'L' && (content[20] & 0xFF) == 0x2F) {
            int bits = (content[21] & 0xFF)
                    | ((content[22] & 0xFF) << 8)
                    | ((content[23] & 0xFF) << 16)
                    | ((content[24] & 0xFF) << 24);
            return new int[] {(bits & 0x3FFF) + 1, ((bits >>> 14) & 0x3FFF) + 1};
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == 'X') {
            int width = (content[24] & 0xFF)
                    | ((content[25] & 0xFF) << 8)
                    | ((content[26] & 0xFF) << 16);
            int height = (content[27] & 0xFF)
                    | ((content[28] & 0xFF) << 8)
                    | ((content[29] & 0xFF) << 16);
            return new int[] {width + 1, height + 1};
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == ' ') {
            if ((content[23] & 0xFF) != 0x9D
                    || (content[24] & 0xFF) != 0x01
                    || (content[25] & 0xFF) != 0x2A) {
                return null;
            }
            int width = (content[26] & 0xFF) | ((content[27] & 0xFF) << 8);
            int height = (content[28] & 0xFF) | ((content[29] & 0xFF) << 8);
            return new int[] {width & 0x3FFF, height & 0x3FFF};
        }
        return null;
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record ImageInfo(String contentType, int width, int height) {
    }
}
```

- [ ] **Step 4: 运行并见证通过**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=PhotoFileValidatorTest
```

Expected: 3 tests, 0 failures。

- [ ] **Step 5: 提交**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
git add services/platform-api/src
git commit -m "feat: validate real photo formats server-side"
```

---

### Task 3: 草稿照片服务与嘉宾接口

**Files:**
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/package-info.java`（`allowedDependencies` 增加 `"storage::application"`）
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/GuestProfilePhotoController.java`
- Modify: `services/platform-api/src/main/resources/application.yml`（multipart 上限）
- Modify: `services/platform-api/src/main/java/com/love/archive/common/web/GlobalExceptionHandler.java`（multipart 异常映射）
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/ProfilePhotoServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfilePhotoApiTest.java`

**Interfaces:**
- Consumes: `PhotoFileValidator.validate`、`ObjectStorageService.put/exists/signDownloadUrl/delete`、`GuestAccountStatusQuery.requireActive`、`GuestProfileMapper`、`ProfilePhotoMapper`
- Produces: `ProfilePhotoView(long id, String category, String sha256, long sizeBytes, String contentType, int width, int height, int sortOrder, String downloadUrl, OffsetDateTime createdAt)`
- Produces: `ProfilePhotoService.upload(long accountId, PhotoCategory category, byte[] content)` → `ProfilePhotoView`
- Produces: `ProfilePhotoService.list(long accountId)` → `List<ProfilePhotoView>`
- Produces: `ProfilePhotoService.delete(long accountId, long photoId)`

- [ ] **Step 1: 更新模块依赖并写失败测试**

`guest/package-info.java` 的 `allowedDependencies` 改为：

```java
allowedDependencies = {
        "common::web",
        "common::security",
        "audit::application",
        "identity::application",
        "identity::security",
        "storage::application"
})
```

`ProfilePhotoServiceTest.java`（测试用 `@MockitoBean` 替换 `ObjectStorageService`，真实 PostgreSQL 数据）：

```java
package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.review.persistence.ProfileRevisionPhotoEntity;
import com.love.archive.review.persistence.ProfileRevisionPhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ProfilePhotoServiceTest extends ApiIntegrationTest {

    private static final String AVATAR_KEY = "profiles/1/avatar/a1b2c3.jpg";

    @Autowired private ProfilePhotoService photoService;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfilePhotoMapper photoMapper;
    @Autowired private ProfileRevisionMapper revisionMapper;
    @Autowired private ProfileRevisionPhotoMapper revisionPhotoMapper;
    @MockitoBean private ObjectStorageService storageService;

    private long accountId;
    private long profileId;

    @BeforeEach
    void seedDraftOwner() {
        resetDatabase();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("photo-service-admin");
        admin.setDisplayName("Photo Service Admin");
        admin.setPasswordHash("not-used");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminMapper.insert(admin);

        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext("photo-service".getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac("photo-service");
        account.setPasswordHash("not-used");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedByAdminId(admin.getId());
        account.setActivatedAt(OffsetDateTime.now());
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        account.setVersion(0L);
        accountMapper.insert(account);
        accountId = account.getId();

        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(accountId);
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        profileMapper.insert(profile);
        profileId = profile.getId();

        when(storageService.put(eq(AVATAR_KEY), any(byte[].class), eq("image/jpeg")))
                .thenReturn(new StoredObjectView(
                        AVATAR_KEY, "loveplatform-1314980040", 10, "image/jpeg", "a".repeat(64)));
        when(storageService.signDownloadUrl(any(String.class), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void uploadStoresAvatarAndReturnsViewWithSignedUrl() {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        assertThat(view.category()).isEqualTo("AVATAR");
        assertThat(view.width()).isEqualTo(100);
        assertThat(view.height()).isEqualTo(80);
        assertThat(view.downloadUrl()).startsWith("https://loveplatform-1314980040");
        ProfilePhotoEntity stored = photoMapper.selectOne(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId));
        assertThat(stored.getObjectKey()).startsWith("profiles/" + accountId + "/avatar/");
        assertThat(stored.getSha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void rejectsSecondAvatarAndSeventhLifePhoto() {
        photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        assertCode(() -> photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes()),
                "PHOTO_COUNT_LIMIT_EXCEEDED");

        for (int i = 0; i < 6; i++) {
            photoService.upload(accountId, PhotoCategory.LIFE, imageBytes());
        }
        assertCode(() -> photoService.upload(accountId, PhotoCategory.LIFE, imageBytes()),
                "PHOTO_COUNT_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsUploadOrDeleteWhenPendingReview() {
        profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .set(GuestProfileEntity::getStatus, ProfileStatus.PENDING_REVIEW));

        assertCode(() -> photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes()),
                "PHOTO_NOT_EDITABLE");
        assertCode(() -> photoService.delete(accountId, 1L), "PHOTO_NOT_EDITABLE");
    }

    @Test
    void rejectsUploadBeforeDraftExists() {
        profileMapper.deleteById(profileId);
        assertCode(() -> photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes()),
                "PROFILE_NOT_STARTED");
    }

    @Test
    void deleteRemovesRowAndObjectWhenNotSnapshotted() {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

        photoService.delete(accountId, view.id());

        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
        verify(storageService).delete(any(String.class));
    }

    @Test
    void deleteKeepsObjectWhenSnapshotted() {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        ProfilePhotoEntity photo = photoMapper.selectById(view.id());
        insertRevisionSnapshot(photo.getObjectKey());

        photoService.delete(accountId, view.id());

        verify(storageService, never()).delete(any(String.class));
        assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    }

    @Test
    void deleteRejectsForeignPhotoAsNotFound() {
        ProfilePhotoView view = photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        long foreignId = view.id();
        profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .set(GuestProfileEntity::getUserAccountId, 999_999L));

        assertCode(() -> photoService.delete(accountId, foreignId), "PHOTO_NOT_FOUND");
    }

    @Test
    void listReturnsOwnedPhotosWithSignedUrls() {
        photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());
        photoService.upload(accountId, PhotoCategory.LIFE, imageBytes());

        assertThat(photoService.list(accountId)).hasSize(2);
        assertThat(photoService.list(accountId))
                .allMatch(photo -> photo.downloadUrl().startsWith("https://loveplatform"));
    }

    private void insertRevisionSnapshot(String objectKey) {
        OffsetDateTime now = OffsetDateTime.now();
        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(profileId);
        revision.setRevisionNumber(1);
        revision.setStatus(RevisionStatus.APPROVED);
        revision.setSubmittedByAccountId(accountId);
        revision.setSubmittedAt(now);
        revision.setReviewDeadlineAt(now.plusHours(24));
        revision.setSubmissionKeyHmac(UUID.randomUUID().toString());
        revision.setRequestPayloadSha256("b".repeat(64));
        revision.setVersion(0L);
        revision.setCreatedAt(now);
        revisionMapper.insert(revision);

        ProfileRevisionPhotoEntity snapshot = new ProfileRevisionPhotoEntity();
        snapshot.setProfileRevisionId(revision.getId());
        snapshot.setCategory(PhotoCategory.AVATAR);
        snapshot.setObjectKey(objectKey);
        snapshot.setSha256("a".repeat(64));
        snapshot.setSizeBytes(10L);
        snapshot.setContentType("image/jpeg");
        snapshot.setWidth(100);
        snapshot.setHeight(80);
        snapshot.setSortOrder(0);
        snapshot.setCreatedAt(now);
        revisionPhotoMapper.insert(snapshot);
    }

    private static byte[] imageBytes() {
        return "fake-image-bytes".getBytes(StandardCharsets.UTF_8);
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }
}
```

`imageBytes()` 必须返回能通过 `PhotoFileValidator` 的真实图片（Task 2 已单测校验逻辑），用 `ImageIO` 生成 100×80 PNG，各调用点方法签名加 `throws Exception`：

```java
private static byte[] imageBytes() throws Exception {
    BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
}
```

对应 import 增加 `java.awt.image.BufferedImage`、`java.io.ByteArrayOutputStream`、`javax.imageio.ImageIO`。

`deleteRejectsForeignPhotoAsNotFound` 改为“第二个账号的照片对第一个账号不可见”（不能改当前档案的 `user_account_id`，否则 `requireOwnedProfile` 先抛 `PROFILE_NOT_STARTED`）：

```java
@Test
void deleteRejectsForeignPhotoAsNotFound() throws Exception {
    // 第二个账号与其档案
    long otherAccountId = insertAccount("photo-foreign");
    GuestProfileEntity otherProfile = new GuestProfileEntity();
    otherProfile.setProfileNo(UUID.randomUUID());
    otherProfile.setUserAccountId(otherAccountId);
    otherProfile.setStatus(ProfileStatus.DRAFT);
    otherProfile.setVersion(0L);
    otherProfile.setCreatedAt(OffsetDateTime.now());
    otherProfile.setUpdatedAt(OffsetDateTime.now());
    profileMapper.insert(otherProfile);
    when(storageService.put(anyString(), any(byte[].class), anyString()))
            .thenReturn(new StoredObjectView(
                    "profiles/" + otherAccountId + "/avatar/other.jpg",
                    "loveplatform-1314980040", 10, "image/jpeg", "d".repeat(64)));
    ProfilePhotoView foreign = photoService.upload(
            otherAccountId, PhotoCategory.AVATAR, imageBytes());

    assertCode(() -> photoService.delete(accountId, foreign.id()), "PHOTO_NOT_FOUND");
}
```

`insertAccount(String seed)` 辅助方法（复用 `accountId` 前的账号插入逻辑）与本测试类其它用例保持一致。

- [ ] **Step 2: 运行并见证失败**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfilePhotoServiceTest,GuestProfilePhotoApiTest
```

Expected: 编译失败，`cannot find symbol: class ProfilePhotoService`。

- [ ] **Step 3: 实现视图、服务、控制器与配置**

`ProfilePhotoView.java`：

```java
package com.love.archive.guest.application;

import java.time.OffsetDateTime;

public record ProfilePhotoView(
        long id,
        String category,
        String sha256,
        long sizeBytes,
        String contentType,
        int width,
        int height,
        int sortOrder,
        String downloadUrl,
        OffsetDateTime createdAt) {
}
```

`ProfilePhotoService.java`：

```java
package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.identity.application.GuestAccountStatusQuery;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

    private static final int MAX_LIFE_PHOTOS = 6;
    private static final Duration URL_TTL = Duration.ofMinutes(15);

    private final GuestAccountStatusQuery accountStatusQuery;
    private final GuestProfileMapper profileMapper;
    private final ProfilePhotoMapper photoMapper;
    private final ObjectStorageService storageService;
    private final PhotoFileValidator photoFileValidator;

    @Transactional
    public ProfilePhotoView upload(long accountId, PhotoCategory category, byte[] content) {
        accountStatusQuery.requireActive(accountId);
        GuestProfileEntity profile = requireOwnedProfile(accountId);
        requireEditable(profile);
        PhotoFileValidator.ImageInfo image = photoFileValidator.validate(content);
        requireCountAvailable(profile.getId(), category);

        String objectKey = photoKey(accountId, category, image.contentType());
        StoredObjectView stored = storageService.put(objectKey, content, image.contentType());
        OffsetDateTime now = OffsetDateTime.now();
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profile.getId());
        photo.setCategory(category);
        photo.setObjectKey(stored.objectKey());
        photo.setSha256(stored.sha256());
        photo.setSizeBytes(stored.sizeBytes());
        photo.setContentType(stored.contentType());
        photo.setWidth(image.width());
        photo.setHeight(image.height());
        photo.setSortOrder(photoMapper.selectMaxSortOrder(profile.getId(), category));
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        try {
            photoMapper.insert(photo);
        } catch (RuntimeException exception) {
            storageService.delete(objectKey);
            throw exception;
        }
        return toView(photo);
    }

    @Transactional(readOnly = true)
    public List<ProfilePhotoView> list(long accountId) {
        accountStatusQuery.requireActive(accountId);
        GuestProfileEntity profile = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        if (profile == null) {
            return List.of();
        }
        return photoMapper.selectList(Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId())
                        .orderByAsc(ProfilePhotoEntity::getCategory)
                        .orderByAsc(ProfilePhotoEntity::getSortOrder))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public void delete(long accountId, long photoId) {
        accountStatusQuery.requireActive(accountId);
        GuestProfileEntity profile = requireOwnedProfile(accountId);
        requireEditable(profile);
        ProfilePhotoEntity photo = photoMapper.selectOne(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getId, photoId)
                        .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId()));
        if (photo == null) {
            throw photoNotFound();
        }
        photoMapper.deleteById(photo.getId());
        if (photoMapper.countRevisionReferences(photo.getObjectKey()) == 0) {
            storageService.delete(photo.getObjectKey());
        }
    }

    private GuestProfileEntity requireOwnedProfile(long accountId) {
        GuestProfileEntity profile = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        if (profile == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROFILE_NOT_STARTED", "请先保存档案草稿");
        }
        return profile;
    }

    private static void requireEditable(GuestProfileEntity profile) {
        if (profile.getStatus() != ProfileStatus.DRAFT
                && profile.getStatus() != ProfileStatus.CHANGES_REQUESTED
                && profile.getStatus() != ProfileStatus.APPROVED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PHOTO_NOT_EDITABLE", "当前档案状态不允许修改照片");
        }
    }

    private void requireCountAvailable(long profileId, PhotoCategory category) {
        long existing = photoMapper.selectCount(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                        .eq(ProfilePhotoEntity::getCategory, category));
        if (category == PhotoCategory.AVATAR && existing >= 1) {
            throw countLimit();
        }
        if (category == PhotoCategory.LIFE && existing >= MAX_LIFE_PHOTOS) {
            throw countLimit();
        }
    }

    private static String photoKey(long accountId, PhotoCategory category, String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        return "profiles/" + accountId + "/"
                + category.name().toLowerCase() + "/" + UUID.randomUUID() + "." + extension;
    }

    private ProfilePhotoView toView(ProfilePhotoEntity photo) {
        return new ProfilePhotoView(
                photo.getId(),
                photo.getCategory().name(),
                photo.getSha256(),
                photo.getSizeBytes(),
                photo.getContentType(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getSortOrder(),
                storageService.signDownloadUrl(photo.getObjectKey(), URL_TTL),
                photo.getCreatedAt());
    }

    private static ApiException countLimit() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PHOTO_COUNT_LIMIT_EXCEEDED",
                "头像最多 1 张，生活照最多 6 张");
    }

    private static ApiException photoNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "照片不存在");
    }
}
```

`GuestProfilePhotoController.java`：

```java
package com.love.archive.guest.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.guest.application.ProfilePhotoService;
import com.love.archive.guest.application.ProfilePhotoView;
import com.love.archive.guest.domain.PhotoCategory;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/guest/profile/photos")
@RequiredArgsConstructor
public class GuestProfilePhotoController {

    private final ProfilePhotoService photoService;
    private final GuestAccountIdentity guestIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<ProfilePhotoView>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam PhotoCategory category,
            HttpServletRequest request) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PHOTO_CONTENT_INVALID", "文件内容不能为空");
        }
        ProfilePhotoView created = photoService.upload(
                guestIdentity.currentGuestAccountId(), category, content);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, RequestIdFilter.current(request)));
    }

    @GetMapping
    public ApiResponse<List<ProfilePhotoView>> list(HttpServletRequest request) {
        return ApiResponse.success(
                photoService.list(guestIdentity.currentGuestAccountId()),
                RequestIdFilter.current(request));
    }

    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> delete(
            @PathVariable long photoId,
            HttpServletRequest request) {
        photoService.delete(guestIdentity.currentGuestAccountId(), photoId);
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
```

`application.yml` 在 `spring:` 下新增：

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

`GlobalExceptionHandler` 增加三个处理器：

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
ResponseEntity<ApiResponse<Void>> handleUploadSize(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.failure(
            "PHOTO_TOO_LARGE", "单张照片不能超过 10 MiB", RequestIdFilter.current(request)));
}

@ExceptionHandler(MissingServletRequestPartException.class)
ResponseEntity<ApiResponse<Void>> handleMissingPart(HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ApiResponse.failure(
            "PHOTO_CONTENT_INVALID", "文件内容不能为空", RequestIdFilter.current(request)));
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
ResponseEntity<ApiResponse<Void>> handleTypeMismatch(HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ApiResponse.failure(
            "PHOTO_CATEGORY_INVALID", "照片类别不正确", RequestIdFilter.current(request)));
}
```

对应 import：`org.springframework.web.multipart.MaxUploadSizeExceededException`、`org.springframework.web.multipart.support.MissingServletRequestPartException`、`org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`。

`GuestProfilePhotoApiTest` 新增非法类别用例：

```java
@Test
void rejectsInvalidCategory() throws Exception {
    mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                    .file("file", pngBytes())
                    .param("category", "VIDEO")
                    .cookie(guestCookie))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PHOTO_CATEGORY_INVALID"));
}
```

`GuestProfilePhotoApiTest.java`（核心用例）：

```java
package com.love.archive.guest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.guest.application.GuestProfileDraftService;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.identity.application.GuestProvisioningService;
import com.love.archive.identity.security.PasswordHasher;
import com.love.archive.identity.web.ProvisionedGuestView;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import com.love.archive.testsupport.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GuestProfilePhotoApiTest extends ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private GuestProvisioningService provisioningService;
    @Autowired private GuestProfileDraftService draftService;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private ObjectStorageService storageService;

    private long adminId;
    private Cookie guestCookie;

    @BeforeEach
    void prepareAuthenticatedGuest() throws Exception {
        resetDatabase();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        adminId = insertAdmin("photo-api-admin", "photo-admin-2026");
        ProvisionedGuestView guest = provisioningService.provision(
                adminId, "13800138000", "PAY-PHOTO-API", 199_00L,
                OffsetDateTime.now().minusMinutes(5), "v0.3", null, "photo-api-provision");
        guestCookie = activateAndLogin(
                "13800138000", guest.initialCredential(), "Photo-password-2026");
        draftService.save(guest.accountId(), new SaveGuestProfileCommand(
                null, "男", java.time.LocalDate.of(1995, 5, 20), 178, "本科",
                "工程师", "20-30万", "杭州", "wx-photo-api", "dy-photo-api",
                "photo api nickname",
                java.net.URI.create("https://www.douyin.com/user/photo-api"),
                java.util.List.of()), "photo-api-draft");
        when(storageService.put(anyString(), any(byte[].class), anyString()))
                .thenReturn(new StoredObjectView(
                        "profiles/1/avatar/x.jpg", "loveplatform-1314980040",
                        10, "image/jpeg", "c".repeat(64)));
        when(storageService.signDownloadUrl(anyString(), any()))
                .thenReturn("https://loveplatform-1314980040.cos.ap-guangzhou.myqcloud.com/signed");
    }

    @Test
    void photoEndpointsRequireGuestLogin() throws Exception {
        mockMvc.perform(get("/api/v1/guest/profile/photos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsValidPngAndReturns201() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "AVATAR")
                        .cookie(guestCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("AVATAR"))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
    }

    @Test
    void rejectsFakeFormatAndOversizedFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", "not-an-image".getBytes())
                        .param("category", "AVATAR")
                        .cookie(guestCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_FORMAT_UNSUPPORTED"));

        mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", new byte[10 * 1024 * 1024 + 1])
                        .param("category", "AVATAR")
                        .cookie(guestCookie))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
    }

    @Test
    void deletesOwnedPhoto() throws Exception {
        MvcResult upload = mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                        .file("file", pngBytes())
                        .param("category", "LIFE")
                        .cookie(guestCookie))
                .andExpect(status().isCreated())
                .andReturn();
        long photoId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(upload.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asLong();

        mockMvc.perform(delete("/api/v1/guest/profile/photos/{photoId}", photoId)
                        .cookie(guestCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/guest/profile/photos").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private long insertAdmin(String username, String rawPassword) {
        char[] password = rawPassword.toCharArray();
        String hash;
        try {
            hash = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(username);
        admin.setDisplayName("Photo API Admin");
        admin.setPasswordHash(hash);
        admin.setStatus(AdminStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private Cookie activateAndLogin(String phone, String credential, String password)
            throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/guest/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","initialCredential":"%s","newPassword":"%s"}
                                """.formatted(phone, credential, password)))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/guest/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookies()[0];
    }
}
```

- [ ] **Step 4: 运行并见证通过**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfilePhotoServiceTest,GuestProfilePhotoApiTest,ModularityTest
```

Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
git add services/platform-api/src
git commit -m "feat: add guest profile photo draft endpoints"
```

---

### Task 4: 提交快照固化与详情可见

**Files:**
- Modify: `services/platform-api/src/main/java/com/love/archive/review/package-info.java`（`allowedDependencies` 增加 `"storage::application"`）
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileSnapshot.java`（新增 `photos`）
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/DatabaseGuestProfileSnapshotProvider.java`（加载照片、头像必填）
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java`（固化快照、详情照片）
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileRevisionView.java`（新增 `photos`）
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewService.java`（详情照片）
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewDetail.java`（新增 `photos`）
- Modify: 既有提交/审核测试（`ProfileSubmissionServiceTest`、`GuestProfileSubmissionApiTest`、`ProfileReviewServiceTest`、`AdminProfileReviewApiTest`）：提交前插入头像照片
- Test: 上述测试新增用例

**Interfaces:**
- Produces: `GuestProfileSnapshot.Photo(String category, String objectKey, String sha256, long sizeBytes, String contentType, int width, int height, int sortOrder)`
- Produces: `GuestProfileSnapshot.photos()` → `List<Photo>`
- Produces: `ProfileRevisionView.Photo(String category, String sha256, long sizeBytes, String contentType, int width, int height, int sortOrder, String downloadUrl)`
- Produces: `ProfileRevisionView.photos()` → `List<Photo>`
- Consumes: `ObjectStorageService.signDownloadUrl`（review 模块）

- [ ] **Step 1: 更新模块依赖、快照模型，并给既有测试补头像数据**

`review/package-info.java` 的 `allowedDependencies` 增加 `"storage::application"`。

`GuestProfileSnapshot` 增加组件与嵌套 record：

```java
public record GuestProfileSnapshot(
        long profileId,
        long accountId,
        long profileVersion,
        Long pendingRevisionId,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        byte[] wechatIdCiphertext,
        String wechatIdHmac,
        byte[] douyinIdCiphertext,
        String douyinIdHmac,
        byte[] douyinNicknameCiphertext,
        byte[] douyinProfileUrlCiphertext,
        List<FieldValue> dynamicFields,
        List<Photo> photos) {

    public GuestProfileSnapshot {
        // 现有防御性复制不变
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
    }

    public record Photo(
            String category,
            String objectKey,
            String sha256,
            long sizeBytes,
            String contentType,
            int width,
            int height,
            int sortOrder) {
    }
}
```

`DatabaseGuestProfileSnapshotProvider.lockAndValidate` 中，在构造快照前加载照片并校验头像：

```java
List<ProfilePhotoEntity> photos = photoMapper.selectList(
        Wrappers.<ProfilePhotoEntity>lambdaQuery()
                .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId())
                .orderByAsc(ProfilePhotoEntity::getCategory)
                .orderByAsc(ProfilePhotoEntity::getSortOrder));
boolean hasAvatar = photos.stream().anyMatch(
        photo -> photo.getCategory() == PhotoCategory.AVATAR);
if (!hasAvatar) {
    throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "PROFILE_VALIDATION_FAILED",
            "缺少必填字段: avatar");
}
```

并把 `photos` 映射进 `GuestProfileSnapshot` 构造（新增构造参数在最后）。

既有四个测试类在“保存草稿并提交”前插入头像（直接写 `profile_photo` 行，不依赖 COS）：

```java
private void insertAvatarPhoto(long profileId) {
    ProfilePhotoEntity photo = new ProfilePhotoEntity();
    photo.setGuestProfileId(profileId);
    photo.setCategory(PhotoCategory.AVATAR);
    photo.setObjectKey("profiles/" + profileId + "/avatar/fixture.jpg");
    photo.setSha256("e".repeat(64));
    photo.setSizeBytes(10L);
    photo.setContentType("image/jpeg");
    photo.setWidth(100);
    photo.setHeight(100);
    photo.setSortOrder(0);
    OffsetDateTime now = OffsetDateTime.now();
    photo.setCreatedAt(now);
    photo.setUpdatedAt(now);
    photoMapper.insert(photo);
}
```

对应测试类需要新增字段注入 `ProfilePhotoMapper photoMapper`，并在 `saveCompleteDraft()`/准备阶段调用。

`CanonicalSnapshotHasherTest` 构造 `GuestProfileSnapshot` 的调用需在末尾追加 `List.of()`（新 `photos` 组件）；`CanonicalSnapshotHasher` 把照片纳入幂等摘要（照片属于提交内容，键绑定必须覆盖），在动态字段循环之后追加：

```java
List<GuestProfileSnapshot.Photo> photos = snapshot.photos().stream()
        .sorted(Comparator.comparing(GuestProfileSnapshot.Photo::category)
                .thenComparing(GuestProfileSnapshot.Photo::sortOrder))
        .toList();
output.writeInt(photos.size());
for (GuestProfileSnapshot.Photo photo : photos) {
    writeEntry(output, "photo_category", photo.category());
    writeEntry(output, "photo_object_key", photo.objectKey());
    writeEntry(output, "photo_sha256", photo.sha256());
    writeEntry(output, "photo_size_bytes", photo.sizeBytes());
    writeEntry(output, "photo_content_type", photo.contentType());
    writeEntry(output, "photo_width", photo.width());
    writeEntry(output, "photo_height", photo.height());
    writeEntry(output, "photo_sort_order", photo.sortOrder());
}
```

- [ ] **Step 2: 写新增失败用例**

在 `ProfileSubmissionServiceTest` 增加：

```java
@Test
void rejectsSubmissionWithoutAvatarPhoto() {
    saveCompleteDraft();
    recordPaidAuthorization(authorizationDocumentId);
    recordConsent(authorizationDocumentId, NOW.plusDays(1));

    assertCode(() -> submissionService.submit(accountId, "submit-no-avatar", REQUEST_ID),
            "PROFILE_VALIDATION_FAILED");
}

@Test
void snapshotsCurrentPhotosIntoImmutableRevision() {
    saveCompleteDraft();
    insertAvatarPhoto(profile().getId());
    recordPaidAuthorization(authorizationDocumentId);
    recordConsent(authorizationDocumentId, NOW.plusDays(1));

    ProfileRevisionView revision = submissionService.submit(accountId, "submit-photo", REQUEST_ID);

    assertThat(revision.photos()).hasSize(1);
    assertThat(revision.photos().getFirst().category()).isEqualTo("AVATAR");
    assertThat(revisionPhotoMapper.selectCount(Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                    .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revision.id())))
            .isEqualTo(1);
    ProfileRevisionPhotoEntity snapshot = revisionPhotoMapper.selectOne(
            Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                    .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revision.id()));
    assertThat(snapshot.getObjectKey()).isEqualTo("profiles/" + profile().getId() + "/avatar/fixture.jpg");
}
```

在 `ProfileReviewServiceTest` 增加：

```java
@Test
void detailReturnsSnapshottedPhotos() {
    long revisionId = submitPending("review-photo-detail");
    ProfileReviewDetail detail = service.detail(revisionId);
    assertThat(detail.photos()).hasSize(1);
    assertThat(detail.photos().getFirst().category()).isEqualTo("AVATAR");
}
```

- [ ] **Step 3: 运行并见证失败**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfileSubmissionServiceTest,ProfileReviewServiceTest
```

Expected: 编译失败或 `缺少必填字段: avatar` 失败（快照模型尚未实现）。

- [ ] **Step 4: 实现快照固化与详情照片**

`ProfileSubmissionService` 在 `insertSnapshotMarkPendingAndAudit` 的动态字段插入循环之后追加：

```java
for (GuestProfileSnapshot.Photo photo : snapshot.photos()) {
    ProfileRevisionPhotoEntity stored = new ProfileRevisionPhotoEntity();
    stored.setProfileRevisionId(revision.getId());
    stored.setCategory(PhotoCategory.valueOf(photo.category()));
    stored.setObjectKey(photo.objectKey());
    stored.setSha256(photo.sha256());
    stored.setSizeBytes(photo.sizeBytes());
    stored.setContentType(photo.contentType());
    stored.setWidth(photo.width());
    stored.setHeight(photo.height());
    stored.setSortOrder(photo.sortOrder());
    stored.setCreatedAt(submittedAt);
    revisionPhotoMapper.insert(stored);
}
```

`ProfileRevisionView` 完整代码：

```java
package com.love.archive.review.application;

import com.love.archive.review.domain.RevisionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ProfileRevisionView(
        long id,
        int revisionNumber,
        RevisionStatus status,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewDeadlineAt,
        OffsetDateTime reviewedAt,
        long version,
        List<FieldValue> dynamicFields,
        List<Photo> photos) {

    public ProfileRevisionView {
        dynamicFields = List.copyOf(dynamicFields);
        photos = List.copyOf(photos);
    }

    public record FieldValue(
            String fieldCode,
            String fieldLabel,
            String dataType,
            String displayOption,
            String textValue,
            Long integerValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            Boolean booleanValue,
            String optionValue) {
    }

    public record Photo(
            String category,
            String sha256,
            long sizeBytes,
            String contentType,
            int width,
            int height,
            int sortOrder,
            String downloadUrl) {
    }
}
```

`ProfileSubmissionService.toView` 加载照片并签名：

```java
List<ProfileRevisionView.Photo> photos = revisionPhotoMapper.selectList(
                Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                        .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revision.getId())
                        .orderByAsc(ProfileRevisionPhotoEntity::getCategory)
                        .orderByAsc(ProfileRevisionPhotoEntity::getSortOrder))
        .stream()
        .map(photo -> new ProfileRevisionView.Photo(
                photo.getCategory().name(),
                photo.getSha256(),
                photo.getSizeBytes(),
                photo.getContentType(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getSortOrder(),
                storageService.signDownloadUrl(photo.getObjectKey(), Duration.ofMinutes(15))))
        .toList();
return new ProfileRevisionView(
        revision.getId(), revision.getRevisionNumber(), revision.getStatus(),
        revision.getGender(), revision.getBirthDate(), revision.getHeightCm(),
        revision.getEducation(), revision.getOccupation(), revision.getIncomeRange(),
        revision.getCity(), revision.getSubmittedAt(), revision.getReviewDeadlineAt(),
        revision.getReviewedAt(), revision.getVersion(), fields, photos);
```

`ProfileRevisionView` 新增 import：`com.love.archive.review.persistence.ProfileRevisionPhotoEntity`、`com.love.archive.review.persistence.ProfileRevisionPhotoMapper`、`com.love.archive.storage.application.ObjectStorageService`、`java.time.Duration`。

`ProfileReviewService.detail` 加载该版本的照片行，签名后放入 `ProfileReviewDetail.photos`：

```java
List<ProfileRevisionView.Photo> photos = revisionPhotoMapper.selectList(
                Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                        .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revisionId)
                        .orderByAsc(ProfileRevisionPhotoEntity::getCategory)
                        .orderByAsc(ProfileRevisionPhotoEntity::getSortOrder))
        .stream()
        .map(photo -> new ProfileRevisionView.Photo(
                photo.getCategory().name(),
                photo.getSha256(),
                photo.getSizeBytes(),
                photo.getContentType(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getSortOrder(),
                storageService.signDownloadUrl(photo.getObjectKey(), Duration.ofMinutes(15))))
        .toList();
```

`ProfileReviewDetail` 增加 `List<ProfileRevisionView.Photo> photos` 组件（放在 `differences` 之前），`toDetail` 传入该列表并在构造器中 `List.copyOf`。

依赖注入：`ProfileSubmissionService` 与 `ProfileReviewService` 各新增 `ObjectStorageService` 与 `ProfileRevisionPhotoMapper` 字段（`@RequiredArgsConstructor` 自动注入）。

`ProfileReviewServiceTest.submitPending` 与 `ProfileSubmissionServiceTest` 的 `saveCompleteDraft()` 一样需要先插入头像照片，否则提交在 `lockAndValidate` 阶段即失败。

- [ ] **Step 5: 运行并见证通过**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload/services/platform-api
../../mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  test -Dtest=ProfileSubmissionServiceTest,ProfileReviewServiceTest,GuestProfileSubmissionApiTest,AdminProfileReviewApiTest,ModularityTest
```

Expected: 全部通过。

- [ ] **Step 6: 提交**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
git add services/platform-api/src
git commit -m "feat: snapshot profile photos into immutable revisions"
```

---

### Task 5: 守卫、文档与全量回归

**Files:**
- Modify: `services/platform-api/src/test/java/com/love/archive/architecture/SensitiveDataGuardTest.java`（日志模式加入 `objectKey`/`object_key`）
- Modify: `README.md`（照片接口与限制）
- Modify: `.env.example` 无需变更（COS 已存在）；`compose.yaml` 无需变更

- [ ] **Step 1: 扩展敏感日志守卫**

`SensitiveDataGuardTest.productionLoggingDoesNotReferenceSensitiveRequestFields` 的正则追加 `|objectKey|object_key`：

```java
.doesNotContainPattern("(?i)LOGGER\\.(trace|debug|info|warn|error)\\("
        + "[^;]*(password|phone|credential|openid|unionid"
        + "|wechat|douyin|clientIp|sessionReference|objectKey|object_key)");
```

先运行一次见证守卫通过（当前生产代码已无此类日志）。

- [ ] **Step 2: 更新 README**

在“对象存储（腾讯云 COS）”一节补充：

```markdown
## 照片上传

- 嘉宾接口：`POST /api/v1/guest/profile/photos`（multipart：`file` + `category`，类别 `AVATAR`/`LIFE`）、`GET /api/v1/guest/profile/photos`、`DELETE /api/v1/guest/profile/photos/{photoId}`。
- 限制：头像 1 张、生活照最多 6 张；单张 ≤ 10 MiB；仅 JPEG/PNG/WebP；服务端校验真实格式与最小 64×64 尺寸。
- 提交建档时头像必填；照片随草稿保存、随提交固化进不可变版本快照；审核详情与嘉宾版本详情返回照片元数据与 15 分钟短时签名 URL。
- 草稿删除立即删除 COS 对象；进入版本快照后对象保留用于历史追溯。
```

- [ ] **Step 3: 全量验证**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
export JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_USER_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/maven-home
./mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  -pl services/platform-api test
./mvnw -o -Dmaven.repo.local=/Users/alex/Documents/ChatGPT/love/.toolchains/m2 \
  -pl services/platform-api package -DskipTests
git diff --check
git status --short
```

Expected: 全量测试通过（含 1 个默认跳过的 `CosLiveSmokeTest`）、JAR 构建成功、无空白错误、工作区仅预期变更。

- [ ] **Step 4: 提交**

```bash
cd /Users/alex/Documents/ChatGPT/love/.worktrees/photo-upload
git add README.md services/platform-api/src
git commit -m "docs: verify profile photo upload slice"
```

记录最终测试数、包结果与后续任务（前端两个子项目）于提交说明。

package com.love.archive.course.application;

import org.springframework.stereotype.Component;

/**
 * 课程插图与封面的格式识别。
 *
 * <p>只认魔数，**不信客户端声明的 Content-Type**——那个字段由浏览器（或伪造请求的人）
 * 随便填，拿它当真等于让任何文件都能以 {@code image/png} 的名义存进桶里，
 * 之后被当成图片下发给用户。</p>
 *
 * <p>与访客照片的 {@code PhotoFileValidator} 是两份实现：那个在 guest 模块，
 * course 不依赖 guest（也不该依赖）。它还额外要求最小 64×64 并解码出宽高，
 * 那是头像的需求；课程插图不限尺寸，所以这里只做格式识别。</p>
 */
@Component
public final class CourseImageValidator {

    /** 与 {@code ObjectStorageService.put} 的上限一致。 */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** 返回规范化后的 Content-Type。 */
    public String validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw CourseErrors.imageInvalid();
        }
        if (content.length > MAX_BYTES) {
            throw CourseErrors.imageTooLarge();
        }
        String contentType = sniff(content);
        if (contentType == null) {
            throw CourseErrors.imageTypeUnsupported();
        }
        return contentType;
    }

    private static String sniff(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}

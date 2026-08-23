package com.love.archive.course.application;

import com.love.archive.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 课程模块的错误码集中在这里。
 *
 * <p>散落在各个 service 里 {@code new ApiException(...)} 时，同一个语义很容易写出两个码，
 * 前端就得同时认两个。集中之后「有哪些错误码」一眼能数清。</p>
 */
final class CourseErrors {

    private CourseErrors() {
    }

    static ApiException vipRequired() {
        return new ApiException(
                HttpStatus.FORBIDDEN, "COURSE_VIP_REQUIRED", "这节课需要会员才能观看");
    }

    /**
     * 访客侧「不存在」与「未发布」故意用同一个码：分开会让人能靠状态码探测
     * 哪些草稿 id 是存在的，等于泄露还没上架的排期。
     */
    static ApiException courseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在");
    }

    static ApiException collectionNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_COLLECTION_NOT_FOUND", "合集不存在");
    }

    static ApiException collectionNameDuplicate() {
        return new ApiException(
                HttpStatus.CONFLICT, "COURSE_COLLECTION_NAME_DUPLICATE", "已有同名合集");
    }

    static ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT, "COURSE_VERSION_CONFLICT", "课程已被他人修改，请刷新后重试");
    }

    static ApiException contentRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_CONTENT_REQUIRED", "图文与纯文本课程必须填写正文");
    }

    static ApiException videoRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_VIDEO_REQUIRED", "视频课程必须先上传视频");
    }

    static ApiException markdownTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, "COURSE_MARKDOWN_TOO_LARGE", "Markdown 文件不能超过 1 MiB");
    }

    static ApiException markdownInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_MARKDOWN_INVALID", "Markdown 文件必须是 UTF-8 文本");
    }

    static ApiException uploadNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "COURSE_UPLOAD_NOT_FOUND", "上传会话不存在或已结束");
    }

    static ApiException uploadPartInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "COURSE_UPLOAD_PART_INVALID", message);
    }

    static ApiException videoTypeUnsupported() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_VIDEO_TYPE_UNSUPPORTED", "只支持 MP4、MOV 与 WebM 视频");
    }

    static ApiException imageTypeUnsupported() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_IMAGE_TYPE_UNSUPPORTED", "只支持 JPEG、PNG 与 WebP 图片");
    }

    static ApiException imageInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "COURSE_IMAGE_INVALID", "图片内容不能为空");
    }

    static ApiException imageTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, "COURSE_IMAGE_TOO_LARGE", "单张图片不能超过 10 MiB");
    }

    static ApiException videoTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, "COURSE_VIDEO_TOO_LARGE", "视频不能超过 2 GiB");
    }
}

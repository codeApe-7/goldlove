package com.love.archive.course.application;

/** 一块传完的回执。浏览器要攒齐全部 etag 才能 complete。 */
public record VideoPartView(int partNumber, String etag) {
}

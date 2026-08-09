package com.love.archive.review.application;

public record ProfileFieldDifference(
        String fieldCode,
        String fieldLabel,
        String oldValue,
        String newValue) {
}

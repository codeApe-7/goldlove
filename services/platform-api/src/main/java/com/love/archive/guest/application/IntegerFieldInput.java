package com.love.archive.guest.application;

public record IntegerFieldInput(String fieldCode, Long value) implements ProfileFieldInput {
}

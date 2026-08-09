package com.love.archive.guest.application;

import java.time.LocalDate;

public record DateFieldInput(String fieldCode, LocalDate value) implements ProfileFieldInput {
}

package com.love.archive.guest.application;

import java.math.BigDecimal;

public record DecimalFieldInput(String fieldCode, BigDecimal value) implements ProfileFieldInput {
}

package com.love.archive.guest.application;

public sealed interface ProfileFieldInput permits TextFieldInput, IntegerFieldInput,
        DecimalFieldInput, DateFieldInput, BooleanFieldInput, OptionFieldInput {

    String fieldCode();
}

package com.love.archive.guest.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.guest.application.BooleanFieldInput;
import com.love.archive.guest.application.DateFieldInput;
import com.love.archive.guest.application.DecimalFieldInput;
import com.love.archive.guest.application.IntegerFieldInput;
import com.love.archive.guest.application.OptionFieldInput;
import com.love.archive.guest.application.ProfileFieldInput;
import com.love.archive.guest.application.ProfilePhotoTarget;
import com.love.archive.guest.application.SaveGuestProfileCommand;
import com.love.archive.guest.application.TextFieldInput;
import com.love.archive.guest.domain.ProfileFieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;

public record SaveGuestProfileRequest(
        Long expectedVersion,
        @Size(max = 32) String gender,
        Integer age,
        Integer heightCm,
        @Size(max = 100) String education,
        @Size(max = 200) String occupation,
        @Size(max = 100) String incomeRange,
        @Size(max = 100) String city,
        @Size(max = 200) String wechatId,
        @Size(max = 200) String douyinId,
        List<@Valid DynamicFieldRequest> dynamicFields,
        PhotoCollectionRequest photos) {

    public SaveGuestProfileCommand toCommand() {
        List<ProfileFieldInput> fields = dynamicFields == null
                ? List.of()
                : dynamicFields.stream().map(DynamicFieldRequest::toInput).toList();
        return new SaveGuestProfileCommand(
                expectedVersion, gender, age, heightCm, education, occupation,
                incomeRange, city, wechatId, douyinId, fields,
                photos == null ? ProfilePhotoTarget.empty() : photos.toTarget());
    }

    public record PhotoCollectionRequest(
            @Size(max = 1024) String avatar,
            @Size(max = 3) List<@Size(max = 1024) String> life) {

        ProfilePhotoTarget toTarget() {
            return new ProfilePhotoTarget(avatar, life);
        }
    }

    public record DynamicFieldRequest(
            @NotBlank @Size(max = 64) String fieldCode,
            @NotNull ProfileFieldType dataType,
            String textValue,
            Long integerValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            Boolean booleanValue,
            String optionValue) {

        ProfileFieldInput toInput() {
            int populated = countPopulated();
            if (populated != 1) {
                throw invalidValue();
            }
            return switch (dataType) {
                case TEXT, LONG_TEXT -> {
                    if (textValue == null) {
                        throw invalidValue();
                    }
                    yield new TextFieldInput(fieldCode, textValue);
                }
                case INTEGER -> {
                    if (integerValue == null) {
                        throw invalidValue();
                    }
                    yield new IntegerFieldInput(fieldCode, integerValue);
                }
                case DECIMAL -> {
                    if (decimalValue == null) {
                        throw invalidValue();
                    }
                    yield new DecimalFieldInput(fieldCode, decimalValue);
                }
                case DATE -> {
                    if (dateValue == null) {
                        throw invalidValue();
                    }
                    yield new DateFieldInput(fieldCode, dateValue);
                }
                case BOOLEAN -> {
                    if (booleanValue == null) {
                        throw invalidValue();
                    }
                    yield new BooleanFieldInput(fieldCode, booleanValue);
                }
                case SINGLE_OPTION -> {
                    if (optionValue == null) {
                        throw invalidValue();
                    }
                    yield new OptionFieldInput(fieldCode, optionValue);
                }
            };
        }

        private int countPopulated() {
            int count = 0;
            count += textValue == null ? 0 : 1;
            count += integerValue == null ? 0 : 1;
            count += decimalValue == null ? 0 : 1;
            count += dateValue == null ? 0 : 1;
            count += booleanValue == null ? 0 : 1;
            count += optionValue == null ? 0 : 1;
            return count;
        }

        private static ApiException invalidValue() {
            return new ApiException(HttpStatus.BAD_REQUEST,
                    "FIELD_VALUE_INVALID", "动态字段必须提供且只能提供一个类型值");
        }
    }
}

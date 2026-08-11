package com.love.archive.guest.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.guest.application.ProfilePhotoService;
import com.love.archive.guest.application.ProfilePhotoView;
import com.love.archive.guest.application.StagedPhotoView;
import com.love.archive.guest.domain.PhotoCategory;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/guest/profile")
@RequiredArgsConstructor
public class GuestProfilePhotoController {

    private final ProfilePhotoService photoService;
    private final GuestAccountIdentity guestIdentity;

    @PostMapping("/photo-uploads")
    public ApiResponse<StagedPhotoView> upload(
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
        StagedPhotoView staged = photoService.upload(
                guestIdentity.currentGuestAccountId(), category, content);
        return ApiResponse.success(staged, RequestIdFilter.current(request));
    }

    @GetMapping("/photos")
    public ApiResponse<List<ProfilePhotoView>> list(HttpServletRequest request) {
        return ApiResponse.success(
                photoService.list(guestIdentity.currentGuestAccountId()),
                RequestIdFilter.current(request));
    }

}

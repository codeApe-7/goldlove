package com.love.archive.guest.web;

import com.love.archive.common.security.GuestAccountIdentity;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.ApiResponse;
import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.guest.application.ProfilePhotoService;
import com.love.archive.guest.application.ProfilePhotoView;
import com.love.archive.guest.domain.PhotoCategory;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/guest/profile/photos")
@RequiredArgsConstructor
public class GuestProfilePhotoController {

    private final ProfilePhotoService photoService;
    private final GuestAccountIdentity guestIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<ProfilePhotoView>> upload(
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
        ProfilePhotoView created = photoService.upload(
                guestIdentity.currentGuestAccountId(), category, content);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, RequestIdFilter.current(request)));
    }

    @GetMapping
    public ApiResponse<List<ProfilePhotoView>> list(HttpServletRequest request) {
        return ApiResponse.success(
                photoService.list(guestIdentity.currentGuestAccountId()),
                RequestIdFilter.current(request));
    }

    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> delete(
            @PathVariable long photoId,
            HttpServletRequest request) {
        photoService.delete(guestIdentity.currentGuestAccountId(), photoId);
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}

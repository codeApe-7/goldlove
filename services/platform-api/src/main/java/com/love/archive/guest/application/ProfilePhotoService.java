package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

    private static final int MAX_LIFE_PHOTOS = 6;
    private static final Duration URL_TTL = Duration.ofMinutes(15);

    private final GuestProfileMapper profileMapper;
    private final ProfilePhotoMapper photoMapper;
    private final ObjectStorageService storageService;
    private final PhotoFileValidator photoFileValidator;

    @Transactional
    public ProfilePhotoView upload(long accountId, PhotoCategory category, byte[] content) {
        GuestProfileEntity profile = requireOwnedProfile(accountId);
        requireEditable(profile);
        PhotoFileValidator.ImageInfo image = photoFileValidator.validate(content);
        if (category == PhotoCategory.AVATAR) {
            replaceExistingAvatar(profile.getId());
        } else {
            requireLifeCountAvailable(profile.getId());
        }

        String objectKey = photoKey(accountId, category, image.contentType());
        StoredObjectView stored = storageService.put(objectKey, content, image.contentType());
        OffsetDateTime now = OffsetDateTime.now();
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profile.getId());
        photo.setCategory(category);
        photo.setObjectKey(stored.objectKey());
        photo.setSortOrder(photoMapper.selectMaxSortOrder(profile.getId(), category));
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        try {
            photoMapper.insert(photo);
        } catch (RuntimeException exception) {
            storageService.delete(objectKey);
            throw exception;
        }
        return toView(photo);
    }

    private void replaceExistingAvatar(long profileId) {
        List<ProfilePhotoEntity> existing = photoMapper.selectList(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                        .eq(ProfilePhotoEntity::getCategory, PhotoCategory.AVATAR));
        for (ProfilePhotoEntity photo : existing) {
            photoMapper.deleteById(photo.getId());
            if (photoMapper.countRevisionReferences(photo.getObjectKey()) == 0) {
                storageService.delete(photo.getObjectKey());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProfilePhotoView> list(long accountId) {
        GuestProfileEntity profile = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        if (profile == null) {
            return List.of();
        }
        return photoMapper.selectList(Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId())
                        .orderByAsc(ProfilePhotoEntity::getCategory)
                        .orderByAsc(ProfilePhotoEntity::getSortOrder))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public void delete(long accountId, long photoId) {
        GuestProfileEntity profile = requireOwnedProfile(accountId);
        requireEditable(profile);
        ProfilePhotoEntity photo = photoMapper.selectOne(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getId, photoId)
                        .eq(ProfilePhotoEntity::getGuestProfileId, profile.getId()));
        if (photo == null) {
            throw photoNotFound();
        }
        photoMapper.deleteById(photo.getId());
        if (photoMapper.countRevisionReferences(photo.getObjectKey()) == 0) {
            storageService.delete(photo.getObjectKey());
        }
    }

    private GuestProfileEntity requireOwnedProfile(long accountId) {
        GuestProfileEntity profile = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        if (profile == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROFILE_NOT_STARTED", "请先保存档案草稿");
        }
        return profile;
    }

    private static void requireEditable(GuestProfileEntity profile) {
        if (profile.getStatus() != ProfileStatus.DRAFT
                && profile.getStatus() != ProfileStatus.CHANGES_REQUESTED
                && profile.getStatus() != ProfileStatus.APPROVED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PHOTO_NOT_EDITABLE", "当前档案状态不允许修改照片");
        }
    }

    private void requireLifeCountAvailable(long profileId) {
        long existing = photoMapper.selectCount(
                Wrappers.<ProfilePhotoEntity>lambdaQuery()
                        .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                        .eq(ProfilePhotoEntity::getCategory, PhotoCategory.LIFE));
        if (existing >= MAX_LIFE_PHOTOS) {
            throw countLimit();
        }
    }

    private static String photoKey(long accountId, PhotoCategory category, String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        return "profiles/" + accountId + "/"
                + category.name().toLowerCase() + "/" + UUID.randomUUID() + "." + extension;
    }

    private ProfilePhotoView toView(ProfilePhotoEntity photo) {
        return new ProfilePhotoView(
                photo.getId(),
                photo.getCategory().name(),
                photo.getObjectKey(),
                photo.getSortOrder(),
                storageService.signDownloadUrl(photo.getObjectKey(), URL_TTL),
                photo.getCreatedAt());
    }

    private static ApiException countLimit() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PHOTO_COUNT_LIMIT_EXCEEDED",
                "头像最多 1 张，生活照最多 6 张");
    }

    private static ApiException photoNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "照片不存在");
    }
}

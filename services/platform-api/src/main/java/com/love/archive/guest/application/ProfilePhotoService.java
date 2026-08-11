package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfilePhotoEntity;
import com.love.archive.guest.persistence.ProfilePhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import com.love.archive.storage.application.StoredObjectView;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

    private static final Duration URL_TTL = Duration.ofMinutes(15);

    private final GuestProfileMapper profileMapper;
    private final ProfilePhotoMapper photoMapper;
    private final ObjectStorageService storageService;
    private final PhotoFileValidator photoFileValidator;

    public StagedPhotoView upload(long accountId, PhotoCategory category, byte[] content) {
        PhotoFileValidator.ImageInfo image = photoFileValidator.validate(content);
        String objectKey = photoKey(accountId, category, image.contentType());
        StoredObjectView stored = storageService.put(objectKey, content, image.contentType());
        return new StagedPhotoView(
                stored.objectKey(),
                category.name(),
                storageService.signDownloadUrl(stored.objectKey(), URL_TTL));
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

}

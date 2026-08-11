package com.love.archive.guest.application;

import java.util.List;

public record ProfilePhotoTarget(String avatar, List<String> life) {

    public ProfilePhotoTarget {
        life = life == null ? List.of() : List.copyOf(life);
    }

    public static ProfilePhotoTarget empty() {
        return new ProfilePhotoTarget(null, List.of());
    }
}

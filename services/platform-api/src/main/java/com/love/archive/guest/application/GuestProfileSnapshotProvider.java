package com.love.archive.guest.application;

public interface GuestProfileSnapshotProvider {

    GuestProfileSnapshot lockAndValidate(long accountId);
}

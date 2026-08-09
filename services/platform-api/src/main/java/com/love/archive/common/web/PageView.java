package com.love.archive.common.web;

import java.util.List;

public record PageView<T>(List<T> items, long page, long size, long total) {

    public PageView {
        items = List.copyOf(items);
    }
}

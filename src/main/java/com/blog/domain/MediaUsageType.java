package com.blog.domain;

public enum MediaUsageType {
    ARTICLE("article"),
    COVER("cover"),
    PROFILE("profile"),
    PROJECT("project"),
    OTHER("other");

    private final String path;

    MediaUsageType(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}

package com.summerbuddies.custommissions.mission;

/** The three kinds of mission. Daily missions are AI-authored and expire; the rest are authored chains. */
public enum MissionCategory {
    DAILY,
    PRIMARY,
    SECONDARY;

    /** Parse forgivingly; defaults to {@link #SECONDARY} for unknown/blank values. */
    public static MissionCategory parse(String raw) {
        if (raw == null) {
            return SECONDARY;
        }
        return switch (raw.trim().toLowerCase()) {
            case "daily" -> DAILY;
            case "primary" -> PRIMARY;
            default -> SECONDARY;
        };
    }

    public String id() {
        return name().toLowerCase();
    }
}

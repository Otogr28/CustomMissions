package com.summerbuddies.custommissions.mission.json;

/**
 * Flat JSON bag for one objective — every possible field for every type (StoryKit's ActionDto style).
 * {@code ObjectiveFactory} reads only the fields relevant to {@code type} and applies defaults.
 */
public final class ObjectiveDto {
    public String type;
    public String description;
    public Boolean optional;
    public Integer count;

    // kill_entity (entity id, or "#namespace:tag")
    public String entity;

    // collect_item / deliver_item_to_npc
    public String item;

    // reach_location
    public String dimension;
    public Integer x;
    public Integer y;
    public Integer z;
    public Integer radius;
    public String waypoint;
    public String waypointColor;

    // talk_to_npc / deliver_item_to_npc
    public String npcUuid;
    public String npcName;

    // advancement
    public String advancement;

    // use_block / place_block
    public String block;

    // custom_signal
    public String signal;
}

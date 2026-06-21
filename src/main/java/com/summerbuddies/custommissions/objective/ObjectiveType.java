package com.summerbuddies.custommissions.objective;

/** The kinds of objective the mission DSL supports. Each maps to one {@code MissionEvent} matcher. */
public enum ObjectiveType {
    KILL_ENTITY,
    COLLECT_ITEM,
    REACH_LOCATION,
    TALK_TO_NPC,
    ENTER_DIMENSION,
    ADVANCEMENT,
    USE_BLOCK,
    PLACE_BLOCK,
    DELIVER_ITEM_TO_NPC,
    CUSTOM_SIGNAL
}

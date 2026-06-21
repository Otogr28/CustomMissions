package com.summerbuddies.custommissions.mission.json;

/**
 * Flat JSON bag for one reward (also used for on-accept / on-complete hooks). {@code RewardFactory} reads
 * only the fields relevant to {@code type}.
 */
public final class RewardDto {
    public String type;

    // item
    public String item;
    public Integer count;

    // xp
    public Integer amount;

    // command
    public String command;
    public Boolean asPlayer;

    // cutscene
    public String script;

    // unlock
    public String gate;

    // companion
    public String companion;

    // lore_stage_advance (absent => +1)
    public Integer to;
}

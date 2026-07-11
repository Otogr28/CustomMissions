package com.summerbuddies.custommissions.mission.json;

import java.util.List;

/** Top-level JSON shape of a mission file. Forgiving: unknown fields ignored, missing fields defaulted. */
public final class MissionDto {
    public String id;
    public String category;       // daily | primary | secondary
    public String title;
    public String description;
    public String lore;
    public GiverDto giver;
    public PrerequisitesDto prerequisites;
    public List<ObjectiveDto> objectives;
    public List<RewardDto> rewards;
    public LocationDto location;
    public Integer expiryHours;   // daily missions only
    public List<RewardDto> onAccept;
    public List<RewardDto> onComplete;
    public List<String> assignTo;   // optional explicit player names
    public Boolean autoAccept;      // if true, auto-assign to a player as soon as prerequisites are met
}

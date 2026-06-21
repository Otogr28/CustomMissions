package com.summerbuddies.custommissions.mission.json;

import java.util.List;

/** JSON shape of mission prerequisites. All fields optional (absent = no constraint). */
public final class PrerequisitesDto {
    public Integer loreStage;
    public List<String> flags;
    public List<String> priorMissions;
    public String dimension;
}

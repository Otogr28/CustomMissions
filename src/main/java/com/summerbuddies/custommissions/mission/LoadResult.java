package com.summerbuddies.custommissions.mission;

import com.summerbuddies.custommissions.Constants;

import java.util.ArrayList;
import java.util.List;

/** Outcome of a {@code MissionManager} load: counts plus human-readable warning lines. */
public final class LoadResult {

    public int missions;
    public int dailies;
    public int objectives;
    public final List<String> warnings = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
        Constants.LOG.warn("[custommissions] {}", message);
    }

    public String summary() {
        return "loaded " + missions + " mission(s) (" + dailies + " daily), " + objectives + " objective(s)"
                + (warnings.isEmpty() ? "" : " — " + warnings.size() + " warning(s)");
    }
}

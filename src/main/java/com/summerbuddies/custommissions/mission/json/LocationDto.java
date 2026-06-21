package com.summerbuddies.custommissions.mission.json;

/** JSON shape of a location / waypoint. {@code dimension} and coordinates required to be useful. */
public final class LocationDto {
    public String dimension;
    public Integer x;
    public Integer y;
    public Integer z;
    public Integer radius;
    public String waypoint;       // display name on the marker
    public String waypointColor;  // "#RRGGBB"
}

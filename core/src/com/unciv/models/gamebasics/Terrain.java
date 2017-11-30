package com.unciv.models.gamebasics;

import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

import java.util.Collection;

public class Terrain extends NamedStats implements ICivilopedia {
    public String Type; // baseTerrain or terrainFeature

    /// <summary>
/// For base terrain - comma-delimited 256 RGB values, e.g. "116,88,62"
/// </summary>
    public String RGB;
//
//public Color Color
//        {
//        get
//        {
//        var rgbStringValues = RGB.Split(',');
//        var rgbs = rgbStringValues.Select(x => int.Parse(x)).ToArray();
//        return Extensions.ColorFrom256RGB(rgbs[0], rgbs[1], rgbs[2]);
//        }
//        }

    public boolean OverrideStats = false;

    /// <summary>
/// If true, other terrain layers can come over this one. For mountains, lakes etc. this is false
/// </summary>
    public boolean CanHaveOverlay = true;

    /// <summary>
/// If true, nothing can be built here - not even resource improvements
/// </summary>
    public boolean Unbuildable = false;

    /// <summary>
/// For terrain features
/// </summary>
    public Collection<String> OccursOn;

    /// <summary>
/// For terrain features - which technology alllows removal of this feature
/// </summary>
    public String RemovalTech;

    @Override
    public String GetDescription() {
        return ""+new FullStats(this);
    }
}




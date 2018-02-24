package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;

import java.util.Collection;

public class Terrain extends NamedStats implements ICivilopedia {
    public TerrainType type; // BaseTerrain or TerrainFeature

    public boolean overrideStats = false;

    /***
     * If true, other terrain layers can come over this one. For mountains, lakes etc. this is false
     */

    public boolean canHaveOverlay = true;

    /***
     *If true, nothing can be built here - not even resource improvements
     */
    public boolean unbuildable = false;

    /***
     * For terrain features
     */
    public Collection<String> occursOn;

    /***
     *For terrain features - which technology alllows removal of this feature
     */
    public String removalTech;
    public int movementCost=1;

    public String rgb;

    @Override
    public String getDescription() {
        return ""+this.clone();
    }
}




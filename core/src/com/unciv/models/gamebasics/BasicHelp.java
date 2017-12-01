package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;

public class BasicHelp extends NamedStats implements ICivilopedia {
    public String description;

    @Override
    public String getDescription() {
        return description;
    }
}

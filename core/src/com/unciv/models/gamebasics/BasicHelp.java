package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;

public class BasicHelp extends NamedStats implements ICivilopedia {
    public String Description;

    @Override
    public String GetDescription() {
        return Description;
    }
}

package com.unciv.models.gamebasics;

import com.unciv.models.stats.INamed;

public class BasicHelp implements ICivilopedia, INamed {
    public String description;
    public String name;

    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }
}

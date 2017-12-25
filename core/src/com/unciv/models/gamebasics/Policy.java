package com.unciv.models.gamebasics;

import com.unciv.models.LinqCollection;
import com.unciv.models.stats.INamed;

/**
 * Created by LENOVO on 12/22/2017.
 */

public class Policy implements INamed{
    public String name;
    public String description;
    public String branch;
    public int row;
    public int column;
    public LinqCollection<String> requires;

    @Override
    public String getName() {
        return name;
    }
}


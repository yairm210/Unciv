package com.unciv.models.gamebasics;

import java.util.Collection;

public class StringUtils {
    public static String join(String delimiter, Collection<String> collection) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String str : collection) {
            if (stringBuilder.length() != 0) stringBuilder.append(delimiter);
            stringBuilder.append(str);
        }
        return stringBuilder.toString();
    }
}

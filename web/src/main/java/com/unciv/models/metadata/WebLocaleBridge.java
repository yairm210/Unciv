package com.unciv.models.metadata;

import org.teavm.jso.JSBody;

public final class WebLocaleBridge {
    private WebLocaleBridge() {
    }

    @JSBody(
            params = {"languageTag", "first", "second"},
            script =
                    "if (first == null && second == null) return 0;\n"
                            + "if (first == null) return -1;\n"
                            + "if (second == null) return 1;\n"
                            + "var cache = (typeof globalThis !== 'undefined' ? globalThis : window);\n"
                            + "cache = cache.__uncivLocaleCollators || (cache.__uncivLocaleCollators = {});\n"
                            + "var key = languageTag || '';\n"
                            + "var options = { usage: 'sort', sensitivity: 'accent' };\n"
                            + "var collator = cache[key];\n"
                            + "if (!collator) {\n"
                            + "  collator = (typeof Intl !== 'undefined' && Intl.Collator)\n"
                            + "    ? new Intl.Collator(languageTag || undefined, options)\n"
                            + "    : null;\n"
                            + "  cache[key] = collator || false;\n"
                            + "}\n"
                            + "if (collator && collator.compare) return collator.compare(first, second);\n"
                            + "return String(first).localeCompare(String(second), languageTag || undefined, options);")
    public static int compare(String languageTag, String first, String second) {
        return 0;
    }
}

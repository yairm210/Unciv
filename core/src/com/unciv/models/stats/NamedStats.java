package com.unciv.models.stats;


public class NamedStats extends FullStats implements INamed {
        public String name;

        public String getName() {
                return name;
        }

        public String toString() {
                return name;
        }
}

package com.example.teamstatus;

import java.util.Random;

public class Util {
    private static final Random random = new Random();

    public static Random getRandom() {
        return random;
    }

    public static void setRandomSeed(long seed) {
        random.setSeed(seed);
    }
}
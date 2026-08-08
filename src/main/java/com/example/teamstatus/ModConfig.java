package com.example.teamstatus;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_SELF;
    public static final ModConfigSpec.IntValue POSITION_X;
    public static final ModConfigSpec.IntValue POSITION_Y;

    static {
        BUILDER.push("client");

        SHOW_SELF = BUILDER
            .comment("If true, shows your own status in the team panel.")
            .define("showSelf", true);

        POSITION_X = BUILDER
            .comment("Panel X position in pixels from the left edge of the screen.")
            .defineInRange("positionX", 10, 0, 2560);

        POSITION_Y = BUILDER
            .comment("Panel Y position in pixels from the bottom edge of the screen.")
            .defineInRange("positionY", 10, 0, 1440);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}

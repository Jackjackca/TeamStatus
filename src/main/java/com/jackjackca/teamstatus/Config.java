package com.jackjackca.teamstatus;

import net.neoforged.neoforge.common.ModConfigSpec;

/** TeamStatus client/common configuration. */
public final class Config {

    static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    /** Whether the local player's own panel is shown in addition to teammates. */
    public static final ModConfigSpec.BooleanValue HUD_SHOW_SELF;
    /** Horizontal anchor percentage of the GUI width, 0 = left edge, 100 = right edge.
     *  An int range of 0..100 makes the NeoForge config screen render this as a slider. */
    public static final ModConfigSpec.IntValue HUD_ANCHOR_X_PERCENT;
    /** Vertical anchor percentage of the GUI height, 0 = top edge, 100 = bottom edge. */
    public static final ModConfigSpec.IntValue HUD_ANCHOR_Y_PERCENT;
    /** Fixed inset from the anchored screen edges, in GUI-scaled pixels (kept off the edges). */
    public static final ModConfigSpec.IntValue HUD_EDGE_INSET;
    /** Vertical gap between stacked member panels, in GUI-scaled pixels. */
    public static final ModConfigSpec.IntValue HUD_VERTICAL_GAP;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue SHOW_EFFECTS;
    public static final ModConfigSpec.BooleanValue SHOW_HANDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("hud");
        HUD_ENABLED = builder
                .comment("Whether the team status HUD is rendered.")
                .define("enabled", true);
        HUD_SHOW_SELF = builder
                .comment("Show your own status panel in the HUD (pinned at the stack bottom).",
                        "Your own mining/eating/attack animations are derived locally with no network traffic.")
                .define("showSelf", true);
        HUD_ANCHOR_X_PERCENT = builder
                .comment("Horizontal anchor as a percentage of the screen width (slider).",
                        "0 = left edge, 100 = right edge. Panels grow upward.")
                .defineInRange("anchorXPercent", 0, 0, 100);
        HUD_ANCHOR_Y_PERCENT = builder
                .comment("Vertical anchor as a percentage of the screen height (slider).",
                        "0 = top edge, 100 = bottom edge. Default 100 = bottom-left anchoring.")
                .defineInRange("anchorYPercent", 100, 0, 100);
        HUD_EDGE_INSET = builder
                .comment("Fixed inset from the anchored screen edges, in scaled pixels.",
                        "At the default bottom-left anchor this is the distance from the left and bottom edges.")
                .defineInRange("edgeInset", 10, 0, 200);
        HUD_VERTICAL_GAP = builder
                .comment("Vertical gap between member panels, in scaled pixels.")
                .defineInRange("verticalGap", 8, 0, 32);
        HUD_SCALE = builder
                .comment("Scale multiplier of the HUD.")
                .defineInRange("scale", 1.0D, 0.5D, 3.0D);
        SHOW_EFFECTS = builder
                .comment("Show teammates' status effect icons and durations.")
                .define("showEffects", true);
        SHOW_HANDS = builder
                .comment("Show teammates' main hand item.")
                .define("showHands", true);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }
}

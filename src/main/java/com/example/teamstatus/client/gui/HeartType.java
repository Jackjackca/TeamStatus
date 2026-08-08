package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

@SuppressWarnings("null")
public enum HeartType {
    NORMAL((byte) 0),
    POISONED((byte) 1),
    WITHERED((byte) 2),
    ABSORBING((byte) 3),
    FROZEN((byte) 4);

    private final byte id;

    HeartType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static HeartType fromId(byte id) {
        for (HeartType type : values()) {
            if (type.id == id) return type;
        }
        return NORMAL;
    }

    public static HeartType fromPlayerState(Player player) {
        if (player.hasEffect(MobEffects.POISON)) return POISONED;
        if (player.hasEffect(MobEffects.WITHER)) return WITHERED;
        if (player.getTicksFrozen() > 0) return FROZEN;
        return NORMAL;
    }

    public ResourceLocation getTexture(boolean half, boolean blinking) {
        String prefix = switch (this) {
            case POISONED -> "poisoned_";
            case WITHERED -> "withered_";
            case ABSORBING -> "absorbing_";
            case FROZEN -> "frozen_";
            default -> "";
        };
        String suffix = half ? "half" : "full";
        String blink = blinking ? "_blinking" : "";
        return ResourceLocation.withDefaultNamespace("hud/heart/" + prefix + suffix + blink);
    }

    public ResourceLocation getContainerTexture(boolean hardcore) {
        String suffix = hardcore ? "hardcore" : "";
        return ResourceLocation.withDefaultNamespace("hud/heart/container" + suffix);
    }

    public ResourceLocation getContainerBlinkTexture(boolean hardcore) {
        String suffix = hardcore ? "hardcore" : "";
        return ResourceLocation.withDefaultNamespace("hud/heart/container_blinking" + suffix);
    }
}

package com.jackjackca.teamstatus.client.visual;

import java.util.ArrayList;
import java.util.List;

import com.jackjackca.teamstatus.client.render.HealthRenderer;
import com.jackjackca.teamstatus.client.render.HealthRenderer.HeartSlot;
import com.jackjackca.teamstatus.client.state.ClientHealthFxState;
import com.jackjackca.teamstatus.common.state.TeamMemberState;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;

/**
 * State &rarr; heart descriptors. Faithfully reproduces the vanilla {@code Gui#renderHearts}
 * behavior for remote players (heart type, low-health shake, regeneration pulse, damage/heal
 * flash). Descriptors are built by {@link #build} and cached in
 * {@link MemberPanelCache} at gui-tick granularity; {@link #render} only paints them.
 * <p>
 * The blood hearts and the absorption hearts are delivered as two separate groups so the
 * renderer can place absorption rows ABOVE the blood rows (vanilla ordering) with a 1px gap
 * between rows; the single shake-random stream is still consumed in reverse global-slot order
 * exactly as vanilla does.
 */
public final class HealthVisual {

    private static final ResourceLocation POISON_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.POISON.value());
    private static final ResourceLocation WITHER_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.WITHER.value());
    private static final ResourceLocation REGENERATION_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.REGENERATION.value());

    private static final int LOW_HEALTH_THRESHOLD = 4;

    /** Ordered groups: absorption rows render above the blood rows. */
    public record HeartLayout(List<HeartSlot> blood, List<HeartSlot> absorption) {
    }

    private HealthVisual() {
    }

    /** Rebuilds the per-slot descriptors (cache miss path only). */
    public static HeartLayout build(MemberVisualContext context) {
        TeamMemberState member = context.member();
        int guiTicks = context.guiTicks();

        int healthSlots = Math.max(1, Mth.ceil(member.maxHealth() / 2.0F));
        int healthPoints = Mth.ceil(member.health());
        int absorptionPoints = Mth.ceil(member.absorptionAmount());
        int absorptionSlots = Mth.ceil(member.absorptionAmount() / 2.0F);
        int totalSlots = healthSlots + absorptionSlots;

        int displayHealth = ClientHealthFxState.displayHealth(member.id(), healthPoints);
        boolean blinking = ClientHealthFxState.isBlinking(member.id(), guiTicks);

        Gui.HeartType paint = styleFor(member);
        Gui.HeartType absorptionPaint =
                paint == Gui.HeartType.WITHERED ? Gui.HeartType.WITHERED : Gui.HeartType.ABSORBING;

        boolean shaking = healthPoints + absorptionPoints <= LOW_HEALTH_THRESHOLD;
        int pulseSlot = regenerationPulseSlot(member, guiTicks, displayHealth, healthPoints);

        // One shared random stream, seeded once per gui tick and consumed in reverse global
        // slot order (blood first, then absorption): this matches Gui#renderHearts exactly.
        // Keep the int multiplication (with overflow) before the widening cast, as vanilla does.
        RandomSource random = RandomSource.create((long) (guiTicks * 312871));
        int[] shakeOffsets = new int[totalSlots];
        for (int slot = totalSlots - 1; slot >= 0; slot--) {
            shakeOffsets[slot] = shaking ? random.nextInt(2) : 0;
        }

        List<HeartSlot> blood = new ArrayList<>(healthSlots);
        for (int slot = 0; slot < healthSlots; slot++) {
            int offsetY = shakeOffsets[slot];
            // Regeneration wave only sweeps blood hearts, never absorption hearts.
            if (slot == pulseSlot) {
                offsetY -= 2;
            }

            int pointsIndex = slot * 2;

            Gui.HeartType blinkFill = null;
            boolean blinkHalf = false;
            if (blinking && pointsIndex < displayHealth) {
                blinkFill = paint;
                blinkHalf = pointsIndex + 1 == displayHealth;
            }

            Gui.HeartType fill = null;
            boolean fillHalf = false;
            if (pointsIndex < healthPoints) {
                fill = paint;
                fillHalf = pointsIndex + 1 == healthPoints;
            }

            blood.add(new HeartSlot(fill, fillHalf, blinkFill, blinkHalf,
                    blinking, context.hardcore(), offsetY));
        }

        List<HeartSlot> absorption = new ArrayList<>(Math.max(0, absorptionSlots));
        for (int i = 0; i < absorptionSlots; i++) {
            int globalSlot = healthSlots + i;
            int absorptionIndex = i * 2;
            if (absorptionIndex < absorptionPoints) {
                boolean half = absorptionIndex + 1 == absorptionPoints;
                absorption.add(new HeartSlot(absorptionPaint, half, null, false,
                        blinking, context.hardcore(), shakeOffsets[globalSlot]));
            } else {
                absorption.add(HeartSlot.empty(context.hardcore(),
                        shakeOffsets[globalSlot], blinking));
            }
        }
        return new HeartLayout(List.copyOf(blood), List.copyOf(absorption));
    }

    public static void render(GuiGraphics gui, int x, int y, HeartLayout layout) {
        HealthRenderer.draw(gui, x, y, layout.blood(), layout.absorption());
    }

    public static int height(HeartLayout layout) {
        return HealthRenderer.height(layout.blood().size(), layout.absorption().size());
    }

    /** Mirrors {@code Gui.HeartType.forPlayer}: poison, then wither, then frozen, else normal. */
    private static Gui.HeartType styleFor(TeamMemberState member) {
        boolean poisoned = member.effects().stream().anyMatch(e -> e.effectId().equals(POISON_ID));
        boolean withered = member.effects().stream().anyMatch(e -> e.effectId().equals(WITHER_ID));
        if (poisoned) {
            return Gui.HeartType.POISIONED;
        }
        if (withered) {
            return Gui.HeartType.WITHERED;
        }
        if (member.vitals().frozen()) {
            return Gui.HeartType.FROZEN;
        }
        return Gui.HeartType.NORMAL;
    }

    /**
     * Vanilla regeneration wave: {@code tickCount % ceil(f + 5)} where
     * {@code f = max(maxHealth, displayHealth, currentHealth)}; {@code -1} keeps every heart
     * still when the member has no regeneration effect.
     */
    private static int regenerationPulseSlot(TeamMemberState member, int guiTicks,
                                             int displayHealth, int healthPoints) {
        boolean regenerating = member.effects().stream()
                .anyMatch(e -> e.effectId().equals(REGENERATION_ID));
        if (!regenerating) {
            return -1;
        }
        float effectiveMax = Math.max(member.maxHealth(), Math.max(displayHealth, healthPoints));
        return (int) (guiTicks % Mth.ceil(effectiveMax + 5.0F));
    }
}

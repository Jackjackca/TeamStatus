package com.jackjackca.teamstatus.client.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jackjackca.teamstatus.Config;
import com.jackjackca.teamstatus.client.render.EffectRenderer;
import com.jackjackca.teamstatus.client.render.EffectRenderer.EffectEntry;
import com.jackjackca.teamstatus.common.state.EffectState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * State &rarr; effect descriptors (icon sprites + compact duration/amplifier labels) with
 * wrapping layout delegated to {@link EffectRenderer}. The entry list is rebuilt at gui-tick
 * granularity and cached, so sprite registry lookups and string formatting never run per
 * render frame. Honors the show-effects config.
 */
public final class EffectVisual {

    private EffectVisual() {
    }

    /** Rebuilds the effect entries (cache miss path only); empty when disabled. */
    public static List<EffectEntry> build(MemberVisualContext context) {
        if (!enabled(context)) {
            return List.of();
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<EffectEntry> entries = new ArrayList<>();
        for (EffectState effect : context.member().effects()) {
            // Vanilla hides effects whose showIcon flag is false from HUD/inventory displays.
            if (!effect.showIcon()) {
                continue;
            }
            Optional<Holder.Reference<MobEffect>> holder =
                    BuiltInRegistries.MOB_EFFECT.getHolder(effect.effectId());
            holder.ifPresent(reference -> entries.add(new EffectEntry(
                    minecraft.getMobEffectTextures().get(reference),
                    formatLabel(effect), effect.ambient())));
        }
        return entries;
    }

    public static void render(GuiGraphics gui, int x, int y, int width, List<EffectEntry> entries) {
        if (!entries.isEmpty()) {
            EffectRenderer.draw(gui, x, y, width, entries);
        }
    }

    public static int height(List<EffectEntry> entries, int width, Font font) {
        return EffectRenderer.height(entries, width, font);
    }

    private static boolean enabled(MemberVisualContext context) {
        return Config.SHOW_EFFECTS.get() && !context.member().effects().isEmpty();
    }

    private static String formatLabel(EffectState effect) {
        String duration;
        if (effect.duration() < 0) {
            duration = "inf";
        } else {
            int totalSeconds = effect.duration() / 20;
            duration = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
        }
        if (effect.amplifier() > 0) {
            return duration + " L" + (effect.amplifier() + 1);
        }
        return duration;
    }
}

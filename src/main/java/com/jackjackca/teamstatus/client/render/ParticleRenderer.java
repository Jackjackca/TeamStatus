package com.jackjackca.teamstatus.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Very small floating-text "particle" system: health loss/healing numbers float up and fade
 * out over a member's HUD panel. All updates happen on the client tick; render position uses
 * {@code partialTick}.
 */
public final class ParticleRenderer {

    public static final ParticleRenderer INSTANCE = new ParticleRenderer();

    private static final int LIFETIME_TICKS = 30;
    private static final int FADE_AFTER_TICKS = 18;

    private final Map<UUID, List<FloatingText>> particles = new HashMap<>();

    private ParticleRenderer() {
    }

    public void spawn(UUID memberId, String text, int color) {
        particles.computeIfAbsent(memberId, k -> new ArrayList<>()).add(new FloatingText(text, color));
    }

    public void clientTick() {
        Iterator<Map.Entry<UUID, List<FloatingText>>> it = particles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<FloatingText>> entry = it.next();
            entry.getValue().removeIf(text -> {
                text.age++;
                return text.age >= LIFETIME_TICKS;
            });
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    public void render(GuiGraphics guiGraphics, UUID memberId, int centerX, int y, float partialTick) {
        List<FloatingText> texts = particles.get(memberId);
        if (texts == null || texts.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int stack = 0;
        for (FloatingText text : texts) {
            float age = text.age + partialTick;
            int alpha = age <= FADE_AFTER_TICKS ? 255
                    : (int) (255.0F * (LIFETIME_TICKS - age) / (LIFETIME_TICKS - FADE_AFTER_TICKS));
            alpha = Math.max(0, Math.min(255, alpha));
            int colorWithAlpha = (alpha << 24) | (text.color & 0x00FFFFFF);
            int textWidth = font.width(text.text);
            float renderY = y - age * 0.6F - stack * 9.0F;
            guiGraphics.drawString(font, text.text, centerX - textWidth / 2.0F, renderY, colorWithAlpha, true);
            stack++;
        }
    }

    public void retainOnly(Iterable<UUID> presentMemberIds) {
        particles.keySet().retainAll(toSet(presentMemberIds));
    }

    private static java.util.Set<UUID> toSet(Iterable<UUID> ids) {
        java.util.Set<UUID> set = new java.util.HashSet<>();
        ids.forEach(set::add);
        return set;
    }

    public void clear() {
        particles.clear();
    }

    private static final class FloatingText {
        private final String text;
        private final int color;
        private int age;

        private FloatingText(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}

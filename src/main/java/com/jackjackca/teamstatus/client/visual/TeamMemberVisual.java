package com.jackjackca.teamstatus.client.visual;

import com.jackjackca.teamstatus.client.render.FoodRenderer;
import com.jackjackca.teamstatus.client.render.HitIconRenderer;
import com.jackjackca.teamstatus.client.render.ParticleRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Composition for one member panel: rebuilds the cached descriptor lists at gui-tick
 * granularity via {@link MemberPanelCache}, measures the stacked sections from those cached
 * lists, paints the name row and delegates each section to its dedicated visual. No panel
 * background is painted.
 * <p>
 * Layout: a player avatar occupies the left column; the health, food, armor and effect rows
 * (plus the hand action zone) start at {@link #CONTENT_X}. The avatar, held item and mining
 * target all share one vertical center line: the midpoint of the combined health + food +
 * armor height.
 * <p>
 * The steady-state frame path allocates nothing: all lists, the trimmed name and the row
 * heights are reused until the member snapshot or the gui tick changes. The hand action zone
 * (swinging item + target) is animated per frame but performs pure math and direct draws.
 */
public final class TeamMemberVisual {

    /** Left avatar column. */
    public static final int AVATAR_SIZE = 18;
    private static final int AVATAR_X = 0;
    private static final int AVATAR_GAP = 3;
    /** Everything right of the avatar column starts here. */
    public static final int CONTENT_X = AVATAR_X + AVATAR_SIZE + AVATAR_GAP;

    /** Legacy content width is 116; the panel grows by the avatar column on the left. */
    private static final int CONTENT_WIDTH = 116;
    public static final int PANEL_WIDTH = CONTENT_X + CONTENT_WIDTH;

    private static final int NAME_ROW_HEIGHT = 10;
    /** Name shares the content column; leave a few px of right margin inside the panel. */
    private static final int NAME_MAX_WIDTH = PANEL_WIDTH - CONTENT_X - 4;
    /** Vertical gap between the stacked sections (health, food, armor, effects). */
    private static final int SECTION_GAP = 1;
    /**
     * Visual leading-edge compensation within the content column. The 9px heart/food/armor
     * sprites have ~1px of transparent padding on their left, so their visible content starts
     * at CONTENT_X + 1 while font glyphs start at their own x = 0; shift the name right by
     * the same pixel so it lines up with the visible left edge of the icon rows.
     */
    private static final int NAME_X = CONTENT_X + 1;
    private static final int PARTICLE_TOP_OFFSET = 2;

    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_OFFLINE = 0xFFAAAAAA;
    private static final int COLOR_DEAD = 0xFFFF6666;

    /** Reused out-parameter slot for the food exhaustion width during a cache rebuild. */
    private static final int[] FOOD_WIDTH_SCRATCH = new int[1];

    private TeamMemberVisual() {
    }

    /**
     * Measures the panel, building the cached descriptors if needed (cache miss only). Safe
     * to call in the layout pass before drawing; the following {@link #render} reuses the
     * same cache entry with zero rebuilding.
     *
     * @return total painted height of this member's panel
     */
    public static int measure(MemberVisualContext context) {
        MemberPanelCache.Entry cache = MemberPanelCache.get(context.member().id());
        if (MemberPanelCache.isStale(cache, context, PANEL_WIDTH)) {
            rebuild(cache, context);
        }
        return panelHeight(cache);
    }

    private static int panelHeight(MemberPanelCache.Entry cache) {
        // Must mirror the y increments in render() exactly so stacked panels never overlap.
        int blockHeight = NAME_ROW_HEIGHT + cache.healthHeight + SECTION_GAP
                + FoodRenderer.ROW_HEIGHT + SECTION_GAP;
        if (cache.armorHeight > 0) {
            blockHeight += cache.armorHeight + SECTION_GAP;
        }
        if (cache.effectsHeight > 0) {
            blockHeight += cache.effectsHeight + SECTION_GAP;
        }
        return blockHeight;
    }

    /** Paints the panel at {@code topY}; call {@link #measure} first (same frame). */
    public static void render(GuiGraphics gui, int topY, MemberVisualContext context) {
        Font font = context.font();
        MemberPanelCache.Entry cache = MemberPanelCache.get(context.member().id());
        if (MemberPanelCache.isStale(cache, context, PANEL_WIDTH)) {
            rebuild(cache, context);
        }

        int y = topY;

        String name = cache.trimmedName;
        int nameColor = !context.member().online() ? COLOR_OFFLINE
                : !context.member().alive() ? COLOR_DEAD : COLOR_NAME;
        gui.drawString(font, name, NAME_X, y, nameColor, true);
        y += NAME_ROW_HEIGHT;

        int healthRowY = y;
        HealthVisual.render(gui, CONTENT_X, y, cache.hearts);
        y += cache.healthHeight + SECTION_GAP;

        FoodVisual.render(gui, CONTENT_X, y, cache.drumsticks, cache.exhaustionWidth);
        y += FoodRenderer.ROW_HEIGHT + SECTION_GAP;

        // Shared vertical center line: midpoint of the health + food (+ armor) extent from
        // the heart row's top. The avatar and the hand action zone anchor to this center.
        int regionHeight = cache.healthHeight + SECTION_GAP + FoodRenderer.ROW_HEIGHT;
        if (cache.armorHeight > 0) {
            regionHeight += SECTION_GAP + cache.armorHeight;
        }
        float centerY = healthRowY + regionHeight / 2.0F;

        HitIconRenderer.drawPlayerFace(gui, context.member().id(), context.member().name(),
                AVATAR_X, Math.round(centerY - AVATAR_SIZE / 2.0F), AVATAR_SIZE);

        // Item + mining/attack target sit to the right of the bar rows, on the center line.
        HandActionVisual.render(gui, context, CONTENT_X, centerY);

        if (cache.armorHeight > 0) {
            ArmorVisual.render(gui, CONTENT_X, y, cache.armor);
            y += cache.armorHeight + SECTION_GAP;
        }

        if (cache.effectsHeight > 0) {
            EffectVisual.render(gui, CONTENT_X-2, y, PANEL_WIDTH - CONTENT_X, cache.effects);
            y += cache.effectsHeight + SECTION_GAP;
        }

        ParticleRenderer.INSTANCE.render(gui, context.member().id(), PANEL_WIDTH / 2,
                topY + PARTICLE_TOP_OFFSET, context.partialTick());
    }

    /** Cache miss path: rebuilds every 2D descriptor list for one member at most once/tick. */
    private static void rebuild(MemberPanelCache.Entry cache, MemberVisualContext context) {
        cache.hearts = HealthVisual.build(context);
        cache.healthHeight = HealthVisual.height(cache.hearts);

        cache.drumsticks = FoodVisual.build(context, FOOD_WIDTH_SCRATCH);
        cache.exhaustionWidth = FOOD_WIDTH_SCRATCH[0];

        cache.armor = ArmorVisual.build(context);
        cache.armorHeight = ArmorVisual.height(cache.armor);

        cache.effects = EffectVisual.build(context);
        cache.effectsHeight = cache.effects.isEmpty()
                ? 0 : EffectVisual.height(cache.effects, PANEL_WIDTH - CONTENT_X, context.font());

        cache.trimmedName = trim(context.font(), context.member().name());
        MemberPanelCache.markBuilt(cache, context, PANEL_WIDTH);
    }

    private static String trim(Font font, String name) {
        if (font.width(name) <= NAME_MAX_WIDTH) {
            return name;
        }
        String trimmed = name;
        while (!trimmed.isEmpty() && font.width(trimmed + "…") > NAME_MAX_WIDTH) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }
}

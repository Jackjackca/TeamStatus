package com.jackjackca.teamstatus.client.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.jackjackca.teamstatus.Config;
import com.jackjackca.teamstatus.TeamStatus;
import com.jackjackca.teamstatus.client.state.ClientTeamState;
import com.jackjackca.teamstatus.client.visual.MemberVisualContext;
import com.jackjackca.teamstatus.client.visual.TeamMemberVisual;
import com.jackjackca.teamstatus.common.state.TeamMemberState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The single registered HUD layer (via {@link RegisterGuiLayersEvent}).
 * <p>
 * Edge-relative anchoring (the recommended HUD positioning approach): the anchor is a
 * percentage of the GUI width/height (0/100 = bottom-left by default) plus a fixed pixel
 * inset, computed each frame from the actual GUI-scaled screen size. Panels stack upward.
 * The transform applies scale before translation, so the anchor math uses post-scale
 * dimensions and the stack never drifts toward the top-left; if the stack is taller than
 * the space above the anchor, its top is clipped rather than the bottom overflowing.
 */
public final class TeamHudRenderer implements LayeredDraw.Layer {

    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(TeamStatus.MODID, "team_status");

    private static final TeamHudRenderer INSTANCE = new TeamHudRenderer();

    /** Teammate ordering only changes when a new snapshot arrives, so it is cached. */
    private Collection<TeamMemberState> lastSortedSource;
    private List<TeamMemberState> orderedCache = List.of();

    private TeamHudRenderer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, INSTANCE);
    }

    @Override
    public void render(@Nonnull GuiGraphics gui, @Nonnull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.HUD_ENABLED.get() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        var members = ClientTeamState.getMembers();
        boolean showSelf = Config.HUD_SHOW_SELF.get();
        TeamMemberState localSelf = showSelf ? ClientTeamState.getLocalSelf() : null;
        if (members.isEmpty() && localSelf == null) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        UUID selfId = minecraft.player.getUUID();
        int guiTicks = minecraft.gui.getGuiTicks();
        boolean hardcore = minecraft.level != null && minecraft.level.getLevelData().isHardcore();

        List<TeamMemberState> ordered = orderedMembers(members, selfId);

        float anchorXPct = Config.HUD_ANCHOR_X_PERCENT.get() / 100.0F;
        float anchorYPct = Config.HUD_ANCHOR_Y_PERCENT.get() / 100.0F;
        int inset = Config.HUD_EDGE_INSET.get();
        int gap = Config.HUD_VERTICAL_GAP.get();
        float scale = Config.HUD_SCALE.get().floatValue();

        // All geometry below is in POST-scale (unscaled panel) coordinates; divide screen
        // pixels by scale so the visual inset and anchor stay independent of HUD_SCALE.
        float invScale = 1.0F / scale;
        int screenW = gui.guiWidth();
        int screenH = gui.guiHeight();

        // Horizontal slider: t=0 keeps the panel's left edge at the inset, t=1 keeps its
        // right edge at the inset — interpolating between the two keeps it on screen.
        float leftOriginX = inset * invScale;
        float rightOriginX = (screenW - inset) * invScale - TeamMemberVisual.PANEL_WIDTH;
        float originX = leftOriginX + anchorXPct * (rightOriginX - leftOriginX);

        // Vertical slider: position of the self panel's bottom edge. Panels always grow
        // upward from this base; t=1 (default) puts the base inset above the bottom edge.
        // If the stack is taller than the space above, its top clips (by design).
        float originY = (inset + anchorYPct * (screenH - 2 * inset)) * invScale;

        var pose = gui.pose();
        pose.pushPose();
        // Scale FIRST, then translate, otherwise the scale multiplies the translation and
        // pushes the stack toward the top-left corner.
        pose.scale(scale, scale, 1.0F);
        pose.translate(originX, originY, 0.0F);

        // Single bottom-up pass: self (locally synthesized, pinned at the bottom) first, then
        // teammates. Each topY depends only on the previous panel's measured height.
        int cursorY = 0;
        if (localSelf != null) {
            MemberVisualContext context =
                    new MemberVisualContext(localSelf, minecraft.font, partialTick, guiTicks, hardcore);
            int topY = -TeamMemberVisual.measure(context);
            TeamMemberVisual.render(gui, topY, context);
            cursorY = topY - gap;
        }
        for (TeamMemberState member : ordered) {
            MemberVisualContext context =
                    new MemberVisualContext(member, minecraft.font, partialTick, guiTicks, hardcore);
            int topY = cursorY - TeamMemberVisual.measure(context);
            TeamMemberVisual.render(gui, topY, context);
            cursorY = topY - gap;
        }

        pose.popPose();
    }

    /**
     * Returns the cached teammate ordering, rebuilding it only when the snapshot instance
     * changed. The local player is defensively excluded (the server already excludes self,
     * but this also tolerates older/mixed servers); online first, then by name.
     */
    private List<TeamMemberState> orderedMembers(Collection<TeamMemberState> source, UUID selfId) {
        if (source == lastSortedSource) {
            return orderedCache;
        }
        List<TeamMemberState> sorted = new ArrayList<>(source.size());
        for (TeamMemberState member : source) {
            if (!member.id().equals(selfId)) {
                sorted.add(member);
            }
        }
        sorted.sort(Comparator
                .comparing((TeamMemberState m) -> !m.online())
                .thenComparing(TeamMemberState::name));
        orderedCache = List.copyOf(sorted);
        lastSortedSource = source;
        return orderedCache;
    }
}

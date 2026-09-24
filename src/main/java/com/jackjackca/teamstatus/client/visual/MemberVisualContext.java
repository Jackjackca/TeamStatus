package com.jackjackca.teamstatus.client.visual;

import com.jackjackca.teamstatus.common.state.TeamMemberState;

import net.minecraft.client.gui.Font;

/**
 * Everything a section visual needs to convert one member's synced state into render
 * descriptors: the immutable member snapshot (read directly, no interpolation), the shared
 * font instance, the current frame's partial tick (particle and hand-swing motion), the
 * client gui tick counter and the world's hardcore flag. Per-member hand action state is
 * read directly by the hand action visual from its own state holder.
 */
public record MemberVisualContext(TeamMemberState member, Font font, float partialTick,
                                  int guiTicks, boolean hardcore) {
}

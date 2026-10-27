package com.jackjackca.teamstatus.client.render.pip;

import javax.annotation.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Picture-in-picture render state for the mining target: an isometric 3D block cube optionally
 * overlaid with the destroy-stage crack decal, rendered into an off-screen texture and blitted
 * into the deferred GUI tree at {@link #bounds()}.
 * <p>
 * The on-screen rectangle is pre-transformed by the caller from the current 2D GUI pose, since
 * PIP states are placed by absolute screen bounds rather than inheriting the GUI matrix stack.
 */
public record BlockInGuiState(BlockState blockState,
                              int crackStage,
                              int x0,
                              int y0,
                              int x1,
                              int y1,
                              float scale,
                              @Nullable ScreenRectangle scissorArea,
                              @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    public BlockInGuiState(BlockState blockState, int crackStage,
                           int x0, int y0, int x1, int y1,
                           @Nullable ScreenRectangle scissorArea) {
        this(blockState, crackStage, x0, y0, x1, y1, 1.0F, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}

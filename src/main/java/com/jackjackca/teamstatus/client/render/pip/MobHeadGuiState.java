package com.jackjackca.teamstatus.client.render.pip;

import javax.annotation.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;

/**
 * Picture-in-picture render state drawing only the head {@code ModelPart} of a mob's entity
 * model, angled like a head item. The concrete entity type is resolved (and a temporary
 * client-side entity reified) inside {@link MobHeadGuiRenderer}.
 */
public record MobHeadGuiState(Identifier entityType,
                              int x0,
                              int y0,
                              int x1,
                              int y1,
                              float scale,
                              @Nullable ScreenRectangle scissorArea,
                              @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    public MobHeadGuiState(Identifier entityType,
                           int x0, int y0, int x1, int y1,
                           @Nullable ScreenRectangle scissorArea) {
        this(entityType, x0, y0, x1, y1, 1.0F, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}

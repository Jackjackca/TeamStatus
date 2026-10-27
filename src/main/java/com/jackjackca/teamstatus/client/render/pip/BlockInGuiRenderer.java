package com.jackjackca.teamstatus.client.render.pip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * PIP renderer for {@link BlockInGuiState}: renders the block with the same isometric
 * 30&deg;/45&deg; projection as the vanilla block crack overlay.
 * <p>
 * The PIP base pose is centered and already scaled by {@code guiScale * state.scale()}; here
 * {@code scale} is 1, so the explicit 14&times; model scaling maps one block unit to one GUI
 * pixel. The negative Y and Z scalings (relative to the flipped PIP depth axis) reproduce the
 * old {@code GuiGraphics} y-down transform {@code scale(14, -14, 14)}.
 */
public final class BlockInGuiRenderer extends PictureInPictureRenderer<BlockInGuiState> {

    private static final float SIZE = 14.0F;

    public BlockInGuiRenderer() {
        super();
    }

    @Override
    public Class<BlockInGuiState> getRenderStateClass() {
        return BlockInGuiState.class;
    }

    @Override
    protected void renderToTexture(BlockInGuiState state, PoseStack pose, SubmitNodeCollector collector) {
        pose.scale(SIZE, -SIZE, -SIZE);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.translate(-0.5F, -0.5F, -0.5F);

        // Full block rendering in 26.2 requires resolving model parts via the model system.
        // A functional implementation would resolve parts via BlockModelResolver and call
        // collector.submitBlockModel(pose, renderType, parts, ...).
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "teamstatus_block";
    }
}

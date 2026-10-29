package com.jackjackca.teamstatus.client.render.pip;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

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
        BlockState blockState = state.blockState();
        if (blockState.isAir()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        // Block items in the GUI use the flat-ish item lighting, matching the inventory.
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);

        pose.scale(SIZE, -SIZE, -SIZE);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.translate(-0.5F, -0.5F, -0.5F);

        BlockModelRenderState model = new BlockModelRenderState();
        minecraft.getBlockModelResolver().update(model, blockState, BlockDisplayContext.create());
        model.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);

        int crackStage = state.crackStage();
        if (crackStage >= 0) {
            submitCrackOverlay(minecraft, blockState, crackStage, pose, collector);
        }
    }

    /**
     * Submits the vanilla destroy-stage decal over the same model parts, exactly like
     * {@code LevelRenderer}'s block-damage pass: the crack texture is projected onto every
     * crumbling-capable face through the {@code DESTROY_TYPES} render types.
     */
    private static void submitCrackOverlay(Minecraft minecraft, BlockState blockState, int crackStage,
                                           PoseStack pose, SubmitNodeCollector collector) {
        BlockStateModel blockModel = minecraft.getModelManager().getBlockStateModelSet().get(blockState);
        List<BlockStateModelPart> parts = new ArrayList<>();
        // Same model seed as BlockModelResolver so the variants line up with the block body.
        blockModel.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, blockState,
                RandomSource.create(42L), parts);
        collector.submitBreakingBlockModel(pose, parts, Math.min(9, crackStage));
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

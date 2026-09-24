package com.jackjackca.teamstatus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Renders a true 3D block inside the 2D HUD (isometric projection) for the mining target.
 * <p>
 * The crack overlay uses the exact vanilla technique from
 * {@code LevelRenderer#renderBlockDamage}: a {@link SheetedDecalTextureGenerator} wraps the
 * {@link ModelBakery#DESTROY_TYPES} buffer and is joined to the block's own consumers through
 * {@link VertexMultiConsumer}, so the destroy texture is projected onto every face of the
 * model (all three faces visible to the user) in a single draw instead of a coplanar second
 * pass that depth-fights.
 */
public final class BlockTargetRenderer {

    /** On-screen size of the cube in scaled GUI pixels. */
    public static final float SIZE = 14.0F;

    private BlockTargetRenderer() {
    }

    /**
     * @param centerX    cube center X in panel coordinate space
     * @param centerY    cube center Y
     * @param crackStage 0..9 vanilla destroy stage; negative renders no crack overlay
     */
    public static void draw(GuiGraphics gui, BlockState state, float centerX, float centerY, int crackStage) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = gui.bufferSource();
        PoseStack pose = gui.pose();

        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        // Y flip: GUI y points down while block models assume y up. After that flip, a
        // POSITIVE 30° X tilt points the top face toward the viewer and projects it above
        // the center (a negative tilt would show the bottom instead); 45° Y then reveals
        // the left and right side faces — the classic top-down isometric triad.
        pose.scale(SIZE, -SIZE, SIZE);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.translate(-0.5F, -0.5F, -0.5F);

        MultiBufferSource source = buffer;
        if (crackStage >= 0) {
            int stage = Math.max(0, Math.min(9, crackStage));
            VertexConsumer decal = new SheetedDecalTextureGenerator(
                    buffer.getBuffer(ModelBakery.DESTROY_TYPES.get(stage)), pose.last(), 1.0F);
            // Project the destroy decal onto every face that supports crumbling, alongside
            // the block's own texture consumer.
            source = renderType -> {
                VertexConsumer normal = buffer.getBuffer(renderType);
                return renderType.affectsCrumbling() ? VertexMultiConsumer.create(decal, normal) : normal;
            };
        }

        minecraft.getBlockRenderer().renderSingleBlock(state, pose, source,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);

        pose.popPose();
        // Ends the solid buffers and the destroy-stage decal buffer together.
        gui.flush();
    }
}

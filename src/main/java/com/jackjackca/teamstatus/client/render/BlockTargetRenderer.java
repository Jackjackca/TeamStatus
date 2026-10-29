package com.jackjackca.teamstatus.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.block.state.BlockState;

import com.jackjackca.teamstatus.client.render.pip.BlockInGuiState;

/**
 * Renders a true 3D block inside the 2D HUD (isometric projection) for the mining target.
 * <p>
 * Since the 1.21.5 GUI rework {@code GuiGraphics} no longer exposes an immediate
 * {@code MultiBufferSource}: 3D GUI content is submitted as picture-in-picture render states
 * and drawn into off-screen textures by registered PIP renderers. The on-screen bounds are
 * transformed through the current 2D GUI pose because PIP states use absolute screen
 * coordinates rather than inheriting the GUI matrix.
 * <p>
 * The crack overlay uses the exact vanilla technique from
 * {@code LevelRenderer#renderBlockDamage}: a {@code SheetedDecalTextureGenerator} wraps the
 * destroy-stage buffer and is joined to the block's consumers through {@code VertexMultiConsumer},
 * implemented in {@link com.jackjackca.teamstatus.client.render.pip.BlockInGuiRenderer}.
 */
public final class BlockTargetRenderer {

    /** On-screen size of the cube in scaled GUI pixels. */
    public static final float SIZE = 14.0F;

    /**
     * Extra margin around the 14 px slot in scaled GUI pixels. The isometric projection of a
     * 14 px cube spans about 20 px horizontally and 22 px vertically, so the PIP texture must
     * be padded to this size or the render bounds clip the silhouette like a smaller mask.
     */
    public static final int PADDING = 4;

    private BlockTargetRenderer() {
    }

    /**
     * @param centerX    cube center X in panel coordinate space
     * @param centerY    cube center Y
     * @param crackStage 0..9 vanilla destroy stage; negative renders no crack overlay
     */
    public static void draw(GuiGraphicsExtractor gui, BlockState state, float centerX, float centerY, int crackStage) {
        int x = Math.round(centerX - SIZE / 2.0F) - PADDING;
        int y = Math.round(centerY - SIZE / 2.0F) - PADDING;
        int size = Math.round(SIZE) + PADDING * 2;
        ScreenRectangle bounds = new ScreenRectangle(x, y, size, size)
                .transformAxisAligned(gui.pose());
        gui.submitPictureInPictureRenderState(new BlockInGuiState(
                state, crackStage,
                bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                gui.peekScissorStack()));
    }
}

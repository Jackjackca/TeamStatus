package com.jackjackca.teamstatus.client.render;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ResolvableProfile;

import com.jackjackca.teamstatus.client.render.pip.MobHeadGuiRenderer;
import com.jackjackca.teamstatus.client.render.pip.MobHeadGuiState;

/**
 * Flat struck-target icons for the attack animation: a player's face, or a mob's real head
 * rendered from its own model and skin texture (slightly angled, like a head item), with a
 * translucent red hit-flash overlay. Types without a resolvable head model fall back to the
 * spawn-egg icon, and types without an egg to a generic marker.
 * <p>
 * The mob head is drawn as a picture-in-picture GUI element (see {@link MobHeadGuiRenderer});
 * temporary entity reification is cached there.
 */
public final class HitIconRenderer {

    public static final int ICON_SIZE = 14;
    private static final int EGG_OFFSET = -1;
    private static final int FALLBACK_COLOR = 0xFFFF5555;

    private HitIconRenderer() {
    }

    /** Draws the player face; resolves asynchronously cached skins through the skin manager. */
    public static void drawPlayerFace(GuiGraphicsExtractor gui, UUID playerId, String name, int x, int y) {
        drawPlayerFace(gui, playerId, name, x, y, ICON_SIZE);
    }

    /** Draws the player face at an arbitrary square size. */
    public static void drawPlayerFace(GuiGraphicsExtractor gui, UUID playerId, String name, int x, int y,
                                      int size) {
        GameProfile profile = new GameProfile(playerId, name != null ? name : playerId.toString());
        ResolvableProfile resolvable = ResolvableProfile.createResolved(profile);
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        PlayerFaceExtractor.extractRenderState(gui, resolvable, 0, 0, size);
        pose.popMatrix();
    }

    /**
     * Draws the mob's head using the entity model/texture. Returns {@code false} when the
     * type or its head model cannot be resolved, so the caller can use the egg fallback.
     */
    public static boolean drawMobHead(GuiGraphicsExtractor gui, Identifier entityType, int x, int y) {
        if (!MobHeadGuiRenderer.isHeadSupported(entityType)) {
            return false;
        }
        ScreenRectangle bounds = new ScreenRectangle(x, y, ICON_SIZE, ICON_SIZE)
                .transformAxisAligned(gui.pose());
        gui.submitPictureInPictureRenderState(new MobHeadGuiState(
                entityType,
                bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                gui.peekScissorStack()));
        return true;
    }

    /** Spawn-egg fallback for entity types whose head cannot be rendered. */
    public static void drawMobEggFallback(GuiGraphicsExtractor gui, Identifier entityType, int x, int y) {
        ItemStack stack = eggFor(entityType);
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        if (stack != null) {
            gui.item(stack, EGG_OFFSET, EGG_OFFSET);
        } else {
            // Generic fallback: small red cross marker so the strike is still visible.
            gui.fill(3, 6, 11, 8, FALLBACK_COLOR);
            gui.fill(6, 3, 8, 11, FALLBACK_COLOR);
        }
        pose.popMatrix();
    }

    /**
     * Red overlay whose alpha follows the accumulated hit flash.
     *
     * @param strength 0..1
     */
    public static void drawHitFlash(GuiGraphicsExtractor gui, int x, int y, float strength) {
        if (strength <= 0.0F) {
            return;
        }
        int alpha = (int) (Math.max(0.0F, Math.min(1.0F, strength)) * 255.0F) << 24;
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        gui.fill(0, 0, ICON_SIZE, ICON_SIZE, alpha | 0x00FF0000);
        pose.popMatrix();
    }

    /** Drops cached temporary entities (they reference the old client level). */
    public static void clearTempEntities() {
        MobHeadGuiRenderer.clearCaches();
    }

    private static ItemStack eggFor(Identifier entityType) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityType)
                .flatMap(SpawnEggItem::byId)
                .map(Holder::value)
                .map(ItemStack::new)
                .orElse(null);
    }
}

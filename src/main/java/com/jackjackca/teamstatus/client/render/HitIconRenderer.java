package com.jackjackca.teamstatus.client.render;

import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Flat struck-target icons for the attack animation: a player's face, or a mob's real head
 * rendered from its own model and skin texture (slightly angled, like a head item), with a
 * translucent red hit-flash overlay. Entities are reified client-side from the synced
 * {@link EntityType} only (no world presence needed) and cached; types without a resolvable
 * head model fall back to the spawn-egg icon, and types without an egg to a generic marker.
 */
public final class HitIconRenderer {

    public static final int ICON_SIZE = 14;
    /** Render depth above the 3D mining cube (z up to about half its size) but below items. */
    private static final float ICON_Z = 100.0F;
    private static final float HEAD_YAW_DEG = 28.0F;
    private static final int EGG_OFFSET = -1;
    private static final int FALLBACK_COLOR = 0xFFFF5555;

    private static final Map<EntityType<?>, LivingEntity> TEMP_ENTITIES = new java.util.HashMap<>();
    private static final Map<EntityType<?>, SpawnEggItem> EGGS = new java.util.HashMap<>();
    private static final Map<UUID, GameProfile> PROFILES = new java.util.HashMap<>();
    private static final Map<ResourceLocation, EntityType<?>> TYPES = new java.util.HashMap<>();

    private HitIconRenderer() {
    }

    /** Draws the player face; resolves asynchronously cached skins through the skin manager. */
    public static void drawPlayerFace(GuiGraphics gui, UUID playerId, String name, int x, int y) {
        drawPlayerFace(gui, playerId, name, x, y, ICON_SIZE);
    }

    /** Draws the player face at an arbitrary square size. */
    public static void drawPlayerFace(GuiGraphics gui, UUID playerId, String name, int x, int y,
                                      int size) {
        GameProfile profile = PROFILES.computeIfAbsent(playerId,
                id -> new GameProfile(id, name != null ? name : id.toString()));
        PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, ICON_Z);
        PlayerFaceRenderer.draw(gui, skin, 0, 0, size);
        pose.popPose();
    }

    /**
     * Draws the mob's head using the entity model/texture. Returns {@code false} when the
     * type or its head model cannot be resolved, so the caller can use the egg fallback.
     */
    public static boolean drawMobHead(GuiGraphics gui, ResourceLocation entityType, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        EntityType<?> type = resolveType(entityType);
        if (type == null) {
            return false;
        }
        LivingEntity entity = tempEntity(type);
        if (entity == null) {
            return false;
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> living)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        EntityModel<LivingEntity> model = (EntityModel<LivingEntity>) living.getModel();
        ModelPart head = headPart(model);
        if (head == null) {
            return false;
        }

        model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ResourceLocation texture = textureOf(living, entity);

        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x + ICON_SIZE / 2.0F, y + ICON_SIZE / 2.0F, ICON_Z);
        // Head cubes are 8 model units; scale maps them to ICON_SIZE, y flipped for GUI.
        float s = ICON_SIZE / 8.0F;
        pose.scale(s, -s, s);
        pose.mulPose(Axis.YP.rotationDegrees(HEAD_YAW_DEG));
        // Cube spans y = -8..0 around the neck pivot, so shift its center to the origin.
        pose.translate(0.0F, 4.0F, 0.0F);
        head.render(pose, gui.bufferSource().getBuffer(RenderType.entityCutoutNoCull(texture)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
        gui.flush();
        return true;
    }

    /** Spawn-egg fallback for entity types whose head cannot be rendered. */
    public static void drawMobEggFallback(GuiGraphics gui, ResourceLocation entityType, int x, int y) {
        SpawnEggItem egg = eggFor(entityType);
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, ICON_Z);
        if (egg != null) {
            gui.renderItem(new ItemStack(egg), EGG_OFFSET, EGG_OFFSET);
        } else {
            // Generic fallback: small red cross marker so the strike is still visible.
            gui.fill(3, 6, 11, 8, FALLBACK_COLOR);
            gui.fill(6, 3, 8, 11, FALLBACK_COLOR);
        }
        pose.popPose();
    }

    /**
     * Red overlay whose alpha follows the accumulated hit flash.
     *
     * @param strength 0..1
     */
    public static void drawHitFlash(GuiGraphics gui, int x, int y, float strength) {
        if (strength <= 0.0F) {
            return;
        }
        int alpha = (int) (Math.max(0.0F, Math.min(1.0F, strength)) * 255.0F) << 24;
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, ICON_Z + 1.0F);
        gui.fill(0, 0, ICON_SIZE, ICON_SIZE, alpha | 0x00FF0000);
        pose.popPose();
    }

    /** The concrete entity type is only known at runtime, so the narrowed cast stays local. */
    @SuppressWarnings("unchecked")
    private static ResourceLocation textureOf(LivingEntityRenderer<?, ?> renderer, LivingEntity entity) {
        return ((LivingEntityRenderer<LivingEntity, ?>) renderer).getTextureLocation(entity);
    }

    private static ModelPart headPart(EntityModel<LivingEntity> model) {
        if (model instanceof HeadedModel headed && headed.getHead() != null) {
            return headed.getHead();
        }
        if (model instanceof HierarchicalModel<?> hierarchical && hierarchical.root().hasChild("head")) {
            return hierarchical.root().getChild("head");
        }
        return null;
    }

    /** Drops cached temporary entities (they reference the old client level). */
    public static void clearTempEntities() {
        TEMP_ENTITIES.clear();
        EGGS.clear();
        PROFILES.clear();
        TYPES.clear();
    }

    private static SpawnEggItem eggFor(ResourceLocation entityType) {
        EntityType<?> type = resolveType(entityType);
        if (type == null) {
            return null;
        }
        // HashMap permits null values, so containsKey distinguishes a cached "no egg" from a
        // type never looked up; the egg lookup then happens at most once per type.
        if (EGGS.containsKey(type)) {
            return EGGS.get(type);
        }
        SpawnEggItem egg = SpawnEggItem.byId(type);
        EGGS.put(type, egg);
        return egg;
    }

    private static EntityType<?> resolveType(ResourceLocation id) {
        if (TYPES.containsKey(id)) {
            return TYPES.get(id);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        TYPES.put(id, type);
        return type;
    }

    private static LivingEntity tempEntity(EntityType<?> type) {
        LivingEntity cached = TEMP_ENTITIES.get(type);
        if (cached != null) {
            return cached;
        }
        net.minecraft.world.entity.Entity created = type.create(Minecraft.getInstance().level);
        if (created instanceof LivingEntity living) {
            TEMP_ENTITIES.put(type, living);
            return living;
        }
        return null;
    }
}

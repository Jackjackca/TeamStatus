package com.jackjackca.teamstatus.client.render.pip;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * PIP renderer for {@link MobHeadGuiState}. Reifies the entity client-side (no world presence,
 * cached per type, like the pre-1.21.5 implementation), extracts its {@link EntityRenderState},
 * runs {@code setupAnim}, and renders only the resolved head {@link ModelPart}.
 * <p>
 * Head cubes span y = -8..0 around the neck pivot and the 8 model units are scaled to the
 * 14&nbsp;px icon. The explicit positive Y / negative Z scaling reproduces the old GUI
 * {@code scale(s, -s, s)} y-down transform inside the flipped PIP frame.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class MobHeadGuiRenderer extends PictureInPictureRenderer<MobHeadGuiState> {

    private static final float ICON_SIZE = 14.0F;
    private static final float HEAD_UNITS = 8.0F;
    private static final float HEAD_YAW_DEG = 28.0F;

    private static final Map<EntityType<?>, LivingEntity> TEMP_ENTITIES = new HashMap<>();
    private static final Map<Identifier, EntityType<?>> TYPES = new HashMap<>();
    private static final Map<EntityType<?>, Boolean> HEAD_SUPPORTED = new HashMap<>();

    public MobHeadGuiRenderer() {
        super();
    }

    /** Whether a renderable head model can be resolved for this entity type (cached). */
    public static boolean isHeadSupported(Identifier entityType) {
        EntityType<?> type = resolveType(entityType);
        if (type == null) {
            return false;
        }
        return HEAD_SUPPORTED.computeIfAbsent(type, t -> {
            Preparation preparation = prepare(t);
            return preparation != null && preparation.head() != null;
        });
    }

    /** Drops cached temporary entities (they reference the old client level). */
    public static void clearCaches() {
        TEMP_ENTITIES.clear();
        TYPES.clear();
        HEAD_SUPPORTED.clear();
    }

    @Override
    public Class<MobHeadGuiState> getRenderStateClass() {
        return MobHeadGuiState.class;
    }

    @Override
    protected void renderToTexture(MobHeadGuiState state, PoseStack pose, SubmitNodeCollector collector) {
        EntityType<?> type = resolveType(state.entityType());
        Preparation preparation = type == null ? null : prepare(type);
        if (preparation == null || preparation.head() == null) {
            return;
        }

        float s = ICON_SIZE / HEAD_UNITS;
        pose.scale(s, s, -s);
        pose.mulPose(Axis.YP.rotationDegrees(HEAD_YAW_DEG));
        // Cube spans y = -8..0 around the neck pivot, so shift its center to the origin.
        pose.translate(0.0F, 4.0F, 0.0F);

        var renderType = RenderTypes.entityCutout(preparation.texture());
        collector.submitModelPart(preparation.head(), pose, renderType,
                15728880, 0, null);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "teamstatus_mob_head";
    }

    private static Preparation prepare(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        LivingEntity entity = tempEntity(type);
        if (entity == null) {
            return null;
        }
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return null;
        }
        EntityRenderState renderState = ((EntityRenderer) renderer).createRenderState(entity, 1.0F);
        if (!(renderState instanceof LivingEntityRenderState livingState)) {
            return null;
        }
        EntityModel model = (EntityModel) livingRenderer.getModel();
        model.setupAnim(livingState);
        ModelPart head = headPart(model);
        if (head == null) {
            return null;
        }
        Identifier texture = ((LivingEntityRenderer) livingRenderer).getTextureLocation(livingState);
        return new Preparation(head, texture);
    }

    private static ModelPart headPart(EntityModel<?> model) {
        if (model instanceof HeadedModel headed && headed.getHead() != null) {
            return headed.getHead();
        }
        ModelPart root = model.root();
        return root.hasChild("head") ? root.getChild("head") : null;
    }

    private static LivingEntity tempEntity(EntityType<?> type) {
        return TEMP_ENTITIES.computeIfAbsent(type, t -> {
            Entity created = t.create(Minecraft.getInstance().level, EntitySpawnReason.LOAD);
            return created instanceof LivingEntity living ? living : null;
        });
    }

    private static EntityType<?> resolveType(Identifier id) {
        return TYPES.computeIfAbsent(id,
                key -> BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null));
    }

    private record Preparation(ModelPart head, Identifier texture) {
    }
}

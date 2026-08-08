package com.example.teamstatus.client.gui;

import com.example.teamstatus.TeamMember;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.world.entity.EntityType.*;

@SuppressWarnings({"null", "deprecation"})
public class UseStatusRenderer {
    private static final int ITEM_SIZE = 16;
    private static final int BAR_HEIGHT = 2;
    private static final int DESTROY_STAGES = 10;
    // 预缓存所有破坏阶段纹理 ResourceLocation (仅 10 个, 避免每帧字符串拼接+对象创建)
    private static final ResourceLocation[] DESTROY_STAGE_TEXTURES = new ResourceLocation[DESTROY_STAGES];
    static {
        for (int i = 0; i < DESTROY_STAGES; i++) {
            DESTROY_STAGE_TEXTURES[i] = ResourceLocation.withDefaultNamespace("block/destroy_stage_" + i);
        }
    }
    private static final ResourceLocation ATTACK_HIT_SPRITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/enchanted_hit.png");

    private static final Map<EntityType<?>, ResourceLocation> ENTITY_TEXTURES = Map.ofEntries(
        Map.entry(ZOMBIE, ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png")),
        Map.entry(SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png")),
        Map.entry(STRAY, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/stray.png")),
        Map.entry(WITHER_SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png")),
        Map.entry(CREEPER, ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png")),
        Map.entry(SPIDER, ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png")),
        Map.entry(CAVE_SPIDER, ResourceLocation.withDefaultNamespace("textures/entity/spider/cave_spider.png")),
        Map.entry(ENDERMAN, ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png")),
        Map.entry(PIG, ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png")),
        Map.entry(COW, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png")),
        Map.entry(SHEEP, ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep.png")),
        Map.entry(CHICKEN, ResourceLocation.withDefaultNamespace("textures/entity/chicken.png")),
        Map.entry(PIGLIN, ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin.png")),
        Map.entry(ZOMBIFIED_PIGLIN, ResourceLocation.withDefaultNamespace("textures/entity/zombie_piglin.png")),
        Map.entry(GHAST, ResourceLocation.withDefaultNamespace("textures/entity/ghast/ghast.png")),
        Map.entry(SLIME, ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png")),
        Map.entry(MAGMA_CUBE, ResourceLocation.withDefaultNamespace("textures/entity/magma_cube/magma_cube.png")),
        Map.entry(BLAZE, ResourceLocation.withDefaultNamespace("textures/entity/blaze/blaze.png")),
        Map.entry(WITCH, ResourceLocation.withDefaultNamespace("textures/entity/witch.png")),
        Map.entry(IRON_GOLEM, ResourceLocation.withDefaultNamespace("textures/entity/iron_golem.png")),
        Map.entry(SNOW_GOLEM, ResourceLocation.withDefaultNamespace("textures/entity/snow_golem.png")),
        Map.entry(VILLAGER, ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png"))
    );
    // 未注册实体类型的回退纹理缓存 (避免每帧创建 ResourceLocation)
    private static final Map<EntityType<?>, ResourceLocation> ENTITY_FALLBACK_CACHE = new HashMap<>();

    public static void computeState(TeamMember member, UseStatusRenderState state) {
        ItemStack heldItem = member.getMainHandItem();
        InteractionStateMachine machine = state.getStateMachine();
        UseStatusRenderParams params = state.getRenderParams();
        params.reset();
        
        int guiTicks = Minecraft.getInstance().gui.getGuiTicks();
        
        if (heldItem.isEmpty() || !member.isUsingItem() || member.getUseDuration() <= 0) {
            state.showCooldown = false;
            state.cooldownBarWidth = 0;
        } else {
            state.showCooldown = true;
            float progress = 1.0f - ((float) member.getUseRemaining() / member.getUseDuration());
            state.cooldownBarWidth = (int) (ITEM_SIZE * Math.max(0, Math.min(1, progress)));
        }
        
        if (!heldItem.isEmpty()) {
            machine.updateHolding(guiTicks);
        } else {
            machine.clearHolding(guiTicks);
        }
        
        // 进食: 纯网络驱动 (EatingActionPayload → updateEatingState → machine.startEating)
        // 不在此处做客户端推断, 避免与网络包冲突

        // 使用工具: 纯客户端推断 (无对应网络包)
        if (member.isUsingItem() && member.getUseDuration() > 0) {
            FoodProperties food = heldItem.getItem().getFoodProperties(heldItem, null);
            if (food == null && machine.getCurrentState() == InteractionStateMachine.State.HOLDING) {
                machine.startUsing(member.getUseDuration(), guiTicks);
            }
        }
        
        machine.update(guiTicks);
        
        precomputeRenderParams(machine, state, guiTicks, heldItem);
    }

    private static void precomputeRenderParams(InteractionStateMachine machine, UseStatusRenderState state, 
                                              int guiTicks, ItemStack heldItem) {
        UseStatusRenderParams params = state.getRenderParams();
        
        switch (machine.getCurrentState()) {
            case EATING:
                precomputeEatingParams(machine, params, guiTicks, heldItem);
                break;
            case USING:
                precomputeUsingParams(machine, params, guiTicks);
                break;
            case ATTACKING:
                precomputeAttackingParams(machine, params, guiTicks);
                break;
            case MINING:
                precomputeMiningParams(machine, params, guiTicks);
                break;
            case HOLDING:
            case IDLE:
                break;
        }
    }

    private static void precomputeEatingParams(InteractionStateMachine machine, UseStatusRenderParams params, int guiTicks, ItemStack heldItem) {
        // === 原版数学来源: ItemInHandRenderer.applyEatTransform (L266-L280) ===
        // 原版参数:
        //   f  = getUseItemRemainingTicks() - partialTick + 1.0  (已消耗tick数, 从高递减到1)
        //   f1 = f / getUseDuration()  (剩余进度, 从~1.0递减到~0.0)
        //   原版中 progress = 1 - f1, 即我们的 stateProgress (0→1)
        float elapsed = guiTicks - machine.getStateStartTick();
        float progress = machine.getStateProgress(guiTicks);

        // --- 原版指数衰减曲线: f3 = 1.0 - pow(f1, 27.0) ---
        // 映射到我们的progress: f1 = 1.0 - progress
        // 因此 f3 = 1.0 - pow(1.0 - progress, 27.0)
        // 行为: progress 0→0.2 时 f3 快速升至 0.99+, 然后保持近1.0直到吃完
        // 效果: 物品迅速移向"嘴部"位置并保持, 模拟"送入口中"
        float f1 = 1.0f - progress;
        float decay = 1.0f - (float) Math.pow(f1, 27.0);

        // --- 原版Y轴抖动: |cos(f / 4.0 * PI)| * EAT_EXTRA_JIGGLE_SCALE ---
        // 半波整流余弦波, 周期 = 4 tick, 只产生向上弹跳(模拟送入嘴中的高频抖动)
        // 原版条件: f1 < 0.8 (即 progress > 0.2) 时才播放
        float bobY = 0;
        if (progress > 0.2f) {
            bobY = (float) Math.abs(Math.cos(elapsed / 4.0f * Math.PI)) * 2.0f;
        }

        // === 2D降维映射 ===
        // 原版3D变换 (叠加在基础手臂定位上):
        //   translate(f3 * 0.6, f3 * -0.5, 0)        → 手向嘴方向位移
        //   rotateY(f3 * 90° * handSign)              → 手腕扭转(3D, 2D无法映射)
        //   rotateX(f3 * 10°)                         → 前倾(2D映为Z旋转分量)
        //   rotateZ(f3 * 30° * handSign)              → 侧倾(直接映为Z旋转)
        //
        // 2D等效: 仅保留Y轴位移 (嘴部偏移 + 周期弹跳), X轴不计算
        params.wobbleY = decay * -3.0f + bobY;
        // Z旋转 = 啃咬交替晃动(sin驱动) + 渐进侧倾(原版30°的2D近似)
        params.wobbleRot = (float) Math.sin(elapsed * Math.PI / 2.0) * 5.0f * decay + decay * 12.0f;
        params.scale = 1.0f;

        // 碎屑粒子 (使用食物物品的真实贴图)
        if ((guiTicks % 4) == 0 && params.lastParticleSpawnTick != guiTicks && progress > 0.3f) {
            ResourceLocation itemId = heldItem.getItem().builtInRegistryHolder().key().location();
            params.shouldSpawnParticles = true;
            params.particleSprite = ResourceLocation.fromNamespaceAndPath(
                itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            params.particleX = ((float)Math.random() - 0.5f) * 8f;
            params.particleY = ((float)Math.random() - 0.5f) * 4f;
            params.particleCount = 3;
            params.lastParticleSpawnTick = guiTicks;
        }
    }

    private static void precomputeUsingParams(InteractionStateMachine machine, UseStatusRenderParams params, int guiTicks) {
        params.scale = 1.0f + (float) Math.sin(guiTicks * 0.5f) * 0.05f;
    }

    private static void precomputeAttackingParams(InteractionStateMachine machine, UseStatusRenderParams params, int guiTicks) {
        int hitTimeDelta = guiTicks - machine.getLastAttackTick();

        if (hitTimeDelta < 10) {
            params.flashIntensity = Mth.lerp(hitTimeDelta / 10f, 1.0f, 0.0f);
            params.shakeAngle = (float) Math.sin(hitTimeDelta * 2.0f) * (5f * (1 - hitTimeDelta / 10f));
        }

        if (hitTimeDelta == 0 && params.lastParticleSpawnTick != guiTicks) {
            params.shouldSpawnParticles = true;
            params.particleSprite = ATTACK_HIT_SPRITE;
            params.particleX = ((float)Math.random() - 0.5f) * 4f;
            params.particleY = ((float)Math.random() - 0.5f) * 4f;
            params.particleCount = 5;
            params.lastParticleSpawnTick = guiTicks;
        }

        // === 原版挥击曲线: ItemInHandRenderer.applyItemArmAttackTransform (L308-L316) ===
        // 原版: float f1 = Mth.sin(Mth.sqrt(swingProgress) * PI)
        // 替代线性lerp(-30°, 60°), 使用原版阻尼曲线驱动挥击角度
        // 行为: 25%进度即达峰值(全力劈砍), 然后75%平滑回举
        params.slashProgress = machine.getStateProgress(guiTicks);
        float swingCurve = (float) Math.sin(Math.sqrt(params.slashProgress) * Math.PI);
        // 映射: -30°(举起) → 60°(全力劈砍), 由swingCurve的非线性曲线驱动
        params.swingAngle = -30.0f + swingCurve * 90.0f;
        // 同时驱动微小平移, 模拟"突进劈砍"的打击感
        params.wobbleX = swingCurve * -3.0f;
        params.wobbleY = swingCurve * -2.0f;
    }

    private static void precomputeMiningParams(InteractionStateMachine machine, UseStatusRenderParams params, int guiTicks) {
        params.destroyStage = Mth.clamp((int) (machine.getMineProgress() * DESTROY_STAGES), 0, DESTROY_STAGES - 1);

        // === 原版挥击曲线: ItemInHandRenderer.applyItemArmAttackTransform (L308-L316) ===
        // 原版: float f1 = Mth.sin(Mth.sqrt(swingProgress) * PI)
        // 特性: sqrt将progress压缩, sin在前25%进度即达峰值(1.0), 然后75%平滑回落
        // 产生"快速下劈, 缓慢回抽"的阻尼力量感
        //
        // swingProgress 来源: LivingEntity.updateSwingTime
        //   attackAnim = swingTime / getCurrentSwingDuration()
        //   默认挥击周期 = 6 tick (即 getCurrentSwingDuration() = 6)
        int swingDuration = 6;
        float swingProgress = (float)(guiTicks % swingDuration) / swingDuration;

        float swingCurve = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);

        // 映射到Z轴旋转: 最大旋转 -60°(向左下挥击), swingCurve ∈ [0, 1]
        params.swingAngle = swingCurve * -60.0f;

        // === 原版位移公式 (ItemInHandRenderer L530-L536) ===
        //   X位移: -0.4 * sin(sqrt(progress) * PI)      → 前后突进
        //   Y位移:  0.2 * sin(sqrt(progress) * PI * 2)   → 先上后下
        // 2D降维: 利用swingCurve同时驱动X/Y微小平移, 产生"劈砍打击感"
        params.wobbleX = swingCurve * -3.0f;
        params.wobbleY = swingCurve * -2.0f;

        // 方块渲染缓存 + 粒子
        if (machine.getTargetBlock() != null) {
            Minecraft mc = Minecraft.getInstance();
            // === 使用 BlockModelShaper.getParticleIcon 获取方块纹理精灵 ===
            params.cachedBlockSprite = mc.getBlockRenderer()
                .getBlockModelShaper().getParticleIcon(machine.getTargetBlock());
            // 破坏阶段纹理: 从预缓存的静态 Sprite 数组查找 (BLOCK_ATLAS 只有 10 个, 固定路径)
            params.cachedDestroyStageSprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(
                DESTROY_STAGE_TEXTURES[params.destroyStage]);

            float mineProgress = machine.getMineProgress();
            boolean shouldBreak = mineProgress >= 1.0f;
            boolean shouldTickParticles = !shouldBreak && guiTicks % 8 == 0
                && params.lastParticleSpawnTick != guiTicks && mineProgress > 0;

            if (shouldBreak || shouldTickParticles) {
                // 粒子纹理: 使用预缓存的方块粒子纹理 (TextureAtlasSprite)
                params.shouldSpawnParticles = true;
                params.particleAtlasSprite = params.cachedBlockSprite;
                if (shouldBreak) {
                    params.particleX = ((float)Math.random() - 0.5f) * 4f;
                    params.particleY = ((float)Math.random() - 0.5f) * 4f;
                    params.particleCount = 12;
                    params.shouldResetMiningProgress = true;
                } else {
                    params.particleX = ((float)Math.random() - 0.5f) * 8f;
                    params.particleY = ((float)Math.random() - 0.5f) * 8f;
                    params.particleCount = 2;
                    params.lastParticleSpawnTick = guiTicks;
                }
            }
        }
    }

    public static void render(GuiGraphics guiGraphics, TeamMember member, int x, int y, float partialTick) {
        UseStatusRenderState state = member.getUseState();
        InteractionStateMachine machine = state.getStateMachine();
        UseStatusRenderParams params = state.getRenderParams();
        ItemStack heldItem = member.getMainHandItem();
        
        if (heldItem.isEmpty() && machine.getCurrentState() == InteractionStateMachine.State.IDLE) {
            return;
        }
        
        // 获取 guiTicks 一次, 避免 renderEating/renderMining 重复调用 Minecraft.getInstance()
        int guiTicks = Minecraft.getInstance().gui.getGuiTicks();
        
        switch (machine.getCurrentState()) {
            case EATING:
                renderEating(guiGraphics, heldItem, state, params, x, y, guiTicks, partialTick);
                break;
            case USING:
                renderUsing(guiGraphics, heldItem, state, params, x, y);
                break;
            case ATTACKING:
                renderAttacking(guiGraphics, heldItem, machine, state, params, x, y);
                break;
            case MINING:
                renderMining(guiGraphics, heldItem, machine, state, params, x, y, guiTicks, partialTick);
                break;
            case HOLDING:
                renderHolding(guiGraphics, heldItem, state, x, y);
                break;
            case IDLE:
                break;
        }
        
        if (params.shouldSpawnParticles) {
            float px = x + ITEM_SIZE / 2f + params.particleX;
            float py = y + ITEM_SIZE / 2f + params.particleY;
            if (params.particleAtlasSprite != null) {
                HudParticleManager.INSTANCE.spawnParticlesAtlas(px, py, params.particleAtlasSprite, params.particleCount);
            } else if (params.particleSprite != null) {
                HudParticleManager.INSTANCE.spawnParticles(px, py, params.particleSprite, params.particleCount);
            }
            // 重置标志, 防止下一帧(144Hz)重复生成 (computeState 在 20Hz 中设置)
            params.shouldSpawnParticles = false;
        }
        
        if (params.shouldResetMiningProgress) {
            machine.updateMiningProgress(0.0f);
        }
    }

    private static void renderHolding(GuiGraphics guiGraphics, ItemStack heldItem, UseStatusRenderState state, int x, int y) {
        guiGraphics.renderItem(heldItem, x, y);
        renderCooldown(guiGraphics, state, x, y);
    }

    private static void renderUsing(GuiGraphics guiGraphics, ItemStack heldItem, UseStatusRenderState state, 
                                   UseStatusRenderParams params, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        poseStack.translate(x + ITEM_SIZE / 2f, y + ITEM_SIZE / 2f, 0);
        poseStack.scale(params.scale, params.scale, 1.0f);
        poseStack.translate(-ITEM_SIZE / 2f, -ITEM_SIZE / 2f, 0);
        
        guiGraphics.renderItem(heldItem, 0, 0);
        poseStack.popPose();
        
        renderCooldown(guiGraphics, state, x, y);
    }

    private static void renderEating(GuiGraphics guiGraphics, ItemStack heldItem, UseStatusRenderState state,
                                    UseStatusRenderParams params, int x, int y, int guiTicks, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // === 用 partialTick 插值重新计算动画曲线 (消除20Hz量化阶梯) ===
        InteractionStateMachine machine = state.getStateMachine();
        float elapsed = (guiTicks - machine.getStateStartTick()) + partialTick;
        float progress = machine.getStateProgress(guiTicks);
        float f1 = 1.0f - progress;
        float decay = 1.0f - (float) Math.pow(f1, 27.0);
        float bobY = 0;
        if (progress > 0.2f) {
            bobY = (float) Math.abs(Math.cos(elapsed / 4.0f * Math.PI)) * 2.0f;
        }
        float wobbleY = decay * -3.0f + bobY;
        float wobbleRot = -((float) Math.sin(elapsed * Math.PI / 2.0) * 5.0f * decay + decay * 12.0f);

        // === 物理支点: 底部中心 (x+8, y+16) → 模拟"手托着食物底部" ===
        poseStack.translate(x + ITEM_SIZE / 2f, y + ITEM_SIZE, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(wobbleRot));
        poseStack.translate(-ITEM_SIZE / 2f, -ITEM_SIZE, 0);
        poseStack.translate(0, wobbleY, 0);

        guiGraphics.renderItem(heldItem, 0, 0);
        poseStack.popPose();
        renderCooldown(guiGraphics, state, x, y);
    }

    private static void renderMining(GuiGraphics guiGraphics, ItemStack heldItem, 
                                    InteractionStateMachine machine, UseStatusRenderState state,
                                    UseStatusRenderParams params, int x, int y, int guiTicks, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();

        // === 用 partialTick 插值重新计算挥击曲线 (消除6tick周期的量化阶梯) ===
        int swingDuration = 6;
        float swingProgress = (float)(guiTicks % swingDuration + partialTick) / swingDuration;
        float swingCurve = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
        float swingAngle = swingCurve * -60.0f;
        float toolWobbleX = swingCurve * -3.0f;
        float toolWobbleY = swingCurve * -2.0f;

        // === 工具渲染 (左侧): 全尺寸, 物理支点 → 右下角 ===
        poseStack.pushPose();
        poseStack.translate(x + ITEM_SIZE, y + ITEM_SIZE, 2);
        poseStack.mulPose(Axis.ZP.rotationDegrees(swingAngle));
        poseStack.translate(-ITEM_SIZE, -ITEM_SIZE, 0);
        poseStack.translate(toolWobbleX, toolWobbleY, 0);
        guiGraphics.renderItem(heldItem, 0, 0);
        poseStack.popPose();

        // === 方块渲染 (右侧): 全尺寸, 使用 blit 立即模式 ===
        if (params.cachedBlockSprite != null) {
            poseStack.pushPose();
            poseStack.translate(x + ITEM_SIZE, y, 0);

            // 1. 先绘制方块纹理 (getParticleIcon 返回的粒子纹理, 任何方块都有效)
            guiGraphics.blit(0, 0, 0, ITEM_SIZE, ITEM_SIZE, params.cachedBlockSprite);

            // 2. 再绘制破坏阶段覆盖 (立即模式, 保证在方块之上)
            if (params.cachedDestroyStageSprite != null) {
                RenderSystem.enableBlend();
                // 乘法混合 (与原版 CRUMBLING_TRANSPARENCY 一致): 裂纹纹理叠加在方块纹理上变暗
                RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR);
                guiGraphics.blit(0, 0, 0, ITEM_SIZE, ITEM_SIZE, params.cachedDestroyStageSprite);
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            }

            // 3. 耐久条风格破坏进度 (底部, 绿→黄→红 渐变)
            float progress = Mth.clamp(machine.getMineProgress(), 0, 1);
            if (progress > 0) {
                int barWidth = (int) (ITEM_SIZE * progress);
                int barColor = Mth.color(1.0f - progress, progress, 0.0f); // 绿→红
                guiGraphics.fill(0, ITEM_SIZE - BAR_HEIGHT, ITEM_SIZE, ITEM_SIZE, 0xFF373737); // 背景(灰)
                guiGraphics.fill(0, ITEM_SIZE - BAR_HEIGHT, barWidth, ITEM_SIZE, 0xFF000000 | barColor); // 进度(渐变)
            }

            poseStack.popPose();
        }
    }

    private static void renderAttacking(GuiGraphics guiGraphics, ItemStack heldItem,
                                       InteractionStateMachine machine, UseStatusRenderState state,
                                       UseStatusRenderParams params, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();

        // === 武器渲染 (左侧): 全尺寸, 物理支点 → 右下角 ===
        //
        // 矩阵入栈流程:
        //   1. translate(pivot)      → 移至武器右下角 (支点)
        //   2. mulPose(Z旋转)        → 围绕支点旋转 (原版sin(sqrt)*PI阻尼曲线)
        //   3. translate(-16, -16)   → 反向平移, 使物品右下角对齐支点
        //   4. translate(wobble)     → 施加劈砍位移
        //   5. renderItem            → 渲染武器 (全尺寸)
        //   6. popPose               → 恢复矩阵
        poseStack.pushPose();
        poseStack.translate(x + ITEM_SIZE, y + ITEM_SIZE, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(params.swingAngle));
        poseStack.translate(-ITEM_SIZE, -ITEM_SIZE, 0);
        poseStack.translate(params.wobbleX, params.wobbleY, 0);
        guiGraphics.renderItem(heldItem, 0, 0);
        poseStack.popPose();

        // === 目标实体渲染 (右侧): 全尺寸, 命中闪烁 + 震动 ===
        if (machine.getTargetEntity() != null) {
            poseStack.pushPose();
            poseStack.translate(x + ITEM_SIZE, y, 0);

            if (params.shakeAngle != 0) {
                poseStack.translate(ITEM_SIZE / 2f, ITEM_SIZE / 2f, 0);
                poseStack.mulPose(Axis.ZP.rotationDegrees(params.shakeAngle));
                poseStack.translate(-ITEM_SIZE / 2f, -ITEM_SIZE / 2f, 0);
            }

            renderEntityIcon(guiGraphics, machine.getTargetEntity(), 0, 0);

            if (params.flashIntensity > 0) {
                guiGraphics.fill(0, 0, ITEM_SIZE, ITEM_SIZE, 
                    (int)(params.flashIntensity * 0xAA) << 24 | 0xFF0000);
            }

            poseStack.popPose();
        }
    }

    private static void renderEntityIcon(GuiGraphics guiGraphics, net.minecraft.world.entity.EntityType<?> entityType, int x, int y) {
        ResourceLocation texture = ENTITY_TEXTURES.get(entityType);
        if (texture == null) {
            texture = ENTITY_FALLBACK_CACHE.computeIfAbsent(entityType,
                et -> ResourceLocation.withDefaultNamespace("textures/entity/" + et.builtInRegistryHolder().key().location().getPath() + "/" + et.builtInRegistryHolder().key().location().getPath() + ".png"));
        }
        guiGraphics.blit(texture, x, y, 0, 0, ITEM_SIZE, ITEM_SIZE, 64, 64);
    }

    private static void renderCooldown(GuiGraphics guiGraphics, UseStatusRenderState state, int x, int y) {
        if (state.showCooldown) {
            guiGraphics.fill(x, y + ITEM_SIZE + 1, x + ITEM_SIZE, y + ITEM_SIZE + 1 + BAR_HEIGHT, 0xFF373737);
            guiGraphics.fill(x, y + ITEM_SIZE + 1, x + state.cooldownBarWidth, y + ITEM_SIZE + 1 + BAR_HEIGHT, 0xFF00BCD4);
        }
    }
}
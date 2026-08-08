package com.example.teamstatus;

import com.example.teamstatus.client.gui.AvatarRenderState;
import com.example.teamstatus.client.gui.AvatarRenderer;
import com.example.teamstatus.client.gui.HeartRenderState;
import com.example.teamstatus.client.gui.HeartTextures;
import com.example.teamstatus.client.gui.HeartType;
import com.example.teamstatus.client.gui.HungerBarRenderer;
import com.example.teamstatus.client.gui.HungerRenderState;
import com.example.teamstatus.client.gui.InteractionStateMachine;
import com.example.teamstatus.client.gui.UseStatusRenderState;
import com.example.teamstatus.client.gui.UseStatusRenderer;
import com.example.teamstatus.network.PlayerStatusPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

@SuppressWarnings("null")
public class TeamMember {
    protected final UUID playerId;
    protected final String playerName;
    protected Player player;
    protected float health;
    protected float maxHealth;
    protected int hunger;
    protected int maxHunger;
    protected float saturation;
    protected float exhaustion;
    
    protected boolean isUsingItem;
    protected ItemStack mainHandItem;
    protected int useDuration;
    protected int useRemaining;
    
    // 动画状态
    protected float prevHealth;
    protected int hurtTime;
    protected int blinkRemaining;      // 闪烁倒计时（tick），替代原 System.currentTimeMillis() 时间戳
    protected int invulnerableTime;    // 无敌时间，用于控制回血闪烁
    public static final int AVATAR_TINT_NONE = 0;
    public static final int AVATAR_TINT_HURT = 1;
    public static final int AVATAR_TINT_HEAL = 2;
    protected int avatarTint;
    protected boolean isHealBlink;

    // 心形渲染预计算缓存（20Hz tick 阶段填充，render 阶段只读）
    private static final int MAX_HEARTS = 20;
    private final HeartRenderState[] heartStates;
    {
        heartStates = new HeartRenderState[MAX_HEARTS];
        for (int i = 0; i < MAX_HEARTS; i++) heartStates[i] = new HeartRenderState();
    }
    protected int activeHeartCount;

    // 头像/食物条/物品使用 预计算缓存（20Hz tick 阶段填充，render 阶段只读）
    private final AvatarRenderState avatarState = new AvatarRenderState();
    private final HungerRenderState hungerState = new HungerRenderState();
    private final UseStatusRenderState useState = new UseStatusRenderState();
    
    // 效果/状态字段
    protected byte heartType;
    protected float absorption;
    protected boolean isHardcore;
    protected boolean hasRegeneration;
    protected boolean hasHungerEffect;
    
    
    public TeamMember(Player player) {
        this.player = player;
        this.playerId = player.getUUID();
        this.playerName = player.getName().getString();
        this.maxHunger = 20;
        this.mainHandItem = ItemStack.EMPTY;
        update();
    }
    
    public TeamMember(UUID playerId, String playerName) {
        this.player = null;
        this.playerId = playerId;
        this.playerName = playerName;
        this.maxHunger = 20;
        this.mainHandItem = ItemStack.EMPTY;
    }

    public void update() {
        if (player == null) return;
        
        this.prevHealth = this.health;
        
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.hunger = player.getFoodData().getFoodLevel();
        this.saturation = player.getFoodData().getSaturationLevel();
        this.exhaustion = player.getFoodData().getExhaustionLevel();
        
        this.heartType = HeartType.fromPlayerState(player).getId();
        this.absorption = player.getAbsorptionAmount();
        this.isHardcore = player.level().getLevelData().isHardcore();
        this.hasRegeneration = player.hasEffect(MobEffects.REGENERATION);
        this.hasHungerEffect = player.hasEffect(MobEffects.HUNGER);
        
        this.isUsingItem = player.isUsingItem();
        if (!this.isUsingItem) {
            this.useDuration = 0;
            this.useRemaining = 0;
        }
        
        this.mainHandItem = player.getMainHandItem().copy();

        // 动画状态：受伤/回血检测
        if (Float.compare(this.health, this.prevHealth) < 0) {
            this.hurtTime = 10;
            this.blinkRemaining = 20;
            this.invulnerableTime = 20;
            this.isHealBlink = false;
        } else if (Float.compare(this.health, this.prevHealth) > 0) {
            if (this.invulnerableTime > 0) {
                this.blinkRemaining = 10;
                this.isHealBlink = true;
            }
        }
    }

    public void updateFromPayload(PlayerStatusPayload payload) {
        byte dirty = payload.dirtyFields();
        if (dirty == 0) return;  // 心跳包，无数据更新
        
        this.prevHealth = this.health;
        
        if ((dirty & PlayerStatusPayload.HEALTH) != 0) {
            this.health = payload.health();
            this.maxHealth = payload.maxHealth();
        }
        if ((dirty & PlayerStatusPayload.HUNGER) != 0) {
            this.hunger = payload.hunger();
            this.maxHunger = 20;
            this.saturation = payload.saturation();
            this.exhaustion = payload.exhaustion();
        }
        if ((dirty & PlayerStatusPayload.USE) != 0) {
            this.isUsingItem = payload.isUsingItem();
            this.useDuration = payload.useDuration();
            this.useRemaining = payload.useRemaining();
        }
        if ((dirty & PlayerStatusPayload.ITEM) != 0) {
            this.mainHandItem = payload.mainHandItem().copy();
        }
        if ((dirty & PlayerStatusPayload.EFFECTS) != 0) {
            this.heartType = payload.heartType();
            this.absorption = payload.absorption();
            this.isHardcore = payload.isHardcore();
            this.hasRegeneration = payload.hasRegeneration();
            this.hasHungerEffect = payload.hasHungerEffect();
        }
        
        // 动画状态：受伤/回血检测
        if (Float.compare(this.health, this.prevHealth) < 0) {
            this.hurtTime = 10;
            this.blinkRemaining = 20;  // 受伤闪烁 20 tick
            this.invulnerableTime = 20;
            this.isHealBlink = false;
        } else if (Float.compare(this.health, this.prevHealth) > 0) {
            if (this.invulnerableTime > 0 && this.invulnerableTime < 20) {
                this.blinkRemaining = 10;  // 回血闪烁 10 tick
                this.isHealBlink = true;
            }
        }
    }
    
    public Player getPlayer() { return player; }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public int getHunger() { return hunger; }
    public int getMaxHunger() { return maxHunger; }
    public float getSaturation() { return saturation; }
    public float getExhaustion() { return exhaustion; }
    public float getHealthPercent() { return maxHealth > 0 ? health / maxHealth : 0; }
    public float getHungerPercent() { return maxHunger > 0 ? (float) hunger / maxHunger : 0; }
    public boolean isUsingItem() { return isUsingItem; }
    public void setUsingItem(boolean using) { this.isUsingItem = using; }
    public ItemStack getMainHandItem() { return mainHandItem; }
    public int getUseDuration() { return useDuration; }
    public int getUseRemaining() { return useRemaining; }
    public void setUseProgress(int duration, int remaining) { this.useDuration = duration; this.useRemaining = remaining; }
    
    // 效果/状态 getters
    public byte getHeartType() { return heartType; }
    public HeartType getHeartTypeEnum() { return HeartType.fromId(heartType); }
    public float getAbsorption() { return absorption; }
    public boolean isHardcore() { return isHardcore; }
    public boolean hasRegeneration() { return hasRegeneration; }
    public boolean hasHungerEffect() { return hasHungerEffect; }
    
    // 动画状态（已转为内部状态，外部仅通过预计算缓存访问）
    public int getHurtTime() { return hurtTime; }
    public float getPrevHealth() { return prevHealth; }
    
    // 心形预计算缓存访问器
    public HeartRenderState[] getHeartStates() { return heartStates; }
    public int getActiveHeartCount() { return activeHeartCount; }

    // 其他预计算缓存访问器
    public AvatarRenderState getAvatarState() { return avatarState; }
    public HungerRenderState getHungerState() { return hungerState; }
    public UseStatusRenderState getUseState() { return useState; }
    
    /**
     * 20Hz tick 阶段：递减计时器 + 闪烁状态 + 预计算所有心形渲染参数。
     * 由 TeamTracker.onRenderTick 调用，guiTicks 由调用方传入以保持同步。
     */
    public void tickAnimations(int guiTicks) {
        if (hurtTime > 0) hurtTime--;
        if (invulnerableTime > 0) invulnerableTime--;
        if (blinkRemaining > 0) blinkRemaining--;
        
        // 头像闪烁 tint
        if (blinkRemaining > 0 && blinkRemaining / 3 % 2 == 1) {
            avatarTint = isHealBlink ? AVATAR_TINT_HEAL : AVATAR_TINT_HURT;
        } else {
            avatarTint = AVATAR_TINT_NONE;
        }
        
        // 预计算所有渲染状态
        computeHeartStates(guiTicks);
        AvatarRenderer.computeState(this, avatarState);
        HungerBarRenderer.computeState(this, hungerState, guiTicks);
        UseStatusRenderer.computeState(this, useState);
    }
    
    /**
     * 在 20Hz tick 阶段预计算每颗心的完整渲染参数。
     * 消除 144Hz+ 渲染循环中的 Math.ceil / Random / ResourceLocation 创建。
     */
    private void computeHeartStates(int guiTicks) {
        int heartCount = Math.min((int) Math.ceil(maxHealth / 2.0f), 10);
        int hp = (int) Math.ceil(health);
        int prevHp = (int) Math.ceil(prevHealth);
        int absHearts = (int) Math.ceil(absorption / 2.0f);
        
        boolean blinking = blinkRemaining > 0 && blinkRemaining / 3 % 2 == 1;
        
        int totalHearts = Math.min(heartCount + absHearts, MAX_HEARTS);
        this.activeHeartCount = totalHearts;
        
        // 再生跳动索引
        int regenIndex = -1;
        if (hasRegeneration) {
            regenIndex = guiTicks % (int) Math.ceil(maxHealth + 5.0f);
        }
        
        // 确定性随机种子（与原版 guiTicks * 312871L 一致）
        Util.setRandomSeed((long) (guiTicks * 312871L));
        
        HeartType type = getHeartTypeEnum();
        ResourceLocation containerTex = blinking ? HeartTextures.CONTAINER_BLINK : HeartTextures.CONTAINER;
        
        for (int i = 0; i < totalHearts; i++) {
            HeartRenderState s = heartStates[i];
            
            int yOffset = 0;
            // 低血量抖动：血量 + 吸收 <= 4
            if (hp + absHearts <= 4) {
                yOffset += Util.getRandom().nextInt(2);
            }
            // 再生跳动
            if (i < heartCount && i == regenIndex) {
                yOffset -= 2;
            }
            
            s.container = containerTex;
            s.yOffset = yOffset;
            
            boolean isAbsorptionHeart = i >= heartCount;
            
            if (isAbsorptionHeart) {
                int absIndex = i - heartCount;
                int absHp = (int) Math.ceil(absorption);
                int absPoint = absIndex * 2;
                if (absPoint < absHp) {
                    boolean halfHeart = absPoint + 1 == absHp;
                    s.fill = HeartTextures.get(type, halfHeart);
                } else {
                    s.fill = null;
                }
                s.diff = null;
            } else {
                // 前景填充
                if (i * 2 < hp) {
                    boolean halfHeart = i * 2 + 1 == hp;
                    s.fill = HeartTextures.get(type, halfHeart);
                } else {
                    s.fill = null;
                }
                
                // 受伤差值覆盖
                if (blinking && prevHp != hp) {
                    int prevFilled = Math.min(Math.max(prevHp - i * 2, 0), 2);
                    int curFilled = Math.min(Math.max(hp - i * 2, 0), 2);
                    if (prevFilled != curFilled) {
                        if (prevFilled >= 2) {
                            s.diff = HeartTextures.get(type, false);
                        } else if (prevFilled == 1) {
                            s.diff = HeartTextures.get(type, true);
                        } else {
                            s.diff = null;
                        }
                    } else {
                        s.diff = null;
                    }
                } else {
                    s.diff = null;
                }
            }
        }
        
        // 清理残留状态（如果心数减少）
        for (int i = totalHearts; i < MAX_HEARTS; i++) {
            heartStates[i].reset();
        }
    }
    
    public int getAvatarTint() { return avatarTint; }

    // === 交互状态更新方法（状态机驱动）===

    public void updateMiningState(BlockState targetBlock, float mineProgress, boolean isActive, int guiTicks) {
        InteractionStateMachine machine = useState.getStateMachine();
        if (isActive) {
            if (machine.getCurrentState() != InteractionStateMachine.State.MINING) {
                machine.startMining(targetBlock, guiTicks);
            } else if (machine.getTargetBlock() != targetBlock) {
                machine.updateMiningTarget(targetBlock);
                machine.updateMiningProgress(0.0f);
            }
            float currentProgress = machine.getMineProgress();
            float newProgress = Math.min(1.0f, currentProgress + mineProgress);
            machine.updateMiningProgress(newProgress);
        } else {
            machine.stopInteraction(guiTicks);
        }
    }

    public void updateAttackState(EntityType<?> targetEntity, boolean isActive, int guiTicks) {
        InteractionStateMachine machine = useState.getStateMachine();
        if (isActive && targetEntity != null) {
            machine.startAttacking(targetEntity, guiTicks);
        } else {
            machine.stopInteraction(guiTicks);
        }
    }

    public void updateEatingState(int actionDuration, boolean isActive, int guiTicks) {
        InteractionStateMachine machine = useState.getStateMachine();
        if (isActive) {
            machine.startEating(actionDuration, guiTicks);
        } else {
            machine.stopInteraction(guiTicks);
        }
    }

    public void clearInteractionState(int guiTicks) {
        useState.getStateMachine().transitionTo(InteractionStateMachine.State.IDLE, guiTicks);
    }
}

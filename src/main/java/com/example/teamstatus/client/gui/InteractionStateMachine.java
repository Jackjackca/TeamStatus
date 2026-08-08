package com.example.teamstatus.client.gui;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

public class InteractionStateMachine {
    
    public enum State {
        IDLE,
        HOLDING,
        EATING,
        USING,
        ATTACKING,
        MINING
    }
    
    private State currentState = State.IDLE;
    private State previousState = State.IDLE;
    private int stateStartTick;
    private int actionDuration;
    
    private BlockState targetBlock;
    private float mineProgress;
    
    private EntityType<?> targetEntity;
    private int lastAttackTick;
    
    public void update(int guiTicks) {
        switch (currentState) {
            case EATING:
            case USING:
                if (actionDuration > 0 && guiTicks - stateStartTick >= actionDuration) {
                    transitionTo(State.HOLDING, guiTicks);
                }
                break;
            case ATTACKING:
                if (guiTicks - stateStartTick >= 20) {
                    transitionTo(State.HOLDING, guiTicks);
                }
                break;
            case MINING:
                if (mineProgress >= 1.0f) {
                    transitionTo(State.HOLDING, guiTicks);
                }
                break;
            case HOLDING:
                break;
            case IDLE:
                break;
        }
    }
    
    public void transitionTo(State newState, int guiTicks) {
        if (newState == currentState) {
            if (newState == State.MINING || newState == State.ATTACKING) {
                stateStartTick = guiTicks;
            }
            return;
        }
        
        previousState = currentState;
        currentState = newState;
        stateStartTick = guiTicks;
        
        switch (newState) {
            case IDLE:
                resetActionState();
                break;
            case HOLDING:
                actionDuration = 0;
                targetBlock = null;
                mineProgress = 0.0f;
                targetEntity = null;
                break;
            case EATING:
                targetBlock = null;
                mineProgress = 0.0f;
                break;
            case USING:
                targetBlock = null;
                mineProgress = 0.0f;
                break;
            case ATTACKING:
                actionDuration = 20;
                targetBlock = null;
                mineProgress = 0.0f;
                break;
            case MINING:
                actionDuration = 0;
                break;
        }
    }
    
    public void startEating(int duration, int guiTicks) {
        this.actionDuration = duration;
        transitionTo(State.EATING, guiTicks);
    }
    
    public void startUsing(int duration, int guiTicks) {
        this.actionDuration = duration;
        transitionTo(State.USING, guiTicks);
    }
    
    public void startAttacking(EntityType<?> target, int guiTicks) {
        this.targetEntity = target;
        this.lastAttackTick = guiTicks;
        transitionTo(State.ATTACKING, guiTicks);
    }
    
    public void startMining(BlockState block, int guiTicks) {
        this.targetBlock = block;
        this.mineProgress = 0.0f;
        transitionTo(State.MINING, guiTicks);
    }
    
    public void updateMiningTarget(BlockState block) {
        this.targetBlock = block;
        this.mineProgress = 0.0f;
    }
    
    public void updateMiningProgress(float progress) {
        if (currentState == State.MINING) {
            this.mineProgress = progress;
        }
    }
    
    public void stopInteraction(int guiTicks) {
        if (currentState == State.HOLDING) {
            transitionTo(State.IDLE, guiTicks);
        } else {
            transitionTo(State.HOLDING, guiTicks);
        }
    }
    
    private void resetActionState() {
        this.actionDuration = 0;
        this.targetBlock = null;
        this.mineProgress = 0.0f;
        this.targetEntity = null;
        this.lastAttackTick = 0;
    }
    
    public void updateHolding(int guiTicks) {
        if (currentState == State.IDLE) {
            transitionTo(State.HOLDING, guiTicks);
        }
    }
    
    public void clearHolding(int guiTicks) {
        if (currentState == State.HOLDING) {
            transitionTo(State.IDLE, guiTicks);
        }
    }
    
    public State getCurrentState() {
        return currentState;
    }
    
    public State getPreviousState() {
        return previousState;
    }
    
    public int getStateStartTick() {
        return stateStartTick;
    }
    
    public int getActionDuration() {
        return actionDuration;
    }
    
    public float getStateProgress(int guiTicks) {
        if (actionDuration <= 0) return 0.0f;
        return Math.min(1.0f, (float)(guiTicks - stateStartTick) / actionDuration);
    }
    
    public BlockState getTargetBlock() {
        return targetBlock;
    }
    
    public float getMineProgress() {
        return mineProgress;
    }
    
    public EntityType<?> getTargetEntity() {
        return targetEntity;
    }
    
    public int getLastAttackTick() {
        return lastAttackTick;
    }
    
    public boolean isActive() {
        return currentState != State.IDLE;
    }
    
    public boolean isInteracting() {
        return currentState == State.EATING || currentState == State.USING 
            || currentState == State.ATTACKING || currentState == State.MINING;
    }
}
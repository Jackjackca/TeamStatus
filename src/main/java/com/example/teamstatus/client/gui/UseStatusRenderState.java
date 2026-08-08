package com.example.teamstatus.client.gui;

public class UseStatusRenderState {
    public boolean showCooldown;
    public int cooldownBarWidth;
    
    private final InteractionStateMachine stateMachine = new InteractionStateMachine();
    private final UseStatusRenderParams renderParams = new UseStatusRenderParams();
    
    public InteractionStateMachine getStateMachine() {
        return stateMachine;
    }
    
    public UseStatusRenderParams getRenderParams() {
        return renderParams;
    }
}
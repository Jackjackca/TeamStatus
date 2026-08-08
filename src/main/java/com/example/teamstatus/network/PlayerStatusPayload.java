package com.example.teamstatus.network;

import com.example.teamstatus.client.gui.HeartType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

@SuppressWarnings("null")
public record PlayerStatusPayload(
    byte dirtyFields,
    UUID playerId,
    String playerName,
    float health,
    float maxHealth,
    int hunger,
    float saturation,
    float exhaustion,
    boolean isUsingItem,
    ItemStack mainHandItem,
    int useDuration,
    int useRemaining,
    byte heartType,
    float absorption,
    boolean isHardcore,
    boolean hasRegeneration,
    boolean hasHungerEffect
) implements CustomPacketPayload {
    
    public static final byte HEALTH = 1;
    public static final byte HUNGER = 2;
    public static final byte USE = 4;
    public static final byte ITEM = 8;
    public static final byte EFFECTS = 16;
    public static final byte FULL_STATUS = (byte) (HEALTH | HUNGER | USE | ITEM | EFFECTS);
    
    public static final Type<PlayerStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamstatus", "player_status"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStatusPayload> STREAM_CODEC = StreamCodec.of(
        PlayerStatusPayload::encode,
        PlayerStatusPayload::decode
    );
    
    public static PlayerStatusPayload heartbeat(UUID playerId, String playerName) {
        return new PlayerStatusPayload((byte) 0, playerId, playerName,
            0, 0, 20, 0, 0, false, ItemStack.EMPTY, 0, 0,
            HeartType.NORMAL.getId(), 0, false, false, false);
    }
    
    public static PlayerStatusPayload full(UUID playerId, String playerName,
            float health, float maxHealth, int hunger, float saturation, float exhaustion,
            boolean isUsingItem, ItemStack mainHandItem, int useDuration, int useRemaining,
            byte heartType, float absorption, boolean isHardcore, boolean hasRegeneration, boolean hasHungerEffect) {
        return new PlayerStatusPayload(FULL_STATUS, playerId, playerName,
            health, maxHealth, hunger, saturation, exhaustion, isUsingItem, mainHandItem.copy(), useDuration, useRemaining,
            heartType, absorption, isHardcore, hasRegeneration, hasHungerEffect);
    }
    
    private static void encode(RegistryFriendlyByteBuf buf, PlayerStatusPayload payload) {
        buf.writeByte(payload.dirtyFields());
        buf.writeUUID(payload.playerId());
        buf.writeUtf(payload.playerName());
        
        byte dirty = payload.dirtyFields();
        if ((dirty & HEALTH) != 0) {
            buf.writeFloat(payload.health());
            buf.writeFloat(payload.maxHealth());
        }
        if ((dirty & HUNGER) != 0) {
            buf.writeInt(payload.hunger());
            buf.writeFloat(payload.saturation());
            buf.writeFloat(payload.exhaustion());
        }
        if ((dirty & USE) != 0) {
            buf.writeBoolean(payload.isUsingItem());
            buf.writeInt(payload.useDuration());
            buf.writeInt(payload.useRemaining());
        }
        if ((dirty & ITEM) != 0) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.mainHandItem());
        }
        if ((dirty & EFFECTS) != 0) {
            buf.writeByte(payload.heartType());
            buf.writeFloat(payload.absorption());
            buf.writeBoolean(payload.isHardcore());
            buf.writeBoolean(payload.hasRegeneration());
            buf.writeBoolean(payload.hasHungerEffect());
        }
    }
    
    private static PlayerStatusPayload decode(RegistryFriendlyByteBuf buf) {
        byte dirty = buf.readByte();
        UUID playerId = buf.readUUID();
        String playerName = buf.readUtf();
        
        float health = 0, maxHealth = 20, saturation = 0, exhaustion = 0;
        int hunger = 20, useDuration = 0, useRemaining = 0;
        boolean isUsingItem = false;
        ItemStack mainHandItem = ItemStack.EMPTY;
        byte heartType = HeartType.NORMAL.getId();
        float absorption = 0;
        boolean isHardcore = false, hasRegeneration = false, hasHungerEffect = false;
        
        if ((dirty & HEALTH) != 0) {
            health = buf.readFloat();
            maxHealth = buf.readFloat();
        }
        if ((dirty & HUNGER) != 0) {
            hunger = buf.readInt();
            saturation = buf.readFloat();
            exhaustion = buf.readFloat();
        }
        if ((dirty & USE) != 0) {
            isUsingItem = buf.readBoolean();
            useDuration = buf.readInt();
            useRemaining = buf.readInt();
        }
        if ((dirty & ITEM) != 0) {
            mainHandItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf).copy();
        }
        if ((dirty & EFFECTS) != 0) {
            heartType = buf.readByte();
            absorption = buf.readFloat();
            isHardcore = buf.readBoolean();
            hasRegeneration = buf.readBoolean();
            hasHungerEffect = buf.readBoolean();
        }
        
        return new PlayerStatusPayload(dirty, playerId, playerName,
            health, maxHealth, hunger, saturation, exhaustion,
            isUsingItem, mainHandItem, useDuration, useRemaining,
            heartType, absorption, isHardcore, hasRegeneration, hasHungerEffect);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

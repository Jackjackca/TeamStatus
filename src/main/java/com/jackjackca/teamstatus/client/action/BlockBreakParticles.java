package com.jackjackca.teamstatus.client.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Client-only 2D "block debris" particles for the mining target. Vanilla break particles are
 * world particles that cross-dimension teammates never receive, so the HUD fakes the same
 * look with small textured quads using the block's particle sprite
 * ({@code BakedModel#getParticleIcon}).
 * <p>
 * Coordinates are relative to the rendered target's center (pixels, y-down); the visual
 * layer adds the on-screen center. Motion is the closed parabola
 * {@code y = y0 + vy*t + 0.5*g*t^2} evaluated with {@code age + partialTick}, so tick
 * advancement and render interpolation never disagree. All access is on the client main
 * thread.
 */
public final class BlockBreakParticles {

    public static final BlockBreakParticles INSTANCE = new BlockBreakParticles();

    private static final int BURST_COUNT = 12;
    private static final int CHIP_COUNT = 2;
    private static final float GRAVITY = 0.22F;
    private static final int MIN_LIFE = 10;
    private static final int LIFE_SPREAD = 8;
    private static final int PARTICLE_SIZE = 2;

    private final Map<UUID, List<Chip>> particles = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    private BlockBreakParticles() {
    }

    /** Big debris cloud when the block finishes breaking. */
    public void breakBurst(UUID memberId, BlockState state) {
        TextureAtlasSprite sprite = particleIcon(state);
        if (sprite == null) {
            return;
        }
        List<Chip> chips = particles.computeIfAbsent(memberId, k -> new ArrayList<>());
        for (int i = 0; i < BURST_COUNT; i++) {
            chips.add(new Chip(sprite,
                    (random.nextFloat() - 0.5F) * 14.0F,
                    (random.nextFloat() - 0.5F) * 4.0F,
                    (random.nextFloat() - 0.5F) * 1.6F,
                    -random.nextFloat() * 2.2F - 0.4F,
                    MIN_LIFE + random.nextInt(LIFE_SPREAD)));
        }
    }

    /** A couple of small chips when the crack stage advances. */
    public void stageChips(UUID memberId, BlockState state) {
        TextureAtlasSprite sprite = particleIcon(state);
        if (sprite == null) {
            return;
        }
        List<Chip> chips = particles.computeIfAbsent(memberId, k -> new ArrayList<>());
        for (int i = 0; i < CHIP_COUNT; i++) {
            chips.add(new Chip(sprite,
                    (random.nextFloat() - 0.5F) * 10.0F,
                    random.nextFloat() * 2.0F,
                    (random.nextFloat() - 0.5F) * 1.0F,
                    -random.nextFloat() * 1.4F,
                    MIN_LIFE + random.nextInt(LIFE_SPREAD)));
        }
    }

    public void clientTick() {
        Iterator<Map.Entry<UUID, List<Chip>>> it = particles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Chip>> entry = it.next();
            entry.getValue().removeIf(chip -> ++chip.age >= chip.life);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /** Renders debris around the target center. */
    public void render(GuiGraphics gui, UUID memberId, float centerX, float centerY, float partialTick) {
        List<Chip> chips = particles.get(memberId);
        if (chips == null || chips.isEmpty()) {
            return;
        }
        // Above the 3D cube (writes real depth) and the item: particles are 2D sprites at a
        // fixed high z so the cube cannot occlude them.
        var pose = gui.pose();
        pose.pushPose();
        // The sprite blit only accepts integer x/y; do the sub-pixel placement with a
        // floating pose translation so motion stays smooth at high refresh rates instead
        // of snapping to whole pixels (which read as a 20 Hz stutter).
        pose.translate(centerX, centerY, 200.0F);
        for (Chip chip : chips) {
            float t = chip.age + partialTick;
            float x = chip.x0 + chip.vx * t;
            float y = chip.y0 + chip.vy * t + 0.5F * GRAVITY * t * t;
            // Continuous fade over the last 4 ticks of life.
            float remaining = chip.life - t;
            float alpha = Math.max(0.0F, Math.min(1.0F, remaining / 4.0F));
            gui.setColor(1.0F, 1.0F, 1.0F, alpha);
            pose.pushPose();
            pose.translate(x, y, 0.0F);
            gui.blit(0, 0, 0, PARTICLE_SIZE, PARTICLE_SIZE, chip.sprite);
            pose.popPose();
        }
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        gui.flush();
    }

    public void retainOnly(Iterable<UUID> presentMemberIds) {
        java.util.Set<UUID> set = new java.util.HashSet<>();
        presentMemberIds.forEach(set::add);
        particles.keySet().retainAll(set);
    }

    public void clear() {
        particles.clear();
    }

    private static TextureAtlasSprite particleIcon(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state)
                .getParticleIcon(ModelData.EMPTY);
    }

    private static final class Chip {
        private final TextureAtlasSprite sprite;
        private final float x0;
        private final float y0;
        private final float vx;
        private final float vy;
        private final int life;
        private int age;

        private Chip(TextureAtlasSprite sprite, float x0, float y0, float vx, float vy, int life) {
            this.sprite = sprite;
            this.x0 = x0;
            this.y0 = y0;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
        }
    }
}

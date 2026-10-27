package com.jackjackca.teamstatus.client.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-only 2D "block debris" particles for the mining target. Vanilla break particles are
 * world particles that cross-dimension teammates never receive, so the HUD fakes the same
 * look with small textured quads using the block's particle sprite
 * ({@code BlockStateModel#particleIcon}).
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
    public void render(GuiGraphicsExtractor gui, UUID memberId, float centerX, float centerY, float partialTick) {
        List<Chip> chips = particles.get(memberId);
        if (chips == null || chips.isEmpty()) {
            return;
        }
        // Particles are 2D sprites submitted in frame order after the 3D cube PIP element.
        var pose = gui.pose();
        pose.pushMatrix();
        // The sprite blit only accepts integer x/y; do the sub-pixel placement with a
        // floating pose translation so motion stays smooth at high refresh rates instead
        // of snapping to whole pixels (which read as a 20 Hz stutter).
        pose.translate(centerX, centerY);
        for (Chip chip : chips) {
            float t = chip.age + partialTick;
            float x = chip.x0 + chip.vx * t;
            float y = chip.y0 + chip.vy * t + 0.5F * GRAVITY * t * t;
            // Continuous fade over the last 4 ticks of life.
            float remaining = chip.life - t;
            float alpha = Math.max(0.0F, Math.min(1.0F, remaining / 4.0F));
            int color = ((int) (alpha * 255.0F) << 24) | 0x00FFFFFF;
            pose.pushMatrix();
            pose.translate(x, y);
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, chip.sprite,
                    0, 0, PARTICLE_SIZE, PARTICLE_SIZE, color);
            pose.popMatrix();
        }
        pose.popMatrix();
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
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        ItemStackRenderState renderState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(renderState, stack,
                ItemDisplayContext.NONE, mc.level, null, 0);
        Material.Baked baked = renderState.pickParticleMaterial(RandomSource.create());
        return baked != null ? baked.sprite() : null;
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

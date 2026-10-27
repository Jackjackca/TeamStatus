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

/**
 * Client-only 2D "food crumbs / drink droplets" particles for the item-use (eat/drink)
 * action, structured exactly like {@link BlockBreakParticles}: small textured quads tinted
 * from the used item's model particle icon ({@code ItemStackRenderState#pickParticleIcon}),
 * emitted one per chew tick while using and as a larger burst when the item is consumed.
 * <p>
 * Coordinates are relative to the rendered item slot's center (pixels, y-down); the visual
 * layer adds the on-screen center. Motion is the closed parabola
 * {@code y = y0 + vy*t + 0.5*g*t^2} evaluated with {@code age + partialTick}. Crumbs flick
 * upward off the food and then fall, unlike mining debris which only falls. All access is on
 * the client main thread.
 */
public final class UseActionParticles {

    public static final UseActionParticles INSTANCE = new UseActionParticles();

    private static final int BURST_COUNT = 10;
    /** One crumb per chew tick; mirrors the two chips per mining crack stage. */
    private static final int CRUMB_COUNT = 1;
    private static final float GRAVITY = 0.18F;
    private static final int MIN_LIFE = 8;
    private static final int LIFE_SPREAD = 7;
    private static final int PARTICLE_SIZE = 2;

    private final Map<UUID, List<Crum>> particles = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    private UseActionParticles() {
    }

    /** Small crumb flicked off the item on a chew tick. */
    public void crumb(UUID memberId, ItemStack stack) {
        TextureAtlasSprite sprite = particleIcon(stack);
        if (sprite == null) {
            return;
        }
        List<Crum> crumbs = particles.computeIfAbsent(memberId, k -> new ArrayList<>());
        for (int i = 0; i < CRUMB_COUNT; i++) {
            crumbs.add(new Crum(sprite,
                    (random.nextFloat() - 0.5F) * 8.0F,
                    -random.nextFloat() * 4.0F,
                    (random.nextFloat() - 0.5F) * 1.2F,
                    -random.nextFloat() * 1.2F - 0.4F,
                    MIN_LIFE + random.nextInt(LIFE_SPREAD)));
        }
    }

    /** Larger burst when the use finishes and the item is consumed. */
    public void finishBurst(UUID memberId, ItemStack stack) {
        TextureAtlasSprite sprite = particleIcon(stack);
        if (sprite == null) {
            return;
        }
        List<Crum> crumbs = particles.computeIfAbsent(memberId, k -> new ArrayList<>());
        for (int i = 0; i < BURST_COUNT; i++) {
            crumbs.add(new Crum(sprite,
                    (random.nextFloat() - 0.5F) * 10.0F,
                    (random.nextFloat() - 0.5F) * 4.0F,
                    (random.nextFloat() - 0.5F) * 1.8F,
                    -random.nextFloat() * 2.0F - 0.6F,
                    MIN_LIFE + random.nextInt(LIFE_SPREAD)));
        }
    }

    public void clientTick() {
        Iterator<Map.Entry<UUID, List<Crum>>> it = particles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Crum>> entry = it.next();
            entry.getValue().removeIf(crum -> ++crum.age >= crum.life);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /** Renders crumbs around the item slot center. */
    public void render(GuiGraphicsExtractor gui, UUID memberId, float centerX, float centerY, float partialTick) {
        List<Crum> crumbs = particles.get(memberId);
        if (crumbs == null || crumbs.isEmpty()) {
            return;
        }
        var pose = gui.pose();
        pose.pushMatrix();
        // Sub-pixel placement via floating pose translation (same approach as mining debris)
        // keeps the motion smooth at high refresh rates.
        pose.translate(centerX, centerY);
        for (Crum crum : crumbs) {
            float t = crum.age + partialTick;
            float x = crum.x0 + crum.vx * t;
            float y = crum.y0 + crum.vy * t + 0.5F * GRAVITY * t * t;
            float remaining = crum.life - t;
            float alpha = Math.max(0.0F, Math.min(1.0F, remaining / 4.0F));
            int color = ((int) (alpha * 255.0F) << 24) | 0x00FFFFFF;
            pose.pushMatrix();
            pose.translate(x, y);
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, crum.sprite,
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

    private static TextureAtlasSprite particleIcon(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) {
            return null;
        }
        ItemStackRenderState renderState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(renderState, stack,
                ItemDisplayContext.NONE, mc.level, null, 0);
        Material.Baked baked = renderState.pickParticleMaterial(RandomSource.create());
        return baked != null ? baked.sprite() : null;
    }

    private static final class Crum {
        private final TextureAtlasSprite sprite;
        private final float x0;
        private final float y0;
        private final float vx;
        private final float vy;
        private final int life;
        private int age;

        private Crum(TextureAtlasSprite sprite, float x0, float y0, float vx, float vy, int life) {
            this.sprite = sprite;
            this.x0 = x0;
            this.y0 = y0;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
        }
    }
}

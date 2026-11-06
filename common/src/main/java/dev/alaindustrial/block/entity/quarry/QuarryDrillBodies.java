package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.core.quarry.QuarryMotion;
import dev.alaindustrial.entity.QuarryDrillBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The two solid bodies a formed quarry keeps on its drill (MOD-952, owner 2026-11-06): the head, as wide as the head's
 * cage and as tall as head and cage, and the pipe above it up to the mount under the cart. Moved with the drill every
 * server tick, from the same pose the renderer draws ({@link QuarryDigger#pose}), so what the player bumps into is
 * where the drill is drawn.
 *
 * <p>The bodies are never saved: a lost one — a chunk unloaded, a body killed by a command — is made again on the next
 * tick, and a body whose controller is gone removes itself ({@link QuarryDrillBody}).
 */
public final class QuarryDrillBodies {

	/** The head's width, blocks: its cage is 28 pixels across. */
	private static final float HEAD_WIDTH = 28.0F / 16.0F;
	/** The pipe's width, blocks: its four rods stand 23 pixels apart. */
	private static final float PIPE_WIDTH = 23.0F / 16.0F;
	/** How often the turning head hurts what stands under it, ticks, and how much (MOD-952, spec stage C). */
	private static final int HURT_EVERY = 10;
	private static final float HURT = 4.0F;
	/** The bottom of the mount under the cart, above the bottom of the foundations, blocks. */
	private static final float MOUNT_BOTTOM = 96.0F / 16.0F;

	private @Nullable QuarryDrillBody head;
	private @Nullable QuarryDrillBody pipe;

	/** Places both bodies on the drill at the level's game time; makes them if they are not there. */
	public void update(ServerLevel level, QuarryFrameLayout layout, QuarryDigger digger) {
		if (!digger.placed()) {
			clear();
			return;
		}
		QuarryMotion.Pose pose = digger.pose(level.getGameTime());
		double x = layout.alongX() ? pose.along() : pose.across();
		double z = layout.alongX() ? pose.across() : pose.along();
		float restingTip = layout.baseY() + QuarryFramePlacements.drillRestingTip();
		float tip = Math.min(pose.tip(), restingTip);
		float headHeight = QuarryFramePlacements.drillHeadHeight();
		float pipeBottom = tip + headHeight;
		float pipeHeight = Math.max(0.1F, layout.baseY() + MOUNT_BOTTOM - pipeBottom);
		head = body(level, head, x, tip, z, HEAD_WIDTH, headHeight);
		pipe = body(level, pipe, x, pipeBottom, z, PIPE_WIDTH, pipeHeight);
		if (pose.spinning() && level.getGameTime() % HURT_EVERY == 0) {
			hurtUnder(level, x, tip, z);
		}
	}

	/** The turning head hurts every creature standing in or just under it. */
	private static void hurtUnder(ServerLevel level, double x, float tip, double z) {
		double half = HEAD_WIDTH / 2.0D;
		AABB under = new AABB(x - half, tip - 1.0D, z - half, x + half, tip + 0.5D, z + half);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, under, LivingEntity::isAlive)) {
			victim.hurtServer(level, level.damageSources().generic(), HURT);
		}
	}

	/** Removes both bodies: the frame came apart, the bit is out, or the controller goes. */
	public void clear() {
		if (head != null) {
			head.discard();
			head = null;
		}
		if (pipe != null) {
			pipe.discard();
			pipe = null;
		}
	}

	/** Places {@code body} - or a new one, placed before it joins the level so it never shows up at the origin. */
	private static QuarryDrillBody body(ServerLevel level, @Nullable QuarryDrillBody body, double x, double y, double z,
			float width, float height) {
		if (body != null && !body.isRemoved() && body.level() == level) {
			body.place(x, y, z, width, height);
			return body;
		}
		QuarryDrillBody made = new QuarryDrillBody(QuarryDrillBody.type(), level);
		made.place(x, y, z, width, height);
		level.addFreshEntity(made);
		return made;
	}
}

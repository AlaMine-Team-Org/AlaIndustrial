package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.MobWheelControllerBlock;
import dev.alaindustrial.block.MobWheelGateBlock;
import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mob wheel's client-side animation (MOD-763), held by the drive's block entity: the drawn wheel's angle,
 * the gate's swing and the occupant's running legs. Never saved and never synced — every client derives it
 * from the synced speed, the gate's block state and the occupant id, one tick at a time.
 *
 * <p><b>The wheel.</b> Its angle is integrated tick by tick rather than computed from the game clock, because
 * its speed varies: a clock-based angle jumps whenever the speed changes ({@code RotorSpin} documents this).
 * The drawn speed eases towards the synced one, so the wheel winds up and coasts down.
 *
 * <p><b>The legs.</b> The server holds the occupant still, so on its own the client would decay its leg swing
 * to nothing. Block entities tick after entities on the client ({@code Minecraft.tick} runs
 * {@code ClientLevel.tickEntities}, then {@code tickBlockEntities}), so {@link #tick} runs after the mob's own
 * {@code LivingEntity.calculateEntityAnimation} has eased its walk speed towards zero and advanced its phase by
 * that eased speed. The phase is then topped up so it advances by exactly the target speed this tick, and both
 * the current and the previous speed end at the target: the renderer's interpolation sees an even stride with
 * no jump at the tick boundary, whatever the mob's own tick did.
 */
public final class MobWheelAnimation {
	/**
	 * Wheel turn per tick at 100 % speed, radians. The deck surface sits about 1.3 blocks from the axle, so
	 * this moves it about 0.12 blocks a tick — a brisk walk for a pig.
	 */
	private static final float WHEEL_RADIANS_PER_TICK = 0.09F;
	/** How fast the drawn wheel catches up with a new speed, per tick. */
	private static final float WHEEL_EASE = 0.15F;
	/** Gate swing per tick, as a fraction of fully open: about 0.4 s end to end. */
	private static final float GATE_STEP = 0.125F;
	/** Walk-animation speed (0..1, vanilla's leg-swing amplitude) of the occupant at 100 % wheel speed. */
	private static final float WALK_AT_NOMINAL = 0.7F;
	private static final float TURN = (float) (Math.PI * 2.0);
	private static final int NONE = MobWheelBlockEntity.NO_OCCUPANT;

	private float wheelAngle;
	private float wheelAnglePrev;
	private float wheelSpeed;
	private float gateOpen;
	private float gateOpenPrev;
	private boolean gateSeen;
	/** The occupant whose legs were driven last tick, and where its walk phase stood after that. */
	private int walkDrivenId = NONE;
	private float walkPositionAfter;

	/**
	 * One client tick of the drive at {@code pos}, whose block state is {@code state}. A distracted occupant
	 * (D9) is left to turn towards its target: its heading is not forced along the run.
	 */
	void tick(Level level, BlockPos pos, BlockState state, boolean running, int speedPercent, int occupantId,
			boolean distracted) {
		boolean formed = state.getValue(MobWheelStructure.FORMED);
		float target = running ? speedPercent / 100.0F : 0.0F;
		wheelSpeed += (target - wheelSpeed) * WHEEL_EASE;
		if (target == 0.0F && wheelSpeed < 1.0E-3F) {
			wheelSpeed = 0.0F;
		}
		wheelAnglePrev = wheelAngle;
		// Negative about the canonical +Z axle: the deck under the mob moves back while it runs to +x.
		wheelAngle -= wheelSpeed * WHEEL_RADIANS_PER_TICK;
		if (wheelAngle < -TURN) {
			wheelAngle += TURN;
			wheelAnglePrev += TURN;
		}

		gateOpenPrev = gateOpen;
		float gateTarget = formed && gateIsOpen(level, pos, state) ? 1.0F : 0.0F;
		if (!gateSeen) {
			gateSeen = true;
			gateOpen = gateTarget;
			gateOpenPrev = gateTarget;
		} else if (gateOpen < gateTarget) {
			gateOpen = Math.min(gateTarget, gateOpen + GATE_STEP);
		} else if (gateOpen > gateTarget) {
			gateOpen = Math.max(gateTarget, gateOpen - GATE_STEP);
		}

		faceOccupant(level, formed && !distracted ? occupantId : NONE, state);
		driveLegs(level, running ? occupantId : NONE, speedPercent);
	}

	/**
	 * Keep a held occupant's body, head and look along the run on this client too. The server sets the same
	 * heading every tick, but the client runs the mob's own body-rotation smoothing between packets, and that
	 * lets the body drift off the run; this tick comes after the mob's, so it has the last word.
	 */
	private static void faceOccupant(Level level, int occupantId, BlockState state) {
		if (occupantId != NONE && level.getEntity(occupantId) instanceof Mob mob) {
			MobWheelRunner.faceAlongRun(mob,
					MobWheelStructure.runDirection(state.getValue(MobWheelControllerBlock.FACING)).toYRot());
		}
	}

	private static boolean gateIsOpen(Level level, BlockPos pos, BlockState state) {
		BlockState gate = level.getBlockState(MobWheelStructure.at(pos,
				state.getValue(MobWheelControllerBlock.FACING), MobWheelStructure.GATE));
		return gate.is(ModContent.MOB_WHEEL_GATE.get()) && gate.getValue(MobWheelGateBlock.OPEN);
	}

	private void driveLegs(Level level, int occupantId, int speedPercent) {
		Entity entity = occupantId == NONE ? null : level.getEntity(occupantId);
		if (!(entity instanceof LivingEntity living)) {
			walkDrivenId = NONE;
			return;
		}
		WalkAnimationState walk = living.walkAnimation;
		float target = Math.min(1.0F, WALK_AT_NOMINAL * speedPercent / 100.0F);
		// How far the mob's own tick moved the phase since it was left here; nothing known on the first tick.
		float moved = walkDrivenId == occupantId ? walk.position() - walkPositionAfter : 0.0F;
		walk.setSpeed(target);
		// update(): speedOld = target, speed = target - moved, phase += target - moved.
		walk.update(target - moved, 1.0F, 1.0F);
		walk.setSpeed(target);
		walkPositionAfter = walk.position();
		walkDrivenId = occupantId;
	}

	/** Wheel angle about its axle for this frame, radians (canonical, right-handed about +Z). */
	public float wheelAngle(float partialTick) {
		return Mth.lerp(partialTick, wheelAnglePrev, wheelAngle);
	}

	/** How far the gate is open for this frame: 0 closed .. 1 open. */
	public float gateOpenness(float partialTick) {
		return Mth.lerp(partialTick, gateOpenPrev, gateOpen);
	}
}

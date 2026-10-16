package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.CraterWaves;
import dev.alaindustrial.core.structure.ReactorBlast;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One reactor accident's crater (MOD-736): the primary blast, the cascade of waves that widens it when the shell
 * did not hold, and what is left behind — a lava lake at the bottom, walls layered from irradiated soil at the top
 * through basalt and blackstone down to magma, and a ring of fire on the rim.
 *
 * <p>Everything here is keyed to what the blasts ACTUALLY destroyed (decision 9 of MOD-471): a wave goes off only
 * in a cell an earlier blast emptied ({@link CraterWaves}), and the aftermath lands only in or beside such cells,
 * never on a cell a ray chose and something refused. A protection mod that cancels a wave therefore stops the crater
 * at its border without this class knowing such mods exist.
 */
public final class ReactorCrater {

	/**
	 * How one blast is set off: the world's own {@code level.explode} in play, a stand-in in a gametest.
	 * {@code loud} is the primary blast; a wave is not.
	 */
	@FunctionalInterface
	public interface Detonator {
		void detonate(ServerLevel level, BlockPos at, float power, boolean fire, ExplosionDamageCalculator calculator,
				boolean loud);
	}

	/**
	 * The world's explosion, and the only one a blast may go through: {@code level.explode} is where a protection
	 * mod can stop it (see {@link ReactorBlast}). {@code BLOCK} rather than {@code MOB} for the reason given there.
	 *
	 * <p><b>A wave makes no sound and only a puff of smoke.</b> Two hundred vanilla bangs in two seconds read as a
	 * crate of TNT going off, not as one reactor (owner playtest, MOD-736); the reactor's one blast is the primary,
	 * and {@link #announce} makes it sound like one.
	 */
	public static final Detonator WORLD = (level, at, power, fire, calculator, loud) -> {
		double x = at.getX() + 0.5;
		double y = at.getY() + 0.5;
		double z = at.getZ() + 0.5;
		if (loud) {
			level.explode(null, null, calculator, x, y, z, power, fire, Level.ExplosionInteraction.BLOCK);
		} else {
			level.explode(null, null, calculator, x, y, z, power, fire, Level.ExplosionInteraction.BLOCK,
					ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE, WeightedList.of(),
					Holder.direct(SoundEvents.EMPTY));
		}
	};

	private final ServerLevel level;
	private final BlockPos epicentre;
	private final int radius;
	private final int wavePower;
	private final Detonator detonator;
	private final List<BlockPos> destroyed = new ArrayList<>();
	private final Set<BlockPos> destroyedSet = new HashSet<>();
	private final Set<BlockPos> refused = new HashSet<>();
	private final CraterWaves.Field field;
	private final List<CraterWaves.Target> plan;
	private int next;

	/**
	 * @param radius     crater radius the cascade is planned for; 0 plans no waves (a contained blast)
	 * @param spacing    distance between neighbouring waves, blocks
	 * @param wavePower  explosion power of each wave
	 * @param detonator  how each blast is set off — {@link #WORLD} outside tests
	 */
	public ReactorCrater(ServerLevel level, BlockPos epicentre, int radius, int spacing, int wavePower,
			Detonator detonator) {
		this.level = level;
		this.epicentre = epicentre.immutable();
		this.radius = radius;
		this.wavePower = wavePower;
		this.detonator = detonator;
		this.field = new CraterWaves.Field(spacing);
		this.plan = CraterWaves.plan(radius, spacing);
	}

	/**
	 * Sets off one blast and returns the cells it destroyed that no earlier blast of this crater had.
	 *
	 * <p>A cell counts as destroyed when a ray chose it and it is now air — or fire, which the primary blast lights
	 * in some of the very cells it emptied.
	 */
	public List<BlockPos> blast(BlockPos at, float power, boolean fire, boolean hurtsEntities) {
		ReactorBlastCalculator calculator =
				new ReactorBlastCalculator(hurtsEntities, ReactorConfig.reactorBlastTerrainResistanceCap);
		detonator.detonate(level, at, power, fire, calculator, hurtsEntities);
		List<BlockPos> fresh = new ArrayList<>();
		for (BlockPos cell : calculator.candidates()) {
			BlockState now = level.getBlockState(cell);
			if (now.isAir() || now.getBlock() instanceof BaseFireBlock) {
				if (destroyedSet.add(cell)) {
					destroyed.add(cell);
					fresh.add(cell);
					refused.remove(cell);
					field.offer(cell.getX() - epicentre.getX(), cell.getY() - epicentre.getY(),
							cell.getZ() - epicentre.getZ());
				}
			} else if (!destroyedSet.contains(cell)) {
				refused.add(cell);
			}
		}
		return fresh;
	}

	/**
	 * Sets off up to {@code max} waves of the cascade, nearest first; returns {@code true} once the plan is spent.
	 *
	 * <p>A planned wave with nothing destroyed near it is skipped and does not count against {@code max}: the
	 * crater has not reached that far on that side, or something refused the waves that would have.
	 */
	public boolean step(int max) {
		int fired = 0;
		while (next < plan.size() && fired < max) {
			CraterWaves.Cell centre = field.centreFor(plan.get(next++));
			if (centre == null) {
				continue;
			}
			blast(epicentre.offset(centre.x(), centre.y(), centre.z()), wavePower, false, false);
			fired++;
		}
		return next >= plan.size();
	}

	/**
	 * The crater's voice: one deep blast heard far across the world and a burst of fireballs filling the hole, so
	 * the cascade that follows in silence reads as the same explosion rather than as many small ones.
	 */
	public void announce() {
		double x = epicentre.getX() + 0.5;
		double y = epicentre.getY() + 0.5;
		double z = epicentre.getZ() + 0.5;
		// Volume above 1 widens the range a sound is heard at (16 blocks per unit), not its loudness up close.
		float reach = Math.max(4.0f, radius);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, reach, 0.45f);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, reach, 0.6f);
		double spread = Math.max(1.0, radius / 2.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, x, y, z, Math.max(4, radius),
				spread, spread / 2, spread, 0.0);
	}

	/** Lava and fallout for a contained blast; for a crater, lava and the layered, burning walls. */
	public void finish(boolean crater) {
		if (!crater) {
			ReactorBlast.pourLava(level, destroyed, epicentre, ReactorConfig.reactorBlastLavaCells);
			ReactorBlast.scatterFallout(level, destroyed, epicentre, ReactorConfig.reactorFalloutRadius);
			return;
		}
		poolLava(CraterWaves.lavaCells(ReactorConfig.reactorBlastLavaCells,
				ReactorConfig.reactorCraterLavaCellsPerRadius, radius));
		layerWalls();
	}

	/**
	 * Pours the crater's lava as a lake on its floor, where it can be seen from the rim.
	 *
	 * <p>Not into the lowest destroyed cells: a crater's lowest cells are the deepest of the narrow shafts single rays
	 * drilled, and lava poured there was invisible from above (owner playtest, MOD-736). Instead, cells of the bottom
	 * quarter that stand on solid ground, nearest the crater's axis first. Under the sea the lake meets water and sets
	 * to obsidian and stone, which is what lava does.
	 */
	private void poolLava(int cells) {
		if (cells <= 0 || destroyed.isEmpty()) {
			return;
		}
		int top = Integer.MIN_VALUE;
		int bottom = Integer.MAX_VALUE;
		for (BlockPos at : destroyed) {
			top = Math.max(top, at.getY());
			bottom = Math.min(bottom, at.getY());
		}
		double span = Math.max(1, top - bottom);
		List<BlockPos> floor = new ArrayList<>();
		for (BlockPos at : destroyed) {
			if ((top - at.getY()) / span < 0.75 || !level.getBlockState(at).isAir()) {
				continue;
			}
			BlockPos below = at.below();
			if (!destroyedSet.contains(below) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				floor.add(at);
			}
		}
		floor.sort(Comparator.<BlockPos>comparingLong(at -> {
			long dx = at.getX() - epicentre.getX();
			long dz = at.getZ() - epicentre.getZ();
			return dx * dx + dz * dz;
		}).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		for (int i = 0; i < Math.min(cells, floor.size()); i++) {
			level.setBlock(floor.get(i), Blocks.LAVA.defaultBlockState(), 3);
		}
	}

	/**
	 * Dresses the crater's walls in layers by depth, then rings its rim with fire.
	 *
	 * <p>From the top down: irradiated soil of mixed strength (the fallout — on the rim, where people walk, not
	 * within a radius of an epicentre that sits on the crater's floor), then basalt and blackstone, then blackstone
	 * and magma, and magma above the lava lake at the bottom (owner playtest, MOD-736). A wall block is dressed only
	 * if it is solid, breakable, not part of a reactor shell, holds no block entity, touches a destroyed cell and was
	 * not a candidate something refused — one block into what the blast left, never into a claim it was stopped at.
	 */
	private void layerWalls() {
		if (destroyed.isEmpty()) {
			return;
		}
		int top = Integer.MIN_VALUE;
		int bottom = Integer.MAX_VALUE;
		for (BlockPos at : destroyed) {
			top = Math.max(top, at.getY());
			bottom = Math.min(bottom, at.getY());
		}
		double span = Math.max(1, top - bottom);
		RandomSource random = level.getRandom();
		int percent = ReactorConfig.reactorCraterScorchPercent;
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos at : destroyed) {
			for (Direction side : Direction.values()) {
				BlockPos wall = at.relative(side);
				if (destroyedSet.contains(wall) || refused.contains(wall) || !seen.add(wall)) {
					continue;
				}
				BlockState state = level.getBlockState(wall);
				// Sand and gravel always: left as they are, the ones over a hollow rain into the crater as falling
				// blocks for seconds after the blast (owner playtest, MOD-736).
				if (!dressable(state, wall)
						|| !(state.getBlock() instanceof FallingBlock) && random.nextInt(100) >= percent) {
					continue;
				}
				level.setBlock(wall, layerAt((top - wall.getY()) / span, random), 3);
			}
		}
		ringWithFire(top, span, random);
	}

	/** The block a wall gets at {@code depth} — 0 at the crater's top, 1 at its floor. */
	private static BlockState layerAt(double depth, RandomSource random) {
		int roll = random.nextInt(100);
		if (depth < 0.25) {
			if (roll < 65) {
				return ModContent.IRRADIATED_SOIL.get().defaultBlockState().setValue(IrradiatedSoilBlock.INTENSITY,
						1 + random.nextInt(IrradiatedSoilBlock.MAX_INTENSITY));
			}
			return roll < 85 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
		}
		if (depth < 0.55) {
			return roll < 55 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
		}
		if (depth < 0.8) {
			return roll < 45 ? Blocks.BLACKSTONE.defaultBlockState()
					: roll < 65 ? Blocks.BASALT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		return roll < 65 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
	}

	/**
	 * Fire in the top band of the crater: a destroyed cell that is still empty, sits on a sturdy floor and is within
	 * the upper fifth of the hole catches with {@code reactorCraterFirePercent}.
	 */
	private void ringWithFire(int top, double span, RandomSource random) {
		int percent = ReactorConfig.reactorCraterFirePercent;
		if (percent <= 0) {
			return;
		}
		for (BlockPos at : destroyed) {
			if ((top - at.getY()) / span > 0.2 || !level.getBlockState(at).isAir()) {
				continue;
			}
			BlockPos below = at.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
					|| random.nextInt(100) >= percent) {
				continue;
			}
			level.setBlock(at, BaseFireBlock.getState(level, at), 3);
		}
	}

	private boolean dressable(BlockState state, BlockPos at) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
				&& state.isCollisionShapeFullBlock(level, at) && state.getDestroySpeed(level, at) >= 0
				&& !RoomValidator.kindOf(state).isShell();
	}

	public ServerLevel level() {
		return level;
	}

	/** Every cell this crater's blasts destroyed so far, in the order they were found. */
	public List<BlockPos> destroyed() {
		return destroyed;
	}
}

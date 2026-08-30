package dev.alaindustrial.core.structure;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The accident at the top of the scale (MOD-471): the blast itself and everything it leaves behind.
 *
 * <p><b>The explosion goes through {@code level.explode} and nowhere else.</b> That is the whole of
 * this mod's land-claim compatibility, and it is worth stating because the alternative looks more
 * convenient: {@code ServerExplosion} is a public class with a public constructor, and building one
 * by hand would hand us the list of destroyed blocks that {@code explode} throws away. It would also
 * skip {@code EventHooks.onExplosionStart}, which is the one point where a protection mod on NeoForge
 * can stop us — and on Fabric, where the API has no explosion event at all, protection mods mixin
 * into this very call. Losing the list is the price of being blockable, and it is the right trade.
 *
 * <p><b>So the aftermath is derived from what actually changed, not from a radius.</b> A snapshot of
 * the solid cells is taken before the blast and diffed after it; lava, fire and fallout land only in
 * cells that were solid and are now air. If a claim cancelled the explosion, the diff is empty and
 * there is no aftermath at all — the protection is respected without this file knowing that any
 * protection mod exists. Without that rule the lava would pour into a neighbour's claim through a
 * blast that mod had just refused.
 */
public final class ReactorBlast {

	private ReactorBlast() {
	}

	/**
	 * Cells that hold something now, inside the box the aftermath may ever touch.
	 *
	 * <p>Deliberately NOT the whole blast reach. At the shipped ceiling a ray travels some seventy
	 * blocks, and snapshotting that would be three million cells; the aftermath only ever lands within
	 * {@code reactorFalloutRadius}, so that is what gets remembered. Everything the blast destroys
	 * further out is simply destroyed, with no lava and no fallout on it.
	 *
	 * <p>Skips cells in chunks that are not loaded. Reading them would force-load — and, on an
	 * unexplored border, generate — a chunk purely so we could decide where to put ash.
	 */
	public static Set<BlockPos> snapshotSolids(ServerLevel level, BlockPos centre, int radius) {
		Set<BlockPos> solid = new HashSet<>();
		if (radius <= 0) {
			return solid;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
			for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
				// Asked ONCE per column, and asked of the chunk source rather than of the level: the whole
				// hasChunkAt family is deprecated in 26.2, and level.getBlockState would load — and on an
				// unexplored border generate — a chunk purely so we could decide where to put ash. Reading
				// straight off the chunk we resolved is also the pattern RadiationSources already uses.
				LevelChunk chunk = level.getChunkSource().getChunkNow(
						SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
				if (chunk == null) {
					continue;
				}
				for (int y = centre.getY() - radius; y <= centre.getY() + radius; y++) {
					cursor.set(x, y, z);
					if (!level.isInWorldBounds(cursor) || chunk.getBlockState(cursor).isAir()) {
						continue;
					}
					solid.add(cursor.immutable());
				}
			}
		}
		return solid;
	}

	/** Of the remembered cells, the ones the blast actually emptied. */
	public static List<BlockPos> destroyedSince(ServerLevel level, Set<BlockPos> solidBefore) {
		List<BlockPos> destroyed = new ArrayList<>();
		for (BlockPos at : solidBefore) {
			if (level.getBlockState(at).isAir()) {
				destroyed.add(at);
			}
		}
		return destroyed;
	}

	/**
	 * Sets it off.
	 *
	 * <p>{@code ExplosionInteraction.BLOCK} rather than {@code MOB}: {@code MOB} routes through
	 * {@code canEntityGrief} and from there into the vanilla {@code mobGriefing} rule, and MOD-469
	 * already settled that a machine quietly obeying a rule about mobs is a surprise to the operator
	 * who set it for creepers. {@code BLOCK} answers only to {@code blockExplosionDropDecay}, which is
	 * the rule that is actually about this.
	 */
	public static void detonate(ServerLevel level, Vec3 centre, float power, boolean fire) {
		level.explode(null, null, null, centre.x, centre.y, centre.z, power, fire,
				Level.ExplosionInteraction.BLOCK);
	}

	/**
	 * Pours lava into the bottom of the crater.
	 *
	 * <p>The lowest destroyed cells first, so the lava settles where a crater's molten core would be
	 * rather than dribbling off the rim. Sources, not flowing blocks — a flowing block placed by hand
	 * vanishes on the next fluid tick, which MOD-469 already learned.
	 */
	public static int pourLava(ServerLevel level, List<BlockPos> destroyed, BlockPos centre, int cells) {
		if (cells <= 0 || destroyed.isEmpty()) {
			return 0;
		}
		List<BlockPos> ordered = new ArrayList<>(destroyed);
		ordered.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
				.thenComparingDouble(at -> at.distSqr(centre))
				// Deterministic to the last cell: two positions at the same height and the same distance
				// must not depend on set iteration order, or a gametest asserting the crater becomes a
				// coin toss (the lesson blockpos-aslong-not-monotone, applied to a tie-break).
				.thenComparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ));
		int poured = 0;
		for (BlockPos at : ordered) {
			if (poured >= cells) {
				break;
			}
			if (!level.getBlockState(at).isAir()) {
				continue;
			}
			level.setBlock(at, Blocks.LAVA.defaultBlockState(), 3);
			poured++;
		}
		return poured;
	}

	/**
	 * Settles irradiated ground over the scar.
	 *
	 * <p>On the SURFACE of the crater only — a destroyed cell whose neighbour below still holds
	 * something the ash can stick to, and whose own space is open sky from here. Filling the whole
	 * hollow would bury the fallout where nobody walks, which is the same mistake MOD-469's melt picker
	 * made when half its hits went underground and the visible scar came out half as wide as promised.
	 */
	public static int scatterFallout(ServerLevel level, List<BlockPos> destroyed, BlockPos centre,
			int radius) {
		if (!Config.reactorFalloutEnabled || radius <= 0) {
			return 0;
		}
		BlockState fallout = ModContent.IRRADIATED_SOIL.get().defaultBlockState()
				.setValue(IrradiatedSoilBlock.INTENSITY, IrradiatedSoilBlock.MAX_INTENSITY);
		long reach = (long) radius * radius;
		int placed = 0;
		for (BlockPos at : destroyed) {
			if (at.distSqr(centre) > reach || !level.getBlockState(at).isAir()) {
				continue;
			}
			if (!level.getBlockState(at.below()).is(ModTags.Blocks.FALLOUT_REPLACEABLE)) {
				continue;
			}
			if (!level.getBlockState(at.above()).isAir()) {
				continue;
			}
			level.setBlock(at.below(), fallout, 3);
			placed++;
		}
		return placed;
	}

	/**
	 * The cue that the countdown is running, played at the controller.
	 *
	 * <p>Tightens as the deadline approaches — the same idea as the pointed warning MOD-469 puts on a
	 * single doomed block, stretched over two or three minutes. A constant drizzle of particles for
	 * that long stops being read within the first thirty seconds.
	 */
	public static void telegraphCountdown(ServerLevel level, BlockPos pos, int remaining, int total) {
		int period = remaining * 4 > total ? 20 : remaining * 8 > total ? 10 : 5;
		if (remaining % period != 0) {
			return;
		}
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
				6, 0.35, 0.25, 0.35, 0.01);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
				4, 0.35, 0.25, 0.35, 0.01);
		if (remaining * 8 <= total) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.5f);
		}
	}
}

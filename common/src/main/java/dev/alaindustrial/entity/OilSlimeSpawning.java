package dev.alaindustrial.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.fluid.FluidImmersion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Where an oil slime may appear on its own (MOD-767, docs/mobs/oil_slime.md, natural spawning): next to
 * crude oil, in the dark, never on Peaceful, and only while its chunk holds fewer oil slimes than the cap.
 * A spawn egg or a spawner skips every rule but the vanilla ground check.
 */
public final class OilSlimeSpawning {

	/** Crude oil must be within this many blocks horizontally of the spawn position… */
	static final int OIL_REACH_HORIZONTAL = 4;
	/** …and within this many vertically. */
	static final int OIL_REACH_VERTICAL = 2;
	/** Brightest light an oil slime appears in on its own, as for a surface slime in a swamp. */
	static final int MAX_LIGHT = 7;
	/** Half the height of the box the per-chunk cap counts in. */
	private static final int CAP_HALF_HEIGHT = 64;

	private OilSlimeSpawning() {
	}

	/** The registered spawn rule of {@code alaindustrial:oil_slime}. */
	public static boolean canSpawn(EntityType<OilSlime> type, ServerLevelAccessor level, EntitySpawnReason reason,
			BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason) || reason == EntitySpawnReason.SPAWN_ITEM_USE) {
			return Mob.checkMobSpawnRules(type, level, reason, pos, random);
		}
		return Config.oilSlimeNaturalSpawn
				&& level.getDifficulty() != Difficulty.PEACEFUL
				&& level.getMaxLocalRawBrightness(pos) <= MAX_LIGHT
				&& oilNearby(level, pos)
				&& oilSlimesInChunk(level, pos) < Config.oilSlimeSpawnCapPerChunk
				&& Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	/** Whether a cell of crude oil — source or flowing — lies within reach of {@code pos}. */
	public static boolean oilNearby(LevelReader level, BlockPos pos) {
		BlockPos from = pos.offset(-OIL_REACH_HORIZONTAL, -OIL_REACH_VERTICAL, -OIL_REACH_HORIZONTAL);
		BlockPos to = pos.offset(OIL_REACH_HORIZONTAL, OIL_REACH_VERTICAL, OIL_REACH_HORIZONTAL);
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			if (FluidImmersion.of(level.getFluidState(p).getType()) == FluidImmersion.OIL) {
				return true;
			}
		}
		return false;
	}

	/** Oil slimes inside the chunk column of {@code pos}, within {@link #CAP_HALF_HEIGHT} blocks of its height. */
	public static int oilSlimesInChunk(ServerLevelAccessor level, BlockPos pos) {
		int minX = pos.getX() & ~15;
		int minZ = pos.getZ() & ~15;
		AABB chunk = new AABB(minX, pos.getY() - CAP_HALF_HEIGHT, minZ,
				minX + 16, pos.getY() + CAP_HALF_HEIGHT, minZ + 16);
		return level.getEntitiesOfClass(OilSlime.class, chunk).size();
	}
}

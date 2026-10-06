package dev.alaindustrial.block.entity;

import dev.alaindustrial.compat.ServerDrops;
import dev.alaindustrial.core.environment.MobWheelDistraction;
import dev.alaindustrial.core.environment.MobWheelProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the mob wheel (MOD-763) does to the mob itself, apart from the drive's bookkeeping: how the occupant
 * is pinned to the running surface and turned along the run, when it is distracted by a player (D9), what its
 * species' trait does to it while held (D8), how a led mob gives up its lead, what the weather and biome at
 * the wheel are, and whether hay lies close enough to help it rest. Which mobs it takes: {@link MobWheelRoster}.
 */
public final class MobWheelRunner {
	private MobWheelRunner() {
	}

	/** An item stack's id as a string ({@code "minecraft:carrot"}), what the feed table is keyed by. */
	static String itemIdOf(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.toString();
	}

	/**
	 * Put the occupant exactly on {@code anchor} (feet on the running surface, never sunk into the planks)
	 * with no motion, no path and no fall, and — unless it is distracted ({@code faceRun} false) — facing
	 * strictly along the run. Server block entities tick after the level's entities, so what this sets is what
	 * the tick ends with and what the clients are sent.
	 */
	static void hold(Mob mob, Vec3 anchor, float yaw, boolean faceRun) {
		if (!mob.position().equals(anchor)) {
			mob.setPos(anchor);
		}
		mob.setDeltaMovement(Vec3.ZERO);
		mob.setOnGround(true);
		mob.resetFallDistance();
		mob.getNavigation().stop();
		if (faceRun) {
			faceAlongRun(mob, yaw);
		}
	}

	/**
	 * Whether the occupant is distracted (D9): its target is a live, non-spectator player within
	 * {@link MobWheelDistraction#RANGE_BLOCKS}. Brain-driven mobs (piglins) answer {@code getTarget} from their
	 * brain, so the same question covers them.
	 */
	static boolean isDistracted(Mob mob) {
		LivingEntity target = mob.getTarget();
		boolean livePlayer = target instanceof Player player && player.isAlive() && !player.isSpectator();
		return MobWheelDistraction.isDistracted(livePlayer, livePlayer ? mob.distanceToSqr(target) : 0.0);
	}

	/**
	 * What the world at the wheel is like this tick (D8 bonuses): night, rain falling on {@code top} (the block
	 * above the wheel's middle, so the wheel's own cells do not shelter it), and the biome there — snowy when its
	 * precipitation is snow, hot and dry when it has none and its base temperature is above 1.
	 */
	static MobWheelProfile.Surroundings surroundings(Level level, BlockPos top) {
		Biome biome = level.getBiome(top).value();
		boolean snowy = biome.getPrecipitationAt(top, level.getSeaLevel()) == Biome.Precipitation.SNOW;
		boolean hotDry = !biome.hasPrecipitation() && biome.getBaseTemperature() > 1.0F;
		return new MobWheelProfile.Surroundings(level.isDarkOutside(), level.isRainingAt(top), snowy, hotDry);
	}

	/**
	 * Apply the occupant's species trait for this tick of being held (D8). A creeper that is running and was
	 * not lit by a player does not swell; a piglin or brute that is about to start turning into a zombified
	 * piglin is made immune. Returns {@code true} when this tick GRANTED that immunity, so the drive can take
	 * back exactly what it gave — a piglin that was immune before keeps its immunity on release.
	 */
	static boolean applyTrait(Mob mob, MobWheelProfile profile, boolean running) {
		switch (profile.trait()) {
			case NO_SWELL -> {
				if (running && mob instanceof Creeper creeper && !creeper.isIgnited()) {
					creeper.setSwellDir(-1);
				}
			}
			case NO_ZOMBIFICATION -> {
				if (mob instanceof AbstractPiglin piglin && piglin.isConverting()) {
					piglin.setImmuneToZombification(true);
					return true;
				}
			}
			case NONE, SELF_RESTORE -> { }
		}
		return false;
	}

	/** Take back the zombification immunity the wheel granted ({@link #applyTrait}) from a released piglin. */
	static void revokeGrantedImmunity(Mob mob) {
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(false);
		}
	}

	/** The witch's sip when it restores itself in the wheel (D8). */
	static void playSelfRestore(Mob mob) {
		mob.playSound(SoundEvents.WITCH_DRINK, 1.0F, 1.0F);
	}

	/**
	 * Point a mob's body, head and look along the run, the previous-tick values too so nothing is interpolated
	 * from a stale heading. Shared with the client animation, where the body would otherwise drift.
	 */
	public static void faceAlongRun(Mob mob, float yaw) {
		mob.setYRot(yaw);
		mob.yRotO = yaw;
		mob.setYHeadRot(yaw);
		mob.yHeadRotO = yaw;
		mob.setYBodyRot(yaw);
		mob.yBodyRotO = yaw;
	}

	/**
	 * Unleash a mob that is being led into the wheel. No lead is dropped (it would land inside the wheel):
	 * the player gets it back instead, unless they build with infinite materials.
	 */
	static void takeLeadBack(Mob mob, Player player) {
		if (mob instanceof Leashable leashed && leashed.isLeashed()) {
			leashed.removeLeash();
			if (!player.hasInfiniteMaterials()) {
				ServerDrops.placeBackInInventory(player, new ItemStack(Items.LEAD));
			}
		}
	}

	/** Whether a hay bale lies within {@code radius} blocks of the structure's box {@code bounds} (D5). */
	static boolean hayNearby(Level level, AABB bounds, int radius) {
		AABB box = bounds.inflate(radius);
		for (BlockPos probe : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ,
				(int) box.maxX - 1, (int) box.maxY - 1, (int) box.maxZ - 1)) {
			if (level.getBlockState(probe).is(Blocks.HAY_BLOCK)) {
				return true;
			}
		}
		return false;
	}
}

package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Torch-like glow that follows a thrusting jetpack (MOD-148 playtest request: "light up the area
 * around me while I fly at night"). Minecraft has no per-entity dynamic light, so the effect is a
 * single {@code minecraft:light} block moved to the flying player's position each thrust tick — the
 * standard vanilla-only technique.
 *
 * <p><b>Leak-free by a refresh sweep, not per-event cleanup.</b> A moving light block would orphan
 * an invisible light source the moment its manager stopped running — a player who logs out, dies or
 * unequips mid-flight. Instead of chasing every such exit with its own hook, each placed light
 * records the game tick it was last refreshed; {@link #sweep} runs once per server tick (from the
 * existing per-loader end-of-server-tick hook) and clears any light that was NOT refreshed this tick.
 * A thrusting player refreshes their light every tick, so it stays; anything that stops the flight
 * tick — for any reason — leaves the entry unrefreshed and it is gone one tick later. One mechanism
 * covers land, release, disconnect, death and chunk-unload alike.
 *
 * <p>Server-authoritative: the block change replicates to clients, which relight from it. A light
 * level of 0 (config) disables the whole feature — no block is ever placed.
 */
public final class JetpackLight {

	/** A light block this class placed for one player: where it is, in which dimension, and the game
	 * tick it was last refreshed (the sweep's liveness signal). */
	private record Placed(ResourceKey<Level> dimension, BlockPos pos, long tick) {
	}

	private static final Map<UUID, Placed> LIGHTS = new ConcurrentHashMap<>();

	// UPDATE_CLIENTS only: replicate the block (which relights on both sides) without running
	// neighbor updates every tick along the flight path (no redstone/observer churn under the player).
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private JetpackLight() {
	}

	/**
	 * Keep a light block on the thrusting player this tick (called from the thrust branch of
	 * {@link JetpackItem#serverFlightTick}). Moves the light when the player crosses into a new block,
	 * refreshes its liveness stamp every tick, and places into air or a water source only — never over
	 * a real block. A configured level of 0 disables the feature.
	 */
	public static void ignite(ServerLevel level, Player player, long gameTime) {
		int lightLevel = Math.min(LightBlock.MAX_LEVEL, Config.jetpackFlightLightLevel);
		if (lightLevel <= 0) {
			return;
		}
		UUID id = player.getUUID();
		BlockPos pos = player.blockPosition().above(); // torso height — clear air while airborne
		Placed current = LIGHTS.get(id);
		if (current != null && (!current.pos.equals(pos) || current.dimension != level.dimension())) {
			clearBlock(level.getServer(), current);
			current = null;
		}
		if (current == null && !placeLight(level, pos, lightLevel)) {
			// Couldn't place (the spot is a solid block) — leave no entry, so the sweep has nothing to do.
			LIGHTS.remove(id);
			return;
		}
		LIGHTS.put(id, new Placed(level.dimension(), pos, gameTime));
	}

	/**
	 * Drop the player's light now (called from the non-thrust exits of the flight tick: on the
	 * ground, jump released, drained without a glide). Idempotent — no entry, nothing happens.
	 */
	public static void extinguish(ServerLevel level, Player player) {
		Placed placed = LIGHTS.remove(player.getUUID());
		if (placed != null) {
			clearBlock(level.getServer(), placed);
		}
	}

	/**
	 * Once per server tick: clear every light that was not refreshed this tick. A live thrust stamps
	 * {@code gameTime} onto its entry the same tick, so it survives; a flight that ended for any
	 * reason left its entry on an older tick and is swept. This is the only cleanup path that needs no
	 * knowledge of WHY the flight stopped.
	 */
	public static void sweep(MinecraftServer server, long gameTime) {
		LIGHTS.values().removeIf(placed -> {
			if (placed.tick == gameTime) {
				return false;
			}
			clearBlock(server, placed);
			return true;
		});
	}

	/**
	 * Clear every remaining light before the world saves on shutdown (MOD-176). The per-tick sweep
	 * keeps a live thrust's light for exactly one tick — if the server stops during that tick, the
	 * light would be saved into the chunk while {@code LIGHTS} (in-memory only) forgets it on
	 * restart, leaving an invisible permanent light source in the world. Called from the
	 * server-stopping hook of both loaders, before the level save.
	 */
	public static void shutdown(MinecraftServer server) {
		LIGHTS.values().forEach(placed -> clearBlock(server, placed));
		LIGHTS.clear();
	}

	/** Place the light (waterlogged inside a water source), reporting whether the spot allowed it. */
	private static boolean placeLight(ServerLevel level, BlockPos pos, int lightLevel) {
		BlockState target = level.getBlockState(pos);
		// Source blocks only (MOD-176): WATERLOGGED is a boolean, so a flowing block's level would be
		// lost and clearBlock would revert it to defaultBlockState() = a new SOURCE — flying up a
		// waterfall must not fabricate infinite-water columns. Flowing water simply gets no light.
		boolean water = target.is(Blocks.WATER) && target.getFluidState().isSource();
		if (!target.isAir() && !water) {
			return false;
		}
		BlockState light = Blocks.LIGHT.defaultBlockState()
				.setValue(LightBlock.LEVEL, lightLevel)
				.setValue(LightBlock.WATERLOGGED, water);
		return level.setBlock(pos, light, FLAGS);
	}

	/** Remove one placed light, touching the spot only if it still holds OUR light block (never a
	 * block a player put there since); a waterlogged light reverts to its water, otherwise to air. */
	private static void clearBlock(MinecraftServer server, Placed placed) {
		ServerLevel level = server.getLevel(placed.dimension);
		if (level == null) {
			return;
		}
		BlockState state = level.getBlockState(placed.pos);
		if (!state.is(Blocks.LIGHT)) {
			return;
		}
		BlockState replacement = state.getValue(LightBlock.WATERLOGGED)
				? Blocks.WATER.defaultBlockState()
				: Blocks.AIR.defaultBlockState();
		level.setBlock(placed.pos, replacement, FLAGS);
	}
}

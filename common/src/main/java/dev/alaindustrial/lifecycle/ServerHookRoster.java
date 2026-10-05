package dev.alaindustrial.lifecycle;

import dev.alaindustrial.block.CableDyeing;
import dev.alaindustrial.block.OilLiquidBlock;
import dev.alaindustrial.chat.WelcomeMessage;
import dev.alaindustrial.command.demo.DemoStandDropSweeper;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.guide.ArchiveRecordSync;
import dev.alaindustrial.item.misc.GuideBookGiver;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.net.LevelStateRegistry;
import dev.alaindustrial.core.radiation.GeigerTicker;
import dev.alaindustrial.core.radiation.RadiationTicker;
import dev.alaindustrial.entity.SoulVesselKills;
import dev.alaindustrial.item.fluid.VanillaBucketDeposit;
import dev.alaindustrial.item.wearable.JetpackLight;
import dev.alaindustrial.network.ConfigSync;
import dev.alaindustrial.stats.PlayerStatsTracker;
import dev.alaindustrial.teleporter.TeleportWarmupManager;
import dev.alaindustrial.worldgen.VillagePoolInjector;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Every server-side hook of the mod, per event and in order, declared once for both loaders (MOD-706,
 * CORE-7): what runs on the end of a server tick, when a player joins or leaves, is hurt or dies, when a
 * level unloads and when the server starts or stops — and the early block-use chain, which runs on
 * both logical sides of a right-click.
 *
 * <p><b>The loaders only connect events to the {@code on*} methods below</b>, one line per event —
 * Fabric's {@code ServerTickEvents} / {@code ServerPlayConnectionEvents} / {@code ServerLifecycleEvents}
 * and friends in {@code IndustrializationFabric}, NeoForge's game-bus events in
 * {@code IndustrializationNeoForge}. What each event does, and in which order, is the list here. A
 * condition that is the loader's own way of saying the same thing (Fabric's {@code damageTaken > 0}
 * against NeoForge's {@code getHealthDamage() > 0}) stays in the loader. {@code arch_check.py} refuses
 * a direct call of the core services from a loader entry point, so a new ticker cannot be wired on one
 * loader by name again.
 *
 * <p><b>Before MOD-706 the order had already drifted</b>: NeoForge swept the jetpack light after the
 * radiation and Geiger tickers, Fabric before them. The task's research.md shows the two orders cannot
 * be told apart (the sweep and the tickers share no state, the light block is invisible to the
 * radiation ray, and neither side draws on the other's randomness); the Fabric order is the one kept.
 */
public final class ServerHookRoster {

	private ServerHookRoster() {
	}

	/** End of every server tick, once per loaded level, before {@link #SERVER_TICK}: the network managers. */
	public static final List<Consumer<ServerLevel>> SERVER_TICK_PER_LEVEL = List.of(
			NetworkManager::tickAll,
			ItemNetworkManager::tickAll,
			FluidNetworkManager::tickAll,
			MonitorNetworkManager::tickAll);

	/** End of every server tick, once per server, after {@link #SERVER_TICK_PER_LEVEL}. */
	public static final List<Consumer<MinecraftServer>> SERVER_TICK = List.of(
			// MOD-092: teleport warmups are per player, not per level.
			TeleportWarmupManager::tickAll,
			// MOD-133: fold pending per-player stat deltas into attachments on the configured cadence.
			server -> PlayerStatsTracker.get().onServerTick(server),
			// MOD-148: clear any jetpack glow whose flight ended — the one cleanup path for every exit.
			server -> JetpackLight.sweep(server, server.getTickCount()),
			// MOD-470: per-player radiation exposure, on its own configurable cadence.
			RadiationTicker::tickAll,
			// MOD-475: the Geiger counter clicks every tick; the sweep above sets the step, this spends it.
			GeigerTicker::tick,
			// MOD-674: drops that rain onto the demo stand after it was built (decaying leaves).
			DemoStandDropSweeper::tick);

	/**
	 * A player took damage (the loader passes only a server player that lost health). Three hooks — hurt,
	 * death, leave — are needed for the warmup, not two: the damage event does not fire for a killing
	 * blow, and death does not disconnect the player.
	 */
	public static final List<Consumer<ServerPlayer>> PLAYER_HURT = List.of(
			// MOD-092: a hit breaks a teleport countdown.
			TeleportWarmupManager::cancelHurt);

	/** A living entity died. */
	public static final List<BiConsumer<LivingEntity, DamageSource>> LIVING_DEATH = List.of(
			// MOD-092: death breaks a teleport countdown.
			(entity, source) -> {
				if (entity instanceof ServerPlayer player) {
					TeleportWarmupManager.cancel(player);
				}
			},
			// MOD-278: a hostile mob killed by a player banks one soul into their vessel.
			SoulVesselKills::onDeath);

	/** A player left the server — while still online, so their state can still be written. */
	public static final List<Consumer<ServerPlayer>> PLAYER_LEAVE = List.of(
			// MOD-133: flush this player's pending stats now; the next tick's flush runs after they left.
			player -> PlayerStatsTracker.get().flushPlayer(player),
			player -> TeleportWarmupManager.forget(player.getUUID()),
			// MOD-475: the counter reading is server-side and must not outlive the session.
			player -> GeigerTicker.forget(player.getUUID()));

	/** A player joined the server — in this order, so the greeting never precedes the book. */
	public static final List<Consumer<ServerPlayer>> PLAYER_JOIN = List.of(
			// MOD-067: the Guide Book on first join (once per player; SavedData ledger).
			GuideBookGiver::giveIfNeeded,
			// MOD-596: greet the world, once per world.
			WelcomeMessage::sendIfNeeded,
			// MOD-513: the archive record for the book's first page, every login.
			ArchiveRecordSync::sendOnJoin,
			// MOD-695: the server's balance, so the client's tooltips and screens show its numbers.
			ConfigSync::sendOnJoin);

	/**
	 * A server level unloaded. MOD-401: one sweep over everything that holds per-level state instead of
	 * naming managers — the by-name list is what leaked the fluid manager's levels.
	 */
	public static final List<Consumer<ServerLevel>> LEVEL_UNLOAD = List.of(
			LevelStateRegistry::clearLevel);

	/**
	 * The server is starting, before any level or worldgen exists. MOD-062: the Industrialist house goes
	 * into the village pools now — a pool memoizes its size on first use.
	 */
	public static final List<Consumer<MinecraftServer>> SERVER_STARTING = List.of(
			VillagePoolInjector::inject);

	/** The server is stopping, before the world and the players are saved. */
	public static final List<Consumer<MinecraftServer>> SERVER_STOPPING = List.of(
			// MOD-133: fold every pending delta before the player save; STOPPED would lose the last tail.
			server -> PlayerStatsTracker.get().flush(server),
			// MOD-176: a mid-flight glow light must not be saved into the chunk as an orphan.
			JetpackLight::shutdown,
			// MOD-475: the readings are static; one left behind would greet the next world.
			server -> GeigerTicker.clear());

	/** The server has stopped. MOD-401: the whole-server sweep of per-level state. */
	public static final List<Consumer<MinecraftServer>> SERVER_STOPPED = List.of(
			server -> LevelStateRegistry.clearAll(),
			server -> PlayerStatsTracker.get().clear());

	/**
	 * One link of the early block-use chain: given the click, a result other than {@code PASS} takes the
	 * interaction and ends the chain.
	 */
	@FunctionalInterface
	public interface UseBlockHook {
		InteractionResult use(Level level, Player player, InteractionHand hand, BlockHitResult hit);
	}

	/**
	 * A player right-clicked a block, before vanilla handles it (Fabric {@code UseBlockCallback}, NeoForge
	 * {@code PlayerInteractEvent.RightClickBlock}; both fire on the client and the server side). The FIRST
	 * result other than {@code PASS} wins and the rest of the chain does not run — the semantics both
	 * loaders had with three separate listeners (research.md §1.3: Fabric's invoker returns the first
	 * non-PASS; NeoForge's {@code addListener} does not deliver a cancelled event to the next listener).
	 */
	public static final List<UseBlockHook> USE_BLOCK = List.of(
			// MOD-077: shift-right-clicking a mod fluid tank with a vanilla lava bucket loads the tank
			// instead of spilling — the seam runs before vanilla's sneak bypass reaches BucketItem#useOn.
			VanillaBucketDeposit::tryDeposit,
			// MOD-238: flint and steel on an oil cell lights it — oil has an empty outline shape, so the
			// click always lands on the block BEHIND it, where vanilla's lighter would fail.
			OilLiquidBlock::tryLight,
			// MOD-666: a dye on an insulated cable paints it; with Shift, the whole run — vanilla never
			// lets a sneaking player's held item reach the block.
			CableDyeing::tryDye);

	/** End of a server tick: the per-level hooks for every level, then the per-server hooks. */
	public static void onServerTick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Consumer<ServerLevel> hook : SERVER_TICK_PER_LEVEL) {
				hook.accept(level);
			}
		}
		run(SERVER_TICK, server);
	}

	/** A server player lost health to damage. */
	public static void onPlayerHurt(ServerPlayer player) {
		run(PLAYER_HURT, player);
	}

	/** A living entity died from {@code source}. */
	public static void onLivingDeath(LivingEntity entity, DamageSource source) {
		for (BiConsumer<LivingEntity, DamageSource> hook : LIVING_DEATH) {
			hook.accept(entity, source);
		}
	}

	/** A player is leaving the server. */
	public static void onPlayerLeave(ServerPlayer player) {
		run(PLAYER_LEAVE, player);
	}

	/** A player joined the server. */
	public static void onPlayerJoin(ServerPlayer player) {
		run(PLAYER_JOIN, player);
	}

	/** A server level unloaded. */
	public static void onLevelUnload(ServerLevel level) {
		run(LEVEL_UNLOAD, level);
	}

	/** The server is starting. */
	public static void onServerStarting(MinecraftServer server) {
		run(SERVER_STARTING, server);
	}

	/** The server is stopping. */
	public static void onServerStopping(MinecraftServer server) {
		run(SERVER_STOPPING, server);
	}

	/** The server has stopped. */
	public static void onServerStopped(MinecraftServer server) {
		run(SERVER_STOPPED, server);
	}

	/** A player right-clicked a block: the first non-{@code PASS} result of the chain, else {@code PASS}. */
	public static InteractionResult onUseBlock(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
		for (UseBlockHook hook : USE_BLOCK) {
			InteractionResult result = hook.use(level, player, hand, hit);
			if (result != InteractionResult.PASS) {
				return result;
			}
		}
		return InteractionResult.PASS;
	}

	private static <T> void run(List<Consumer<T>> hooks, T argument) {
		for (Consumer<T> hook : hooks) {
			hook.accept(argument);
		}
	}
}

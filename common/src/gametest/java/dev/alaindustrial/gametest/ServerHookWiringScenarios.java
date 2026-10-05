package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.radiation.GeigerTicker;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.wearable.JetpackLight;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.stats.PlayerStatsStore;
import dev.alaindustrial.stats.PlayerStatsTracker;
import dev.alaindustrial.teleporter.TeleportWarmupManager;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MOD-706 batch 0 — characterization of the server END-tick wiring each loader does by hand today
 * ({@code ServerTickEvents.END_SERVER_TICK} on Fabric, {@code ServerTickEvent.Post} on NeoForge).
 *
 * <p><b>The rule every scenario here keeps: nothing calls the ticker it checks.</b> The existing suites
 * ({@code JetpackScenarios}, {@code RadiationScenarios}, {@code PlayerStatsScenarios}, the energy suites)
 * drive their subsystem directly, so they stay green with the loader's tick hook deleted. Here each
 * scenario only sets up state that the subsystem's tick alone moves, and then lets the server run: the
 * state moves only if the loader really calls the ticker. MOD-706 moves those calls into one shared
 * roster; these scenarios have to be green before and after.
 *
 * <p><b>Detached players where the hook must find nobody.</b> {@link AlaGameTestHelper#detachedSurvivalPlayer}
 * is not in the player list, and the teleport warmup and the Geiger readings both drop an entry whose
 * player is not online — silently, with no packet to a connection the detached player does not have.
 * That is what makes those two observable without touching anything the rest of a batch shares.
 *
 * <p><b>Not covered here, and why</b> (the list lives in the task's research.md): the level-unload and
 * server-stop sweeps (a gametest cannot unload a level or stop the server), join and disconnect (a mock
 * player never joins through the real handshake on both loaders alike), the damage and death hooks (the
 * cancel path sends a payload, which a mock connection refuses on NeoForge), and the demo-stand sweeper
 * (already observed through the real tick by {@code DemoStandScenarios.demoStandSweepsLateDrops}).
 *
 * <p>One block-use scenario rides along: the early right-click chain (bucket deposit, oil lighting, cable
 * dye — in that order on both loaders) is reached through {@code ServerPlayerGameMode#useItemOn}, the way
 * a player's click is. {@code OilScenarios.fun04BurnGateOnThenOff} already does that for the middle link;
 * this one does it for the LAST link, so a chain that stops early is seen too.
 */
public final class ServerHookWiringScenarios {

	/** The scenarios of this class, replayed by both lanes ({@link ScenarioRoster}). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ServerHookWiringScenarios::energyNetworksTickFromTheServerTick,
						"hooks_energy_networks_tick").ticks(80),
				RosterEntry.of(ServerHookWiringScenarios::teleportWarmupsTickFromTheServerTick,
						"hooks_teleport_warmups_tick").ticks(40),
				RosterEntry.of(ServerHookWiringScenarios::playerStatsFoldFromTheServerTick,
						"hooks_player_stats_fold").ticks(400),
				RosterEntry.of(ServerHookWiringScenarios::jetpackLightsSweptFromTheServerTick,
						"hooks_jetpack_lights_swept").ticks(40),
				RosterEntry.of(ServerHookWiringScenarios::geigerReadingsSpentFromTheServerTick,
						"hooks_geiger_readings_spent").ticks(40),
				RosterEntry.of(ServerHookWiringScenarios::radiationSweepRunsFromTheServerTick,
						"hooks_radiation_sweep_runs").ticks(120),
				RosterEntry.of(ServerHookWiringScenarios::useBlockChainReachesTheCableDye,
						"hooks_use_block_chain_reaches_cable_dye").ticks(40));

		private Roster() {}
	}

	private ServerHookWiringScenarios() {
	}

	/**
	 * MOD-706-HOOK01 — the energy networks tick from the server tick: a creative source, two cables and a
	 * macerator, never driven by hand. The cable-to-consumer transfer happens only inside
	 * {@code NetworkManager.tickAll} (see {@link EnergyLine#drive}), so a charged consumer means the
	 * loader ticked the network.
	 *
	 * @implements MOD-706-HOOK01 — the energy networks tick from the server tick
	 */
	public static void energyNetworksTickFromTheServerTick(GameTestHelper helper) {
		EnergyLine line = EnergyLine.in(helper)
				.generator(ModContent.CREATIVE_ENERGY_SOURCE.get(), ItemStack.EMPTY)
				.cables(2)
				.consumer(ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64))
				.build();
		BlockPos consumer = line.consumerPos();
		helper.succeedWhen(() -> {
			MachineBlockEntity machine = helper.getBlockEntity(consumer, MachineBlockEntity.class);
			if (machine.getEnergyStorage().getAmount() <= 0) {
				helper.fail("the macerator behind two cables received no EU from the server tick alone — "
						+ "the loader does not tick NetworkManager");
			}
		});
	}

	/**
	 * MOD-706-HOOK02 — teleport warmups tick from the server tick: a warmup whose player is not online is
	 * dropped by {@code TeleportWarmupManager.tickAll} on its next pass, and by nothing else.
	 *
	 * @implements MOD-706-HOOK02 — teleport warmups tick from the server tick
	 */
	public static void teleportWarmupsTickFromTheServerTick(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		TeleportWarmupManager.start(player, new TeleportPoint(helper.getLevel().dimension(),
				helper.absolutePos(new BlockPos(5, 2, 5)), "mod706"));
		if (!TeleportWarmupManager.isWarming(player)) {
			helper.fail("start() did not register the warmup — the rig is broken");
			return;
		}
		helper.succeedWhen(() -> {
			if (TeleportWarmupManager.isWarming(player)) {
				helper.fail("a warmup whose player is offline was never swept — the loader does not call "
						+ "TeleportWarmupManager.tickAll");
			}
		});
	}

	/**
	 * MOD-706-HOOK03 — player statistics are folded from the server tick: a recorded delta reaches the
	 * player's attachment within the flush cadence ({@code statsFlushTicks}).
	 *
	 * <p>Other suites call {@code PlayerStatsTracker.clear()} while a batch runs, which would drop this
	 * delta; the delta is therefore recorded again whenever it is found missing, so a concurrent clear
	 * only delays the answer instead of failing it. (A concurrent direct {@code flush} could fold it
	 * early — that only weakens a green, it never makes a correct wiring red.)
	 *
	 * @implements MOD-706-HOOK03 — player statistics are folded from the server tick
	 */
	public static void playerStatsFoldFromTheServerTick(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Identifier probe = Industrialization.id("mod706_wiring_probe");
		long before = PlayerStatsStore.get(player).euProducedTotal();
		PlayerStatsTracker tracker = PlayerStatsTracker.get();
		tracker.recordProduction(server, player.getUUID(), probe, 7);
		if (tracker.pendingEuFor(player.getUUID()) <= 0) {
			helper.fail("the tracker refused the delta for an online survival player — the rig is broken");
			return;
		}
		helper.succeedWhen(() -> {
			if (PlayerStatsStore.get(player).euProducedTotal() > before) {
				return;
			}
			if (tracker.pendingEuFor(player.getUUID()) <= 0) {
				tracker.recordProduction(server, player.getUUID(), probe, 7);
			}
			helper.fail("the recorded EU never reached the attachment — the loader does not call "
					+ "PlayerStatsTracker.onServerTick");
		});
	}

	/**
	 * MOD-706-HOOK04 — the jetpack glow is swept from the server tick: a light nobody refreshes is gone a
	 * tick later, because {@code JetpackLight.sweep} cleared it.
	 *
	 * @implements MOD-706-HOOK04 — the jetpack glow is swept from the server tick
	 */
	public static void jetpackLightsSweptFromTheServerTick(GameTestHelper helper) {
		if (ToolConfig.jetpackFlightLightLevel <= 0) {
			helper.fail("precondition: jetpackFlightLightLevel is 0 in this run's config, so no light is ever placed");
			return;
		}
		ServerLevel level = helper.getLevel();
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		BlockPos lightPos = player.blockPosition().above();
		level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		JetpackLight.ignite(level, player, level.getServer().getTickCount());
		if (!level.getBlockState(lightPos).is(Blocks.LIGHT)) {
			helper.fail("ignite() placed no light at " + lightPos + " — the rig is broken");
			return;
		}
		helper.succeedWhen(() -> {
			if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
				helper.fail("a light nobody refreshed is still there — the loader does not call JetpackLight.sweep");
			}
		});
	}

	/**
	 * MOD-706-HOOK05 — the Geiger counter's reading is spent from the server tick: a reading for a player
	 * who is not online is dropped by {@code GeigerTicker.tick} on its next pass, and by nothing else (the
	 * radiation sweep walks the player list, which a detached player is not on).
	 *
	 * @implements MOD-706-HOOK05 — the Geiger reading is spent from the server tick
	 */
	public static void geigerReadingsSpentFromTheServerTick(GameTestHelper helper) {
		if (RadiationConfig.geigerVolumePercent <= 0) {
			helper.fail("precondition: geigerVolumePercent is 0 in this run's config, so GeigerTicker.tick "
					+ "returns early");
			return;
		}
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		UUID id = player.getUUID();
		GeigerTicker.setStep(player, 2, 0);
		if (!geigerHasReading(id)) {
			helper.fail("setStep() stored no reading — the rig is broken");
			return;
		}
		helper.succeedWhen(() -> {
			if (geigerHasReading(id)) {
				helper.fail("an offline player's reading was never dropped — the loader does not call "
						+ "GeigerTicker.tick");
			}
		});
	}

	/**
	 * MOD-706-HOOK06 — the radiation sweep runs from the server tick: a player online with no counter has
	 * their reading cleared by the sweep ({@code RadiationTicker} → {@code setStep(player, 0, 0)}) within
	 * one sweep interval.
	 *
	 * <p>The time limit is what tells the sweep apart from {@code GeigerTicker.tick}, which would drop the
	 * same reading too — but only once it is stale, after twice the interval. A reading gone within one
	 * interval was cleared by the sweep; one that lingers past it means the sweep is not wired.
	 *
	 * @implements MOD-706-HOOK06 — the radiation sweep runs from the server tick
	 */
	public static void radiationSweepRunsFromTheServerTick(GameTestHelper helper) {
		int interval = Math.max(1, RadiationConfig.radiationTickInterval);
		if (!RadiationConfig.radiationEnabled || interval < 5 || interval > 40) {
			helper.fail("precondition: radiation must be on with radiationTickInterval in 5..40 ticks "
					+ "(enabled=" + RadiationConfig.radiationEnabled + ", interval=" + interval + ")");
			return;
		}
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		MinecraftServer server = helper.getLevel().getServer();
		UUID id = player.getUUID();
		GeigerTicker.setStep(player, 2, 0);
		int start = server.getTickCount();
		helper.succeedWhen(() -> {
			int elapsed = server.getTickCount() - start;
			if (geigerHasReading(id)) {
				helper.fail("the reading of an online player without a counter is still there after " + elapsed
						+ " ticks — the loader does not call RadiationTicker.tickAll");
			}
			if (elapsed > interval + 2) {
				helper.fail("the reading went only after " + elapsed + " ticks (interval " + interval + "): that "
						+ "is GeigerTicker's staleness window, not the radiation sweep");
			}
		});
	}

	/**
	 * MOD-706-HOOK07 — the block-use chain reaches its last link through a real click: a dye right-clicked
	 * on an insulated cable paints it. Vanilla never lets the dye reach the cable on its own (that is why
	 * {@code CableDyeing} rides the loader's early block-use event), so a painted cable means the loader
	 * ran the chain to the end.
	 *
	 * @implements MOD-706-HOOK07 — the block-use chain reaches the cable dye through a real click
	 */
	public static void useBlockChainReachesTheCableDye(GameTestHelper helper) {
		BlockPos cablePos = new BlockPos(2, 2, 2);
		helper.setBlock(cablePos, ModContent.INSULATED_COPPER_CABLE.get());
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.red()));
		BlockPos abs = helper.absolutePos(cablePos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
		CableBlockEntity cable = helper.getBlockEntity(cablePos, CableBlockEntity.class);
		if (cable.color() != DyeColor.RED) {
			helper.fail("a dye clicked on an insulated cable left it " + cable.color() + " (result " + result
					+ ") — the loader's block-use chain did not reach CableDyeing");
			return;
		}
		helper.succeed();
	}

	/** Whether {@code GeigerTicker} holds a reading for {@code player}. Reflection: the map has no reader. */
	@SuppressWarnings("unchecked")
	private static boolean geigerHasReading(UUID player) {
		try {
			Field steps = GeigerTicker.class.getDeclaredField("STEPS");
			steps.setAccessible(true);
			return ((Map<UUID, ?>) steps.get(null)).containsKey(player);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("GeigerTicker.STEPS is gone — update this probe", e);
		}
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 world-scenario bodies for the MOD-133 player-stats attribution rules (shared by the Fabric
 * gametest lane and the NeoForge world lane). Each drives a real macerator via {@code serverTick}
 * (the same technique as {@code CoreEnergyScenarios#maceratorProcessesRecipe}) and asserts against the
 * owner's synced {@link PlayerModStats}, so they pin the actual machine→tracker→attachment path — not
 * a mocked tracker. Every case is a regression guard that fails if its rule is removed:
 *
 * <ul>
 *   <li>a completed operation credits useful EU (XP) to an online, survival owner;</li>
 *   <li>an aborted operation (input pulled before completion) credits nothing — the anti-AFK rule;</li>
 *   <li>a creative owner earns nothing (creative EU is free);</li>
 *   <li>an ownerless machine (structure / {@code /ala demo}) credits nobody;</li>
 *   <li>an offline owner accrues nothing — the gate that also neutralises other mods' fake players;</li>
 *   <li>a running generator credits its owner's career production, but never machine EU;</li>
 *   <li>only EU that fit in the buffer is attributed — never gross production;</li>
 *   <li>gamemode transitions neither wipe existing career EU nor leave accrual stuck off;</li>
 *   <li>a player's logout flushes their pending tail instead of dropping it;</li>
 *   <li>{@code activeTicks} counts a tick once regardless of how many generators fired in it.</li>
 * </ul>
 */
public final class PlayerStatsScenarios {
	private static final BlockPos MAC = new BlockPos(1, 2, 1);
	private static final BlockPos GEO = new BlockPos(1, 2, 1);

	private PlayerStatsScenarios() {
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/** Place a macerator with a full buffer + an emerald input, optionally owned by {@code owner}. */
	private static MaceratorBlockEntity placeMacerator(GameTestHelper helper, UUID owner) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (!(be(helper, MAC) instanceof MaceratorBlockEntity mac)) {
			helper.fail("macerator block entity missing");
			throw new IllegalStateException("unreachable");
		}
		if (owner != null) {
			mac.setOwner(owner, "TestOwner");
		}
		mac.getEnergyStorage().amount = 8000; // > any single op's E_op; bypasses the per-tick cap
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.EMERALD));
		return mac;
	}

	private static void drive(GameTestHelper helper, MaceratorBlockEntity mac, int ticks) {
		for (int i = 0; i < ticks; i++) {
			mac.serverTick(helper.getLevel(), mac.getBlockPos(),
					helper.getLevel().getBlockState(mac.getBlockPos()));
		}
	}

	private static ServerPlayer survivalOwner(GameTestHelper helper) {
		PlayerStatsTracker.get().clear(); // isolate from any prior scenario's pending deltas
		ServerPlayer player = helper.makeMockServerPlayerInLevel(); // defaults to CREATIVE — pin the mode
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A completed macerator operation credits useful EU (the XP source) to its online survival owner. */
	public static void xpFromCompletedWork(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		if (helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == null) {
			helper.fail("mock owner is not in the server player list — attribution can't resolve it");
			return;
		}
		long before = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		MaceratorBlockEntity mac = placeMacerator(helper, player.getUUID());
		drive(helper, mac, 400); // > longest machine duration: completes at least one operation
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long after = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (after <= before) {
			helper.fail("completed macerator op credited no useful EU to owner (before=" + before
					+ ", after=" + after + ")");
			return;
		}
		helper.succeed();
	}

	/** Pulling the input before completion earns no XP — a redstone abort loop must not farm XP. */
	public static void noXpFromAbortedWork(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		MaceratorBlockEntity mac = placeMacerator(helper, player.getUUID());
		drive(helper, mac, 10); // partial progress, well short of completion
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, ItemStack.EMPTY); // abort: input removed
		drive(helper, mac, 10);
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long xp = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (xp != 0) {
			helper.fail("aborted operation wrongly credited useful EU: " + xp);
			return;
		}
		helper.succeed();
	}

	/** A creative owner earns nothing — creative EU is free and must not convert to career XP. */
	public static void noXpForCreativeOwner(GameTestHelper helper) {
		PlayerStatsTracker.get().clear();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE); // the mock already defaults to creative; make it explicit
		MaceratorBlockEntity mac = placeMacerator(helper, player.getUUID());
		drive(helper, mac, 400);
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long xp = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (xp != 0) {
			helper.fail("creative owner wrongly earned useful EU: " + xp);
			return;
		}
		helper.succeed();
	}

	/**
	 * A running generator credits its owner's career production — the total that later converts to the
	 * weak mastery trickle (see {@code Config.euPerXpGenerated}). Uses the geothermal
	 * generator because lava burn is deterministic: unlike the solar family it does not depend on sky
	 * light, time of day or weather inside the test structure.
	 *
	 * <p>Scope, deliberately: this pins the <em>hook</em> — that a generator credits its owner's career
	 * production and nothing else. Remove the hook in {@code AbstractGeneratorBlockEntity} and
	 * {@code euProducedTotal} stays 0 and this fails. The <em>weighting</em> of that production into
	 * mastery points is not asserted here: a geothermal generator burns one lava bucket for ~16k EU and
	 * its buffer caps at 4k, so a world scenario cannot reach the 20k EU that buys a single point —
	 * any XP assertion here would compare zero to zero and pass vacuously. That rule is pinned on the
	 * L1 lane instead ({@code XpDerivationTest}), where the rates are inputs rather than config.
	 */
	public static void generatorProductionAttributedToOwner(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		if (!(be(helper, GEO) instanceof GeothermalGeneratorBlockEntity geo)) {
			helper.fail("geothermal generator block entity missing");
			return;
		}
		geo.setOwner(player.getUUID(), "TestOwner");
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		geo.getEnergyStorage().amount = 0; // empty buffer: every produced EU is actually credited

		BlockPos abs = geo.getBlockPos();
		for (int i = 0; i < 400; i++) {
			geo.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());

		PlayerModStats after = PlayerStatsStore.get(player);
		if (after.euProducedTotal() <= 0) {
			helper.fail("running generator credited no career production to its owner");
			return;
		}
		// The machine term must stay untouched — a generator is not a completed operation.
		if (after.euUsefulConsumedTotal() != 0) {
			helper.fail("generator wrongly credited useful-consumption (machine) EU: "
					+ after.euUsefulConsumedTotal());
			return;
		}
		helper.succeed();
	}

	/**
	 * The buffer cap is respected in attribution: a generator with only partial room credits exactly
	 * what fit, never the gross amount it produced. Geothermal makes 16 EU/t; with 5 EU of room the
	 * owner must be credited 5, not 16.
	 *
	 * <p>Pinning a PARTIALLY-full buffer is deliberate. The obvious version of this test — pin the buffer
	 * to capacity and assert zero production — is <b>vacuous</b>: a generator with no room does not burn
	 * fuel at all, so {@code made} is 0 and the assertion holds even with the whole stats hook deleted.
	 * That version was written first, mutation-tested, and found to pass against a mutant that credited
	 * gross production outside the cap gate. This version fails against exactly that mutant.
	 */
	public static void bufferCapLimitsAttributedProduction(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		if (!(be(helper, GEO) instanceof GeothermalGeneratorBlockEntity geo)) {
			helper.fail("geothermal generator block entity missing");
			return;
		}
		geo.setOwner(player.getUUID(), "TestOwner");
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		long room = 5L; // < geothermalEuPerTick (16), so the cap must bite on the very first tick
		geo.getEnergyStorage().amount = geo.getEnergyStorage().getCapacity() - room;

		BlockPos abs = geo.getBlockPos();
		geo.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());

		long produced = PlayerStatsStore.get(player).euProducedTotal();
		if (produced != room) {
			helper.fail("buffer cap ignored in attribution: room was " + room + " but owner was credited "
					+ produced + " (gross production must not be attributed)");
			return;
		}
		helper.succeed();
	}

	/**
	 * An OFFLINE owner accrues nothing — neither machine work nor generation. This is the gate that also
	 * makes another mod's fake player a no-op: a fake player is never in {@code PlayerList}, so its
	 * synthetic UUID resolves to nobody and no separate fake-player guard is needed (MOD-155).
	 *
	 * <p>Asserted on the tracker's PENDING delta, deliberately, not on the attachment. Attribution is
	 * guarded twice — {@code eligibleOwner} refuses to record for an offline owner, and {@code flush}
	 * drops any delta whose owner is not online — and both leave the attachment untouched. An
	 * attachment-level assertion would therefore stay green with the record-time gate deleted: vacuous.
	 * The pending view is the only place the two guards differ. Mutation-checked: dropping the
	 * {@code player == null} branch from {@code eligibleOwner} makes this scenario fail.
	 *
	 * <p>Both gate call sites are pinned, sequentially in the same position: a mutant that removes the
	 * gate from only {@code recordUsefulWork} or only {@code recordProduction} is still caught. Each half
	 * was mutation-verified separately — the second one only after temporarily disabling the first
	 * assertion, since {@code helper.fail} throws and would otherwise mask it: part 1 fails with
	 * "accrued machine EU: 300", part 2 with "accrued generated EU: 4000". Without that second run the
	 * generator half would have been an unverified claim.
	 */
	public static void noStatsForOfflineOwner(GameTestHelper helper) {
		PlayerStatsTracker.get().clear();
		// A synthetic owner UUID that belongs to no connected player — what a machine placed by another
		// mod's fake player (or one whose owner simply logged off) carries.
		UUID offline = new UUID(0xDEADBEEFL, 0xFEEDL);
		if (helper.getLevel().getServer().getPlayerList().getPlayer(offline) != null) {
			helper.fail("precondition broken: the synthetic offline UUID is a connected player");
			return;
		}

		// 1) Machine work by an offline owner must not be recorded.
		MaceratorBlockEntity mac = placeMacerator(helper, offline);
		drive(helper, mac, 400); // long enough to complete operations, were they credited at all
		long afterWork = PlayerStatsTracker.get().pendingEuFor(offline);
		if (afterWork != 0) {
			helper.fail("offline owner accrued machine EU: " + afterWork + " (the online gate was bypassed)");
			return;
		}

		// 2) Generation by an offline owner must not be recorded either (the other gate call site).
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		if (!(be(helper, GEO) instanceof GeothermalGeneratorBlockEntity geo)) {
			helper.fail("geothermal generator block entity missing");
			return;
		}
		geo.setOwner(offline, "OfflineOwner");
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		geo.getEnergyStorage().amount = 0; // empty buffer: every produced EU would be credited, if it were
		BlockPos abs = geo.getBlockPos();
		for (int i = 0; i < 400; i++) {
			geo.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		long afterGeneration = PlayerStatsTracker.get().pendingEuFor(offline);
		if (afterGeneration != 0) {
			helper.fail("offline owner accrued generated EU: " + afterGeneration
					+ " (the online gate was bypassed on the production path)");
			return;
		}
		helper.succeed();
	}

	/** An ownerless machine (structure-placed / demo stand) credits stats to no one. */
	public static void noStatsForNullOwner(GameTestHelper helper) {
		ServerPlayer bystander = survivalOwner(helper);
		MaceratorBlockEntity mac = placeMacerator(helper, null); // no owner set
		drive(helper, mac, 400);
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long xp = PlayerStatsStore.get(bystander).euUsefulConsumedTotal();
		if (xp != 0) {
			helper.fail("ownerless machine wrongly credited a bystander: " + xp);
			return;
		}
		helper.succeed();
	}

	/**
	 * Gamemode transitions neither wipe existing career EU nor leave the accrual gate stuck. Three
	 * passes on the SAME machine and owner: survival (earns), creative (must not move the total, up or
	 * down), survival again (must resume growing, not restart from zero). This is the pairing of
	 * candidates #2 (survival resumes accrual) and #4 (mode switch does not reset the attachment) from
	 * the MOD-156 coverage request — one richer scenario instead of two thin ones, since the middle
	 * assertion (creative pass changes nothing) is exactly the precondition the resume assertion needs.
	 */
	public static void modeTransitionsPreserveAndResumeAccrual(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		MaceratorBlockEntity mac = placeMacerator(helper, player.getUUID());
		drive(helper, mac, 400); // pass 1: survival, completes at least one operation
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long afterFirstSurvival = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (afterFirstSurvival <= 0) {
			helper.fail("precondition broken: the initial survival pass earned no useful EU");
			return;
		}

		player.setGameMode(GameType.CREATIVE);
		mac.getEnergyStorage().amount = 8000;
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.EMERALD));
		drive(helper, mac, 400); // pass 2: creative, must not move the career total
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long afterCreative = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (afterCreative != afterFirstSurvival) {
			helper.fail("a creative pass changed career EU: was " + afterFirstSurvival + ", now " + afterCreative);
			return;
		}

		player.setGameMode(GameType.SURVIVAL);
		mac.getEnergyStorage().amount = 8000;
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.EMERALD));
		drive(helper, mac, 400); // pass 3: back to survival, accrual must resume from afterCreative, not 0
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long afterSecondSurvival = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (afterSecondSurvival <= afterCreative) {
			helper.fail("switching back to survival did not resume XP accrual (before=" + afterCreative
					+ ", after=" + afterSecondSurvival + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * The logout tail is not lost. {@link PlayerStatsTracker#flushPlayer} is the disconnect-time path —
	 * distinct from the tick-cadence {@link PlayerStatsTracker#flush}, which only runs on
	 * {@code Config.statsFlushTicks} and would otherwise leave a just-completed operation's delta
	 * sitting in {@code pending} past the player's logout, for the NEXT {@link PlayerStatsTracker#flush}
	 * to drop a moment later ({@code getPlayer(uuid) == null} once they are gone). Asserted on both
	 * sides: the pending delta must drain AND the attachment must reflect it, so a flushPlayer that
	 * removed the entry without applying it (losing the tail) would still fail.
	 */
	public static void flushPlayerSavesTailOnLogout(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		MaceratorBlockEntity mac = placeMacerator(helper, player.getUUID());
		drive(helper, mac, 400); // completes an operation; delta lands in `pending`, not yet flushed anywhere

		long pendingBefore = PlayerStatsTracker.get().pendingEuFor(player.getUUID());
		if (pendingBefore <= 0) {
			helper.fail("precondition broken: no pending EU to flush (operation did not complete)");
			return;
		}
		long storedBefore = PlayerStatsStore.get(player).euUsefulConsumedTotal();

		// Simulate the disconnect handler (IndustrializationFabric/NeoForge's DISCONNECT hook) directly,
		// not the tick-cadence flush(server) the other scenarios use.
		PlayerStatsTracker.get().flushPlayer(player);

		long pendingAfter = PlayerStatsTracker.get().pendingEuFor(player.getUUID());
		if (pendingAfter != 0) {
			helper.fail("flushPlayer left a pending tail behind: " + pendingAfter);
			return;
		}
		long storedAfter = PlayerStatsStore.get(player).euUsefulConsumedTotal();
		if (storedAfter <= storedBefore) {
			helper.fail("flushPlayer did not persist the pending tail to the attachment (before=" + storedBefore
					+ ", after=" + storedAfter + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * {@code activeTicks} counts a player's presence in the mod ("active in the mod" time), never the
	 * number of generators that fired. Calls {@link PlayerStatsTracker#recordProduction} directly for
	 * two DIFFERENT generator ids without an intervening {@link PlayerStatsTracker#onServerTick} — i.e.
	 * within the same server tick — and asserts the credited total is exactly +1, not +2. A second phase
	 * crosses a real tick boundary (one {@code onServerTick} call, which clears the per-tick dedup set)
	 * and asserts a further +1, proving the dedup is per-tick, not a one-shot latch that would silently
	 * stop counting active time altogether.
	 *
	 * <p>Calls the tracker directly rather than driving real generator blocks: {@code activeTicks} is
	 * bookkeeping internal to {@link PlayerStatsTracker}, and pinning it through generator ticks would
	 * make the assertion depend on how many blocks happen to fire this tick — exactly the coupling this
	 * test exists to rule out.
	 */
	public static void activeTicksNotScaledByGeneratorCount(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		net.minecraft.resources.Identifier gen1 = dev.alaindustrial.Industrialization.id("diag_test_generator_1");
		net.minecraft.resources.Identifier gen2 = dev.alaindustrial.Industrialization.id("diag_test_generator_2");

		long before = PlayerStatsStore.get(player).activeTicks();

		// Same tick: two distinct generators run; must dedup to one active tick.
		PlayerStatsTracker.get().recordProduction(server, player.getUUID(), gen1, 10);
		PlayerStatsTracker.get().recordActive(server, player.getUUID());
		PlayerStatsTracker.get().recordProduction(server, player.getUUID(), gen2, 10);
		PlayerStatsTracker.get().recordActive(server, player.getUUID());
		PlayerStatsTracker.get().flush(server);
		long afterOneTick = PlayerStatsStore.get(player).activeTicks();
		long deltaOneTick = afterOneTick - before;
		if (deltaOneTick != 1) {
			helper.fail("active ticks scaled with generator count: expected +1 for one tick with 2 generators, got +"
					+ deltaOneTick);
			return;
		}

		// Cross a tick boundary (clears the dedup set) and credit once more: the count must keep growing.
		PlayerStatsTracker.get().onServerTick(server);
		PlayerStatsTracker.get().recordProduction(server, player.getUUID(), gen1, 10);
		PlayerStatsTracker.get().recordActive(server, player.getUUID());
		PlayerStatsTracker.get().flush(server);
		long afterTwoTicks = PlayerStatsStore.get(player).activeTicks();
		if (afterTwoTicks - afterOneTick != 1) {
			helper.fail("active ticks did not accumulate across a new tick: expected +1, got +"
					+ (afterTwoTicks - afterOneTick));
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-156 regression: a generator running into a FULL buffer credits no EU, but must still advance the
	 * "active in the mod" clock. Before the fix both lived inside the same {@code room > 0} branch, so a
	 * saturated network — the normal steady state of a mature base — froze the dashboard's uptime readout
	 * for good. Drives the tracker the way a full-buffer generator tick does: active, but no production.
	 */
	public static void activeTimeAccruesWithFullBuffer(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		// A SOLAR panel, not the geothermal used elsewhere in this file: a fuel burner with no room does not
		// burn at all (made == 0), so it is genuinely idle and this scenario would be vacuous. Solar output
		// depends on sky/light only and keeps producing into a full buffer — the real "saturated base" case.
		helper.setBlock(GEO, ModContent.SOLAR_PANEL.get().defaultBlockState());
		if (!(be(helper, GEO) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity solar)) {
			helper.fail("solar panel block entity missing");
			return;
		}
		solar.setOwner(player.getUUID(), "TestOwner");
		BlockPos abs = solar.getBlockPos();

		// Clear daytime with brightness recomputed synchronously — same recipe the solar suite uses, so the
		// panel actually generates on the very tick we drive (no waiting for the world's own sky update).
		var level = helper.getLevel();
		level.getServer().getCommands()
				.performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set day");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();

		// Control: with room to spare the panel must actually produce here. Without this the main assertion
		// below could pass vacuously at night or under a blocked sky, where nothing is generated at all.
		solar.getEnergyStorage().amount = 0;
		solar.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		PlayerStatsTracker.get().flush(server);
		if (PlayerStatsStore.get(player).euProducedTotal() <= 0) {
			helper.fail("control failed: the panel generated nothing, so the full-buffer case cannot be judged"
					+ " (needs overworld sky and daylight in the test structure)");
			return;
		}

		// The real case: buffer pinned to capacity. No EU can be credited, but the clock must keep running.
		solar.getEnergyStorage().amount = solar.getEnergyStorage().getCapacity();
		long beforeActive = PlayerStatsStore.get(player).activeTicks();
		long beforeProduced = PlayerStatsStore.get(player).euProducedTotal();
		PlayerStatsTracker.get().onServerTick(server); // new tick: clear the per-tick active dedup
		solar.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		PlayerStatsTracker.get().flush(server);

		long activeGain = PlayerStatsStore.get(player).activeTicks() - beforeActive;
		long producedGain = PlayerStatsStore.get(player).euProducedTotal() - beforeProduced;
		if (activeGain != 1) {
			helper.fail("active time stalled on a full buffer: expected +1 active tick, got +" + activeGain);
			return;
		}
		if (producedGain != 0) {
			helper.fail("a full buffer must credit no EU, but production grew by " + producedGain);
			return;
		}
		helper.succeed();
	}
}

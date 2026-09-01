package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 integration suite for the energy network: generator → copper cable → macerator. Covers
 * delivery, split on cable removal, and rejoin on replacement. Compact 3-wide layout to stay inside
 * the default test region. Migrated from legacy {@code NETWORK_DELIVERY/SPLIT/REJOIN}.
 *
 * <p>Drives the line block entities + the per-level {@link NetworkManager} directly (deterministic).
 */
public class NetworkGameTest {

	/** @implements IT-001 — generator delivers EU down a cable to the macerator. @covers R-CON-01,R-CON-05 */
	@GameTest
	public void it001_delivery(GameTestHelper helper) {
		CableEnergyScenarios.generatorDeliversDownCable(helper);
	}

	/**
	 * @implements IT-001-NEG01 — removing the cable splits the net; downstream machine stops.
	 * @covers R-CON-04, R-CON-07
	 *
	 *     <p>Shares one body with {@link #it001Fun02_rejoinResumesFlow} (MOD-445): the rejoin leg only
	 *     means something after a break, so {@link CableEnergyScenarios#networkSplitRejoinResumesFlow}
	 *     drives break-then-rejoin in sequence, asserting the network is gone and the machine starves
	 *     after the break, and re-baselines the wire observation before the rejoin leg.
	 */
	@GameTest
	public void it001Neg01_breakStopsDelivery(GameTestHelper helper) {
		CableEnergyScenarios.networkSplitRejoinResumesFlow(helper);
	}

	/**
	 * @implements IT-001-FUN02 — replacing the cable rejoins the net and flow resumes.
	 * @covers R-CON-09
	 *
	 *     <p>Same shared body as {@link #it001Neg01_breakStopsDelivery} — see there.
	 */
	@GameTest
	public void it001Fun02_rejoinResumesFlow(GameTestHelper helper) {
		CableEnergyScenarios.networkSplitRejoinResumesFlow(helper);
	}

	/**
	 * @implements R-NRG-08 — a single network splits its deliverable energy ~proportionally between
	 * two equal consumers. Two macerators with empty input slots (so they never drain their buffers)
	 * share one cable's worth of generator output; with equal capacity and equal room their buffers
	 * must fill to roughly equal amounts.
	 * @covers R-NRG-08
	 */
	@GameTest
	public void rNrg08_splitsBetweenEqualConsumers(GameTestHelper helper) {
		CableEnergyScenarios.rNrg08_splitsBetweenEqualConsumers(helper);
	}

	/**
	 * @implements R-NRG-09 — a producer-only network (generator + one cable, no consumer) sleeps:
	 *     it has nothing to move, so {@link EnergyNetwork#isAwake()} must be false. Migrated from the
	 *     monolith {@code NETWORK_SLEEP} check.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09_idleNetworkSleeps(GameTestHelper helper) {
		CableEnergyScenarios.rNrg09_idleNetworkSleeps(helper);
	}

	/**
	 * @implements R-NRG-09 (MOD-070) — a source wired to cables with NO consumer still fills the line to
	 *     its buffer capacity: {@code buildProducerOnly} runs a fuelled generator into one cable, and the
	 *     cable must hold exactly {@code cableBuffer} EU afterwards (0 under the old "producer-only sleeps
	 *     immediately, never charges" behaviour). This is why the network then sleeps — the line is full.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09b_sourceFillsCableWithoutConsumer(GameTestHelper helper) {
		CableEnergyScenarios.sourceFillsCableWithoutConsumer(helper);
	}

	/**
	 * @implements R-NRG-09 — an asleep producer-only network wakes when a consumer is placed adjacent
	 *     to the cable WITHOUT replacing a cable: the cable's neighbourChanged hook (fired by
	 *     setBlockAndUpdate) dirties the network, which re-discovers the consumer, becomes awake, and
	 *     delivers EU. Migrated from the monolith {@code NETWORK_WAKE} check.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09_networkWakesOnConsumer(GameTestHelper helper) {
		CableEnergyScenarios.rNrg09_networkWakesOnConsumer(helper);
	}

	// ── MOD-009: BatteryBox charges to 100%; machines served before storage; no self-churn ──────────────

	/**
	 * @implements MOD-009 — a BatteryBox charges all the way to 100% over a multi-cable network. The old
	 *     flat cable-loss term ({@code floor(0.2 × cables)}) was subtracted from the deliverable total,
	 *     so once the BatteryBox's remaining room fell below that loss the last packet never moved and the
	 *     buffer stuck at {@code capacity − loss} (e.g. 19998/20000). With transport now a pure throughput
	 *     limit, the buffer tops off exactly. Pre-charged near full to assert the top-off directly.
	 * @covers R-NRG-01
	 */
	@GameTest(maxTicks = 60)
	public void mod009_batteryBoxChargesToFull(GameTestHelper helper) {
		StorageEnergyScenarios.mod009BatteryBoxChargesToFull(helper);
	}

	// Priority rig: generator + cable + macerator (machine) + BatteryBox (sink) — lives in
	// CableEnergyScenarios (MOD-445).

	/**
	 * @implements MOD-009-PRI — with scarce supply, a working machine is served before a storage sink:
	 *     the macerator fills from the limited generator buffer while the BatteryBox gets only the remainder
	 *     (here 0). Guards the "machines before storage, no BatteryBox starvation" criterion.
	 * @covers R-NRG-08
	 */
	@GameTest(maxTicks = 60)
	public void mod009Pri_machineServedBeforeStorage(GameTestHelper helper) {
		CableEnergyScenarios.mod009Pri_machineServedBeforeStorage(helper);
	}

	// ── TC-CABLE-001-CON04: ring network — rig and drive live in CableEnergyScenarios (MOD-445) ──

	/**
	 * @implements TC-CABLE-001-CON04 — a ring network (two independent cable arms connecting the same
	 *     generator to the same macerator, closed into a cycle by a bypass cable joining the two arms
	 *     directly) delivers EU with no deadlock and no infinite loop: union-find merges the two arms
	 *     into one network the instant the closing cable is placed (tick 0), and {@link
	 *     NetworkManager#tickAll} still completes in a bounded number of ticks.
	 * @covers R-CON-05
	 *
	 *     <p>The two arms are built first as two disjoint networks (verified via {@code networkAt(A0) !=
	 *     networkAt(B0)}); the closing bypass cable is placed last and is cable-to-cable adjacent to both
	 *     arms (RING_A3 and RING_B3), so the union happens through {@link NetworkManager#register}'s
	 *     normal adjacency scan — no product change needed, only a rig that actually closes a cable cycle
	 *     (the prior rig only shared the macerator as a non-cable endpoint, which the union-find graph
	 *     never sees). {@link NetworkManager#networkCount} is per-{@code ServerLevel}, not per-structure,
	 *     so a raw count is unusable here — other gametests run concurrently in the same shared level —
	 *     hence the identity comparison instead of an absolute total.
	 */
	@GameTest
	public void tcCable001Con04_ringNetworkNoDeadlock(GameTestHelper helper) {
		CableEnergyScenarios.ringNetworkMergesOnClose(helper);
	}

	// ── TC-CABLE-001-CON05: two LV generators into one BatteryBox — supply sums, throughput not duplicated ──

	/**
	 * @implements TC-CABLE-001-CON05 — two LV generators (8 EU/t each, {@code Config.fuelEuPerTick})
	 *     feed one BatteryBox through separate cables joined into one network; the delivered EU sums
	 *     both sources (bounded by the LV packet cap per consumer, {@link EnergyTier#LV}), it is not
	 *     dropped or duplicated.
	 * @covers R-CON-01
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Con05_twoGeneratorsSumIntoOneConsumer(GameTestHelper helper) {
		CableEnergyScenarios.twoGeneratorsSumIntoOneConsumer(helper);
	}

	// ── TC-CABLE-001-PHY09: diagonal (edge/corner-only) contact does NOT connect cables ──────────────

	/**
	 * @implements TC-CABLE-001-PHY09 — two cables offset diagonally (shift on both X and Y, touching
	 *     only at a corner/edge, never a shared face) do NOT form one network: connections are strictly
	 *     the 6 orthogonal faces, never diagonal.
	 * @covers R-CON-06
	 */
	@GameTest
	public void tcCable001Phy09_diagonalCablesDoNotConnect(GameTestHelper helper) {
		CableEnergyScenarios.diagonalCablesDoNotConnect(helper);
	}

	// ── TC-CABLE-001-NRG01: throughput cap <=32 EU/t (LV) even when far more is on offer ────────────

	/**
	 * @implements TC-CABLE-001-NRG01 — a generator with an ample pre-charged buffer delivers energy
	 *     through the cable at a bounded rate: at most the per-cable throughput ({@code cableBuffer}
	 *     EU/tick, MOD-070 — the segment carries its buffer size, not the tier voltage) reaches the
	 *     consumer; the surplus is simply not transferred (no overvoltage penalty, R-NRG-04), and the
	 *     cable is not destroyed.
	 * @covers R-NRG-04
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg01_throughputCappedAtLvVoltage(GameTestHelper helper) {
		CableEnergyScenarios.cableThroughputCappedAtLv(helper);
	}

	// ── TC-CABLE-001-NRG02: proportional distance loss over a 10-cable line (MOD-021) ────────────────

	/**
	 * @implements TC-CABLE-001-NRG02 — a 10-cable line loses EU proportional to distance (MOD-021). The
	 *     generator buffer is pre-charged well above the LV packet cap and the BatteryBox is left empty
	 *     (unlimited room), so flow is pinned at the live copper segment buffer; each tick the box gains
	 *     {@code flow - EnergyShare.cableLoss(flow, rate, 10)} EU — strictly less than a lossless line,
	 *     proving the attenuating toll is active and distance-scaled.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 80)
	public void tcCable001Nrg02_lossOverTenCables(GameTestHelper helper) {
		CableEnergyScenarios.mod021LossOverTenCables(helper);
	}

	/** @implements MOD-253 — a 50-cable copper line still delivers and preserves MOD-009 exact top-off. */
	@GameTest(maxTicks = 200)
	public void mod253_longCopperLineStillDeliversAndTopsOff(GameTestHelper helper) {
		CableEnergyScenarios.mod253LongCopperLineStillDeliversAndTopsOff(helper);
	}

	// ── TC-CABLE-001-NRG02b: a single-hop line is loss-free even at a full packet (MOD-021 / MOD-073) ─
	// Rig lives in CableEnergyScenarios (MOD-445).

	/**
	 * @implements TC-CABLE-001-NRG02 (short-line boundary) — at cable-distance 1 the proportional loss
	 *     floors to zero even at a full 32 EU packet ({@code floor(32 × 0.02 × 1) = 0}), so a consumer one
	 *     cable away receives the full throughput. Narrowed from distance 2 to 1 in MOD-073: at 0.02 a
	 *     2-cable hop already loses 1 EU, so only the single-hop case is loss-free.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg02b_noLossOnShortLine(GameTestHelper helper) {
		CableEnergyScenarios.tcCable001Nrg02b_noLossOnShortLine(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG02 (top-off guard, anti-MOD-009) — even on a lossy 10-cable line a
	 *     nearly-full BatteryBox reaches its <em>exact</em> capacity: the last 1-EU top-off packet floors
	 *     to zero loss ({@code floor(1 × 0.02 × 10) = 0}), so it is delivered, not stranded. A flat
	 *     per-tick toll (the removed MOD-009 formula) would leave it stuck at {@code capacity − loss}.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg02c_topsOffExactlyOverLossyLine(GameTestHelper helper) {
		CableEnergyScenarios.tcCable001Nrg02c_topsOffExactlyOverLossyLine(helper);
	}

	// ── TC-CABLE-001-NEG01: cable next to a vanilla furnace — no NPE, no EU leak into it ─────────────

	/**
	 * @implements TC-CABLE-001-NEG01 — a cable adjacent to a vanilla furnace does not leak EU into it:
	 *     {@code EnergyStorage.SIDED.find()} returns null for vanilla blocks (no Team Reborn Energy
	 *     interface exposed), so the network's endpoint discovery simply skips it; no NPE, no crash.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void tcCable001Neg01_vanillaNeighborNoNpe(GameTestHelper helper) {
		// Fabric-only half: the vanilla furnace exposes no Team Reborn Energy view on the face the cable
		// would probe. The loader-neutral half (no NPE over 60 ticks, no EU leaked out of the generator)
		// is the shared body, which places the same three blocks at the same positions.
		BlockPos furnace = new BlockPos(3, 2, 1);
		helper.setBlock(furnace, Blocks.FURNACE);
		EnergyStorage vanillaView = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(furnace),
				Direction.WEST);
		if (vanillaView != null) {
			helper.fail("vanilla furnace unexpectedly exposed an EnergyStorage view");
		}
		CableEnergyScenarios.cableVanillaNeighborNoNpe(helper);
	}

	// ── TC-CABLE-001-NEG02: two cables with no producer/consumer — no phantom EU, no hang over 10k ticks ──

	/**
	 * @implements TC-CABLE-001-NEG02 — two connected cables with neither a producer nor a consumer:
	 *     over 10 000 ticks there is no NPE walking the network, no EU accumulates "in the air", and the
	 *     network stays correctly asleep the whole time (matches {@code rNrg09_idleNetworkSleeps}, but
	 *     stress-tested over a much longer horizon).
	 * @covers R-NRG-09
	 */
	@GameTest(maxTicks = 20)
	public void tcCable001Neg02_twoEmptyCablesTenThousandTicksNoPhantomEu(GameTestHelper helper) {
		CableEnergyScenarios.tcCable001Neg02_twoEmptyCablesTenThousandTicksNoPhantomEu(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG03 — a cabled generator is never drained into the void when its only
	 *     consumer accepts less than a full LV packet. {@code EnergyNetwork.tick} sizes each consumer's
	 *     demand from a simulated {@code insert} (so {@code room} is already capped by the consumer's
	 *     {@code maxInsert}); {@code serveClass} therefore never over-pulls, and {@code returnRoundRobin}
	 *     never receives a surplus to push back into the generator (which publishes {@code maxInsert == 0}
	 *     and would silently destroy it — the same EU-loss shape fixed in {@code EnergyMover}). This drives
	 *     a generator whose only consumer has just 5 EU of room (far below the 32 EU packet cap) and asserts
	 *     the generator loses EXACTLY what the consumer gains — conservation, no void loss. @covers R-NRG-15
	 */
	@GameTest
	public void tcCable001Nrg03_generatorNotDrainedByPartialConsumer(GameTestHelper helper) {
		CableEnergyScenarios.returnRoundRobinNoLeak(helper);
	}

	/**
	 * Storage twin of NRG03 (MOD-445, ran on NeoForge only before): the partial consumer is a Battery
	 * Box with 5 EU of room instead of a machine — the box charges THROUGH the wire, so conservation is
	 * genDrain == boxGain + cableBuffered. @covers R-NRG-15
	 */
	@GameTest
	public void tcCable001Nrg03b_batteryBoxNotDrainedByPartialConsumer(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxConservationPartialConsumer(helper);
	}

	// ── MOD-070: segment-to-segment flow — cables carry a live buffer, energy does not teleport ────

	/**
	 * @implements TC-CABLE-001-NRG04 — MOD-070 accumulation: energy flows through the cable buffers
	 *     segment-to-segment, it does not teleport producer→consumer. After driving a fueled generator
	 *     down a 5-cable line to a working macerator, the intermediate cable holds real EU (asserts >0
	 *     mid-line — impossible under the old teleport model where the buffer was dead) while delivery
	 *     still works, and no cable ever exceeds its tiny {@code cableBuffer} cap (the "no battery from
	 *     wires" ceiling, enforced per segment). @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 80)
	public void tcCable001Nrg04_lineAccumulatesInSegments(GameTestHelper helper) {
		CableEnergyScenarios.lineAccumulatesInSegments(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG05 — MOD-070 break retains at source: once the line has filled,
	 *     removing a middle cable splits the network; the source-side half loses its consumer and sleeps,
	 *     but the EU already buffered in its cables is retained — not voided, not teleported to the now
	 *     disconnected macerator. Asserts the source-side cables still hold >0 EU after the break (the
	 *     "the remainder briefly lingers in the wires" criterion; 0 under the old dead-buffer model).
	 *     @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 100)
	public void tcCable001Nrg05_breakRetainsAtSource(GameTestHelper helper) {
		CableEnergyScenarios.breakRetainsAtSource(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG08 — MOD-219 per-grade throughput: gold carries strictly more EU per
	 *     tick than copper, and tin strictly less. Since a cable's segment buffer IS its throughput
	 *     (MOD-070), this drives the same line three times — once per grade — and compares the live EU
	 *     buffered mid-line. Regression guard for the "recoloured copper" class: before per-cable
	 *     parameters every grade was built with the one shared {@code cableBuffer}, so all three lines
	 *     settled at the same number and both inequalities failed. @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 260)
	public void tcCable001Nrg08_cableGradesCarryTheirOwnBuffer(GameTestHelper helper) {
		CableEnergyScenarios.cableGradesCarryTheirOwnBuffer(helper);
	}

	/**
	 * @implements TC-CABLE-003-PHY01 — MOD-219 in-place grade swap: replacing a copper cable with a gold
	 *     one via setBlock (no break first) must rebuild the segment. All cable blocks share one
	 *     BlockEntityType, so vanilla keeps the old entity across the swap; without the reconcile the
	 *     segment keeps copper's tier AND copper's 12 EU buffer while everything else reports gold.
	 *     @covers R-NRG-14
	 */
	@GameTest(maxTicks = 40)
	public void tcCable003Phy01_inPlaceGradeSwapRebuildsSegment(GameTestHelper helper) {
		CableEnergyScenarios.inPlaceGradeSwapRebuildsSegment(helper);
	}

	/**
	 * @implements TC-CABLE-003-NRG06 — MOD-219 mixed network: splicing ONE gold segment into an otherwise
	 *     copper 10-cable line makes the whole line pay gold's higher loss (0.03 vs copper's 0.02), so the
	 *     same run banks strictly less EU than the all-copper baseline. Unlike the unit tests, which rank
	 *     the grades in isolation, this exercises the real path topology-cache → network → loss term and
	 *     fails if the cache ever stops consulting the strongest cable. @covers R-NRG-14
	 */
	@GameTest(maxTicks = 220)
	public void tcCable003Nrg06_mixedNetworkTakesLossFromStrongestCable(GameTestHelper helper) {
		CableEnergyScenarios.mixedNetworkTakesLossFromStrongestCable(helper);
	}

	@GameTest(maxTicks = 400)
	public void mod259_insulatedCopperLosesLessThanBare(GameTestHelper helper) {
		CableInsulationScenarios.insulatedCopperLosesLessThanBare(helper);
	}

	@GameTest(maxTicks = 400)
	public void mod259_insulatedTinLosesLessThanBare(GameTestHelper helper) {
		CableInsulationScenarios.insulatedTinLosesLessThanBare(helper);
	}

	@GameTest(maxTicks = 400)
	public void mod268_insulatedGoldLosesLessThanBare(GameTestHelper helper) {
		CableInsulationScenarios.insulatedGoldLosesLessThanBare(helper);
	}

	@GameTest(maxTicks = 400)
	public void mod358_insulatedElectrumLosesLessThanBare(GameTestHelper helper) {
		CableInsulationScenarios.insulatedElectrumLosesLessThanBare(helper);
	}

	@GameTest(maxTicks = 400)
	public void mod259_mixedCopperUsesBareLossDeterministically(GameTestHelper helper) {
		CableInsulationScenarios.mixedCopperUsesBareLossDeterministically(helper);
	}

	@GameTest(maxTicks = 40)
	public void mod259_recipesAndVisibility(GameTestHelper helper) {
		CableInsulationScenarios.recipesAndVisibility(helper);
	}

	@GameTest(maxTicks = 40)
	public void mod299_advancedCircuitRecipesAndVisibility(GameTestHelper helper) {
		AdvancedCircuitScenarios.recipesAndVisibility(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod260_energizedBareOnly(GameTestHelper helper) {
		CableShockScenarios.energizedBareOnly(helper);
	}

	@GameTest(maxTicks = 80)
	public void tcBrk001Nrg01_openBreakerCutsTheLine(GameTestHelper helper) {
		CableBreakerScenarios.tcBrk001Nrg01_openBreakerCutsTheLine(helper);
	}

	@GameTest(maxTicks = 80)
	public void tcBrk001Sta01_openBreakerSurvivesCableTicks(GameTestHelper helper) {
		CableBreakerScenarios.tcBrk001Sta01_openBreakerSurvivesCableTicks(helper);
	}

	@GameTest(maxTicks = 80)
	public void tcBrk001Sec01_openBreakerDisarmsTheShock(GameTestHelper helper) {
		CableBreakerScenarios.tcBrk001Sec01_openBreakerDisarmsTheShock(helper);
	}

	@GameTest(maxTicks = 80)
	public void tcBrk001Fun01_removingBreakerRestoresTheLine(GameTestHelper helper) {
		CableBreakerScenarios.tcBrk001Fun01_removingBreakerRestoresTheLine(helper);
	}

	@GameTest(maxTicks = 80)
	public void tcBrk001Vis01_openBreakerGapsTheRun(GameTestHelper helper) {
		CableBreakerScenarios.tcBrk001Vis01_openBreakerGapsTheRun(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod260_retainedBufferIsSafe(GameTestHelper helper) {
		CableShockScenarios.retainedBufferIsSafe(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod269_proximityRadiusRespectsCoverAndConfig(GameTestHelper helper) {
		CableShockScenarios.proximityRadiusRespectsCoverAndConfig(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod279_shockGuardGatesShockAndOpensGraceWindow(GameTestHelper helper) {
		CableShockScenarios.shockGuardGatesShockAndOpensGraceWindow(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod279_shockGuardShieldsFromTheSide(GameTestHelper helper) {
		CableShockScenarios.shockGuardShieldsFromTheSide(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod279_shockGuardInstallRules(GameTestHelper helper) {
		CableShockScenarios.shockGuardInstallRules(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod279_shockGuardPopsWhenDownConnectionAppears(GameTestHelper helper) {
		CableShockScenarios.shockGuardPopsWhenDownConnectionAppears(helper);
	}

	@GameTest(maxTicks = 80)
	public void mod466_insulatedSetProtectsByThePieceAndWearsOut(GameTestHelper helper) {
		CableShockScenarios.insulatedSetProtectsByThePieceAndWearsOut(helper);
	}

	// ── MOD-070: a storage source never charges another storage sink (no battery↔battery wash) ─────

	// Both boxes FACING WEST: BB_SRC's OUT (east/back) feeds the cable; BB_DST's IN (west/front) draws it.
	private static final BlockPos WASH_SRC = new BlockPos(1, 2, 1);
	private static final BlockPos WASH_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos WASH_DST = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-CABLE-001-NRG06 — MOD-314 cascade: a charged BatteryBox connected by cable to an
	 *     empty one (no generator, no machine) DOES charge it, until the two level out. This case used to
	 *     assert the opposite, under MOD-070's blanket "storage never sources for another storage sink"
	 *     rule; that rule stopped battery↔battery washing but also blocked the legitimate "extend the bank
	 *     with a second box" case, which is the bug MOD-314 fixed. Washing is now prevented by construction
	 *     (gradient + half step + deadband) instead of by banning the transfer. @covers R-NRG-08
	 */
	@GameTest(maxTicks = 60)
	public void tcCable001Nrg06_cascadeChargesEmptyBatteryBoxOverCable(GameTestHelper helper) {
		StorageEnergyScenarios.cascadeChargesEmptyBatteryBoxOverCable(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG09 — MOD-314 anti-wash: two BatteryBoxes already at the same fill
	 *     fraction trade nothing, and the pair's total EU does not shrink. The conservation half is the
	 *     load-bearing one: a pair that ping-pongs a packet each way ends up looking level while quietly
	 *     burning MOD-021 loss every lap. @covers R-NRG-08
	 */
	@GameTest(maxTicks = 200)
	public void tcCable001Nrg09_cascadeStopsAtEquilibrium(GameTestHelper helper) {
		StorageEnergyScenarios.cascadeStopsAtEquilibrium(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG10 — MOD-314 regression: a Battery Box with the bus running through it
	 *     (cable on BOTH faces, so it counts as a storage "source" by face role even while empty) must
	 *     still be charged by the cascade. The first cut of the fix keyed its self-serve guard on
	 *     membership of the storage-source list and so silently kept the original bug for this wiring.
	 *     @covers R-NRG-08
	 */
	@GameTest(maxTicks = 100)
	public void tcCable001Nrg10_cascadeChargesMidBusBatteryBox(GameTestHelper helper) {
		StorageEnergyScenarios.cascadeChargesMidBusBatteryBox(helper);
	}

	// ── MOD-070 audit follow-up: storage charges THROUGH the line; lone storage source sleeps ──────

	/**
	 * MOD-214 — an idle producer on the bus must not starve everything past it. Reported in game:
	 * moonlit panels (which give nothing in daylight) sat along the line, and the Battery Box at its end
	 * charged nothing while the cables by the working panels read full. Body is loader-neutral so
	 * NeoForge runs the same scenario.
	 */
	@GameTest(maxTicks = 200)
	public void mod214_storageChargesPastIdleProducer(GameTestHelper helper) {
		StorageEnergyScenarios.storageChargesPastIdleProducer(helper);
	}

	/**
	 * MOD-214 — a BatteryBox whose OUT face also touches a cable must still charge from its IN face.
	 * The reported in-game layout: a cable bus that runs past the box (or two boxes in a row) makes the
	 * box a producer endpoint, and the distance seeding ignored which face produces — poisoning the
	 * cable on its INPUT face and freezing the fill front. Body is loader-neutral so NeoForge runs it too.
	 */
	@GameTest(maxTicks = 100)
	public void mod214_storageChargesWithCabledOutputFace(GameTestHelper helper) {
		StorageEnergyScenarios.storageChargesWithCabledOutputFace(helper);
	}

	/**
	 * @implements MOD-252 — a source must not fence off the sources behind it. The flow field used to be a
	 *     BFS from the sources, so the midpoint between two of them was a local maximum and the
	 *     strictly-downhill pull rule could not cross it: the far generator filled its own dead-end segment
	 *     and stalled at a full buffer forever. Seeded from demand instead, the seam moves to the sinks,
	 *     where it is harmless. Body is loader-neutral so NeoForge runs the same scenario.
	 */
	@GameTest(maxTicks = 80)
	public void mod252_farSourceBehindNearSourceDischarges(GameTestHelper helper) {
		CableEnergyScenarios.mod252FarSourceBehindNearSourceDischarges(helper);
	}

	/**
	 * @implements MOD-252 — the reported shape verbatim: two source groups at different heights on one
	 *     trunk. Topologically identical to the arm case (the cable graph has no notion of Y), pinned
	 *     separately because the playtest report was vertical and sameness is an argument, not a gate.
	 */
	@GameTest(maxTicks = 160)
	public void mod252_verticalSourceGroupsBothDischarge(GameTestHelper helper) {
		CableEnergyScenarios.mod252VerticalSourceGroupsBothDischarge(helper);
	}

	/**
	 * @implements MOD-252 — two consumers on opposite sides of one generator are both served, so reversing
	 *     the field did not simply move the starvation to the other end of the line.
	 */
	@GameTest(maxTicks = 160)
	public void mod252_twoConsumersOnBothSidesOfSource(GameTestHelper helper) {
		CableEnergyScenarios.mod252TwoConsumersOnBothSidesOfSource(helper);
	}

	/**
	 * @implements MOD-252 (review) — the flow field is seeded by every waiting endpoint, machines and
	 *     storage sinks alike. Seeding decides reachability, not priority: with machines alone seeding it,
	 *     one hungry machine fenced off the entire stretch of bus lying past the source, so a Battery Box
	 *     out there stayed at 0 next to dead cable. MOD-009's ordering lives in the serve pass instead.
	 *     Body is loader-neutral so NeoForge runs the same scenario.
	 */
	@GameTest(maxTicks = 200)
	public void mod252_machineAndStorageOnBothSidesOfSource(GameTestHelper helper) {
		CableEnergyScenarios.mod252MachineAndStorageOnBothSidesOfSource(helper);
	}

	/**
	 * @implements MOD-254 (D4) — the line charge rotates its source sweep, so every source on a saturated
	 *     line injects. Starting at index 0 every tick left whoever came later in the list next to a cable
	 *     that was already full, permanently, and the network's rotation cursor was both pinned to 0 on a
	 *     single-producer network and never passed to the charge pass. Body is loader-neutral.
	 */
	@GameTest(maxTicks = 80)
	public void mod254_everySourceFeedsASaturatedLine(GameTestHelper helper) {
		CableEnergyScenarios.mod254EverySourceFeedsASaturatedLine(helper);
	}

	/**
	 * @implements MOD-254 (D3) — a cable buffer claimed by two downstream neighbours is split between them
	 *     instead of going whole to whichever the sweep reached first. Measures the segments flanking the
	 *     fork, because two consumers of one class pool their cable buffers on the serve path and would
	 *     otherwise hide a branch that never carried anything. Body is loader-neutral.
	 */
	@GameTest(maxTicks = 200)
	public void mod254_forkFeedsBothBranches(GameTestHelper helper) {
		CableEnergyScenarios.mod254ForkFeedsBothBranches(helper);
	}

	/**
	 * @implements MOD-252 (D2) — the hole MOD-214 left: its live-supply set was only filled from the
	 *     generator branch, so a base running off a charged Battery Box always hit the fallback and an
	 *     unfuelled generator on the bus went back to cutting the line in two. Body is loader-neutral.
	 */
	@GameTest(maxTicks = 200)
	public void mod252_baseWithoutLiveGeneratorFeedsMachine(GameTestHelper helper) {
		StorageEnergyScenarios.mod252BaseWithoutLiveGeneratorFeedsMachine(helper);
	}

	/**
	 * @implements MOD-255 — a Battery Box wired on both its IN and its OUT face into one network used to
	 *     drink back the EU it had just discharged into the wire: the no-self-churn rule compared positions,
	 *     and on the line path the supply pool is cable buffers, so it never fired. The charge oscillated
	 *     between the box and its own cables and the machines past it stayed on 0 EU. Body is loader-neutral.
	 */
	@GameTest(maxTicks = 200)
	public void mod255_dualRoleBatteryFeedsMachineThroughLine(GameTestHelper helper) {
		StorageEnergyScenarios.mod255DualRoleBatteryFeedsMachineThroughLine(helper);
	}

	/**
	 * @implements MOD-255 — the same both-faces-wired Battery Box with nothing to power must not circulate
	 *     its charge around the ring and burn it on cable loss: with no machine deficit the storage budget
	 *     is zero, so it discharges nothing and the wire stays empty.
	 */
	@GameTest(maxTicks = 80)
	public void mod255_dualRoleBatteryHoldsChargeWithoutConsumers(GameTestHelper helper) {
		StorageEnergyScenarios.mod255DualRoleBatteryHoldsChargeWithoutConsumers(helper);
	}

	/**
	 * @implements TC-CABLE-001-NRG07 — MOD-070 storage-through-line: a BatteryBox charged over a
	 *     multi-cable line pulls its EU THROUGH the wires (not a bypass) — the intermediate cable holds
	 *     real EU while the box fills. This is the storage analogue of NRG04 and the direct regression for
	 *     the in-game bug where a source→cable→BatteryBox link left the cable empty. @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 100)
	public void tcCable001Nrg07_storageChargesThroughLine(GameTestHelper helper) {
		StorageEnergyScenarios.storageChargesThroughLine(helper);
	}

	/**
	 * @implements R-NRG-09 (MOD-070 audit) — a lone storage source (a charged BatteryBox whose OUT face is
	 *     cabled) with NO consumer and NO generator must sleep, not spin forever. Storage discharges into
	 *     the line only for a machine deficit, so with no machine it charges nothing; keeping the network
	 *     awake would run a no-op tick (and an O(cables) scan) every tick indefinitely. Asserts the network
	 *     is asleep and the cable stays empty. @covers R-NRG-09
	 */
	@GameTest(maxTicks = 40)
	public void rNrg09c_loneStorageSourceSleeps(GameTestHelper helper) {
		StorageEnergyScenarios.loneStorageSourceSleeps(helper);
	}

	// ── MOD-156: the LAZY registration path (CableBlockEntity.ensureRegistered, called from
	// onServerTick) must re-register a cable that survives a world/chunk reload while its
	// NetworkManager-side bookkeeping is gone. Every other rig in this file builds via helper.setBlock,
	// which — same as real chunk load — never calls CableBlock#setPlacedBy (no LivingEntity placer), so
	// they all already register lazily on FIRST tick. None of them, though, exercise RE-registration of
	// an already-loaded cable after its network entry disappears, which is exactly what a relog/chunk
	// reload does: the NetworkManager per-level registry (an in-memory IdentityHashMap, never persisted)
	// starts empty again, while a freshly deserialized CableBlockEntity's `registered` field (transient,
	// not saved) also starts false. This rig reproduces BOTH halves of that reset, not just one. ──────

	/**
	 * @implements MOD-156 — a cable that is still block-loaded but whose network registration was lost
	 *     (the exact shape of a player relog / chunk reload: a fresh {@link NetworkManager} registry and
	 *     a fresh {@code registered == false} block entity) re-registers on its own next server tick via
	 *     {@link CableBlockEntity#ensureRegistered()} in {@link CableBlockEntity#onServerTick}, and energy
	 *     delivery resumes. Drives generator → cable A → cable B → BatteryBox to a working baseline, then
	 *     — per-cable, NOT via the level-wide {@link NetworkManager#clear(net.minecraft.server.level.ServerLevel)}
	 *     (this test region shares its {@code ServerLevel} with every other concurrently-running gametest
	 *     structure, so a level-wide clear would corrupt their networks too) — removes both cables from the
	 *     registry with the public {@link NetworkManager#unregister(CableBlockEntity)} and resets each
	 *     entity's {@code registered} flag, reproducing "loaded block, forgotten registration" without
	 *     touching any other structure's state. Asserts the network re-forms, the intermediate cable buffer
	 *     genuinely refills (MOD-070 gotcha: energy flows THROUGH the wire, not around it), and the
	 *     BatteryBox keeps gaining EU past its pre-"reload" baseline.
	 * @covers R-CON-05, R-NRG-09
	 */
	@GameTest(maxTicks = 100)
	public void mod156_lazyPathReregistersAfterReload(GameTestHelper helper) {
		CableEnergyScenarios.mod156_lazyPathReregistersAfterReload(helper);
	}

	/**
	 * @implements MOD-404 — what one network tick over a 50-cable bus costs, asleep and running.
	 *
	 * <p>{@code maxTicks} is generous for the same reason the assembler benchmark's is: the body runs
	 * its three measured phases inside a SINGLE game tick, so the gametest clock is not what bounds it.
	 */
	@GameTest(maxTicks = 100)
	public void perf01_energyNetworkFiftyCableTickCost(GameTestHelper helper) {
		EnergyNetworkPerfScenarios.perf01FiftyCableLineTickCost(helper);
	}

	// ── MOD-479: the creative energy source ──────────────────────────────────────────────

	/** @implements IT-479-BUF — the buffer is still at the configured output after a run that charged a consumer. */
	@GameTest(maxTicks = 200)
	public void it479_bufferHoldsSteadyWhileDelivering(GameTestHelper helper) {
		CreativeEnergySourceScenarios.bufferHoldsSteadyWhileDelivering(helper);
	}

	/** @implements IT-479-DEL — over a run it hands out several times what its own buffer holds. */
	@GameTest(maxTicks = 200)
	public void it479_deliversManyTimesItsOwnCapacity(GameTestHelper helper) {
		CreativeEnergySourceScenarios.deliversManyTimesItsOwnCapacity(helper);
	}

	/** @implements IT-479-SWI — the switch stops delivery, proven against a run that delivered. */
	@GameTest(maxTicks = 100)
	public void it479_switchedOffStopsDelivering(GameTestHelper helper) {
		CreativeEnergySourceScenarios.switchedOffStopsDelivering(helper);
	}

	/** @implements IT-479-ZER — an output of zero behaves exactly like the switch being off. */
	@GameTest(maxTicks = 100)
	public void it479_zeroOutputStopsDelivering(GameTestHelper helper) {
		CreativeEnergySourceScenarios.zeroOutputStopsDelivering(helper);
	}

	/** @implements IT-479-SET — switch and output survive a save/load round trip. */
	@GameTest(maxTicks = 40)
	public void it479_settingsSurviveReload(GameTestHelper helper) {
		CreativeEnergySourceScenarios.settingsSurviveReload(helper);
	}

	/** @implements IT-479-CHA — the charge slot fills an item and is closed to automation on every face. */
	@GameTest(maxTicks = 100)
	public void it479_chargeSlotFillsItemAndRefusesAutomation(GameTestHelper helper) {
		CreativeEnergySourceScenarios.chargeSlotFillsItemAndRefusesAutomation(helper);
	}

	/** @implements IT-479-NOR — no loaded recipe yields the block. */
	@GameTest(maxTicks = 40)
	public void it479_noRecipeYieldsTheBlock(GameTestHelper helper) {
		CreativeEnergySourceScenarios.noRecipeYieldsTheBlock(helper);
	}
}

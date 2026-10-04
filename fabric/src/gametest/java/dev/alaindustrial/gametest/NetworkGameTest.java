package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.CableBlockEntity;
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

	// ── MOD-009: BatteryBox charges to 100%; machines served before storage; no self-churn ──────────────

	// Priority rig: generator + cable + macerator (machine) + BatteryBox (sink) — lives in
	// CableEnergyScenarios (MOD-445).

	// ── TC-CABLE-001-CON04: ring network — rig and drive live in CableEnergyScenarios (MOD-445) ──

	// ── TC-CABLE-001-CON05: two LV generators into one BatteryBox — supply sums, throughput not duplicated ──

	// ── TC-CABLE-001-PHY09: diagonal (edge/corner-only) contact does NOT connect cables ──────────────

	// ── TC-CABLE-001-NRG01: throughput cap <=32 EU/t (LV) even when far more is on offer ────────────

	// ── TC-CABLE-001-NRG02: proportional distance loss over a 10-cable line (MOD-021) ────────────────

	// ── TC-CABLE-001-NRG02b: a single-hop line is loss-free even at a full packet (MOD-021 / MOD-073) ─
	// Rig lives in CableEnergyScenarios (MOD-445).

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

	// ── MOD-070: segment-to-segment flow — cables carry a live buffer, energy does not teleport ────

	// ── MOD-070: a storage source never charges another storage sink (no battery↔battery wash) ─────

	// Both boxes FACING WEST: BB_SRC's OUT (east/back) feeds the cable; BB_DST's IN (west/front) draws it.
	private static final BlockPos WASH_SRC = new BlockPos(1, 2, 1);
	private static final BlockPos WASH_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos WASH_DST = new BlockPos(3, 2, 1);

	// ── MOD-070 audit follow-up: storage charges THROUGH the line; lone storage source sleeps ──────

	// ── MOD-156: the LAZY registration path (CableBlockEntity.ensureRegistered, called from
	// onServerTick) must re-register a cable that survives a world/chunk reload while its
	// NetworkManager-side bookkeeping is gone. Every other rig in this file builds via helper.setBlock,
	// which — same as real chunk load — never calls CableBlock#setPlacedBy (no LivingEntity placer), so
	// they all already register lazily on FIRST tick. None of them, though, exercise RE-registration of
	// an already-loaded cable after its network entry disappears, which is exactly what a relog/chunk
	// reload does: the NetworkManager per-level registry (an in-memory IdentityHashMap, never persisted)
	// starts empty again, while a freshly deserialized CableBlockEntity's `registered` field (transient,
	// not saved) also starts false. This rig reproduces BOTH halves of that reset, not just one. ──────

	// ── MOD-479: the creative energy source ──────────────────────────────────────────────

}

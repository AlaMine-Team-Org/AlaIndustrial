package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 performance smoke for the energy network. Builds a large connected cable field with a generator
 * and a consumer, confirms it unions into one delivering network, then tears the whole field down and
 * times the teardown churn.
 *
 * <p>Each {@link CableBlockEntity#setRemoved()} with two or more cable neighbours re-runs the
 * connected-component BFS ({@code NetworkManager.rebuildComponents}), so a worst-case teardown of an
 * <i>N</i>-cable network is O(N²). This test guards that path against catastrophic regression. The
 * threshold is deliberately generous so it never flaps on a loaded CI box — it only trips if teardown
 * blows up by orders of magnitude (a hang or an accidental super-linear blow-up at this size). The
 * heavy in-world stress on a truly large network is the manual in-game check.
 *
 * <p>Built inside the default 8×8×8 gametest structure: a 6×6×2 cable block (72 cables) with the
 * generator and macerator attached on the region edge. Drives the line block entities + the per-level
 * {@link NetworkManager} directly (deterministic, single synchronous pass). Game tests share one
 * {@code ServerLevel}, so every assertion here is scoped to <i>this</i> field's network (by cable
 * membership), never the level-wide network count.
 */
public class NetworkBenchGameTest {

	// 6×6 cable plane on two stacked layers => 72 connected cables, plus a producer and a consumer.
	private static final int X0 = 1;
	private static final int X1 = 6;
	private static final int Z0 = 1;
	private static final int Z1 = 6;
	private static final int Y0 = 2;
	private static final int Y1 = 3;
	private static final BlockPos GEN = new BlockPos(0, 2, 1); // touches the −x face of cable (1,2,1)
	private static final BlockPos MAC = new BlockPos(7, 3, 6); // touches the +x face of cable (6,3,6)

	/** Generous, non-flapping ceiling for tearing the whole field down (ms). */
	private static final long TEARDOWN_BUDGET_MS = 2000;

	/** Null-safe world lookup (helper.getBlockEntity asserts presence and throws when a block is gone). */
	private BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private void tick(GameTestHelper helper, BlockEntity be) {
		BlockPos p = be.getBlockPos();
		if (be instanceof GeneratorBlockEntity gen) {
			gen.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else if (be instanceof CableBlockEntity c) {
			c.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else if (be instanceof MaceratorBlockEntity mac) {
			mac.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		}
	}

	/** Place the cable field plus a fuelled generator and a working macerator; return every cable pos. */
	private List<BlockPos> buildField(GameTestHelper helper) {
		List<BlockPos> cables = new ArrayList<>();
		for (int y = Y0; y <= Y1; y++) {
			for (int x = X0; x <= X1; x++) {
				for (int z = Z0; z <= Z1; z++) {
					BlockPos p = new BlockPos(x, y, z);
					helper.setBlock(p, ModBlocks.COPPER_CABLE);
					cables.add(p);
				}
			}
		}
		helper.setBlock(GEN, ModBlocks.GENERATOR);
		helper.setBlock(MAC, ModBlocks.MACERATOR);
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
		return cables;
	}

	/** Register every cable by ticking it once (lazy registration), unioning the field into one net. */
	private void registerCables(GameTestHelper helper, List<BlockPos> cables) {
		for (BlockPos p : cables) {
			BlockEntity c = be(helper, p);
			if (c != null) {
				tick(helper, c);
			}
		}
	}

	/**
	 * Large-network smoke + teardown bench (covers R-NRG-08/R-NRG-09, but with no dedicated
	 * case ID — this is an infrastructure check). A 72-cable field unions into a single
	 * network that delivers EU from the generator to the macerator; tearing the whole field down
	 * (which re-runs the component BFS on each cut) completes well within a generous budget and
	 * leaves this field with no network.
	 */
	@GameTest
	public void benchLargeNetworkSmoke(GameTestHelper helper) {
		List<BlockPos> cables = buildField(helper);
		registerCables(helper, cables);

		// One network must own the whole field. Assert on THIS field's network (net.size()), not the
		// level-wide count — game tests share one ServerLevel, so other suites' networks coexist here.
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cables.get(0)));
		if (net == null) {
			helper.fail("no energy network formed on the cable field");
		}
		if (net.size() != cables.size()) {
			helper.fail("field did not union into one network: net=" + net.size() + " cables=" + cables.size());
		}

		// Smoke: drive generator + network + macerator and confirm the consumer receives EU across the
		// whole field (network-scale cable loss is well under the LV transfer cap at this size).
		for (int i = 0; i < 120; i++) {
			BlockEntity gen = be(helper, GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			NetworkManager.tickAll(helper.getLevel());
			BlockEntity mac = be(helper, MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
		long delivered = be(helper, MAC) instanceof MaceratorBlockEntity mac
				? mac.getEnergyStorage().getAmount() : -1;
		if (delivered <= 0) {
			helper.fail("macerator received no EU across the large network: " + delivered);
		}

		// Teardown bench: remove every cable (each cut re-runs the component BFS) and time the churn.
		long startNs = System.nanoTime();
		for (BlockPos p : cables) {
			helper.setBlock(p, Blocks.AIR);
		}
		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

		// This field's network must be gone. Don't assert a level-wide count (other suites share this
		// level) — verify this field's own cables no longer belong to any network.
		boolean fieldGone = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cables.get(0))) == null
				&& NetworkManager.networkAt(helper.getLevel(),
						helper.absolutePos(cables.get(cables.size() - 1))) == null;
		if (!fieldGone) {
			helper.fail("this field's network survived teardown (cables still mapped to a network)");
		}
		Industrialization.LOGGER.info("[bench] teardown of {} cables took {} ms", cables.size(), elapsedMs);
		if (elapsedMs > TEARDOWN_BUDGET_MS) {
			helper.fail("teardown of " + cables.size() + " cables took " + elapsedMs
					+ " ms (> " + TEARDOWN_BUDGET_MS + " ms budget) — component rebuild likely regressed");
		}
		helper.succeed();
	}

	/** Generous ceiling for {@link #benchTickTiming}: 2000 network ticks over the 72-cable field (ms). */
	private static final long TICK_BUDGET_MS = 1500;

	/**
	 * MOD-070 tick-cost smoke. The segment-to-segment transport (propagate one hop + charge entry cables
	 * + serve machines/sinks) runs once per network pass and is O(cables), NOT a per-cable BlockEntity
	 * tick — the whole point of doing it network-side. This times 2000 network ticks over the filled
	 * 72-cable field and guards a deliberately generous absolute budget: it trips only on a catastrophic
	 * regression (an accidental per-block tick, a super-linear pass, or a hang), never on CI jitter.
	 *
	 * <p>The MOD-070 acceptance criterion is "≥500 cables, ≤20 % degradation". The default 8×8×8 gametest
	 * structure caps a single connected field at 72 cables, so the ≥500-scale degradation number is a
	 * manual in-game check (as noted on {@link #benchLargeNetworkSmoke}); this automated lane guards the
	 * per-tick cost shape at the largest in-structure size.
	 */
	@GameTest(maxTicks = 100)
	public void benchTickTiming(GameTestHelper helper) {
		List<BlockPos> cables = buildField(helper);
		registerCables(helper, cables);
		// Warm up so the line is filled and every stage does real work during the timed loop.
		for (int i = 0; i < 60; i++) {
			BlockEntity gen = be(helper, GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			NetworkManager.tickAll(helper.getLevel());
			BlockEntity mac = be(helper, MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
		// Tick the generator (regenerates supply) and macerator (consumes) INSIDE the timed loop so the
		// line stays under real load — otherwise supply drains in ~125 ticks and the rest of the loop
		// times a cheap early-return, not the segment transport (audit finding).
		long startNs = System.nanoTime();
		for (int i = 0; i < 2000; i++) {
			BlockEntity gen = be(helper, GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			NetworkManager.tickAll(helper.getLevel());
			BlockEntity mac = be(helper, MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
		long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
		Industrialization.LOGGER.info("[bench] 2000 network ticks over {} cables took {} ms", cables.size(), elapsedMs);
		if (elapsedMs > TICK_BUDGET_MS) {
			helper.fail("2000 network ticks over " + cables.size() + " cables took " + elapsedMs
					+ " ms (> " + TICK_BUDGET_MS + " ms) — per-tick segment transport likely regressed");
		}
		helper.succeed();
	}
}

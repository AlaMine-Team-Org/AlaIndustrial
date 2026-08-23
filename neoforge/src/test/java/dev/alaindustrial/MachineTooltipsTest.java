package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-022 — headless coverage for the loader-neutral hover-tooltip content ({@link MachineTooltips}). The
 * append logic is a pure function of an {@link ItemStack} + a {@code shiftDown} boolean (no client-only
 * classes — that is why it lives in {@code common}), so it is unit-testable without a render surface: this
 * asserts the CONTENT (which translation keys appear) for machines, the Network Analyzer and non-machine
 * items, in both shift states. Guards the tooltip provider both loaders now register (Fabric
 * {@code ItemTooltipCallback}, NeoForge {@code ItemTooltipEvent}) — the NeoForge side was silently missing
 * before the review.
 *
 * <p>Boots the ephemeral server so {@code ModContent} item/block handles resolve.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class MachineTooltipsTest {

	private static List<String> tooltipKeys(ItemStack stack, boolean shiftDown) {
		List<Component> lines = new ArrayList<>();
		MachineTooltips.append(stack, lines, shiftDown);
		List<String> keys = new ArrayList<>();
		for (Component line : lines) {
			keys.add(line.getContents() instanceof TranslatableContents t ? t.getKey() : line.getString());
		}
		return keys;
	}

	/** A machine block item shows its stat lines + the "hold shift" hint when shift is up. */
	@Test
	void macerator_basicTooltip_showsStatsAndShiftHint(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.MACERATOR.get()), false);
		assertFalse(keys.isEmpty(), "macerator tooltip produced no lines (NeoForge tooltip provider missing?)");
		assertTrue(keys.contains("tooltip.alaindustrial.energy_input"), "basic tooltip missing energy_input line");
		assertTrue(keys.contains("tooltip.alaindustrial.hold_shift"), "basic tooltip missing hold-shift hint");
	}

	/** Holding shift swaps the hint for the detailed lines (tier, per-op energy). */
	@Test
	void macerator_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.MACERATOR.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"), "detailed tooltip missing tier line");
		assertTrue(keys.contains("tooltip.alaindustrial.energy_per_op"), "detailed tooltip missing energy_per_op line");
		assertFalse(keys.contains("tooltip.alaindustrial.hold_shift"), "shift-down must replace the hold-shift hint");
	}

	/** The Network Analyzer tool shows its usage line (and the shift hint when no scan is stored). */
	@Test
	void networkAnalyzer_showsUsageLine(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.NETWORK_ANALYZER.get()), false);
		assertTrue(keys.contains("tooltip.alaindustrial.network_analyzer.usage"),
				"analyzer tooltip missing usage line");
	}

	/** A vanilla non-machine item gets no Ala Industrial lines. */
	@Test
	void nonMachineItem_getsNoLines(MinecraftServer server) {
		assertTrue(tooltipKeys(new ItemStack(Items.DIRT), false).isEmpty(),
				"a non-machine item must not receive machine tooltip lines");
	}

	/**
	 * MOD-432 — the Garden Drone Station passes the machine gate: its basic branch (per-action cost) is
	 * reachable, not dead code behind {@code isMachineBlock()}. Was red on HEAD: the block was absent from
	 * the gate, so the tooltip was empty although the branch and its 20 translations existed.
	 */
	@Test
	void gardenDroneStation_basicTooltip_showsActionCostAndShiftHint(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), false);
		assertTrue(keys.contains("tooltip.alaindustrial.garden_drone_action_cost"),
				"garden drone station basic tooltip missing action-cost line (block not admitted by isMachineBlock?)");
		assertTrue(keys.contains("tooltip.alaindustrial.hold_shift"), "basic tooltip missing hold-shift hint");
	}

	/** MOD-432 — under shift the drone station shows tier, range and buffer instead of the hint. */
	@Test
	void gardenDroneStation_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"), "detailed tooltip missing tier line");
		assertTrue(keys.contains("tooltip.alaindustrial.garden_drone_range"), "detailed tooltip missing range line");
		assertTrue(keys.contains("tooltip.alaindustrial.capacity"), "detailed tooltip missing capacity line");
		assertFalse(keys.contains("tooltip.alaindustrial.hold_shift"), "shift-down must replace the hold-shift hint");
	}

	/**
	 * MOD-432 — the Teleporter passes the machine gate too: it has no basic branch (shift-up shows only the
	 * hint), so its whole tooltip — HV tier, buffer, I/O rule — lives under shift and was dead on HEAD.
	 */
	@Test
	void teleporter_detailedTooltip_onShift(MinecraftServer server) {
		List<String> keys = tooltipKeys(new ItemStack(ModContent.TELEPORTER.get()), true);
		assertTrue(keys.contains("tooltip.alaindustrial.tier_hv"), "teleporter tooltip missing HV tier line");
		assertTrue(keys.contains("tooltip.alaindustrial.buffer"), "teleporter tooltip missing buffer line");
		assertTrue(keys.contains("tooltip.alaindustrial.teleporter_io"),
				"teleporter tooltip missing I/O line (block not admitted by isMachineBlock?)");
		assertFalse(keys.contains("tooltip.alaindustrial.hold_shift"), "shift-down must replace the hold-shift hint");
	}

	/**
	 * MOD-432 — with EU numbers hidden ({@code showEuNumbers=false}) a gated block must still say something
	 * under shift: the drone station falls into the tier-only group of {@code addNonNumericTooltip()}, so the
	 * shift-up hint never points at an empty detailed tooltip.
	 */
	@Test
	void gardenDroneStation_nonNumericTooltip_showsTier(MinecraftServer server) {
		boolean previous = AlaClientConfig.showEuNumbers;
		AlaClientConfig.showEuNumbers = false;
		try {
			List<String> keys = tooltipKeys(new ItemStack(ModContent.GARDEN_DRONE_STATION.get()), true);
			assertTrue(keys.contains("tooltip.alaindustrial.tier_lv"),
					"non-numeric tooltip missing tier line (block absent from addNonNumericTooltip?)");
			assertFalse(keys.contains("tooltip.alaindustrial.hold_shift"), "shift-down must replace the hold-shift hint");
		} finally {
			AlaClientConfig.showEuNumbers = previous;
		}
	}
}

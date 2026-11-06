package dev.alaindustrial.gametest;

import dev.alaindustrial.menu.UpgradeSlotKind;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The upgrade panel's empty arms show what goes into them (MOD-954): every open arm has at least one ghost item, each
 * of them is an item the arm accepts, and a reserved arm shows none.
 */
public final class UpgradeSlotHintScenarios {

	private UpgradeSlotHintScenarios() {
	}

	/** Gametest roster entries for this scenario class (MOD-669). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(UpgradeSlotHintScenarios::everyOpenArmHintsWhatItTakes, "upgrade_slot_hints"));

		private Roster() {
		}
	}

	/**
	 * Each open arm's ghost items are what its tag accepts, never something it refuses; the reserved arms have none.
	 *
	 * @implements MOD-954 — an empty upgrade slot shows the chips that fit it
	 */
	public static void everyOpenArmHintsWhatItTakes(GameTestHelper helper) {
		for (UpgradeSlotKind kind : UpgradeSlotKind.values()) {
			List<Item> hints = kind.hintItems();
			if (kind.isOpen() && hints.isEmpty()) {
				helper.fail("the open arm " + kind + " shows no ghost: a player cannot tell what goes there");
				return;
			}
			if (!kind.isOpen() && !hints.isEmpty()) {
				helper.fail("the reserved arm " + kind + " shows a ghost of " + hints + ", but it accepts nothing");
				return;
			}
			for (Item hint : hints) {
				if (!kind.accepts(new ItemStack(hint))) {
					helper.fail("the arm " + kind + " shows " + hint + " as its ghost but refuses it");
					return;
				}
			}
		}
		if (UpgradeSlotKind.OVERCLOCK.hintItems().size() < 3) {
			helper.fail("the overclock arm must cycle through all three chip tiers, it shows "
					+ UpgradeSlotKind.OVERCLOCK.hintItems());
			return;
		}
		helper.succeed();
	}
}

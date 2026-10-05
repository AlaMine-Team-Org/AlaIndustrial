package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryFed;
import dev.alaindustrial.block.entity.NoUpgradePanel;
import dev.alaindustrial.registry.ContentManifest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Where a machine's slots sit in its one positional inventory: its own slots first, then the four
 * upgrade slots when it has a panel (MOD-080), then the battery drawer when it is fed by one (MOD-679).
 *
 * <p>Computed once, from the same inputs on both sides (MOD-712, BE-1): the block entity builds it from
 * itself in its constructor, and a client menu, which has no block entity behind it, builds the same
 * answer from its block. Before this the block entity's constructor and {@code MachineMenu} each did the
 * arithmetic, and six menus repeated a {@code hasUpgradePanel()} override their block entity also had,
 * with nothing checking that the two agreed.
 *
 * <p>The order is the save format: the drawer goes AFTER the upgrade block, so a save written before the
 * drawer existed loads with every index where it was and the new slot simply empty. A change here moves
 * slots in players' saves — {@code BlockEntitySlotLayoutSnapshotScenarios} names every one that moved.
 *
 * @param baseSlots the machine's own slots (indices {@code 0..baseSlots-1})
 * @param panel whether the four upgrade slots follow them
 * @param batteryDrawer whether the battery drawer slot follows those (only with a panel)
 */
public record SlotLayout(int baseSlots, boolean panel, boolean batteryDrawer) {

	/**
	 * The layout of a block entity: a panel for every {@code MenuProvider} that is not a
	 * {@link NoUpgradePanel}, a drawer for one that is also {@link BatteryFed}.
	 */
	public static SlotLayout of(int baseSlots, boolean menuProvider, boolean panelWanted, boolean batteryFed) {
		boolean panel = menuProvider && panelWanted;
		return new SlotLayout(baseSlots, panel, panel && batteryFed);
	}

	/**
	 * The layout of a client menu's stub container of {@code containerSize} slots. The stub holds the
	 * machine and upgrade slots only — the drawer syncs through a one-slot container of its own — so the
	 * machine's own count is the size less the upgrade block when the block's entity has a panel.
	 */
	public static SlotLayout ofClientStub(Block block, int containerSize) {
		boolean panel = hasPanel(block);
		return new SlotLayout(panel ? containerSize - UpgradePanel.SLOT_COUNT : containerSize, panel, false);
	}

	/**
	 * Whether the block entity of {@code block} carries the upgrade panel — the block alone answers, so the
	 * client, where no block entity exists, agrees with the server. A block the manifest does not know keeps
	 * the default: a panel.
	 */
	public static boolean hasPanel(Block block) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(block);
		if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
			return true;
		}
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			if (def.blocks().contains(key.getPath())) {
				return !NoUpgradePanel.class.isAssignableFrom(def.type());
			}
		}
		return true;
	}

	/** Total inventory size: own slots, the upgrade block, the drawer. */
	public int size() {
		return baseSlots + (panel ? UpgradePanel.SLOT_COUNT : 0) + (batteryDrawer ? 1 : 0);
	}

	/** First index of the upgrade block; equals {@link #baseSlots()}. */
	public int upgradeStart() {
		return baseSlots;
	}

	/** Index of the battery drawer slot — the last index — or -1 when there is none. */
	public int batterySlot() {
		return batteryDrawer ? size() - 1 : -1;
	}
}

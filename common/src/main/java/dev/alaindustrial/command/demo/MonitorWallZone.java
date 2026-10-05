package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Zone <b>monitor</b> (MOD-480): a working monitoring wall you can read from the camera — two
 * stocked chests, smart wires into a powered core, six panels on it, each watching one item.
 * Layout along x from {@code MONITOR_X} (all facing north): chest, wire, core, wire, chest; six
 * panels on the two levels above the wires and the core; a creative energy source behind the core.
 *
 * <p>Domain (coding standard, section 1): Storage.
 */
final class MonitorWallZone implements DemoZone {
	/** Camera of {@code /ala demo tp monitor}. */
	static final DemoStand.TpPoint MONITOR_CAMERA =
			new DemoStand.TpPoint("monitor", 68.0, 7.0, 32.0, 0.0f, 40.0f, false);

	/** Origin of the monitoring wall (MOD-480): x 66..70, z 38..39, on the east side. */
	private static final int MONITOR_Z = 38;
	private static final int MONITOR_X = 66;

	@Override
	public void build(StandWriter w) {
		w.set(MONITOR_X, 1, MONITOR_Z, ModContent.IRON_CHEST.get());
		w.fillSlot(MONITOR_X, 1, MONITOR_Z, 0, new ItemStack(Items.DIAMOND, 37));
		w.fillSlot(MONITOR_X, 1, MONITOR_Z, 1, new ItemStack(Items.IRON_INGOT, 64));
		w.fillSlot(MONITOR_X, 1, MONITOR_Z, 2, new ItemStack(Items.IRON_INGOT, 64));
		w.fillSlot(MONITOR_X, 1, MONITOR_Z, 3, new ItemStack(Items.EMERALD, 5));
		w.set(MONITOR_X + 4, 1, MONITOR_Z, ModContent.IRON_CHEST.get());
		w.fillSlot(MONITOR_X + 4, 1, MONITOR_Z, 0, new ItemStack(Items.GOLD_INGOT, 48));
		w.fillSlot(MONITOR_X + 4, 1, MONITOR_Z, 1, new ItemStack(Items.REDSTONE, 64));
		w.fillSlot(MONITOR_X + 4, 1, MONITOR_Z, 2, new ItemStack(Items.REDSTONE, 64));
		w.fillSlot(MONITOR_X + 4, 1, MONITOR_Z, 3, new ItemStack(Items.REDSTONE, 64));
		w.set(MONITOR_X + 1, 1, MONITOR_Z, ModContent.SMART_WIRE.get());
		w.set(MONITOR_X + 3, 1, MONITOR_Z, ModContent.SMART_WIRE.get());
		w.set(MONITOR_X + 2, 1, MONITOR_Z, ModContent.MONITOR_CORE.get());
		w.configure(MONITOR_X + 2, 1, MONITOR_Z, MonitorCoreBlockEntity.class, "monitor core to charge", core -> {
			core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
			// A bare rack tracks nothing at all, so the stand fits the card that pays for the six
			// panels below — without it every panel would show the yellow cross and the zone would
			// demonstrate the failure mode instead of the feature.
			core.insertCard(new ItemStack(ModContent.CAPACITY_CARD.get()));
			core.setChangedQuietly();
			core.wake();
		});
		// Behind the core: a creative energy source, not a solar panel — the core keeps its charge for as
		// long as the stand stands, at night and in the rain too, instead of for as long as the sun.
		w.set(MONITOR_X + 2, 1, MONITOR_Z + 1, ModContent.CREATIVE_ENERGY_SOURCE.get());
		Item[][] filters = {
				{Items.DIAMOND, Items.IRON_INGOT, Items.EMERALD},
				{Items.GOLD_INGOT, Items.REDSTONE, Items.COAL},
		};
		for (int row = 0; row < filters.length; row++) {
			for (int col = 0; col < filters[row].length; col++) {
				int x = MONITOR_X + 1 + col;
				int y = 2 + row;
				w.set(x, y, MONITOR_Z, ModContent.MONITOR_PANEL.get());
				Item filter = filters[row][col];
				w.configure(x, y, MONITOR_Z, MonitorPanelBlockEntity.class, "monitor panel to set a filter on",
						panel -> panel.setFilter(new ItemStack(filter)));
			}
		}
	}
}

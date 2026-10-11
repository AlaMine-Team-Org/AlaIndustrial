package dev.alaindustrial.gametest;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.energy.ItemBuffers;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModDataComponents;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * MOD-790 — a powered item draws its charge against the SERVER's buffer. A client on a dedicated server holds
 * the player's own file; a server whose buffers differ sends them, and the bar, the "X / Y EU" figures and the
 * HUD must follow the server's numbers while the server's own logic — charging, spending, the clamp — keeps
 * reading this side's {@code ToolConfig}.
 *
 * <p>The snapshot doubles every buffer of {@link ItemBuffers}. A stack holding exactly its local capacity then
 * shows a half bar (6 of 13, where the local balance draws a full one) and {@code shownGet} keeps the whole
 * charge; a stack holding twice the local capacity keeps it shown, while {@code get} still clamps to the local
 * buffer. Every item of {@link PoweredItemCatalog} is checked, so an item whose picking ignores the buffers it
 * is handed is named.
 *
 * <p>No buffer knob is written ({@code PoweredItemEnergySnapshotScenarios} pins them); the snapshot is received
 * and forgotten inside this one synchronous body, so no other scenario sees it.
 */
public final class PoweredItemShownCapacityScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PoweredItemShownCapacityScenarios::shownCapacityFollowsTheServer,
						"powered_item_shown_capacity_follows_the_server"));

		private Roster() {}
	}

	private PoweredItemShownCapacityScenarios() {
	}

	/**
	 * @implements MOD-790-PIC01 — with the server's buffers at twice the local ones, every powered item's bar and
	 *     shown charge follow the server, and the server-side capacity and clamp stay local
	 */
	public static void shownCapacityFollowsTheServer(GameTestHelper helper) {
		Map<String, Object> doubled = new HashMap<>();
		ItemBuffers local = ItemBuffers.live();
		for (RecordComponent component : ItemBuffers.class.getRecordComponents()) {
			doubled.put(component.getName(), 2 * read(component, local));
		}
		List<String> problems = new ArrayList<>();
		ServerBalance.reset();
		try {
			if (!ServerBalance.receive(KnobSnapshot.of(doubled).encode())) {
				helper.fail("the client refused a snapshot of the item buffers");
				return;
			}
			for (Item item : PoweredItemCatalog.poweredItems()) {
				check(problems, item);
			}
		} finally {
			ServerBalance.reset();
		}
		if (!ItemBuffers.live().equals(local)) {
			problems.add("receiving the snapshot changed the local buffers: " + ItemBuffers.live());
		}
		if (!problems.isEmpty()) {
			helper.fail("shown charge does not follow the server: " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	private static void check(List<String> problems, Item item) {
		String id = PoweredItemCatalog.idOf(item);
		ItemStack full = new ItemStack(item);
		long capacity = ItemEnergy.capacity(full);
		ItemEnergy.set(full, capacity);
		if (ItemEnergy.shownCapacity(full) != 2 * capacity) {
			problems.add(id + " shownCapacity=" + ItemEnergy.shownCapacity(full) + " (want the server's " + 2 * capacity
					+ ", local " + capacity + ")");
		}
		if (item.getBarWidth(full) != 6) {
			problems.add(id + "@" + capacity + " bar=" + item.getBarWidth(full) + " (want 6 of the server's "
					+ 2 * capacity + ")");
		}
		if (ItemEnergy.shownGet(full) != capacity || ItemEnergy.get(full) != capacity) {
			problems.add(id + "@" + capacity + " shownGet=" + ItemEnergy.shownGet(full) + " get="
					+ ItemEnergy.get(full));
		}
		// Twice the local buffer, as a server with the doubled buffer would send it: shown whole, clamped by the logic.
		ItemStack brimful = new ItemStack(item);
		brimful.set(ModDataComponents.POUCH_ENERGY.get(), 2 * capacity);
		if (ItemEnergy.shownGet(brimful) != 2 * capacity || item.getBarWidth(brimful) != 13) {
			problems.add(id + "@" + 2 * capacity + " shownGet=" + ItemEnergy.shownGet(brimful) + " bar="
					+ item.getBarWidth(brimful) + " (want the whole charge, a full bar)");
		}
		if (ItemEnergy.get(brimful) != capacity) {
			problems.add(id + "@" + 2 * capacity + " get=" + ItemEnergy.get(brimful) + " (the server clamps to its own "
					+ capacity + ")");
		}
	}

	private static int read(RecordComponent component, ItemBuffers buffers) {
		try {
			return (int) component.getAccessor().invoke(buffers);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read ItemBuffers." + component.getName(), e);
		}
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.MobRepellerHvMenu;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.menu.MobRepellerMvMenu;
import dev.alaindustrial.registry.ContentManifest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 characterization of what every machine menu's sync channels carry (MOD-712, batch 0, BE-7).
 *
 * <p>{@code MenuDataWidthScenarios} guards how MANY channels a menu has; nothing guarded what each one
 * MEANS. The index of a channel is a contract between the block entity's {@code ContainerData} switch,
 * the menu's getter and the screen, written down only in comments. This scenario places every machine of
 * {@link ContentManifest#MENUS} and records each channel's value in two states — as placed, and with its
 * buffer half charged — against a reviewed reference. A channel that moves, a swapped pair or a changed
 * derivation shows up as a different value on its index.
 *
 * <p>The values then travel the way vanilla syncs them: into the menu's CLIENT constructor's stub, one
 * {@code setData} per channel. The reference records what that menu's shared getters ({@code getEnergy},
 * {@code getCapacity}, {@code getProgress}, {@code getMaxProgress}) make of them — the CESU, for one, sends
 * its energy in hundreds and scales it back in its menu — and every machine-specific getter of the menu
 * (its public no-argument {@code get*}/{@code is*} methods returning {@code int}, {@code long} or
 * {@code boolean}, declared below {@link MachineMenu}, read reflectively in name order): the pump's tank
 * level, the fluid machines' permille and fluid ids, the statuses, the reactor console's tabs. So the client
 * half of the contract is pinned too.
 *
 * <p>The SERVER half is asserted, not recorded: each machine's server menu is built and its own data
 * slots are read the way vanilla reads them for the initial sync
 * ({@link AbstractContainerMenu#setSynchronizer} then {@code sendInitialData}); they must be exactly the
 * block entity's channels, in number and in value, in both states, and the server menu's shared getters
 * must agree with the client stub's. The menu is built by {@link MenuProvider#createMenu} — except the
 * three mob repellers, whose {@code createMenu} first pushes the dome payload to the viewer (a side effect
 * the NeoForge mock player refuses); their server menu is built by its server constructor, the same call
 * {@code createMenu} makes after that push, and must be of the stub's class. No machine is skipped: the
 * scenario fails unless every swept machine had its server menu checked, and unless at least
 * {@link #MIN_SERVER_MENUS} were.
 */
public final class MenuChannelValuesScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MenuChannelValuesScenarios::menuChannelsCarryTheReviewedValues,
								"machine_char_menu_channel_values")
						.ticks(200));

		private Roster() {}
	}

	private MenuChannelValuesScenarios() {}

	/** Reused single cell inside the test region; each machine is placed here, read, then cleared. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/** Channels per reference line, so a wide machine (the reactor console) stays readable. */
	private static final int PER_LINE = 12;

	/** Machine-specific getters per reference line. */
	private static final int GETTERS_PER_LINE = 6;

	/** Machines of the sweep on 2026-10-03: 39 built through {@code createMenu}, the 3 repellers by constructor. */
	private static final int MIN_SERVER_MENUS = 42;

	/** The getters the shared line already records; a menu's override of one is not recorded twice. */
	private static final Set<String> SHARED_GETTERS = Set.of("getEnergy", "getCapacity", "getProgress",
			"getMaxProgress");

	/**
	 * @implements MOD-712-CH02 — every machine menu's channels carry the reviewed values when placed and
	 *     when charged, its client menu reads them back through the reviewed getters (shared and
	 *     machine-specific), and its server menu binds exactly the block entity's channels.
	 */
	public static void menuChannelsCarryTheReviewedValues(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		List<String> lines = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		int[] count = new int[2]; // swept, server menus checked
		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			if (!(def.factory().create(1, player.getInventory()) instanceof MachineMenu stub)) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.id()));
			if (block == Blocks.AIR) {
				failures.add(def.id() + ": no block registered under that id");
				continue;
			}
			helper.setBlock(PROBE, block);
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(PROBE));
			count[0]++;
			try {
				if (be instanceof MachineBlockEntity machine) {
					check(def.id(), machine, stub, player, lines, failures, count);
				} else {
					failures.add(def.id() + ": block entity is not a MachineBlockEntity");
				}
			} finally {
				helper.setBlock(PROBE, Blocks.AIR);
			}
		}
		if (count[1] != count[0] || count[1] < MIN_SERVER_MENUS) {
			failures.add("server menus checked: " + count[1] + " of " + count[0] + " swept machines (at least "
					+ MIN_SERVER_MENUS + " expected) — a machine's server menu went unchecked");
		}
		if (!failures.isEmpty()) {
			helper.fail(failures.size() + " machine menus cannot be read:\n  " + String.join("\n  ", failures));
			return;
		}
		ReferenceLines.compare(helper, "MenuChannelValuesSnapshot",
				"Reviewed sync-channel values of every machine menu, placed and half charged (MOD-712) — the"
						+ " reference {@link MenuChannelValuesScenarios} compares against.",
				"*:machine_char_menu_channel*", lines, MenuChannelValuesSnapshot.LINES);
	}

	/** One machine: its server menu must be the stub's class; then both states are recorded and checked. */
	private static void check(String id, MachineBlockEntity machine, MachineMenu stub, ServerPlayer player,
			List<String> lines, List<String> failures, int[] count) {
		AbstractContainerMenu server = serverMenu(machine, player, failures, id);
		if (server == null) {
			return;
		}
		if (server.getClass() != stub.getClass() || !(server instanceof MachineMenu serverMenu)) {
			failures.add(id + ": the server menu is a " + server.getClass().getSimpleName() + ", the client stub a "
					+ stub.getClass().getSimpleName());
			return;
		}
		if (record(id, machine, stub, serverMenu, lines, failures)) {
			count[1]++;
		}
	}

	/**
	 * The machine's server menu: {@code createMenu}, or for a mob repeller its server constructor (see the
	 * class javadoc). Null, with a failure, when neither yields a menu.
	 */
	private static AbstractContainerMenu serverMenu(MachineBlockEntity machine, ServerPlayer player,
			List<String> failures, String id) {
		ContainerLevelAccess access = ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos());
		AbstractContainerMenu menu;
		if (machine instanceof MobRepellerHvBlockEntity hv) {
			menu = new MobRepellerHvMenu(1, player.getInventory(), hv, access);
		} else if (machine instanceof MobRepellerMvBlockEntity mv) {
			menu = new MobRepellerMvMenu(1, player.getInventory(), mv, access);
		} else if (machine instanceof MobRepellerBlockEntity lv) {
			menu = new MobRepellerMenu(1, player.getInventory(), lv, access);
		} else if (machine instanceof MenuProvider provider) {
			menu = provider.createMenu(1, player.getInventory(), player);
		} else {
			failures.add(id + ": block entity provides no menu");
			return null;
		}
		if (menu == null) {
			failures.add(id + ": createMenu built nothing");
		}
		return menu;
	}

	/** Records both states; true when the server menu matched the block entity in both. */
	private static boolean record(String id, MachineBlockEntity machine, MachineMenu stub, MachineMenu server,
			List<String> lines, List<String> failures) {
		ContainerData data = machine.getDataAccess();
		if (stub.getDataChannelCount() != data.getCount()) {
			failures.add(id + ": the client menu has " + stub.getDataChannelCount() + " channels, the block entity"
					+ " projects " + data.getCount());
			return false;
		}
		boolean bound = observe(id + " placed", stub, server, data, lines, failures);
		EnergyBuffer energy = machine.getEnergyStorage();
		energy.setAmountUntracked(energy.getCapacity() / 2);
		return observe(id + " charged", stub, server, data, lines, failures) && bound;
	}

	/**
	 * The raw channels; then — synced into the client menu — what its shared and machine-specific getters
	 * make of them; then whether the server menu's own data slots carry the same channels.
	 */
	private static boolean observe(String what, MachineMenu stub, MachineMenu server, ContainerData data,
			List<String> lines, List<String> failures) {
		values(what, data, lines);
		for (int i = 0; i < data.getCount(); i++) {
			stub.setData(i, data.get(i));
		}
		lines.add(what + " getters: energy=" + stub.getEnergy() + " capacity=" + stub.getCapacity()
				+ " progress=" + stub.getProgress() + "/" + stub.getMaxProgress());
		menuGetters(what, stub, lines, failures);
		return serverMatches(what, server, stub, data, failures);
	}

	/** The server menu's data slots, read through vanilla's initial sync, against the block entity's channels. */
	private static boolean serverMatches(String what, MachineMenu server, MachineMenu stub, ContainerData data,
			List<String> failures) {
		int[] projected = new int[data.getCount()];
		for (int i = 0; i < projected.length; i++) {
			projected[i] = data.get(i);
		}
		int[] synced = syncedDataSlots(server);
		if (!Arrays.equals(projected, synced)) {
			failures.add(what + ": the server menu's data slots " + Arrays.toString(synced)
					+ " are not the block entity's channels " + Arrays.toString(projected));
			return false;
		}
		if (server.getEnergy() != stub.getEnergy() || server.getCapacity() != stub.getCapacity()
				|| server.getProgress() != stub.getProgress() || server.getMaxProgress() != stub.getMaxProgress()) {
			failures.add(what + ": the server menu's shared getters (" + server.getEnergy() + ", "
					+ server.getCapacity() + ", " + server.getProgress() + "/" + server.getMaxProgress()
					+ ") differ from the client menu's");
			return false;
		}
		return true;
	}

	/** What the menu would send a joining viewer as its data slots: vanilla's {@code sendInitialData}. */
	private static int[] syncedDataSlots(AbstractContainerMenu menu) {
		int[][] sent = new int[1][];
		menu.setSynchronizer(new ContainerSynchronizer() {
			@Override
			public void sendInitialData(AbstractContainerMenu m, List<ItemStack> items, ItemStack carried,
					int[] dataSlots) {
				sent[0] = dataSlots.clone();
			}

			@Override
			public void sendSlotChange(AbstractContainerMenu m, int slot, ItemStack stack) {}

			@Override
			public void sendCarriedChange(AbstractContainerMenu m, ItemStack stack) {}

			@Override
			public void sendDataChange(AbstractContainerMenu m, int slot, int value) {}

			@Override
			public RemoteSlot createSlot() {
				return RemoteSlot.PLACEHOLDER;
			}
		});
		return sent[0] == null ? new int[0] : sent[0];
	}

	/** The menu's machine-specific getters, in name order, a few per line; nothing when it has none. */
	private static void menuGetters(String what, MachineMenu stub, List<String> lines, List<String> failures) {
		List<Method> getters = new ArrayList<>();
		for (Method m : stub.getClass().getMethods()) {
			if (isMachineGetter(m)) {
				getters.add(m);
			}
		}
		getters.sort(Comparator.comparing(Method::getName));
		for (int from = 0; from < getters.size(); from += GETTERS_PER_LINE) {
			StringBuilder line = new StringBuilder(what).append(" menu[").append(from).append("]:");
			for (Method m : getters.subList(from, Math.min(getters.size(), from + GETTERS_PER_LINE))) {
				line.append(' ').append(m.getName()).append('=').append(call(m, stub, what, failures));
			}
			lines.add(line.toString());
		}
	}

	/** A public no-argument int/long/boolean get*()/is*() declared below MachineMenu, not a shared getter. */
	private static boolean isMachineGetter(Method m) {
		Class<?> owner = m.getDeclaringClass();
		Class<?> type = m.getReturnType();
		String name = m.getName();
		return owner != MachineMenu.class && MachineMenu.class.isAssignableFrom(owner) && m.getParameterCount() == 0
				&& !Modifier.isStatic(m.getModifiers()) && !m.isBridge() && !m.isSynthetic()
				&& (type == int.class || type == long.class || type == boolean.class)
				&& (name.startsWith("get") || name.startsWith("is")) && !SHARED_GETTERS.contains(name);
	}

	private static String call(Method getter, MachineMenu stub, String what, List<String> failures) {
		try {
			return String.valueOf(getter.invoke(stub));
		} catch (InvocationTargetException e) {
			return "threw " + e.getCause().getClass().getSimpleName();
		} catch (IllegalAccessException e) {
			failures.add(what + ": cannot call " + getter + " — " + e.getMessage());
			return "?";
		}
	}

	private static void values(String prefix, ContainerData data, List<String> lines) {
		int count = data.getCount();
		for (int from = 0; from < count; from += PER_LINE) {
			StringBuilder line = new StringBuilder(prefix).append(" [").append(from).append("]:");
			for (int i = from; i < Math.min(count, from + PER_LINE); i++) {
				line.append(' ').append(data.get(i));
			}
			lines.add(line.toString());
		}
	}
}

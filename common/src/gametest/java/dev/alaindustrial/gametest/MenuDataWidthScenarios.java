package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * MOD-235 — the machine-menu data-width guard, parametric over {@link ContentManifest#MENUS}.
 *
 * <p><b>The bug this exists for.</b> A machine's sync width used to be written twice: the block entity
 * returned it from {@code getDataAccess().getCount()}, and the menu's <em>client</em> constructor built
 * its own {@code new SimpleContainerData(N)} from a magic number. Adding a channel to the block entity
 * and forgetting the menu is invisible on the server — {@code MachineMenu} binds whichever
 * {@link net.minecraft.world.inventory.ContainerData} it was handed, and the server constructor is handed
 * the block entity's — so the whole L2 suite stays green while the real client throws
 * {@code ArrayIndexOutOfBoundsException} out of the render thread the moment the screen reads the channel
 * the stub does not have. That is exactly how MOD-234 shipped a crash past a green build.
 *
 * <p><b>What this asserts.</b> For every menu in the shared manifest that is a {@link MachineMenu}: build
 * the real client constructor (the one a client runs on menu open), place the machine's block, and compare
 * the stub's channel count with the count the placed block entity actually projects. Too narrow → the
 * screen would throw; too wide → the screen would read stale zeros that never sync.
 *
 * <p><b>Why it is parametric.</b> The case list is {@link ContentManifest#MENUS} itself, so a new machine
 * menu joins the guard by being registered — there is no per-machine list here to forget. The
 * {@link #EXPECTED_MACHINE_MENUS} literal keeps that from degrading silently: if the id convention that
 * links a menu to its block ever breaks, machines drop out of the loop, and a count that no longer matches
 * the literal fails the test instead of quietly checking nothing.
 *
 * <p>Loader-neutral (only {@link GameTestHelper} + common content), so both L2 lanes run the same body.
 */
public final class MenuDataWidthScenarios {

	private MenuDataWidthScenarios() {
	}

	/**
	 * How many {@link ContentManifest#MENUS} entries are machine menus (everything except the three chests
	 * and the two teleporter menus, none of which carry a {@link MachineBlockEntity} data bridge). A
	 * literal on purpose — comparing the loop's own count against the manifest would be the tautology that
	 * lets coverage fall to zero unnoticed. Adding a machine menu means bumping this by one.
	 */
	// 31 -> 32 (MOD-418): the Electric Heater's warm-up readout.
	// 33 -> 34 (MOD-424): the Thermal Centrifuge — six channels, the last two being spin and status.
	private static final int EXPECTED_MACHINE_MENUS = 34;

	/** Reused single cell inside the test region; each machine is placed here, read, then cleared. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * Every machine menu's client stub declares exactly as many sync channels as its block entity projects.
	 * Traced by {@code AlaCommonGameTest} TC-CMN-001-REG02.
	 */
	public static void reg02ClientMenuWidthMatchesBlockEntity(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;

		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			AbstractContainerMenu stub = def.factory().create(1, player.getInventory());
			if (!(stub instanceof MachineMenu machineStub)) {
				continue;
			}
			// Every machine menu is registered under its block's own path (macerator, pump, sawmill, …).
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.id()));
			if (block == Blocks.AIR) {
				problems.add(def.id() + ": no block registered under that id — the menu cannot be checked");
				continue;
			}
			helper.setBlock(PROBE, block);
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(PROBE));
			helper.setBlock(PROBE, Blocks.AIR);
			if (!(be instanceof MachineBlockEntity machine)) {
				problems.add(def.id() + ": block entity is " + (be == null ? "missing" : be.getClass().getSimpleName())
						+ ", not a MachineBlockEntity — the menu cannot be checked");
				continue;
			}
			checked++;
			int server = machine.getDataAccess().getCount();
			int client = machineStub.getDataChannelCount();
			if (client != server) {
				problems.add(def.id() + ": client stub has " + client + " channels, "
						+ machine.getClass().getSimpleName() + " projects " + server
						+ " — size the menu's SimpleContainerData from that block entity's DATA_COUNT");
			}
		}

		if (!problems.isEmpty()) {
			helper.fail("menu-data-width: " + String.join("; ", problems));
			return;
		}
		if (checked != EXPECTED_MACHINE_MENUS) {
			helper.fail("menu-data-width: checked " + checked + " machine menus, expected "
					+ EXPECTED_MACHINE_MENUS + " — a menu dropped out of the sweep (renamed block id?) or a new "
					+ "one was added; fix the id or bump EXPECTED_MACHINE_MENUS");
			return;
		}
		helper.succeed();
	}
}

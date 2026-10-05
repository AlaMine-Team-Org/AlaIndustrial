package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorRig.COLUMN;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.SHELL_MAX;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.drive;
import static dev.alaindustrial.gametest.ReactorRig.placeColumn;
import static dev.alaindustrial.gametest.ReactorRig.placeColumnAt;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.reactor.ReactorChannels;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.core.structure.FuelRodMath;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * World scenarios of the controller's console (MOD-618, MOD-620, MOD-622): the channels the panel draws, the
 * «Core» tab's snapshot and the event log. The log helpers are shared with the scenarios that read the log.
 * Reached through the {@link ReactorScenarios} facade, whose names the two lanes run (MOD-713, TST-2).
 */
final class ReactorConsoleScenarios {

	private ReactorConsoleScenarios() {}

	/**
	 * The heat marks the console draws travel on the menu's channels from THIS server's Config, and the
	 * controller's menu carries no slots — not even the player's inventory (MOD-618).
	 *
	 * <p><b>Read at the defaults instead of after moving Config.</b> Scenarios in a batch tick at the same
	 * time, so a threshold moved here would move for every other reactor running beside this one. The two
	 * defaults differ from each other, which is what still catches a swapped index — and the scenario says
	 * so out loud if a future balance pass ever makes two of them equal.
	 */
	public static void consoleChannelsCarryTheServersHeatMarks(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		if (ReactorConfig.reactorHeatWarnPercent == ReactorConfig.reactorMeltdownStartPercent) {
			helper.fail("the two heat thresholds share a default, so a swapped channel would pass unseen");
		}
		ContainerData data = brain.getDataAccess();
		if (data.getCount() != ReactorChannels.COUNT) {
			helper.fail("controller bridge is " + data.getCount() + " wide, expected "
					+ ReactorChannels.COUNT);
		}
		// A reaction that never ran is not short of water: the share reads full, not a stale threshold.
		expectChannel(helper, data, ReactorChannels.COOLANT_SHARE.ordinal(), 100, "coolant share");
		expectChannel(helper, data, ReactorChannels.HEAT_WARN.ordinal(),
				ReactorConfig.reactorHeatWarnPercent, "warning line");
		expectChannel(helper, data, ReactorChannels.HEAT_MELTDOWN.ordinal(),
				ReactorConfig.reactorMeltdownStartPercent, "meltdown line");
		// The room limits the «Room» tab's checklist quotes, from the same Config the scan reads (MOD-619).
		expectChannel(helper, data, ReactorChannels.ROOM_MIN_INNER.ordinal(),
				ReactorConfig.reactorRoomMinInner, "smallest room");
		expectChannel(helper, data, ReactorChannels.ROOM_MAX_INNER.ordinal(),
				ReactorConfig.reactorRoomMaxInner, "largest room");
		expectChannel(helper, data, ReactorChannels.ROOM_MAX_GLASS.ordinal(),
				ReactorConfig.reactorRoomMaxGlassPercent, "glass cap");

		ReactorControllerMenu serverMenu = new ReactorControllerMenu(0,
				helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), brain, ContainerLevelAccess.NULL);
		if (!serverMenu.slots.isEmpty()) {
			helper.fail("server menu carries " + serverMenu.slots.size() + " slots, expected none");
		}
		ReactorControllerMenu clientMenu = new ReactorControllerMenu(0,
				helper.makeMockPlayer(GameType.SURVIVAL).getInventory());
		if (!clientMenu.slots.isEmpty()) {
			helper.fail("client menu carries " + clientMenu.slots.size() + " slots, expected none");
		}
		helper.succeed();
	}

	/**
	 * The «Core» tab's snapshot tells the truth about the racks in the world (MOD-620).
	 *
	 * <p>A two-high stack — a fresh rod and a worn one below, a spent casing above — and some water. What the
	 * snapshot reports is compared with what the racks hold, read back from the racks themselves, so the scenario
	 * pins the fold into stacks rather than restating its own literals. The reactor has no redstone signal, so the
	 * rods keep the wear this scenario gave them.
	 */
	public static void zoneSnapshotMatchesTheRacks(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity low = placeColumn(helper);
		FuelRodAssemblyBlockEntity high = placeColumnAt(helper, COLUMN.above());
		ItemStack worn = new ItemStack(ModContent.URANIUM_FUEL_ROD.get());
		worn.setDamageValue(400);
		low.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		low.insertRod(worn);
		high.insertRod(new ItemStack(ModContent.EMPTY_FUEL_ROD.get()));
		low.setTank(true, 2000);
		drive(helper, brain, ReactorConfig.reactorScanIntervalTicks + 1);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal, so there is no core to snapshot: " + brain.getStatus());
		}

		ReactorZonePayload zone = brain.zoneSnapshot(7);
		int inner = SHELL_MAX - 1;
		if (zone.containerId() != 7 || zone.width() != inner || zone.depth() != inner) {
			helper.fail("snapshot is for container " + zone.containerId() + ", " + zone.width() + " x " + zone.depth()
					+ "; expected 7 and the " + inner + " x " + inner + " interior");
		}
		if (zone.originDx() != 1 - CONTROLLER.getX() || zone.originDz() != 1 - CONTROLLER.getZ()) {
			helper.fail("zone corner is " + zone.originDx() + ", " + zone.originDz() + " from the controller; the "
					+ "interior starts at " + (1 - CONTROLLER.getX()) + ", " + (1 - CONTROLLER.getZ()));
		}
		if (zone.stacks().size() != 1) {
			helper.fail("two columns on one spot are one stack, got " + zone.stacks().size());
		}
		ReactorZone.Stack stack = zone.stacks().get(0);
		long rodEnergy = ReactorCore.rodEnergy(ReactorConfig.reactorEuPerRod, ReactorConfig.reactorRodBurnTicks);
		int fuelled = 0;
		int spent = 0;
		long wearSum = 0;
		int worst = 0;
		long remaining = 0;
		List<ItemStack> racked = new ArrayList<>(low.contents());
		racked.addAll(high.contents());
		for (ItemStack rod : racked) {
			if (rod.is(ModContent.URANIUM_FUEL_ROD.get())) {
				fuelled++;
				int wear = FuelRodMath.wearPermille(rod.getDamageValue());
				wearSum += wear;
				worst = Math.max(worst, wear);
				remaining += FuelRodMath.remainingEnergy(rod.getDamageValue(), rodEnergy);
			} else {
				spent++;
				wearSum += 1000;
				worst = 1000;
			}
		}
		int average = (int) (wearSum / racked.size());
		if (stack.x() != COLUMN.getX() - 1 || stack.z() != COLUMN.getZ() - 1) {
			helper.fail("stack at " + stack.x() + ", " + stack.z() + " of the interior; the column stands at "
					+ (COLUMN.getX() - 1) + ", " + (COLUMN.getZ() - 1));
		}
		if (stack.columns() != 2 || stack.fuelledRods() != fuelled || stack.spentRods() != spent) {
			helper.fail("stack holds " + stack.columns() + " columns, " + stack.fuelledRods() + " fuelled, "
					+ stack.spentRods() + " spent; the racks hold 2, " + fuelled + ", " + spent);
		}
		if (stack.averageWearPermille() != average || stack.worstWearPermille() != worst
				|| stack.remainingEu() != remaining) {
			helper.fail("stack wear " + stack.averageWearPermille() + "/" + stack.worstWearPermille() + " and "
					+ stack.remainingEu() + " EU left; the rods say " + average + "/" + worst + " and " + remaining);
		}
		if (stack.neighbours() != 0) {
			helper.fail("a spent casing is not a fuelled neighbour, got " + stack.neighbours());
		}
		long held = low.waterTank.amount + high.waterTank.amount;
		long capacity = low.waterTank.capacity + high.waterTank.capacity;
		int water = (int) ((held * 200 + capacity) / (capacity * 2));
		if (stack.waterPercent() != water) {
			helper.fail("stack water " + stack.waterPercent() + "%, the tanks hold " + water + "%");
		}
		// The «Coolant» tab's numbers (MOD-621): millibuckets as the tanks hold them, and the two faults judged from
		// the same tanks. One touching pair is one vessel, so the lower column's water keeps the pair from reading dry.
		ReactorZone.Coolant coolant = stack.coolant();
		long steam = low.steamTank.amount + high.steamTank.amount;
		long steamCapacity = low.steamTank.capacity + high.steamTank.capacity;
		if (coolant.water() != held || coolant.waterCapacity() != capacity || coolant.steam() != steam
				|| coolant.steamCapacity() != steamCapacity) {
			helper.fail("stack tanks " + coolant.water() + "/" + coolant.waterCapacity() + " mB water, "
					+ coolant.steam() + "/" + coolant.steamCapacity() + " mB steam; the columns hold " + held + "/"
					+ capacity + " and "
					+ steam + "/" + steamCapacity);
		}
		if (coolant.dry() || coolant.blocked()) {
			helper.fail("a stack with water and an empty steam tank read dry=" + coolant.dry() + ", blocked="
					+ coolant.blocked());
		}
		low.setTank(false, low.steamTank.capacity);
		high.setTank(false, high.steamTank.capacity);
		if (!brain.zoneSnapshot(7).stacks().get(0).coolant().blocked()) {
			helper.fail("a stack whose steam tanks are full does not read as a blocked exhaust");
		}
		low.setTank(true, 0);
		if (!brain.zoneSnapshot(7).stacks().get(0).coolant().dry()) {
			helper.fail("a stack with no water left does not read as dry");
		}
		helper.succeed();
	}

	/**
	 * The «Log» tab's server half (MOD-622): one line per transition, a burst of throttle clicks folded into one line
	 * naming the player, a save that round-trips without a false line, and each player's progress kept apart.
	 *
	 * <p><b>The reload is the point.</b> Most of what the controller knows is not saved and reads as a default until
	 * its first scan, so a log keyed on the live fields would write "stopped" and "started" again for a reactor that
	 * never changed. The reloaded block entity here is built from the saved tag alone and driven on, and the log must
	 * come out of it exactly as it went in. No config is touched: a room, one rod and a redstone block are enough.
	 */
	public static void theEventLogRecordsEachTransitionOnce(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		drive(helper, brain, 120);
		expectLog(helper, brain, "a room that sealed and started",
				ReactorLog.Kind.ROOM_SEALED, ReactorLog.Kind.REACTION_STARTED);
		int inner = SHELL_MAX - 1;
		ReactorLog.Entry sealed = brain.logEntries().get(0);
		ReactorLog.Entry started = brain.logEntries().get(1);
		if (sealed.a() != inner || sealed.b() != inner || sealed.c() != inner) {
			helper.fail("the sealed line reads " + sealed.a() + "x" + sealed.b() + "x" + sealed.c()
					+ ", the interior is " + inner + " on every side");
		}
		if (started.a() != 1 || started.b() != 100) {
			helper.fail("the started line reads " + started.a() + " rods at " + started.b()
					+ "%; one rod went in at 100%");
		}

		// Two clicks by one player inside the merge window are one decision: one line, from where the burst began.
		ServerPlayer operator = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer visitor = AlaGameTestHelper.survivalPlayer(helper);
		if (operator.getUUID().equals(visitor.getUUID())) {
			helper.fail("the two mock players share a UUID, so their progress cannot be told apart");
		}
		ReactorControllerMenu operatorMenu =
				new ReactorControllerMenu(0, operator.getInventory(), brain, ContainerLevelAccess.NULL);
		operatorMenu.clickMenuButton(operator, 60);
		operatorMenu.clickMenuButton(operator, 70);
		expectLog(helper, brain, "two throttle clicks",
				ReactorLog.Kind.ROOM_SEALED, ReactorLog.Kind.REACTION_STARTED, ReactorLog.Kind.DEPTH_CHANGED);
		ReactorLog.Entry depth = brain.logEntries().get(2);
		if (depth.a() != 100 || depth.b() != 70 || !depth.actor().equals(operator.getName().getString())) {
			helper.fail("the throttle line reads " + depth.a() + "% -> " + depth.b() + "% by '" + depth.actor()
					+ "'; expected 100% -> 70% by " + operator.getName().getString());
		}

		// Seen is per player, and only as far as a screen was actually sent: a menu that never pushed the log to its
		// viewer cannot mark it read, and a click claiming more than was sent is capped at what was.
		ReactorControllerMenu visitorMenu =
				new ReactorControllerMenu(1, visitor.getInventory(), brain, ContainerLevelAccess.NULL);
		visitorMenu.clickMenuButton(visitor, ReactorControllerMenu.logSeenButton(depth.seq()));
		// The snapshot is taken as the menu's own broadcast takes it, without the send: a mock player's connection has
		// no channels to receive it on.
		if (operatorMenu.pollLogSnapshot(operator.getUUID()) == null) {
			helper.fail("a screen that has never been sent the log was not due its first snapshot");
		}
		operatorMenu.clickMenuButton(operator, ReactorControllerMenu.logSeenButton(depth.seq() + 5));
		if (brain.logSnapshot(0, operator.getUUID()).seenSeq() != depth.seq()
				|| brain.logSnapshot(1, visitor.getUUID()).seenSeq() != 0) {
			helper.fail("seen reads " + brain.logSnapshot(0, operator.getUUID()).seenSeq() + " for the operator and "
					+ brain.logSnapshot(1, visitor.getUUID()).seenSeq() + " for a visitor never sent the log; expected "
					+ depth.seq() + " and 0");
		}

		// The save carries the log; the tag every nearby player receives with the chunk does not.
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag saved = brain.saveCustomOnly(registries);
		CompoundTag update = brain.getUpdateTag(registries);
		for (String key : List.of("Log", "LogNextSeq", "LogReaders", "LogRunning")) {
			if (!saved.contains(key)) {
				helper.fail("the saved controller has no '" + key + "'");
			}
			if (update.contains(key)) {
				helper.fail("the update tag sent to every nearby player carries '" + key + "'");
			}
		}

		BlockPos absolute = helper.absolutePos(CONTROLLER);
		ReactorControllerBlockEntity reloaded =
				new ReactorControllerBlockEntity(absolute, helper.getLevel().getBlockState(absolute));
		reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
		reloaded.setLevel(helper.getLevel());
		List<ReactorLog.Entry> before = brain.logEntries();
		drive(helper, reloaded, 60);
		if (!reloaded.logEntries().equals(before)) {
			helper.fail("a reload of a running reactor changed its log: " + before + " became "
					+ reloaded.logEntries());
		}
		if (reloaded.logSnapshot(0, operator.getUUID()).seenSeq() != depth.seq()) {
			helper.fail("the operator's progress did not survive the reload");
		}

		// A stop is written once the drone falls silent, with the reason taken on the tick the reaction stopped, and
		// numbered after the saved lines — never reusing one.
		helper.setBlock(CONTROLLER.west(), Blocks.AIR.defaultBlockState());
		drive(helper, reloaded, 5);
		if (reloaded.logEntries().size() != before.size()) {
			helper.fail("a stop was logged before the drone's latch ran out: " + reloaded.logEntries());
		}
		drive(helper, reloaded, 60);
		List<ReactorLog.Entry> after = reloaded.logEntries();
		ReactorLog.Entry stop = after.get(after.size() - 1);
		if (after.size() != before.size() + 1 || stop.kind() != ReactorLog.Kind.REACTION_SCRAMMED
				|| stop.seq() != depth.seq() + 1) {
			helper.fail("after the signal went: " + after + "; expected one REACTION_SCRAMMED numbered "
					+ (depth.seq() + 1));
		}
		helper.succeed();
	}

	static List<ReactorLog.Kind> logKinds(ReactorControllerBlockEntity brain) {
		return brain.logEntries().stream().map(ReactorLog.Entry::kind).toList();
	}

	static long logCount(ReactorControllerBlockEntity brain, ReactorLog.Kind kind) {
		return logKinds(brain).stream().filter(k -> k == kind).count();
	}

	static void expectLog(GameTestHelper helper, ReactorControllerBlockEntity brain, String when,
			ReactorLog.Kind... kinds) {
		if (!logKinds(brain).equals(List.of(kinds))) {
			helper.fail(when + ": the log reads " + logKinds(brain) + ", expected " + List.of(kinds));
		}
	}

	static void expectChannel(GameTestHelper helper, ContainerData data, int index, int expected,
			String what) {
		if (data.get(index) != expected) {
			helper.fail(what + " channel " + index + " reads " + data.get(index) + ", Config says " + expected);
		}
	}
}

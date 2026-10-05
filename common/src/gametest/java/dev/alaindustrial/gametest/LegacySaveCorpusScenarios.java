package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.fresh;
import static dev.alaindustrial.gametest.SaveFormatTestSupport.load;
import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;
import static dev.alaindustrial.gametest.SaveFormatTestSupport.scalarDrift;

import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 save-format corpus (MOD-701, batch 0): one tag per block-entity family, written in the shape
 * release 0.1.194 saves, handed straight to {@code loadWithComponents}. Nothing in a corpus entry
 * comes from the current save path, so this is the "a world saved by the last release still opens"
 * guarantee, in the spirit of {@code PersistenceScenarios.mod556_preRefactorSavesStillLoad}.
 *
 * <p><b>Where the entries come from.</b> Each tag is the key set that the {@code saveAdditional}
 * chain of that block entity writes at tag {@code v0.1.194-mc26.3} (the block-entity sources did not
 * change between that tag and the batch that added this class), with values chosen to be
 * distinguishable from every default. Two entries are older than that on purpose: the Battery Box
 * with no {@code AlaDataVersion} (a save from before MOD-083, repaired by the v0 -> v1 rung) and the
 * kok-sagyz root whose soil is a 26.2 block-state compound (MOD-645) — the one line-specific entry,
 * and it belongs on both lines, because a 26.2 world is opened by a 26.3 build.
 *
 * <p><b>What is checked.</b> The values a player would miss — energy, items on their own indices,
 * fluid, block state — by meaning; and every other scalar key by value, through a re-save
 * ({@link SaveFormatTestSupport#scalarDrift}): a key that no longer loads comes back as its default
 * and is named. The literals are hand-written for the same reason as in the MOD-556 scenarios: a
 * corpus derived from the production code would agree with any rename.
 */
public final class LegacySaveCorpusScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(LegacySaveCorpusScenarios::machinesStillLoad, "save_format_corpus_machines_still_load")
						.fabricId("SaveFormatGameTest", "corpusMachinesStillLoad").ticks(20, 40),
				RosterEntry.of(LegacySaveCorpusScenarios::batteryBoxV0AndV1StillLoad,
								"save_format_corpus_battery_box_v0_and_v1_still_load")
						.fabricId("SaveFormatGameTest", "corpusBatteryBoxV0AndV1StillLoad").ticks(20, 40),
				RosterEntry.of(LegacySaveCorpusScenarios::transportStillLoads,
								"save_format_corpus_transport_still_loads")
						.fabricId("SaveFormatGameTest", "corpusTransportStillLoads").ticks(20, 40),
				RosterEntry.of(LegacySaveCorpusScenarios::storageStillLoads, "save_format_corpus_storage_still_loads")
						.fabricId("SaveFormatGameTest", "corpusStorageStillLoads").ticks(20, 40),
				RosterEntry.of(LegacySaveCorpusScenarios::mc262AgricultureStillLoads,
								"save_format_corpus_mc262_agriculture_still_loads")
						.fabricId("SaveFormatGameTest", "corpusMc262AgricultureStillLoads").ticks(20, 40));

		private Roster() {}
	}

	private LegacySaveCorpusScenarios() {}

	/** The placer stamped on every machine entry: {@code [I;1,2,3,4]}. */
	private static final UUID OWNER = UUIDUtil.uuidFromIntArray(new int[] {1, 2, 3, 4});

	/** Keys every tracking machine writes after its own (MachineBlockEntity#saveAdditional). */
	private static final String MACHINE_TAIL = "StatsActiveTicks:5000L,StatsEnergyIn:120L,StatsEnergyOut:77L,"
			+ "StatsEnergyGenerated:9000L,StatsEnergyConsumed:31L,StatsItemsProcessed:12L,"
			+ "Owner:[I;1,2,3,4],OwnerName:\"Corpus\"";

	private static final String GENERATOR = "{AlaDataVersion:1,Energy:1234L,Progress:0,MaxProgress:0,"
			+ "Items:[{Slot:0b,id:\"minecraft:coal\",count:12},{Slot:1b,id:\"alaindustrial:stats_chip\",count:1}],"
			+ MACHINE_TAIL + ",BurnTime:37,BurnDuration:1600}";

	/** Processing machine: 0 input, 1 output, 2..5 upgrade chips, 6 battery drawer. */
	private static final String MACERATOR = "{AlaDataVersion:1,Energy:600L,Progress:7,MaxProgress:100,"
			+ "Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:3},{Slot:1b,id:\"minecraft:flint\",count:2},"
			+ "{Slot:2b,id:\"alaindustrial:overclocker_chip_i\",count:1},"
			+ "{Slot:5b,id:\"alaindustrial:mute_chip\",count:1},{Slot:6b,id:\"alaindustrial:battery\",count:1}],"
			+ MACHINE_TAIL + "}";

	private static final String REACTOR_CONTROLLER = "{AlaDataVersion:1,Energy:150000L,Progress:0,MaxProgress:0,"
			+ MACHINE_TAIL + ",BoxMinX:-3,BoxMinY:60,BoxMinZ:-2,BoxMaxX:3,BoxMaxY:66,BoxMaxZ:4,Heat:4200L,"
			+ "OverheatWarned:1b,Depth:750,BlastCountdown:0,BlastCountdownTotal:0}";

	/** Current layout: 0 charge, 1 discharge, 2..5 upgrade chips. */
	private static final String BATTERY_BOX_V1 = "{AlaDataVersion:1,Energy:5000L,Progress:0,MaxProgress:0,"
			+ "Items:[{Slot:0b,id:\"alaindustrial:battery\",count:1},{Slot:2b,id:\"alaindustrial:stats_chip\",count:1}],"
			+ MACHINE_TAIL + "}";

	/** Before MOD-083: no version key, no discharge slot, so the chips sat at 1..4. */
	private static final String BATTERY_BOX_V0 = "{Energy:4000L,"
			+ "Items:[{Slot:0b,id:\"alaindustrial:battery\",count:1},{Slot:1b,id:\"alaindustrial:mute_chip\",count:1},"
			+ "{Slot:2b,id:\"alaindustrial:stats_chip\",count:1}]}";

	private static final String CABLE = "{AlaDataVersion:1,Energy:10L,Breaker:1b,BreakerClosed:0b,Color:\"red\"}";

	/** NORTH (ordinal 2) = EXTRACT (1), EAST (ordinal 5) = INSERT (2): {@code 1<<4 | 2<<10}. */
	private static final String ITEM_PIPE = "{AlaDataVersion:1,Energy:0L,FaceModes:2064}";

	private static final String FLUID_PIPE = "{AlaDataVersion:1,Energy:0L,FaceModes:16,SteamSplit:1b,"
			+ "FluidMb:40L,FluidId:\"minecraft:water\"}";

	private static final String FLUID_TANK = "{FluidTankMb:3000L,FluidTankFluid:\"alaindustrial:oil\"}";

	private static final String IRON_CHEST = "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5},"
			+ "{Slot:35b,id:\"minecraft:emerald\",count:64}]}";

	private static final String STORAGE_MODULE = "{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:32},"
			+ "{Slot:26b,id:\"minecraft:gold_ingot\",count:7}]}";

	/** 26.2 {@code BlockState.CODEC} shape: a {@code {Name}} compound, not the 26.3 string. */
	private static final String KOK_SAGYZ_ROOT_MC262 = "{soil:{Name:\"minecraft:sand\"}}";

	/**
	 * @implements R-PER-01 — generator, processing machine and reactor controller entries of release
	 *     0.1.194 load with their energy, items (chips and battery drawer on their own indices) and
	 *     every scalar key intact.
	 * @covers R-PER-01
	 */
	public static void machinesStillLoad(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();

		CompoundTag generatorTag = parse(GENERATOR);
		GeneratorBlockEntity generator = load(fresh(GeneratorBlockEntity::new, ModContent.GENERATOR.get()),
				registries, generatorTag);
		if (!energy(helper, "generator", generator.getEnergyStorage().getAmount(), 1234L)
				|| !item(helper, "generator", generator, 0, Items.COAL, 12)
				|| !item(helper, "generator", generator, 1, ModContent.STATS_CHIP.get(), 1)
				|| !owner(helper, "generator", generator.getOwner())
				|| !noDrift(helper, "generator", generatorTag, generator, registries)) {
			return;
		}

		CompoundTag maceratorTag = parse(MACERATOR);
		MaceratorBlockEntity macerator = load(fresh(MaceratorBlockEntity::new, ModContent.MACERATOR.get()),
				registries, maceratorTag);
		if (!energy(helper, "macerator", macerator.getEnergyStorage().getAmount(), 600L)
				|| !item(helper, "macerator", macerator, 0, Items.RAW_IRON, 3)
				|| !item(helper, "macerator", macerator, 1, Items.FLINT, 2)
				|| !item(helper, "macerator", macerator, macerator.upgradeSlotStart(),
						ModContent.OVERCLOCKER_CHIP_I.get(), 1)
				|| !item(helper, "macerator", macerator, macerator.upgradeSlotStart() + 3, ModContent.MUTE_CHIP.get(), 1)
				|| !item(helper, "macerator", macerator, macerator.batterySlotIndex(), ModContent.BATTERY.get(), 1)
				|| !noDrift(helper, "macerator", maceratorTag, macerator, registries)) {
			return;
		}

		CompoundTag reactorTag = parse(REACTOR_CONTROLLER);
		ReactorControllerBlockEntity reactor = load(
				fresh(ReactorControllerBlockEntity::new, ModContent.REACTOR_CONTROLLER.get()), registries, reactorTag);
		if (!energy(helper, "reactor controller", reactor.getEnergyStorage().getAmount(), 150000L)
				|| !energy(helper, "reactor controller heat", reactor.getHeat(), 4200L)
				|| !noDrift(helper, "reactor controller", reactorTag, reactor, registries)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — a Battery Box of release 0.1.194 (v1) loads as saved, and one saved
	 *     before MOD-083 (v0, no version key) comes back with its chips moved one slot up, the
	 *     discharge slot empty.
	 * @covers R-PER-01
	 */
	public static void batteryBoxV0AndV1StillLoad(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();

		CompoundTag v1Tag = parse(BATTERY_BOX_V1);
		BatteryBoxBlockEntity v1 = load(fresh(BatteryBoxBlockEntity::new, ModContent.BATTERY_BOX.get()),
				registries, v1Tag);
		if (!energy(helper, "battery box v1", v1.getEnergyStorage().getAmount(), 5000L)
				|| !item(helper, "battery box v1", v1, BatteryBoxBlockEntity.CHARGE_SLOT, ModContent.BATTERY.get(), 1)
				|| !empty(helper, "battery box v1", v1, BatteryBoxBlockEntity.DISCHARGE_SLOT)
				|| !item(helper, "battery box v1", v1, v1.upgradeSlotStart(), ModContent.STATS_CHIP.get(), 1)
				|| !noDrift(helper, "battery box v1", v1Tag, v1, registries)) {
			return;
		}

		BatteryBoxBlockEntity v0 = load(fresh(BatteryBoxBlockEntity::new, ModContent.BATTERY_BOX.get()),
				registries, parse(BATTERY_BOX_V0));
		if (!energy(helper, "battery box v0", v0.getEnergyStorage().getAmount(), 4000L)
				|| !item(helper, "battery box v0", v0, BatteryBoxBlockEntity.CHARGE_SLOT, ModContent.BATTERY.get(), 1)
				|| !empty(helper, "battery box v0", v0, BatteryBoxBlockEntity.DISCHARGE_SLOT)
				|| !item(helper, "battery box v0", v0, v0.upgradeSlotStart(), ModContent.MUTE_CHIP.get(), 1)
				|| !item(helper, "battery box v0", v0, v0.upgradeSlotStart() + 1, ModContent.STATS_CHIP.get(), 1)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — cable, item pipe and fluid pipe entries of release 0.1.194 load with
	 *     their buffer, breaker, colour, face modes and fluid intact.
	 * @covers R-PER-01
	 */
	public static void transportStillLoads(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();

		CompoundTag cableTag = parse(CABLE);
		CableBlockEntity cable = load(fresh(CableBlockEntity::new, ModContent.COPPER_CABLE.get()), registries, cableTag);
		if (!energy(helper, "cable", cable.getEnergyStorage().getAmount(), 10L)
				|| !check(helper, cable.hasBreaker() && !cable.isBreakerClosed(),
						"cable: the open breaker did not come back")
				|| !check(helper, cable.color() == DyeColor.RED, "cable: colour red came back as " + cable.color())
				|| !noDrift(helper, "cable", cableTag, cable, registries)) {
			return;
		}

		CompoundTag itemPipeTag = parse(ITEM_PIPE);
		ItemPipeBlockEntity itemPipe = load(fresh(ItemPipeBlockEntity::new, ModContent.ITEM_PIPE.get()),
				registries, itemPipeTag);
		if (!check(helper, itemPipe.faceMode(Direction.NORTH) == PipeFaceMode.EXTRACT
						&& itemPipe.faceMode(Direction.EAST) == PipeFaceMode.INSERT
						&& itemPipe.faceMode(Direction.UP) == PipeFaceMode.NEUTRAL,
				"item pipe: face modes did not come back: north=" + itemPipe.faceMode(Direction.NORTH)
						+ " east=" + itemPipe.faceMode(Direction.EAST))
				|| !noDrift(helper, "item pipe", itemPipeTag, itemPipe, registries)) {
			return;
		}

		CompoundTag fluidPipeTag = parse(FLUID_PIPE);
		FluidPipeBlockEntity fluidPipe = load(fresh(FluidPipeBlockEntity::new, ModContent.FLUID_PIPE.get()),
				registries, fluidPipeTag);
		if (!check(helper, fluidPipe.fluidBuffer.fluid.is(Fluids.WATER) && fluidPipe.fluidBuffer.amount == 40L,
				"fluid pipe: 40 mB of water came back as " + fluidPipe.fluidBuffer.amount + " of "
						+ fluidPipe.fluidBuffer.fluid)
				|| !check(helper, fluidPipe.faceMode(Direction.NORTH) == PipeFaceMode.EXTRACT,
						"fluid pipe: north face mode came back as " + fluidPipe.faceMode(Direction.NORTH))
				|| !noDrift(helper, "fluid pipe", fluidPipeTag, fluidPipe, registries)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — portable tank, iron chest and storage module entries of release 0.1.194
	 *     load with their fluid and with items on the first and the last index.
	 * @covers R-PER-01
	 */
	public static void storageStillLoads(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();

		CompoundTag tankTag = parse(FLUID_TANK);
		FluidTankBlockEntity tank = load(fresh(FluidTankBlockEntity::new, ModContent.FLUID_TANK.get()),
				registries, tankTag);
		if (!check(helper, tank.fluidTank.fluid.is(ModContent.OIL.get()) && tank.fluidTank.amount == 3000L,
				"fluid tank: 3000 mB of oil came back as " + tank.fluidTank.amount + " of " + tank.fluidTank.fluid)
				|| !noDrift(helper, "fluid tank", tankTag, tank, registries)) {
			return;
		}

		IronChestBlockEntity chest = load(fresh(IronChestBlockEntity::new, ModContent.IRON_CHEST.get()),
				registries, parse(IRON_CHEST));
		if (!item(helper, "iron chest", chest, 0, Items.DIAMOND, 5)
				|| !item(helper, "iron chest", chest, 35, Items.EMERALD, 64)) {
			return;
		}

		StorageModuleBlockEntity module = load(fresh(StorageModuleBlockEntity::new, ModContent.STORAGE_MODULE.get()),
				registries, parse(STORAGE_MODULE));
		if (!item(helper, "storage module", module, 0, Items.IRON_INGOT, 32)
				|| !item(helper, "storage module", module, 26, Items.GOLD_INGOT, 7)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — a kok-sagyz root saved by the 26.2 line of release 0.1.194 (soil as a
	 *     {@code {Name}} compound) opens with its sand soil on this line.
	 * @covers R-PER-01
	 */
	public static void mc262AgricultureStillLoads(GameTestHelper helper) {
		KokSagyzRootBlockEntity root = load(fresh(KokSagyzRootBlockEntity::new, ModContent.KOK_SAGYZ_ROOT.get()),
				helper.getLevel().registryAccess(), parse(KOK_SAGYZ_ROOT_MC262));
		if (!check(helper, root.soil().is(Blocks.SAND), "kok-sagyz root: 26.2 sand soil came back as " + root.soil())) {
			return;
		}
		helper.succeed();
	}

	// -- checks: each fails the test with a message naming the entry, and returns false ------------

	private static boolean check(GameTestHelper helper, boolean ok, String message) {
		if (!ok) {
			helper.fail(message);
		}
		return ok;
	}

	private static boolean energy(GameTestHelper helper, String entry, long actual, long expected) {
		return check(helper, actual == expected, entry + ": " + expected + " came back as " + actual);
	}

	private static boolean owner(GameTestHelper helper, String entry, UUID actual) {
		return check(helper, OWNER.equals(actual), entry + ": owner " + OWNER + " came back as " + actual);
	}

	private static boolean item(GameTestHelper helper, String entry, Container container, int slot, Item item,
			int count) {
		ItemStack stack = slot >= 0 && slot < container.getContainerSize() ? container.getItem(slot) : ItemStack.EMPTY;
		return check(helper, stack.is(item) && stack.getCount() == count,
				entry + ": slot " + slot + " should hold " + count + " x " + item + ", holds " + stack);
	}

	private static boolean empty(GameTestHelper helper, String entry, Container container, int slot) {
		return check(helper, container.getItem(slot).isEmpty(),
				entry + ": slot " + slot + " should be empty, holds " + container.getItem(slot));
	}

	private static boolean noDrift(GameTestHelper helper, String entry, CompoundTag corpus, BlockEntity loaded,
			RegistryAccess registries) {
		List<String> drift = scalarDrift(corpus, loaded.saveCustomOnly(registries));
		return check(helper, drift.isEmpty(), entry + ": keys did not come back as saved by 0.1.194: " + drift);
	}
}

package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Zone <b>machines</b> (rows z=20 and z=24): processing machines with full buffers and guaranteed
 * inputs, so they are visibly working (lit + progress) the moment the stand is built.
 *
 * <p>Machines stand on a pitch of six cells (x=4, 10, 16, …) — a free aisle of five between any two,
 * enough to walk round each, break it, and rebuild it without touching a neighbour (MOD-659). Blocks
 * that only make sense as a story stay a cell apart instead: the recycler's slag and ceramic, and
 * the workstation next to the upgrade table. Rows are the first machines row (z=20) and a second
 * row (z=24) behind it; the misc zone shares z=20 to the east of the galvanic bath.
 *
 * <p>Domain (coding standard, section 1): Processing.
 */
final class MachinesZone implements DemoZone {
	private static final LongUnaryOperator FULL = StandWriter.FULL;

	/** Camera of {@code /ala demo tp machines}. */
	static final DemoStand.TpPoint MACHINES_CAMERA =
			new DemoStand.TpPoint("machines", 16.0, 6.0, 14.0, 0.0f, 30.0f, false);

	/** Camera of {@code /ala demo tp machines2}. */
	static final DemoStand.TpPoint MACHINES2_CAMERA =
			new DemoStand.TpPoint("machines2", 44.0, 6.0, 14.0, 0.0f, 30.0f, false);

	@Override
	public void build(StandWriter w) {
		basicMachines(w);
		incubatorAndHeaterPairs(w);
		fluidChain(w);
		assembledStations(w);
		secondRowRest(w);
	}

	/** The first machines row (macerator to sawmill), plus the canning machine and repair bench with hand-set slots. */
	private static void basicMachines(StandWriter w) {
		w.placeWorkingMachine(4, 20, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		w.placeWorkingMachine(10, 20, ModContent.ELECTRIC_FURNACE.get(), new ItemStack(Items.RAW_COPPER, 64));
		w.placeWorkingMachine(16, 20, ModContent.COMPRESSOR.get(),
				new ItemStack(ModContent.IRON_DUST.get(), 64));
		w.placeWorkingMachine(22, 20, ModContent.EXTRACTOR.get(), new ItemStack(Items.GRAVEL, 64));
		// Canning Machine (MOD-383): placeWorkingMachine does not fit — it fills slot 0 only, and this
		// machine needs both a food stack and a stack of empty cans before it will run at all.
		w.set(22, 1, 24, ModContent.CANNING_MACHINE.get());
		w.chargeBuffer(22, 1, 24);
		w.fillSlot(22, 1, 24, CanningMachineBlockEntity.FOOD_SLOT,
				new ItemStack(Items.COOKED_BEEF, 64));
		w.fillSlot(22, 1, 24, CanningMachineBlockEntity.CAN_SLOT,
				new ItemStack(ModContent.EMPTY_CAN.get(), 64));
		// Component Repair Bench (MOD-384): placeWorkingMachine does not fit either — its target slot
		// needs a component that is actually WORN, and a pristine rotor would leave the bench idle on the
		// stand. So the rotor is damaged by hand first, then paired with its T1 material (an iron plate).
		w.set(28, 1, 24, ModContent.COMPONENT_REPAIR_BENCH.get());
		w.chargeBuffer(28, 1, 24);
		ItemStack wornRotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
		wornRotor.setDamageValue(wornRotor.getMaxDamage() / 2);
		w.fillSlot(28, 1, 24, ComponentRepairBenchBlockEntity.TARGET_SLOT, wornRotor);
		w.fillSlot(28, 1, 24, ComponentRepairBenchBlockEntity.MATERIAL_SLOT,
				new ItemStack(ModContent.IRON_PLATE.get(), 64));
		// Sawmill (MOD-150): pre-charged + a stack of logs → visibly sawing (default PLANKS mode).
		w.placeWorkingMachine(34, 20, ModContent.SAWMILL.get(), new ItemStack(Items.OAK_LOG, 64));
	}

	/** The incubator, the polymerizer, and the two heater-underneath pairs (vulcanizer, thermal centrifuge). */
	private static void incubatorAndHeaterPairs(StandWriter w) {
		// Incubator (MOD-118): the 1x2 multiblock. Glass goes on top so the base assembles it into the
		// dome; the slots are filled by hand rather than via placeWorkingMachine because the chip picks
		// the mode and the uranium is a separate fuel slot.
		w.set(40, 1, 20, ModContent.INCUBATOR.get());
		w.set(40, 2, 20, ModContent.INCUBATOR_DOME.get());
		w.chargeBuffer(40, 1, 20);
		w.fillSlot(40, 1, 20, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()));
		w.fillSlot(40, 1, 20, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		w.fillSlot(40, 1, 20, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.DIAMOND, 64));
		// Polymerizer (MOD-019): the fluid-fed machine. placeWorkingMachine does not fit it — its slot 0
		// takes a CONTAINER, not the feedstock, and one bucket would give the stand a single run before
		// the machine went idle. The tank is stocked directly instead, so it runs for ten operations.
		w.set(46, 1, 20, ModContent.POLYMERIZER.get());
		w.chargeBuffer(46, 1, 20);
		w.fillTank(46, 1, 20, PolymerizerBlockEntity.class, be -> be.fluidTank, ModContent.OIL.get(), FULL);
		// Vulcanizer (MOD-258): the electric heater occupies the block directly below the machine.
		// Both buffers are charged and both positional inputs are stocked, so the stand demonstrates a
		// running pair rather than an idle shell. The heater is placed COLD on purpose (MOD-418): the
		// pair opens at x2 with the thermometer climbing and settles at x3 once the first batch has paid
		// for the warm-up, which is the mechanic worth showing — a stand pre-heated behind the player's
		// back would show the destination and hide the ramp.
		w.set(52, 1, 20, ModContent.ELECTRIC_HEATER.get());
		w.chargeBuffer(52, 1, 20);
		w.set(52, 2, 20, ModContent.VULCANIZER.get());
		w.chargeBuffer(52, 2, 20);
		w.fillSlot(52, 2, 20, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		w.fillSlot(52, 2, 20, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		// Thermal Centrifuge (MOD-424): the same heater-underneath pair as the vulcanizer, on the second
		// machines row. Three things must be true before this machine
		// turns at all, so all three are set up rather than only the two the other stands need: the heater
		// below, a stack of uranium dust, and — the one no other machine on the stand wants — a held
		// redstone signal. A redstone BLOCK rather than a lever: the lever's default state is unpowered and
		// wall-mounted, so `set` (which places defaultBlockState and never calls setPlacedBy) would leave a
		// dead switch and an idle centrifuge. It is placed LAST of the three so its neighbour update reaches
		// an already-built machine. Like the vulcanizer's, the heater starts cold on purpose: the stand shows
		// the rotor spinning up while the thermometer climbs, which is the mechanic worth watching.
		w.set(34, 1, 24, ModContent.ELECTRIC_HEATER.get());
		w.chargeBuffer(34, 1, 24);
		w.set(34, 2, 24, ModContent.THERMAL_CENTRIFUGE.get());
		w.chargeBuffer(34, 2, 24);
		w.fillSlot(34, 2, 24, ThermalCentrifugeBlockEntity.INPUT_SLOT,
				new ItemStack(ModContent.URANIUM_DUST.get(), 64));
		w.set(35, 2, 24, Blocks.REDSTONE_BLOCK);
	}

	/** The galvanic bath, the fermenter and the sprinkler: the machines whose feedstock is a stocked tank. */
	private static void fluidChain(StandWriter w) {
		// Galvanic Bath (MOD-127): like the polymerizer its feedstock is a fluid, so the tank is stocked
		// directly rather than through a bucket — one bucket would buy four operations and then the stand
		// would show an idle machine. Both item inputs are filled so it plates continuously.
		w.set(58, 1, 20, ModContent.GALVANIC_BATH.get());
		w.chargeBuffer(58, 1, 20);
		w.fillSlot(58, 1, 20, GalvanicBathBlockEntity.FIBER_SLOT,
				new ItemStack(Items.STRING, 64));
		w.fillSlot(58, 1, 20, GalvanicBathBlockEntity.SILVER_SLOT,
				new ItemStack(ModContent.SILVER_DUST.get(), 64));
		w.fillTank(58, 1, 20, GalvanicBathBlockEntity.class, be -> be.fluidTank, Fluids.WATER, FULL);
		// Fermenter (MOD-146): the head of the organic chain. Stocked like the bath — the tank filled
		// directly, the input slot loaded — so the stand shows it brewing rather than waiting.
		// A second `set` on one cell silently drops the first machine's inventory (ADR-028).
		w.set(40, 1, 24, ModContent.FERMENTER.get());
		w.chargeBuffer(40, 1, 24);
		w.fillSlot(40, 1, 24, FermenterBlockEntity.ORGANIC_SLOT,
				new ItemStack(Items.POISONOUS_POTATO, 64));
		w.fillTank(40, 1, 24, FermenterBlockEntity.class, be -> be.waterTank, Fluids.WATER, FULL);
		// Sprinkler (MOD-525): the tail of the same chain, and the one block here that takes no cable —
		// so it is shown charged with solution instead, beside the farm plot rather than in the machine
		// row. Its head only turns when the tank can pay, which is the whole readout it has.
		w.set(78, 1, 24, ModContent.SPRINKLER.get());
		w.fillTank(78, 1, 24, SprinklerBlockEntity.class, be -> be.tank, ModContent.NUTRIENT_SOLUTION.get(), FULL);
	}

	/** The workstation and the upgrade table, both 1x2 multiblocks shown assembled and powered. */
	private static void assembledStations(StandWriter w) {
		ServerLevel level = w.level();
		BlockPos origin = w.origin();
		// Workstation (MOD-483): the 1x2 multiblock, shown assembled and powered so the stand carries a
		// lit one rather than two loose casings. Both cells are written by hand and the assembly hook is
		// then called explicitly — a programmatic setBlock never runs setPlacedBy, the same reason the
		// airlock's halves are placed cell by cell.
		//
		// A cell written twice loses the first block in silence and leaves a loose casing floating over
		// whatever won (MOD-597) — the ledger in #lastBuildProblems is what says this out loud.
		BlockState workstationCasing = ModContent.WORKSTATION.get().defaultBlockState();
		w.place(origin.offset(54, 1, 24), workstationCasing);
		w.place(origin.offset(54, 2, 24), workstationCasing);
		WorkstationBlock.tryAssemble(level, origin.offset(54, 2, 24));
		w.configure(54, 1, 24, WorkstationBlockEntity.class, "workstation to charge", station -> {
			station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
			station.setChangedQuietly();
		});
		// Upgrade Table (MOD-482): the same 1x2 pattern, shown assembled and powered directly beside
		// the workstation it is built like. Both are 1x2 and assemble VERTICALLY, so standing them
		// shoulder to shoulder (one free cell between) costs nothing — neither scan looks sideways.
		BlockState upgradeTableCasing = ModContent.UPGRADE_TABLE.get().defaultBlockState();
		w.place(origin.offset(56, 1, 24), upgradeTableCasing);
		w.place(origin.offset(56, 2, 24), upgradeTableCasing);
		UpgradeTableBlock.tryAssemble(level, origin.offset(56, 2, 24));
		w.configure(56, 1, 24, UpgradeTableBlockEntity.class, "upgrade table to stock", table -> {
			table.getEnergyStorage().setAmountUntracked(table.getEnergyStorage().getCapacity());
			// A charged drill and the column module it takes: the table shows an upgrade in progress
			// rather than the "no tool" status of an empty bench.
			table.setItem(UpgradeTableBlockEntity.TOOL_SLOT, StandWriter.charged(ModContent.ELECTRIC_DRILL.get()));
			table.setItem(UpgradeTableBlockEntity.MODULE_SLOT, new ItemStack(ModContent.DRILL_COLUMN_MODULE.get()));
			table.setChangedQuietly();
		});
	}

	/** The assembler, recycler with its slag and ceramic, iron furnace, alloy smelter and the distillation tower. */
	private static void secondRowRest(StandWriter w) {
		ServerLevel level = w.level();
		BlockPos origin = w.origin();
		// Assembler (MOD-275): the first MV machine, first of the second machines row, behind the
		// macerator. Charged but idle by design — this slice registers the block and its inventory; the
		// crafting cycle (and with it a blueprint to stock it with) lands in a later slice.
		w.set(4, 1, 24, ModContent.ASSEMBLER.get());
		w.chargeBuffer(4, 1, 24);
		// Recycler (MOD-145): stocked so the stand shows the thing that makes it different — a batch in
		// progress. It gets blades (without them the machine is inert), cobblestone to chew, and a slag
		// block set beside it so the building use of the output is visible in the same glance.
		w.set(46, 1, 24, ModContent.RECYCLER.get());
		w.chargeBuffer(46, 1, 24);
		w.fillSlot(46, 1, 24, RecyclerBlockEntity.BLADE_SLOT,
				new ItemStack(ModContent.RECYCLER_BLADES_TEMPERED.get()));
		w.fillSlot(46, 1, 24, RecyclerBlockEntity.INPUT_SLOT,
				new ItemStack(Items.COBBLESTONE, 64));
		w.set(48, 1, 24, ModContent.SLAG_BLOCK.get());
		// MOD-590 — carbon ceramic beside the slag block it is fired with: the two blocks the Recycler
		// feeds, standing next to each other. Machine, slag, ceramic is that story, a cell apart each.
		w.set(50, 1, 24, ModContent.CARBON_CERAMIC.get());
		// Iron furnace (MOD-115): fuel-burning, not EU — so it is loaded with input + coal instead of a
		// pre-charged buffer, and lights itself on the first tick like a vanilla furnace.
		w.set(28, 1, 20, ModContent.IRON_FURNACE.get());
		w.fillSlot(28, 1, 20, 0, new ItemStack(Items.RAW_IRON, 64));
		w.fillSlot(28, 1, 20, 1, new ItemStack(Items.COAL, 64));
		// Alloy smelter (MOD-064): the second machines row, next to the assembler. Stocked for bronze —
		// and deliberately with the tin in the LAST input rather than the second, so the stand shows the
		// thing that makes this machine different: the components may sit in any slot in any order.
		// The third slot is left empty on purpose; filling it would block the two-component recipe.
		w.set(10, 1, 24, ModContent.ALLOY_SMELTER.get());
		w.chargeBuffer(10, 1, 24);
		w.fillSlot(10, 1, 24, AlloySmelterBlockEntity.INPUT_SLOT_0,
				new ItemStack(Items.COPPER_INGOT, 64));
		w.fillSlot(10, 1, 24, AlloySmelterBlockEntity.INPUT_SLOT_2,
				new ItemStack(ModContent.TIN_INGOT.get(), 64));
		// Distillation Column (MOD-251): the 1×1×3 tower on the second machines row. The three
		// segments are placed explicitly — DemoStand.set uses setBlockAndUpdate, which never calls
		// setPlacedBy, so relying on the base's own placement hook would leave an orphan bottom
		// segment (the MOD-015 gametest lesson). HEIGHT=9 leaves ample headroom on this row.
		dev.alaindustrial.block.DistillationColumnBlock.placeTower(level,
				origin.offset(16, 1, 24));
		w.chargeBuffer(16, 1, 24);
		// Round 2: the Rectification Section on top — the stand shows the full 4-storey refinery.
		w.set(16, 4, 24, ModContent.RECTIFICATION_SECTION.get());
	}
}

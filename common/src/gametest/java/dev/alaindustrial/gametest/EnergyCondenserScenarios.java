package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * L2 suite for the Energy Condenser (MOD-393).
 *
 * <p>The point of the block is what it must NOT do — outcompete machines, drain stores, hand out a
 * tier the bank has not reached — so most of these are negative controls. A test that only proved
 * "banking energy yields a clot" would stay green through every way this block can go wrong.
 */
public final class EnergyCondenserScenarios {

	private EnergyCondenserScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static EnergyCondenserBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModContent.ENERGY_CONDENSER.get(),
				EnergyCondenserBlockEntity.class);
	}

	/**
	 * The output slot shows the tier the bank is worth — and nothing at all below the first threshold.
	 * Includes the "one short" case, because an off-by-one on the boundary is exactly how a player
	 * would get a free tier.
	 */
	public static void condenser_tierFollowsTheBank(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);

		be.getEnergyStorage().amount = Config.clotThresholdI - 1;
		AlaGameTestHelper.drive(be, helper, 1);
		if (!be.getItem(EnergyCondenserBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("one EU short of tier I must still yield nothing");
		}

		expectTier(helper, be, Config.clotThresholdI, ModContent.ENERGY_CLOT_I.get(), "I");
		expectTier(helper, be, Config.clotThresholdII, ModContent.ENERGY_CLOT_II.get(), "II");
		expectTier(helper, be, Config.clotThresholdIII, ModContent.ENERGY_CLOT_III.get(), "III");
		helper.succeed();
	}

	private static void expectTier(GameTestHelper helper, EnergyCondenserBlockEntity be, long banked,
			net.minecraft.world.item.Item expected, String label) {
		be.getEnergyStorage().amount = banked;
		AlaGameTestHelper.drive(be, helper, 1);
		ItemStack shown = be.getItem(EnergyCondenserBlockEntity.OUTPUT_SLOT);
		if (!shown.is(expected)) {
			helper.fail("banked " + banked + " should show tier " + label + ", got " + shown);
		}
	}

	/**
	 * Taking the clot spends the WHOLE bank, not the tier's price. This is the decision the whole
	 * mechanic rests on: pulling out early has to hurt, or waiting means nothing.
	 */
	public static void condenser_takingSpendsTheWholeBank(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);
		// Deliberately just short of tier II: the player takes tier I and burns the surplus with it.
		long banked = Config.clotThresholdII - 1;
		be.getEnergyStorage().amount = banked;
		AlaGameTestHelper.drive(be, helper, 1);

		ItemStack taken = be.removeItem(EnergyCondenserBlockEntity.OUTPUT_SLOT, 1);
		if (!taken.is(ModContent.ENERGY_CLOT_I.get())) {
			helper.fail("just under tier II must hand out tier I, got " + taken);
		}
		if (be.getEnergyStorage().amount != 0) {
			helper.fail("taking the clot must empty the bank, " + be.getEnergyStorage().amount + " left");
		}
		helper.succeed();
	}

	/**
	 * Faces: power enters only through top and bottom, items leave only through the sides. The block
	 * and the block entity must agree — a cable draws its arm from the BLOCK's answer, so a mismatch
	 * shows the player a connected cable that carries nothing.
	 */
	public static void condenser_facesAreSplitBetweenPowerAndItems(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);
		for (Direction dir : Direction.values()) {
			boolean vertical = dir.getAxis() == Direction.Axis.Y;
			boolean takesPower = be.energyPort(dir) != null;
			if (takesPower != vertical) {
				helper.fail("face " + dir + ": power=" + takesPower + ", expected " + vertical);
			}
			boolean movesItems = be.getSlotsForFace(dir).length > 0;
			if (movesItems == vertical) {
				helper.fail("face " + dir + ": items=" + movesItems + ", expected " + !vertical);
			}
		}
		helper.succeed();
	}

	/**
	 * The condenser must never look like a machine to the grid: it is a storage sink (served last), it
	 * refuses the storage cascade and the storage-feed channel, and it cannot give energy back. Those
	 * four together are what stop batteries from discharging on its behalf — checking them by name is
	 * the only way to catch a future refactor that flips one of them.
	 */
	public static void condenser_cannotCompeteWithMachines(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);
		if (!be.isEnergyStorageSink()) {
			helper.fail("condenser must be a storage sink, or machines lose energy to it");
		}
		if (be.acceptsCascade()) {
			helper.fail("condenser must refuse the storage cascade, or it drains battery boxes");
		}
		if (be.storageFeedRate() != 0) {
			helper.fail("condenser must not open the storage-feed channel");
		}
		if (be.getEnergyStorage().maxExtract != 0) {
			helper.fail("condenser must not be able to give energy back");
		}
		// A control on the other side: an ordinary machine is NOT a storage sink. Without this the four
		// assertions above would also pass on a build where every block became a sink.
		MachineBlockEntity macerator =
				AlaGameTestHelper.place(helper, POS.offset(2, 0, 0), ModContent.MACERATOR.get());
		if (macerator.isEnergyStorageSink()) {
			helper.fail("control failed: a macerator must not be a storage sink");
		}
		helper.succeed();
	}

	/**
	 * Regression: every slot the menu binds to the machine must point INSIDE that machine's container —
	 * including after the screen asks for the panel to be repositioned.
	 *
	 * <p>This crashed a live client. The panel-reposition path (the screen calls it on open, to restore
	 * a dragged panel) rebuilt four upgrade slots at menu indices 1..4 and spliced them over the
	 * player-inventory slots sitting there, each bound to a machine container holding exactly one slot.
	 * The very first content packet then wrote past its end and took the client down. Nothing in the
	 * build caught it: it compiles, it passes every other test, and it only fires when a human opens
	 * the screen.
	 */
	public static void condenser_menuSlotsStayInsideTheContainer(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		dev.alaindustrial.menu.EnergyCondenserMenu menu = new dev.alaindustrial.menu.EnergyCondenserMenu(
				1, player.getInventory(), be, be.getDataAccess(),
				net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), be.getBlockPos()));

		// What the screen does on open when a previous panel drag was persisted.
		menu.repositionUpgradeSlots(7, 5);

		int size = be.getContainerSize();
		for (net.minecraft.world.inventory.Slot slot : menu.slots) {
			if (slot.container == be && slot.getContainerSlot() >= size) {
				helper.fail("menu slot points at container index " + slot.getContainerSlot()
						+ " but the condenser holds only " + size + " — the client would crash on the"
						+ " first content packet");
			}
			if (slot instanceof dev.alaindustrial.menu.MachineMenu.UpgradeSlot) {
				helper.fail("the condenser must not carry upgrade slots in its menu");
			}
		}
		helper.succeed();
	}

	/**
	 * Regression: the condenser banks what the GRID hands it, not a private cap of its own.
	 *
	 * <p>It shipped with a 12 EU/t intake, on the belief that a narrow throat was what kept the block
	 * from outrunning machines. It never was — {@code EnergyNetwork#tick} serves consumers in two
	 * separate passes and machines drink from the line first — so the number guarded nothing and
	 * throttled the mechanic to nothing instead: a player's farm of 85 solar panels making 1580 EU/t
	 * fed the condenser the same 12 EU/t that two panels would. Building generation, the single thing
	 * this block exists to reward, changed the result by zero.
	 *
	 * <p>The rig is that farm in miniature and in the same shape: four generators ring one cable, the
	 * condenser sits on top of it, and power can only enter through the Y face. The assertion is
	 * against the SUPPLY the generators offer, never against {@code condenserInputRate} — measuring the
	 * block against its own config value is a tautology that would stay green through any future
	 * re-throttling, which is exactly the bug being guarded here.
	 */
	public static void condenser_banksWhatTheGridOffers(GameTestHelper helper) {
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos[] generatorPositions = {
				cable.offset(-1, 0, 0), cable.offset(1, 0, 0),
				cable.offset(0, 0, -1), cable.offset(0, 0, 1),
		};
		for (BlockPos pos : generatorPositions) {
			helper.setBlock(pos, ModContent.GENERATOR.get());
			if (EnergyScenarioSupport.be(helper, pos)
					instanceof dev.alaindustrial.block.entity.GeneratorBlockEntity gen) {
				gen.setItem(dev.alaindustrial.block.entity.GeneratorBlockEntity.FUEL_SLOT,
						new ItemStack(net.minecraft.world.item.Items.COAL, 64));
			} else {
				helper.fail("no generator block entity at " + pos);
			}
		}
		// Gold, not copper: a 12 EU/t copper segment would cap the line at the very number under test
		// and the rig would "pass" for the wrong reason.
		helper.setBlock(cable, ModContent.GOLD_CABLE.get());
		EnergyCondenserBlockEntity condenser = AlaGameTestHelper.place(helper, cable.above(),
				ModContent.ENERGY_CONDENSER.get(), EnergyCondenserBlockEntity.class);

		int ticks = 200;
		long supplyPerTick = (long) generatorPositions.length * Config.fuelEuPerTick;
		long peakCableBuffer = 0;
		for (int i = 0; i < ticks; i++) {
			// Same order the server uses: sources, then the wire, then the network, then the sink.
			for (BlockPos pos : generatorPositions) {
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, pos));
			}
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));
			dev.alaindustrial.core.energy.NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, condenser);
			if (EnergyScenarioSupport.be(helper, cable)
					instanceof dev.alaindustrial.block.entity.CableBlockEntity wire) {
				peakCableBuffer = Math.max(peakCableBuffer, wire.getEnergyStorage().getAmount());
			}
		}

		long banked = condenser.getEnergyStorage().getAmount();
		// The number the block used to be pinned at, whatever it was fed. Losses and the first ticks of
		// line warm-up keep the real figure under the full 32 EU/t, so the bar sits at the old ceiling
		// rather than at the theoretical maximum.
		long oldCeiling = 12L * ticks;
		if (banked <= oldCeiling) {
			helper.fail("condenser banked " + banked + " EU over " + ticks + " ticks from "
					+ supplyPerTick + " EU/t of generation — at or below the old 12 EU/t cap ("
					+ oldCeiling + "), so its intake is still deaf to how much the grid offers");
		}
		if (banked > supplyPerTick * ticks) {
			helper.fail("condenser banked " + banked + " EU but the generators could only make "
					+ (supplyPerTick * ticks) + " — energy is being created somewhere");
		}
		if (peakCableBuffer <= 0) {
			helper.fail("the cable never held any EU: the condenser was charged bypassing the wire");
		}
		helper.succeed();
	}

	/**
	 * A hungry machine gets everything the generator makes; the condenser banks nothing.
	 *
	 * <p>The predicate test above proves the four flags are set. This proves what the flags are FOR, and
	 * the two are not the same claim: the flags could all be right while the grid served the classes in
	 * the wrong order, and a player would find that as "my macerator stopped when I added a condenser".
	 *
	 * <p>The rig is deliberately short of power — one coal generator (8 EU/t) against a macerator that
	 * will take 32 — so any EU reaching the condenser had to be taken off a machine that wanted it.
	 *
	 * <p>Second phase is the control. "Condenser banked 0" also passes on a rig where the condenser was
	 * never connected at all, which is the exact way this test would rot into decoration. So the
	 * macerator is then removed and the SAME line is driven again: the condenser must start banking.
	 */
	public static void condenser_doesNotStarveAMachine(GameTestHelper helper) {
		BlockPos gen = new BlockPos(1, 2, 1);
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos machinePos = new BlockPos(3, 2, 1);

		helper.setBlock(gen, ModContent.GENERATOR.get());
		if (EnergyScenarioSupport.be(helper, gen)
				instanceof dev.alaindustrial.block.entity.GeneratorBlockEntity generator) {
			generator.setItem(dev.alaindustrial.block.entity.GeneratorBlockEntity.FUEL_SLOT,
					new ItemStack(net.minecraft.world.item.Items.COAL, 64));
		} else {
			helper.fail("no generator block entity at " + gen);
		}
		helper.setBlock(cable, ModContent.GOLD_CABLE.get());
		MachineBlockEntity macerator = AlaGameTestHelper.place(helper, machinePos, ModContent.MACERATOR.get());
		macerator.setItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 64));
		EnergyCondenserBlockEntity condenser = AlaGameTestHelper.place(helper, cable.above(),
				ModContent.ENERGY_CONDENSER.get(), EnergyCondenserBlockEntity.class);

		int ticks = 60;
		drive(helper, gen, cable, machinePos, condenser, ticks);

		if (condenser.getEnergyStorage().getAmount() > 0) {
			helper.fail("condenser banked " + condenser.getEnergyStorage().getAmount()
					+ " EU while a machine on the same line was still hungry — machines must be served first");
		}
		if (macerator.getEnergyStorage().getAmount() <= 0) {
			helper.fail("the macerator received nothing at all — the rig is not delivering power, so the "
					+ "assertion above proves nothing");
		}

		// Control: take the competitor away and the very same line must reach the condenser.
		helper.setBlock(machinePos, net.minecraft.world.level.block.Blocks.AIR);
		drive(helper, gen, cable, machinePos, condenser, ticks);
		if (condenser.getEnergyStorage().getAmount() <= 0) {
			helper.fail("with no machine left the condenser still banked nothing — it was never wired to "
					+ "this line, so the first phase measured nothing");
		}
		helper.succeed();
	}

	/**
	 * A charged Battery Box next to the condenser stays charged.
	 *
	 * <p>A store discharges for a machine's deficit, never to fill another bank — otherwise a condenser
	 * would quietly drain the base's batteries overnight, which is the worst possible failure for a block
	 * whose whole promise is "surplus only". No generator here at all: every EU the condenser could get
	 * would have to come out of the box.
	 *
	 * <p>Control: a machine is then added to the same line, and the box must discharge for it. Without
	 * that half, "the box did not discharge" would also pass on a box wired to nothing.
	 */
	public static void condenser_doesNotDrainAChargedStore(GameTestHelper helper) {
		BlockPos store = new BlockPos(1, 2, 1);
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos machinePos = new BlockPos(3, 2, 1);

		// FACING is the box's INTAKE face and the OUT face is opposite it, so the box must look AWAY
		// from the cable to be able to discharge into it. Pointed at the cable it can only receive —
		// which is how the control below first failed, on a rig that could not have discharged at all.
		helper.setBlock(store, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		MachineBlockEntity box = (MachineBlockEntity) EnergyScenarioSupport.be(helper, store);
		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity();
		long charged = box.getEnergyStorage().getAmount();

		helper.setBlock(cable, ModContent.GOLD_CABLE.get());
		EnergyCondenserBlockEntity condenser = AlaGameTestHelper.place(helper, cable.above(),
				ModContent.ENERGY_CONDENSER.get(), EnergyCondenserBlockEntity.class);

		int ticks = 60;
		drive(helper, store, cable, null, condenser, ticks);

		if (condenser.getEnergyStorage().getAmount() > 0) {
			helper.fail("the condenser banked " + condenser.getEnergyStorage().getAmount()
					+ " EU with no generator present — it drained a store, which it must never do");
		}
		if (box.getEnergyStorage().getAmount() != charged) {
			helper.fail("the battery box lost " + (charged - box.getEnergyStorage().getAmount())
					+ " EU to a condenser");
		}

		// Control: the box must be ABLE to discharge onto this line, or the two checks above are vacuous.
		MachineBlockEntity macerator = AlaGameTestHelper.place(helper, machinePos, ModContent.MACERATOR.get());
		macerator.setItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 64));
		drive(helper, store, cable, machinePos, condenser, ticks);
		if (box.getEnergyStorage().getAmount() >= charged) {
			helper.fail("control failed: the box did not discharge even for a hungry machine, so it was "
					+ "never connected and the assertions above measured nothing");
		}
		helper.succeed();
	}

	/**
	 * A store bolted straight onto the condenser, with no cable, cannot reach it either.
	 *
	 * <p>This was written down as an open hole — "a battery box placed flush bypasses all three
	 * predicates" — and it turns out the geometry closes it: the condenser only takes power through its
	 * Y faces, while a Battery Box's OUT face is always horizontal (its FACING property is horizontal,
	 * and OUT is the opposite of FACING). The two can never meet. Asserted from the role methods
	 * themselves rather than restated as a sentence, so the day someone gives the condenser a side
	 * intake — or the box a vertical output — this fails instead of the doc quietly going stale.
	 *
	 * <p>The control matters as much as the sweep: the box must actually HAVE an OUT face somewhere, or
	 * "no OUT face ever meets an IN face" would be true of a box with no output at all.
	 */
	public static void condenser_cannotBeReachedByAFlushStore(GameTestHelper helper) {
		EnergyCondenserBlockEntity condenser = place(helper);
		BlockPos boxAt = POS.offset(2, 0, 0);
		boolean sawAnOutFace = false;
		boolean sawAnIntakeFace = false;

		for (Direction facing : Direction.Plane.HORIZONTAL) {
			helper.setBlock(boxAt, ModContent.BATTERY_BOX.get().defaultBlockState()
					.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, facing));
			MachineBlockEntity box = (MachineBlockEntity) EnergyScenarioSupport.be(helper, boxAt);
			for (Direction contact : Direction.values()) {
				boolean condenserTakesHere =
						condenser.energyRoleForFace(contact) == dev.alaindustrial.core.energy.EnergyRole.IN;
				boolean boxGivesHere = box.energyRoleForFace(contact.getOpposite())
						== dev.alaindustrial.core.energy.EnergyRole.OUT;
				sawAnIntakeFace |= condenserTakesHere;
				sawAnOutFace |= boxGivesHere;
				if (condenserTakesHere && boxGivesHere) {
					helper.fail("a Battery Box facing " + facing + " would present its OUT face to the "
							+ "condenser's " + contact + " intake with no cable between them — the grid's "
							+ "serve order never sees that transfer, so it would bypass every guard");
				}
			}
		}
		if (!sawAnOutFace || !sawAnIntakeFace) {
			helper.fail("control failed: the sweep saw " + (sawAnOutFace ? "" : "no box OUT face ")
					+ (sawAnIntakeFace ? "" : "no condenser intake face ")
					+ "— it compared nothing");
		}
		helper.succeed();
	}

	/** Server tick order: source, wire, network, then the sinks. */
	private static void drive(GameTestHelper helper, BlockPos source, BlockPos cable,
			@org.jspecify.annotations.Nullable BlockPos machine, EnergyCondenserBlockEntity condenser, int ticks) {
		for (int i = 0; i < ticks; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, source));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));
			dev.alaindustrial.core.energy.NetworkManager.tickAll(helper.getLevel());
			if (machine != null && EnergyScenarioSupport.be(helper, machine) != null) {
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, machine));
			}
			EnergyScenarioSupport.tick(helper, condenser);
		}
	}

	/** No upgrade panel: an overclocker here would turn "sips the surplus" into a pump on the grid. */
	public static void condenser_hasNoUpgradePanel(GameTestHelper helper) {
		EnergyCondenserBlockEntity be = place(helper);
		if (be.hasUpgradePanel() || be.hasUpgradeSlots()) {
			helper.fail("the condenser must not carry an upgrade panel");
		}
		if (be.overclockerCap() != 0 || be.supportsOverclock()) {
			helper.fail("the condenser must not be overclockable");
		}
		// Control: a machine that SHOULD have the panel still has it — otherwise this test would pass
		// on a build where the panel disappeared for everyone.
		MachineBlockEntity macerator =
				AlaGameTestHelper.place(helper, POS.offset(2, 0, 0), ModContent.MACERATOR.get());
		if (!macerator.hasUpgradeSlots()) {
			helper.fail("control failed: a macerator must still have its upgrade panel");
		}
		helper.succeed();
	}
}

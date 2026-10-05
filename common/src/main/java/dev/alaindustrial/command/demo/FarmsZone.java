package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Zone <b>farms</b> (MOD-294, chains along z=46): four working mini-chains, one per key
 * progression, each stocked so it runs the moment the stand is built. The rows on either side are
 * walkways; the north neighbour of every chain cell is air, never an energy block, so nothing from
 * the item-less row (z=44) or the cable rows above can tap a farm.
 *
 * <p>Domain (coding standard, section 1): Agriculture.
 */
final class FarmsZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;
	private static final LongUnaryOperator FULL = StandWriter.FULL;

	/** Camera of {@code /ala demo tp farms}. */
	static final DemoStand.TpPoint FARMS_CAMERA =
			new DemoStand.TpPoint("farms", 38.0, 20.0, 32.0, 0.0f, 55.0f, false);

	@Override
	public void build(StandWriter w) {
		// Farm A — LV cycle: solar → copper cable → battery box → cable → macerator, ore chest beside.
		// The panels are FACING-inert (every horizontal face is OUT), so the east-running chain needs
		// no state juggling — the cable simply meets the panel's OUT face.
		w.set(4, 1, 46, ModContent.SOLAR_PANEL.get());
		w.set(5, 1, 46, ModContent.COPPER_CABLE.get());
		w.placeFacing(6, 1, 46, ModContent.BATTERY_BOX.get(), Direction.WEST);
		w.chargeBuffer(6, 1, 46);
		w.set(7, 1, 46, ModContent.COPPER_CABLE.get());
		w.placeWorkingMachine(8, 46, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		w.set(10, 1, 46, ModContent.IRON_CHEST.get());
		w.fillSlot(10, 1, 46, 0, new ItemStack(Items.RAW_IRON, 64));
		w.fillSlot(10, 1, 46, 1, new ItemStack(Items.COAL, 64));
		// Farm B — fluid line: battery box → pump facing a water cistern → fluid pipes → empty tank
		// that visibly fills. The pump's IN faces are everything but its intake (PumpBlock), so the
		// box, standing on its south face, meets one directly.
		//
		// The pump drinks from the block IN FRONT of it (its FACING is its only intake face), so it
		// faces EAST into a raised cistern: a walled 2x2 pool of sources. The pump removes the source it
		// drinks and the two beside the emptied cell refill it — the cistern is inexhaustible, like a
		// vanilla pond. The stand used to sink one water cell UNDER the pump, which stands over nothing it
		// can drink (MOD-659). Walled on every side, because flowing fluid washes the mod's pipes away
		// (MOD-661): the three-in-a-row cistern this replaced had its east source right under the first
		// pipe of the line and swept it off the stand.
		//
		// MOD-674: the whole line stands on rows z=45..48, one row clear of the showcase frames (z=49).
		// The cistern's back wall used to be laid AT z=49, on top of the two lowest frames' cells: the
		// frames could not hang there and left two holes in the wall, with water lying against it. Nothing
		// of this farm stands on z=49 now, and the water is two cells from it. The pump is the pool's west
		// wall beside its north row and the battery box is the west wall beside its south row.
		w.placeFacing(35, 1, 46, ModContent.PUMP.get(), Direction.EAST);
		w.chargeBuffer(35, 1, 46);
		w.placeFacing(35, 1, 47, ModContent.BATTERY_BOX.get(), Direction.SOUTH);
		w.chargeBuffer(35, 1, 47);
		for (int cx = 36; cx <= 37; cx++) {
			w.set(cx, 1, 45, FLOOR);
			w.set(cx, 1, 46, Blocks.WATER);
			w.set(cx, 1, 47, Blocks.WATER);
			w.set(cx, 1, 48, FLOOR);
		}
		w.set(38, 1, 46, FLOOR);
		w.set(38, 1, 47, FLOOR);
		for (int x = 32; x <= 34; x++) {
			w.set(x, 1, 46, ModContent.FLUID_PIPE.get());
		}
		w.set(31, 1, 46, ModContent.FLUID_TANK.get());
		// Farm C — oil → rubber → cable: an open oil cell beside an oil-fed polymerizer, the
		// heater+vulcanizer pair, and a chest with the chain's inputs and both cable grades to
		// compare in hand.
		w.set(44, -1, 46, FLOOR);
		w.set(44, 0, 46, ModContent.OIL_BLOCK.get());
		w.set(45, 1, 46, ModContent.POLYMERIZER.get());
		w.chargeBuffer(45, 1, 46);
		w.fillTank(45, 1, 46, PolymerizerBlockEntity.class, be -> be.fluidTank, ModContent.OIL.get(), FULL);
		w.set(47, 1, 46, ModContent.ELECTRIC_HEATER.get());
		w.chargeBuffer(47, 1, 46);
		w.set(47, 2, 46, ModContent.VULCANIZER.get());
		w.chargeBuffer(47, 2, 46);
		w.fillSlot(47, 2, 46, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		w.fillSlot(47, 2, 46, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		w.set(49, 1, 46, ModContent.IRON_CHEST.get());
		w.fillSlot(49, 1, 46, 0, new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		w.fillSlot(49, 1, 46, 1, new ItemStack(ModContent.RUBBER.get(), 64));
		w.fillSlot(49, 1, 46, 2, new ItemStack(ModContent.INSULATED_COPPER_CABLE_ITEM.get(), 64));
		// Farm D — mutation: incubator + dome in transform mode, a ripe trellis on moist farmland,
		// and a chest with all three chips. The misc zone's incubator shows duplicate mode; this one
		// shows the mode that consumes the plant and returns the mutated result.
		w.set(66, 1, 46, ModContent.INCUBATOR.get());
		w.set(66, 2, 46, ModContent.INCUBATOR_DOME.get());
		w.chargeBuffer(66, 1, 46);
		w.fillSlot(66, 1, 46, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		w.fillSlot(66, 1, 46, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		w.fillSlot(66, 1, 46, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.SWEET_BERRIES, 64));
		w.ripenTrellis(68, 46);
		w.set(70, 1, 46, ModContent.IRON_CHEST.get());
		w.fillSlot(70, 1, 46, 0, new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get(), 16));
		w.fillSlot(70, 1, 46, 1, new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get(), 16));
		w.fillSlot(70, 1, 46, 2, new ItemStack(ModContent.MUTATION_CHIP_CREATE.get(), 16));
	}
}

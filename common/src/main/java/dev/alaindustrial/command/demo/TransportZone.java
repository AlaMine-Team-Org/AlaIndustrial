package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.function.LongUnaryOperator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Zone <b>transport</b> (rows z=16 and z=18, MOD-659): the two item-pipe grades and the two fluid-pipe
 * grades (MOD-675).
 * They used to stand at the very back of the stand, one row behind the showcase wall — invisible from
 * the front, with the two item grades stacked on top of each other and so, very likely, one network
 * (an advanced pipe IS an item pipe to its neighbour). Now each grade has its own row, one free row
 * between, so the two lines are separate networks that can be compared by eye and by the wrench.
 *
 * <p>The dead monitor kit that used to ride on top of the pipes (an unpowered core without its
 * capacity card, a panel with no filter) is gone: the working monitoring wall at
 * {@link MonitorWallZone} shows every one of those blocks running.
 *
 * <p>Domain (coding standard, section 1): Storage (item pipes) and Fluid (fluid pipes).
 */
final class TransportZone implements DemoZone {
	private static final LongUnaryOperator FULL = StandWriter.FULL;

	/** Camera of {@code /ala demo tp transport}. */
	static final DemoStand.TpPoint TRANSPORT_CAMERA =
			new DemoStand.TpPoint("transport", 7.0, 6.0, 12.0, 0.0f, 40.0f, false);

	/** Camera of {@code /ala demo tp fluids}. */
	static final DemoStand.TpPoint FLUIDS_CAMERA =
			new DemoStand.TpPoint("fluids", 34.0, 6.0, 12.0, 0.0f, 40.0f, false);

	@Override
	public void build(StandWriter w) {
		// MOD-104: a short item-pipe run between two chests. The two end faces remain neutral
		// in the stand; the wrench is used by the player to demonstrate extract/insert arrows.
		w.set(4, 1, 16, ModContent.IRON_CHEST.get());
		w.fillSlot(4, 1, 16, 0, new ItemStack(Items.IRON_INGOT, 32));
		for (int x = 5; x <= 9; x++) {
			w.set(x, 1, 16, ModContent.ITEM_PIPE.get());
		}
		w.set(10, 1, 16, ModContent.IRON_CHEST.get());
		// MOD-581: the advanced grade next to the basic one — the two are meant to be told apart by eye,
		// and a stand showing only one proves nothing about that. Its own row (see the class note).
		w.set(4, 1, 18, ModContent.IRON_CHEST.get());
		w.fillSlot(4, 1, 18, 0, new ItemStack(Items.IRON_INGOT, 32));
		for (int x = 5; x <= 9; x++) {
			w.set(x, 1, 18, ModContent.ITEM_PIPE_ADVANCED.get());
		}
		w.set(10, 1, 18, ModContent.IRON_CHEST.get());

		// The fluid line: tank → pipes → tank, the same read-left-to-right shape, so the two transport
		// systems can be compared side by side. The source tank is seeded so the pipes carry something and
		// show their fluid colour instead of sitting empty.
		w.set(30, 1, 16, ModContent.FLUID_TANK.get());
		w.fillTank(30, 1, 16, FluidTankBlockEntity.class, be -> be.fluidTank, Fluids.WATER, FULL);
		for (int x = 31; x <= 35; x++) {
			w.set(x, 1, 16, ModContent.FLUID_PIPE.get());
		}
		w.set(36, 1, 16, ModContent.FLUID_TANK.get());
		// MOD-675: the advanced fluid pipe on its own row, the way the two item grades stand — separate
		// networks, so the thick line and the thin one can be compared by eye.
		w.set(30, 1, 18, ModContent.FLUID_TANK.get());
		w.fillTank(30, 1, 18, FluidTankBlockEntity.class, be -> be.fluidTank, Fluids.WATER, FULL);
		for (int x = 31; x <= 35; x++) {
			w.set(x, 1, 18, ModContent.FLUID_PIPE_ADVANCED.get());
		}
		w.set(36, 1, 18, ModContent.FLUID_TANK.get());
		// MOD-612: the advanced grade stands next to the basic one, both filled to their OWN capacity —
		// side by side the taller fluid column and the belt around the frame are the whole point of the
		// tier, and a stand that filled both to 8000 would hide it.
		w.set(38, 1, 16, ModContent.FLUID_TANK_ADVANCED.get());
		w.fillTank(38, 1, 16, FluidTankBlockEntity.class, be -> be.fluidTank, Fluids.WATER, FULL);
	}
}

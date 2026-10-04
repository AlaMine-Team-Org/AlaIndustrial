package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Zone <b>reactor</b> (MOD-468, row z=42): every part of the reactor room laid out side by side, a
 * cell apart (x=4, 6, 8, … in MOD-659's doubled layout).
 *
 * <p>A row rather than an actual room, and deliberately so: a sealed 5x5x5 shell would hide its own
 * contents, and the stand exists to SHOW blocks. The pieces are spaced so each reads on its own —
 * casing, glass, feedthrough and lamp in a run, the airlock standing free, the controller facing the
 * camera, and a fuel assembly loaded to the brim so the rods are visible through its casing.
 *
 * <p>Nothing here is wired: the reactor only runs inside a sealed room, and a controller reporting
 * "not formed" on the stand is the honest state for a block sitting in the open.
 *
 * <p>Domain (coding standard, section 1): Reactor.
 */
final class ReactorZone implements DemoZone {
	/** Camera of {@code /ala demo tp reactorrow}. */
	static final DemoStand.TpPoint REACTORROW_CAMERA =
			new DemoStand.TpPoint("reactorrow", 17.0, 6.0, 37.0, 0.0f, 45.0f, false);

	@Override
	public void build(StandWriter w) {
		BlockPos origin = w.origin();
		int z = 42;
		w.set(4, 1, z, ModContent.REACTOR_CASING.get());
		w.set(6, 1, z, ModContent.REACTOR_GLASS.get());
		w.set(8, 1, z, ModContent.REACTOR_PORT.get());
		w.set(10, 1, z, ModContent.REACTOR_LAMP.get());
		w.set(12, 1, z, ModContent.REACTOR_OUTLET.get());

		// The button needs something to hang on, so it gets its own casing block to sit against.
		w.set(14, 1, z, ModContent.REACTOR_CASING.get());
		w.place(origin.offset(14, 2, z),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The lever (MOD-514) stands beside the button it twins, on its own casing block, so the two
		// control blocks can be told apart at a glance: one pulses, one latches.
		w.set(16, 1, z, ModContent.REACTOR_CASING.get());
		w.place(origin.offset(16, 2, z),
				ModContent.REACTOR_LEVER.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The airlock is two blocks: the stand places both halves by hand, because setPlacedBy (which
		// normally raises the upper half) does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		w.place(origin.offset(18, 1, z), door);
		w.place(origin.offset(18, 2, z),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));

		w.place(origin.offset(22, 1, z),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The exhaust, facing south into open air — a nozzle pointing at a block vents nothing, and a
		// stand that showed one buried in a wall would be showing a broken installation.
		w.place(origin.offset(30, 1, z),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.SOUTH));

		// Loaded to four rods, so the stand shows the state the fill level exists to communicate.
		w.place(origin.offset(26, 1, z),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		// Half a tank, so the stand shows the coolant level doing what it is for: a column that is
		// full and a column that is empty look the same from the front, and neither is the state
		// the property exists to communicate.
		w.loadFuelRods(26, 1, z);
	}
}

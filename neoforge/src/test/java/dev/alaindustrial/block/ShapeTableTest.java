package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.alaindustrial.core.item.PipeFaceRender;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-562 — the guard on the regression that cost the client 440 s of its startup: a pipe or cable
 * assembling its shape inside {@code getShape} instead of reading a pre-built one.
 *
 * <p>The defect is invisible to every other kind of check. The game plays correctly, the geometry is
 * right, the gametests pass — only registration slows down, because vanilla asks a block for its
 * shape twenty times per state and MOD-540 took the two pipes to 115 248 states between them. What
 * fails here instead is object identity: a table hands the same {@link VoxelShape} back, an assembly
 * builds a new one on every call.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class ShapeTableTest {

	private static VoxelShape shape(BlockState state) {
		return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private static BlockState pipe(BlockState state, PipeFaceRender render) {
		for (Direction dir : Direction.values()) {
			state = ItemPipeBlock.withRender(state, dir, render);
		}
		return state;
	}

	private static BlockState fluidPipe(BlockState state, PipeFaceRender render) {
		for (Direction dir : Direction.values()) {
			state = FluidPipeBlock.withRender(state, dir, render);
		}
		return state;
	}

	/** Asking twice must not build twice — this is the whole point of the table. */
	@Test
	void shapeIsNotRebuiltPerCall() {
		BlockState state = pipe(ModContent.ITEM_PIPE.get().defaultBlockState(), PipeFaceRender.NEUTRAL);
		assertSame(shape(state), shape(state),
				"item pipe assembles its shape per call — getShape runs 20x per state during registration");

		// The fluid pipe is not a footnote here: its `filled` property doubles the item pipe's count,
		// so it carries 76 832 of the mod's states — more than every other block put together. Left
		// out, this test would go green while the block that owns the majority of the cost went back
		// to assembling per call, and the nine-minute startup would ship again.
		BlockState fluid = fluidPipe(ModContent.FLUID_PIPE.get().defaultBlockState(), PipeFaceRender.NEUTRAL);
		assertSame(shape(fluid), shape(fluid),
				"fluid pipe assembles its shape per call — it carries 76 832 of the mod's block states");

		BlockState cable = ModContent.COPPER_CABLE.get().defaultBlockState();
		for (Direction dir : Direction.values()) {
			cable = cable.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), true);
		}
		assertSame(shape(cable), shape(cable), "cable assembles its shape per call");
	}

	/**
	 * Whether the pipe holds fluid is texture, not geometry — the two states must share one shape.
	 * A table keyed on `filled` would double its entries and quietly halve the saving.
	 */
	@Test
	void filledIsNotGeometry() {
		BlockState base = fluidPipe(ModContent.FLUID_PIPE.get().defaultBlockState(), PipeFaceRender.NEUTRAL);
		assertSame(shape(base.setValue(FluidPipeBlock.FILLED, false)),
				shape(base.setValue(FluidPipeBlock.FILLED, true)),
				"an empty and a filled pipe are the same solid — they must share one shape");
	}

	/**
	 * The routing mode a face shows is texture, not geometry, so the states that differ only by it
	 * must land on one shape. This is what collapses 38 416 states onto 324 shapes; lose it and the
	 * table degenerates into one entry per state.
	 */
	@Test
	void routingModeIsNotGeometry() {
		BlockState base = ModContent.ITEM_PIPE.get().defaultBlockState();
		assertSame(shape(pipe(base, PipeFaceRender.NEUTRAL)), shape(pipe(base, PipeFaceRender.EXTRACT)),
				"neutral and extract draw the same arm and must share one shape");
		assertSame(shape(pipe(base, PipeFaceRender.EXTRACT)), shape(pipe(base, PipeFaceRender.INSERT)),
				"extract and insert draw the same arm and must share one shape");
	}

	/** A dropped arm IS geometry — the table must not fold it onto the level one. */
	@Test
	void lowArmIsGeometry() {
		BlockState base = ModContent.ITEM_PIPE.get().defaultBlockState();
		BlockState level = ItemPipeBlock.withRender(base, Direction.NORTH, PipeFaceRender.NEUTRAL);
		BlockState low = ItemPipeBlock.withRender(base, Direction.NORTH, PipeFaceRender.NEUTRAL_LOW);
		assertNotSame(shape(level), shape(low), "the dropped arm must have its own shape");
	}
}

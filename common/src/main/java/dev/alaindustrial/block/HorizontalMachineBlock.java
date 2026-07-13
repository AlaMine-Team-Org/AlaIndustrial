package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Machine block that faces the player on placement (front texture toward the player). Adds the
 * {@code facing} blockstate property (horizontal), matching the {@code facing=*} model variants in
 * the blockstate JSON. Concrete blocks still supply their codec + block entity factory.
 */
public abstract class HorizontalMachineBlock extends AbstractMachineBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	protected HorizontalMachineBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	/**
	 * The front ({@code FACING}) face is the player-facing facade — energy-inert by R-NRG-03 (see
	 * {@link dev.alaindustrial.block.entity.MachineBlockEntity#facingAwareRole}), so a cable must not
	 * draw a misleading "energy goes here" arm toward it. The other five faces carry the block's
	 * working energy role, so they delegate to the face-agnostic {@link #isCableConnectable()} marker
	 * — which a subclass can still flip to {@code false} (e.g. {@link IronChestBlock}) to stay fully
	 * inert. This is the same per-face idea as the wind mill / battery box overrides (MOD-038), now
	 * applied once at the base so every {@code HorizontalMachineBlock} subclass (generator,
	 * geothermal, macerator, electric furnace, extractor, compressor, water mill, …) inherits it
	 * instead of repeating the override (MOD-061).
	 *
	 * <p><b>Contract for subclasses.</b> The non-{@code FACING} branch MUST delegate to
	 * {@link #isCableConnectable()}, never hardcode {@code true}: that delegation is what keeps
	 * {@link IronChestBlock} (which overrides the no-arg form to {@code false}) inert on all six
	 * faces. Hardcoding {@code true} here would silently re-expose its five non-front faces to a
	 * cable arm. If your subclass exposes energy on {@code FACING} (the pump does — its front is the
	 * fluid-intake + energy-IN face), override this method back to {@code super} or to
	 * {@code isCableConnectable()} so the arm reaches all six faces.
	 *
	 * <p>{@code side} is the world face of this block the cable touches (direction from this block
	 * toward the cable), matching the convention documented on
	 * {@link AbstractMachineBlock#isCableConnectable(BlockState, Direction)}.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		if (side == state.getValue(FACING)) {
			return false; // front facade is energy-inert (R-NRG-03); keep it readable
		}
		return isCableConnectable(); // other 5 faces — delegate to the face-agnostic marker
	}
}

package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lightning rod generator block (MOD-386) — a machine casing with an antenna: an 8px base plate
 * carrying the charge indicator, a copper mast, and the conductor head the strike lands on.
 *
 * <p><b>Rotation-symmetric, like the solar panels.</b> There is no {@code FACING} property. Vanilla's
 * lightning rod can be placed on any of six faces, but only the upright orientation could ever catch
 * anything here, so five of those states would be decoration with no behaviour behind them. Instead
 * the energy contract is the one the solar panels already established for a {@code FACING}-less
 * generator: the {@code UP} face is the working surface — the strike lands there and it emits no EU
 * ({@code EnergyRole.NONE}) — and the other five faces are ordinary generator outputs. Cables may
 * therefore attach anywhere except the top, which is what {@link #isCableConnectable} encodes so no
 * misleading "power flows here" arm is drawn toward a face no EU can cross.
 *
 * <p>The {@code lit} property is flipped by {@code EnergyBlockEntity#updateLit} while the rod is
 * actually bleeding its capacitor into the network, and swaps the indicator band and the head core
 * to the mod's LV blue — so a charged rod is readable across a base without opening its screen.
 *
 * <p><b>The mast appears only once a conductor tip is installed</b> ({@link #TIPPED}). An empty rod
 * is a bare plate: the block that cannot do its job does not pretend to have the part that does it,
 * and the player sees which rods still need a tip without opening four screens.
 *
 * <p>Not a full cube, hence {@code noOcclusion()} in the manifest's block properties and the
 * slab {@link #SHAPE} below (R-PHY-05).
 */
public class LightningRodGeneratorBlock extends AbstractMachineBlock {
	public static final MapCodec<LightningRodGeneratorBlock> CODEC =
			simpleCodec(LightningRodGeneratorBlock::new);

	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/**
	 * Whether a conductor tip is installed. Drives the model: with no tip the block is a bare casing
	 * plate and the mast is simply absent, so "this rod is not configured yet" is visible from across
	 * the base instead of only inside its screen. Flipped by the block entity, which owns the slot.
	 */
	public static final BooleanProperty TIPPED = BooleanProperty.create("tipped");

	/**
	 * <b>The casing plate only — the mast is deliberately outside the shape.</b>
	 *
	 * <p>8px is the solar panel's slab height, and that is the whole point: {@code CableBlock} decides
	 * between a normal arm (y 5..11) and a dropped "low" arm (y 2..8) by asking whether the neighbour's
	 * shape tops out at or below {@code LOW_NEIGHBOUR_THRESHOLD} = 0.5 blocks. Including the mast would
	 * make this block 1.0 tall, a cable beside it would reach for the normal height, and the arm would
	 * hang in the air above the plate — which is exactly how it looked in the first playtest. Reported
	 * as a slab, the rod takes the same tidy dropped arm every solar panel does.
	 *
	 * <p>Consequence, accepted rather than worked around: the mast is decoration with no collision and
	 * no outline, the same as the wind mill's rotor blades, which also sweep outside their block's
	 * shape. The casing underneath is a full 16x16 and is what the player actually clicks.
	 */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

	public LightningRodGeneratorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(TIPPED, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT, TIPPED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LightningRodGeneratorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Everything but the top, matching {@code LightningRodGeneratorBlockEntity#energyRoleForFace}:
	 * {@code UP} is where the lightning lands and carries no EU. Decided from the fixed direction
	 * alone, with no blockstate read, so it is correct the instant the block is placed and cannot
	 * race the block entity's load.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side != Direction.UP;
	}
}

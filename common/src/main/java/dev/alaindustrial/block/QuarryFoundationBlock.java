package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.core.energy.EnergyHostRedirect;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.item.ItemHostRedirect;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A quarry foundation, one of three tiers (MOD-927 shipped them as building blocks; MOD-950 gives them their part in
 * the frame). The tier is the frame's size rule: the weakest of the four foundations sets how wide the pit may be.
 *
 * <p><b>One foundation of the main tower is the quarry's power socket</b>: the one in the bottom layer straight under
 * the controller, which sits on the edge of its tower's head (owner rounds 6 and 7 of MOD-681). The spec runs the cable
 * to the ground rather than up to the controller {@value QuarryFrameScan#HEAD_HEIGHT} blocks above, so while the frame
 * stands this foundation lends the controller's energy port through its four sides and its bottom ({@link
 * EnergyHostRedirect}). {@link #PORT} says so, and only the controller sets it — on that one cell, on a successful scan
 * — so a loose foundation, or any other of the tower's 18, never pretends to be connected. Decided from the block state
 * alone, so the cable arm and the network agree the instant the scan flips it.
 *
 * <p><b>The socket lends the controller's inventory too</b> (MOD-951, owner 2026-11-05): a pipe or a hopper at the
 * foot of the main tower takes the haul out of the controller's buffer and brings a fresh drill bit, through the same
 * faces and on the same {@link #PORT} ({@link ItemHostRedirect}; {@link WorldlyContainerHolder} for the vanilla
 * hopper).
 */
public class QuarryFoundationBlock extends Block
		implements EnergyHostRedirect, ItemHostRedirect, FluidHostRedirect, WorldlyContainerHolder {

	/** Whether this foundation stands under the controller of a formed frame and lends its energy port. */
	public static final BooleanProperty PORT = BooleanProperty.create("port");

	private final int tier;

	public QuarryFoundationBlock(int tier, Properties properties) {
		super(properties);
		this.tier = tier;
		registerDefaultState(defaultBlockState().setValue(PORT, false).setValue(QuarryFramePart.FORMED, false));
	}

	/** The foundation's tier, 1 to 3. */
	public int tier() {
		return tier;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PORT, QuarryFramePart.FORMED);
	}

	/**
	 * The controller stands straight above, {@value QuarryFrameScan#HEAD_HEIGHT} blocks up. The top face lends nothing:
	 * the foundation above it is no cable's place to be.
	 */
	@Override
	@Nullable
	public BlockPos energyHost(BlockState state, BlockPos pos, Direction face) {
		if (!state.getValue(PORT) || face == Direction.UP) {
			return null;
		}
		return pos.above(QuarryFrameScan.HEAD_HEIGHT);
	}

	/** The same host and faces as the energy port: the controller's inventory, through every face but the top. */
	@Override
	@Nullable
	public BlockPos itemHost(BlockState state, BlockPos pos, @Nullable Direction face) {
		if (!state.getValue(PORT) || face == Direction.UP) {
			return null;
		}
		return pos.above(QuarryFrameScan.HEAD_HEIGHT);
	}

	/** The vanilla hopper's way in: the controller's inventory while this is the socket, nothing otherwise. */
	@Override
	@Nullable
	public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
		return ItemHostRedirect.lent(level, pos, state, null);
	}

	/** The socket lends the controller's water tank too (MOD-952), by the same rule. */
	@Override
	public @Nullable BlockPos fluidHost(BlockState state, BlockPos pos, @Nullable Direction face) {
		return itemHost(state, pos, face);
	}

	/**
	 * A water bucket on the socket of a formed frame pours into the controller's tank (MOD-952): the spec feeds the
	 * quarry water by pipe or by bucket into the main tower's foot. Anything else in hand falls through to the click
	 * that opens the screen.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.WATER_BUCKET) || !state.getValue(PORT)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (!(level.getBlockEntity(pos.above(QuarryFrameScan.HEAD_HEIGHT))
				instanceof QuarryControllerBlockEntity controller)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean[] poured = {false};
		EnergyTransactions.get().runCommitting(txn -> poured[0] = controller.cooling.waterTank.insert(
				FluidHolder.of(Fluids.WATER), FluidAmounts.BUCKET, txn) == FluidAmounts.BUCKET);
		if (poured[0]) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	/** Where the socket foundation of the controller at {@code head} is: the bottom layer, straight under it. */
	public static BlockPos foundationBelow(BlockPos head) {
		return head.below(QuarryFrameScan.HEAD_HEIGHT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return QuarryFramePart.renderShape(state, super.getRenderShape(state));
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return QuarryFramePart.occlusion(state, super.getOcclusionShape(state));
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return QuarryFramePart.lightDampening(state, super.getLightDampening(state));
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return QuarryFramePart.skylight(state, super.propagatesSkylightDown(state));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return QuarryFramePart.shape(state, level, pos, super.getShape(state, level, pos, context));
	}

	/** A formed cell opens the frame's controller screen ({@link QuarryFramePart#openController}). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		return QuarryFramePart.openController(state, level, pos, player);
	}

	/** Metal underfoot once formed ({@link QuarryFramePart#sound}). */
	@Override
	protected SoundType getSoundType(BlockState state) {
		return QuarryFramePart.sound(state, super.getSoundType(state));
	}
}

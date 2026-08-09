package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The Distillation Column's segment/base block (MOD-251, round 3 — hand-built multiblock). The
 * player crafts and places THIS block like any other; a single one is an inert "segment blank"
 * ({@code formed=false}). Stack <b>three</b> of them and the tower self-assembles: the bottom one
 * becomes the formed base (master, keeps its block entity and tanks), the two above transform into
 * the middle and top segment blocks.
 *
 * <p>Disassembly is the mirror: breaking the formed base drops it (with its fluids and fouling on
 * the item, portable-tank style) and the segments above <b>degrade back into blanks</b> in place;
 * breaking a middle/top segment drops one segment item and degrades the rest the same way — the
 * base keeps its tanks through the degrade, so nothing is ever silently lost.
 *
 * <p>No {@code FACING}: the tower is rotationally symmetric, its port layout is vertical — see
 * {@link DistillationColumnBlockEntity}. {@code lit} drives the glowing-windows model.
 */
public class DistillationColumnBlock extends AbstractMachineBlock {
	public static final MapCodec<DistillationColumnBlock> CODEC = simpleCodec(DistillationColumnBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** {@code false} = a lone segment blank; {@code true} = the assembled tower's base (master). */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/**
	 * Round-2 voxel silhouette: a full-footprint foundation slab, the ~12px chamfered column and
	 * the four port stubs (so a click on a nozzle lands on the tower, not the block behind it).
	 * Matches the model geometry approximately — shape and renderer geometry are independent.
	 */
	private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
			net.minecraft.world.phys.shapes.Shapes.or(
					Block.box(0, 0, 0, 16, 3, 16),
					Block.box(2, 3, 2, 14, 16, 14),
					Block.box(5, 5, 0, 11, 11, 16),
					Block.box(0, 5, 5, 16, 11, 11));

	public DistillationColumnBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(FORMED, false));
	}

	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
			net.minecraft.world.level.BlockGetter level, BlockPos pos,
			net.minecraft.world.phys.shapes.CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT, FORMED);
	}

	/**
	 * Round 3: placement is ordinary single-block placement (a blank). Assembly happens in
	 * {@link #setPlacedBy} via {@link #tryFormTower} once three blanks stand in a column.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide()) {
			tryFormTower(level, pos);
		}
	}

	/**
	 * Self-assembly (round 3): walk DOWN from {@code pos} over unformed blanks to the lowest one,
	 * then, if it starts a column of three blanks, transform them — bottom → formed base (its block
	 * entity survives the property flip), the two above → the middle/top segment blocks. Public and
	 * static so gametests can drive it directly ({@code helper.setBlock} never calls
	 * {@code setPlacedBy}, MOD-015).
	 */
	public static void tryFormTower(Level level, BlockPos pos) {
		BlockPos base = pos;
		while (isBlank(level.getBlockState(base.below()))) {
			base = base.below();
		}
		if (!isBlank(level.getBlockState(base))
				|| !isBlank(level.getBlockState(base.above()))
				|| !isBlank(level.getBlockState(base.above(2)))) {
			return;
		}
		// Bottom keeps its block + BE, only the property flips; the two above become segments. A
		// blank exposes no ports and opens no menu, so its BE is guaranteed empty — replacing it
		// with a segment loses nothing.
		BlockState baseState = level.getBlockState(base);
		level.setBlockAndUpdate(base, baseState.setValue(FORMED, true));
		level.setBlockAndUpdate(base.above(),
				dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN_MIDDLE.get().defaultBlockState());
		level.setBlockAndUpdate(base.above(2),
				dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN_TOP.get().defaultBlockState());
		level.playSound(null, base, net.minecraft.sounds.SoundEvents.ANVIL_USE,
				net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.2F);
	}

	private static boolean isBlank(BlockState state) {
		return state.getBlock() instanceof DistillationColumnBlock && !state.getValue(FORMED);
	}

	/** Test/demo-stand helper: place the ASSEMBLED tower directly (formed base + segments). */
	public static void placeTower(LevelAccessor level, BlockPos base) {
		level.setBlock(base, dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN.get()
				.defaultBlockState().setValue(FORMED, true), 3);
		level.setBlock(base.above(),
				dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN_MIDDLE.get().defaultBlockState(), 3);
		level.setBlock(base.above(2),
				dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN_TOP.get().defaultBlockState(), 3);
	}

	/**
	 * The formed base is gone (its own loot drop carried the fluids/fouling): the segments above
	 * degrade back into blanks in place — nothing vanishes, nothing double-drops. A removed blank
	 * has no tower to unmake.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		if (state.getValue(FORMED)) {
			degradeSegment(level, pos.above());
			degradeSegment(level, pos.above(2));
		}
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** Turn a formed segment back into a blank in place (round 3 degrade — used by both directions). */
	static void degradeSegment(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).getBlock() instanceof DistillationColumnSegmentBlock) {
			level.setBlockAndUpdate(pos, dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN.get()
					.defaultBlockState());
		}
	}

	/** Degrade the formed BASE back into a blank in place — its BE (tanks, fouling) survives. */
	static void degradeBase(ServerLevel level, BlockPos base) {
		BlockState state = level.getBlockState(base);
		if (state.getBlock() instanceof DistillationColumnBlock && state.getValue(FORMED)) {
			level.setBlockAndUpdate(base, state.setValue(FORMED, false)
					.setValue(LIT, false));
		}
	}

	/**
	 * Wrench-clean (round 2): a plain right-click with the wrench on any tower block scrapes the
	 * coke out — {@code fouling} resets and 1..3 coal pops out (the scrapings are, after all,
	 * nearly coal). Shift+RMB stays the wrench's dismantle, and with nothing to clean the click
	 * falls through to the GUI, so the wrench never "eats" a click for no visible effect.
	 */
	public static net.minecraft.world.InteractionResult tryWrenchClean(ItemStack stack, Level level,
			BlockPos basePos, net.minecraft.world.entity.player.Player player) {
		if (!(stack.getItem() instanceof dev.alaindustrial.item.tool.WrenchItem)
				|| player.isShiftKeyDown()) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (!(level.getBlockEntity(basePos) instanceof DistillationColumnBlockEntity master)
				|| master.getFouling() <= 0) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			int coal = master.cleanFouling();
			net.minecraft.world.level.block.Block.popResource(level, basePos.above(),
					new ItemStack(net.minecraft.world.item.Items.COAL, coal));
			level.playSound(null, basePos, net.minecraft.sounds.SoundEvents.AXE_SCRAPE,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 0.9F);
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.distillation_column.cleaned", coal));
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	protected net.minecraft.world.InteractionResult useItemOn(ItemStack stack, BlockState state,
			Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
			net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		net.minecraft.world.InteractionResult cleaned = tryWrenchClean(stack, level, pos, player);
		if (cleaned != net.minecraft.world.InteractionResult.PASS) {
			return cleaned;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/** A blank explains itself instead of opening a dead menu (round 3). */
	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level,
			BlockPos pos, net.minecraft.world.entity.player.Player player,
			net.minecraft.world.phys.BlockHitResult hit) {
		if (!state.getValue(FORMED)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
						"gui.alaindustrial.distillation_column.build_hint"));
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/** A blank exposes no energy port, so the cable must not draw an arm toward it (round 3). */
	@Override
	public boolean isCableConnectable(BlockState state, net.minecraft.core.Direction side) {
		if (!state.getValue(FORMED)) {
			return false;
		}
		return super.isCableConnectable(state, side);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DistillationColumnBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// machineTicker, not humMachineTicker: the column ships silent by deliberate decision
		// (design session 2026-08-09, recorded in docs/SOUND_TRACKING.md).
		return machineTicker(level);
	}
}

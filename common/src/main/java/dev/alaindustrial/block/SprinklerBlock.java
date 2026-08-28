package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.item.fluid.BucketFluids;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Sprinkler (MOD-525): a squat base with a mast, and a spinning head the block entity renderer
 * draws above it.
 *
 * <p>Rotation-symmetric like the Garden Drone Station — it works a circle, so there is no front to
 * aim, and the blockstate carries one property: {@link #SPRAYING}, which the renderer spins on.
 *
 * <p><b>Three ways to read it, and four ways to fill it.</b> The head spins while it can spray and
 * droplets arc over the plot; a right-click with an empty hand opens the screen, where the gauge
 * sits; and a pipe reads the tank directly. Filling takes a bucket or a capsule in hand, the same in
 * the screen's fill slot, a hopper into that slot, or a pipe on any face.
 *
 * <p>Opening the screen is deliberately left to {@code AbstractMachineBlock}: an override here that
 * printed the level in chat instead swallowed the click, and the menu became unreachable.
 *
 * <p>The screen is deliberately the smallest in the mod — one gauge and a container pair. There is
 * no energy bar because this block takes no EU, and an always-empty bar would read as a fault.
 */
public class SprinklerBlock extends AbstractMachineBlock {
	public static final MapCodec<SprinklerBlock> CODEC = simpleCodec(SprinklerBlock::new);

	/** True while the tank holds enough to spray — the renderer turns the head on this. */
	public static final BooleanProperty SPRAYING = BooleanProperty.create("spraying");

	/** Nozzles on the head — matches {@code SprinklerHeadBlockEntityRenderer.ARM_COUNT}. */
	private static final int NOZZLE_COUNT = 3;
	/** How far from the axis a nozzle sits, in blocks — the renderer's arm length over 16. */
	private static final double NOZZLE_REACH = 0.42;
	/** The head's height in blocks, matching where the renderer draws it. */
	private static final double HEAD_HEIGHT = 0.82;
	/** The head's turn rate while spraying, so emission follows the arms the player can see. */
	private static final double HEAD_RADIANS_PER_TICK = 0.42;

	/**
	 * Mounted under a ceiling rather than standing on a floor — the lantern's own property name, and
	 * the same idea: one block, two ways up, chosen by which face you place it against.
	 *
	 * <p>A greenhouse or a covered plot has a roof and no room for a mast, and a sprinkler is exactly
	 * the block you want hanging over a field. Everything else about it is unchanged: the head still
	 * turns, the spray still arcs down, and the zone is still centred on the block.
	 */
	public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

	/** A low base with a mast; the head beyond it is renderer geometry and has no collision. */
	private static final VoxelShape SHAPE_FLOOR = Shapes.or(
			Block.box(3, 0, 3, 13, 3, 13),
			Block.box(6, 3, 6, 10, 12, 10));

	/** The same silhouette flipped: the plate is against the ceiling, the mast hangs below it. */
	private static final VoxelShape SHAPE_CEILING = Shapes.or(
			Block.box(3, 13, 3, 13, 16, 13),
			Block.box(6, 4, 6, 10, 13, 10));

	public SprinklerBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(SPRAYING, false)
				.setValue(HANGING, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SPRAYING, HANGING);
	}

	/**
	 * Hang it when the player places it against the underside of a block, exactly as a lantern
	 * decides. Clicking any other face — including the top of a floor — stands it up.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HANGING, context.getClickedFace() == Direction.DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HANGING) ? SHAPE_CEILING : SHAPE_FLOOR;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SprinklerBlockEntity(pos, state);
	}

	/**
	 * No face takes a cable. The sprinkler runs on nutrient solution alone, so a cable drawn toward it
	 * would show the player a joint that reads as working while no EU can ever pass — the MOD-199
	 * defect the wind mills and the water mill already fixed on their own inert faces.
	 */
	@Override
	public boolean isCableConnectable() {
		return false;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Manual filling. A capsule is handed to its own {@code useOn}, which already speaks to any
	 * {@code FluidPortHost}; a bucket is exchanged whole here, the same one-bucket-or-nothing contract
	 * the portable tank keeps. Anything else falls through so vanilla placement still works.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof SprinklerBlockEntity sprinkler)) {
			return InteractionResult.PASS;
		}
		if (stack.is(ModContent.VACUUM_CAPSULE.get()) || stack.is(ModContent.FILLED_VACUUM_CAPSULE.get())) {
			return stack.useOn(new UseOnContext(player, hand, hit));
		}
		Fluid incoming = BucketFluids.content(stack);
		if (incoming == Fluids.EMPTY) {
			// TRY_WITH_EMPTY_HAND, not PASS: in 26.2 a PASS here does NOT fall through to
			// useWithoutItem, so the menu became unreachable whenever the player was holding
			// anything at all — which is most of the time. The Fuel Rod Assembly carries the same
			// note for the same reason.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean[] moved = {false};
		EnergyTransactions.get().runCommitting(txn ->
				moved[0] = sprinkler.tank.insert(FluidHolder.of(incoming), FluidAmounts.BUCKET, txn)
						== FluidAmounts.BUCKET);
		if (moved[0]) {
			player.setItemInHand(hand,
					ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * The spray itself: droplets flung off the spinning head, which then arc down onto the plot.
	 *
	 * <p>Thrown from the nozzles rather than scattered over the zone, because the arc is what makes it
	 * read as a sprinkler — the particle's own gravity and drag carry it outward and down, so where it
	 * lands is a consequence of how hard it was thrown rather than a random position. The first
	 * version scattered vanilla bone-meal motes across the radius and looked like drifting confetti:
	 * no source, no direction, no fall.
	 *
	 * <p>The throw is aimed to reach roughly the edge of the served zone, so what a player sees
	 * covered and what the block actually waters are the same circle.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(SPRAYING)) {
			return;
		}
		int radius = Math.max(1, Config.sprinklerRange);
		boolean hanging = state.getValue(HANGING);
		// The head is at the far end of the mast, which swaps ends when the block hangs.
		double headY = hanging ? 1.0 - HEAD_HEIGHT : HEAD_HEIGHT;
		// The head turns at a fixed rate, so the emission angle follows it: droplets leave the nozzles
		// the player can see, not from thin air. Three arms, so three streams 120 degrees apart.
		double headAngle = (level.getGameTime() % 24000L) * HEAD_RADIANS_PER_TICK;
		for (int arm = 0; arm < NOZZLE_COUNT; arm++) {
			if (random.nextInt(2) != 0) {
				continue; // thin the stream: every arm every tick is a wall of particles, not a mist
			}
			double angle = headAngle + arm * (Math.PI * 2.0 / NOZZLE_COUNT);
			double spread = (random.nextDouble() - 0.5) * 0.35;
			double cos = Math.cos(angle + spread);
			double sin = Math.sin(angle + spread);
			// Launch speed scaled to the radius: a wider zone needs a harder throw to cover it. Raised
			// alongside the droplet's gravity — a heavier drop lands sooner, so the same throw covered
			// noticeably less ground, and what the player saw watered would have shrunk inside the zone
			// the block actually serves.
			double speed = (0.12 + random.nextDouble() * 0.06) * radius * 0.5;
			level.addParticle(ModParticles.NUTRIENT_SPRAY,
					pos.getX() + 0.5 + cos * NOZZLE_REACH,
					pos.getY() + headY,
					pos.getZ() + 0.5 + sin * NOZZLE_REACH,
					cos * speed,
					// Hanging, the droplets are already under the ceiling and only need to fall; standing,
					// they get a small lift first so the arc clears the mast.
					hanging ? -0.01 : 0.06 + random.nextDouble() * 0.05,
					sin * speed);
		}
	}
}

package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.menu.DoubleChestMenu;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Shared spine of the mod's tiered storage chests (MOD-391): everything the three tier blocks have in
 * common now that a pair of same-tier chests merges into a double chest the vanilla way.
 *
 * <p>The tiers are <em>not</em> vanilla {@code ChestBlock} subclasses — they inherit
 * {@link HorizontalMachineBlock} for {@code FACING} and the mod's block-family behaviours — so the
 * whole pair mechanic is carried here, ported from the vanilla 26.2 {@code ChestBlock} sources:
 * {@code TYPE}/{@code WATERLOGGED} state, placement (auto-join without sneak, deliberate side-attach
 * with sneak), {@code updateShape} both forming and breaking the pair, half shapes, mirror with
 * {@code TYPE.getOpposite()} (MC-134110), and {@link DoubleBlockCombiner} — which is a free-standing
 * static helper taking the {@code BlockEntityType} + resolvers as parameters, so it does not care
 * about the block's class hierarchy.
 *
 * <p><b>Tier isolation for free:</b> {@link #chestCanConnectTo} is {@code state.is(this)}, exactly
 * like vanilla — an iron chest only pairs with an iron chest, because each tier is its own block
 * instance.
 *
 * <p><b>Automation sees a double as one container (both loaders, no mixin).</b>
 * {@code HopperBlockEntity.getBlockContainer} checks {@code block instanceof WorldlyContainerHolder}
 * <em>before</em> its block-entity branch, and the NeoForge rewrite of the hopper consults that same
 * method <em>before</em> the item capability — so implementing {@link WorldlyContainerHolder} here
 * gives the vanilla hopper, dropper and crafter the combined inventory on both loaders. The mod's own
 * item pipes read the combined view through their loader lookups (Fabric's {@code ItemStorage.SIDED}
 * fallback honours this same interface; NeoForge's chest capability is registered against the
 * combined container in {@code IndustrializationNeoForge}).
 *
 * <p><b>Deliberate deviations from vanilla, kept from the singles' current behaviour:</b> the mod's
 * chests never refuse to open under a solid block or a cat ({@code isChestBlockedAt} is not ported —
 * the blocked predicate is always false), and they do not award the vanilla open-chest stat.
 */
public abstract class AbstractModChestBlock extends HorizontalMachineBlock
		implements SimpleWaterloggedBlock, WorldlyContainerHolder {
	public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	/** Single-chest shape — the squat 14×14×14 column every tier used before doubles existed. */
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 14.0);
	/**
	 * Half-of-a-double shape, keyed by the direction toward the partner: the 14-wide column stretched
	 * to the connected edge ({@code z 0..15}), rotated per facing — vanilla's {@code HALF_SHAPES}.
	 */
	private static final Map<Direction, VoxelShape> HALF_SHAPES =
			Shapes.rotateHorizontal(Block.boxZ(14.0, 0.0, 14.0, 0.0, 15.0));

	/**
	 * The mod's chests are never "blocked": vanilla refuses to open a chest under a solid block or a
	 * sitting cat, ours never did — changing the singles' behaviour is out of MOD-391's scope.
	 */
	private static final BiPredicate<LevelAccessor, BlockPos> NEVER_BLOCKED = (level, pos) -> false;

	protected AbstractModChestBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(TYPE, ChestType.SINGLE)
				.setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(TYPE, WATERLOGGED);
	}

	/**
	 * The tier's block-entity type, for {@link DoubleBlockCombiner} and the client lid ticker. The
	 * {@code ModContent} handle is wildcard-typed, so each tier narrows it with a checked cast.
	 */
	protected abstract BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType();

	/**
	 * Default window title of this tier's double chest — used when neither half carries a custom
	 * name, mirroring vanilla's {@code container.chestDouble}.
	 */
	protected abstract Component defaultDoubleName();

	// --- pair state resolution (ported from vanilla ChestBlock, 26.2) ---

	/** RIGHT is FIRST, LEFT is SECOND — the order {@code CompoundContainer} joins the halves in. */
	public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
		ChestType type = state.getValue(TYPE);
		if (type == ChestType.SINGLE) {
			return DoubleBlockCombiner.BlockType.SINGLE;
		}
		return type == ChestType.RIGHT ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
	}

	/** The horizontal direction from this half toward its partner. */
	public static Direction getConnectedDirection(BlockState state) {
		Direction facing = state.getValue(FACING);
		return state.getValue(TYPE) == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
	}

	/** Same-tier check: each tier is its own block instance, so this is the whole tier gate. */
	public boolean chestCanConnectTo(BlockState state) {
		return state.is(this);
	}

	private @Nullable Direction candidatePartnerFacing(Level level, BlockPos pos, Direction neighbourDirection) {
		BlockState state = level.getBlockState(pos.relative(neighbourDirection));
		return chestCanConnectTo(state) && state.getValue(TYPE) == ChestType.SINGLE ? state.getValue(FACING) : null;
	}

	private ChestType getChestType(Level level, BlockPos pos, Direction facing) {
		if (facing == candidatePartnerFacing(level, pos, facing.getClockWise())) {
			return ChestType.LEFT;
		}
		return facing == candidatePartnerFacing(level, pos, facing.getCounterClockWise())
				? ChestType.RIGHT
				: ChestType.SINGLE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		ChestType type = ChestType.SINGLE;
		Direction facing = context.getHorizontalDirection().getOpposite();
		FluidState replaced = context.getLevel().getFluidState(context.getClickedPos());
		boolean secondaryUse = context.isSecondaryUseActive();
		Direction clickedFace = context.getClickedFace();
		if (clickedFace.getAxis().isHorizontal() && secondaryUse) {
			// Sneak + click on the SIDE of an existing single chest = deliberate attach to it (the
			// new half takes the partner's facing, even if the player looks another way).
			Direction partnerFacing = candidatePartnerFacing(context.getLevel(), context.getClickedPos(),
					clickedFace.getOpposite());
			if (partnerFacing != null && partnerFacing.getAxis() != clickedFace.getAxis()) {
				facing = partnerFacing;
				type = facing.getCounterClockWise() == clickedFace.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
			}
		}
		if (type == ChestType.SINGLE && !secondaryUse) {
			// No sneak = auto-join with a same-facing single neighbour to the left or right.
			type = getChestType(context.getLevel(), context.getClickedPos(), facing);
		}
		return defaultBlockState().setValue(FACING, facing).setValue(TYPE, type)
				.setValue(WATERLOGGED, replaced.is(Fluids.WATER));
	}

	/**
	 * Both halves of the pair mechanic live here: a SINGLE next to a fresh half joins it (takes the
	 * opposite half type), and a half whose partner disappeared falls back to SINGLE. Also keeps the
	 * waterlogged fluid ticking, as every {@code SimpleWaterloggedBlock} must.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		if (chestCanConnectTo(neighbourState) && directionToNeighbour.getAxis().isHorizontal()) {
			ChestType neighbourType = neighbourState.getValue(TYPE);
			if (state.getValue(TYPE) == ChestType.SINGLE
					&& neighbourType != ChestType.SINGLE
					&& state.getValue(FACING) == neighbourState.getValue(FACING)
					&& getConnectedDirection(neighbourState) == directionToNeighbour.getOpposite()) {
				return state.setValue(TYPE, neighbourType.getOpposite());
			}
		} else if (getConnectedDirection(state) == directionToNeighbour) {
			return state.setValue(TYPE, ChestType.SINGLE);
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(TYPE)) {
			case SINGLE -> SHAPE;
			case LEFT, RIGHT -> HALF_SHAPES.get(getConnectedDirection(state));
		};
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	/** Vanilla's MC-134110 fix: mirroring swaps the half, or the pair tears apart in structures. */
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
		return mirror == Mirror.NONE ? rotated : rotated.setValue(TYPE, state.getValue(TYPE).getOpposite());
	}

	// --- combining the two halves ---

	/**
	 * Resolve this position into SINGLE / FIRST+SECOND halves. The blocked predicate is always false
	 * (see {@link #NEVER_BLOCKED}); the combiner itself re-checks tier, facing and half compatibility,
	 * so a half whose neighbour is a foreign chest degrades to single instead of joining it.
	 */
	public DoubleBlockCombiner.NeighborCombineResult<? extends AbstractChestBlockEntity> combine(
			BlockState state, LevelAccessor level, BlockPos pos) {
		return combineTyped(chestBlockEntityType(), state, level, pos);
	}

	private static <S extends AbstractChestBlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineTyped(
			BlockEntityType<S> type, BlockState state, LevelAccessor level, BlockPos pos) {
		return DoubleBlockCombiner.combineWithNeigbour(type, AbstractModChestBlock::getBlockType,
				AbstractModChestBlock::getConnectedDirection, FACING, state, level, pos, NEVER_BLOCKED);
	}

	/**
	 * The container this position answers for: the chest itself when single, the two halves joined
	 * FIRST-then-SECOND (right half first, like vanilla) when double, {@code null} when the block
	 * entity is not there. One resolution point shared by the menu, the comparator, the worldly
	 * adapter and the stock display frame.
	 */
	public @Nullable Container combinedContainer(BlockState state, LevelAccessor level, BlockPos pos) {
		// The combiner is typed to the shared BE base (a super of the captured tier type) — the
		// diamond cannot express that captured wildcard, so the type arguments are explicit.
		return combine(state, level, pos)
				.apply(new DoubleBlockCombiner.Combiner<AbstractChestBlockEntity, Optional<Container>>() {
					@Override
					public Optional<Container> acceptDouble(AbstractChestBlockEntity first,
							AbstractChestBlockEntity second) {
						return Optional.of(new CompoundContainer(first, second));
					}

					@Override
					public Optional<Container> acceptSingle(AbstractChestBlockEntity single) {
						return Optional.of(single);
					}

					@Override
					public Optional<Container> acceptNone() {
						return Optional.empty();
					}
				})
				.orElse(null);
	}

	// --- opening the menu ---

	/**
	 * Unlike the machine base (which opens the block entity at the clicked position), a chest opens
	 * whatever {@link #combine} resolves — for a double that is one shared menu over both halves,
	 * whichever half was clicked.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			MenuProvider provider = chestMenuProvider(state, level, pos);
			if (provider != null) {
				player.openMenu(provider);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private @Nullable MenuProvider chestMenuProvider(BlockState state, Level level, BlockPos pos) {
		return combine(state, level, pos)
				.apply(new DoubleBlockCombiner.Combiner<AbstractChestBlockEntity, Optional<MenuProvider>>() {
					@Override
					public Optional<MenuProvider> acceptDouble(AbstractChestBlockEntity first,
							AbstractChestBlockEntity second) {
						return Optional.of(doubleMenuProvider(first, second));
					}

					@Override
					public Optional<MenuProvider> acceptSingle(AbstractChestBlockEntity single) {
						return Optional.of(single);
					}

					@Override
					public Optional<MenuProvider> acceptNone() {
						return Optional.empty();
					}
				})
				.orElse(null);
	}

	private MenuProvider doubleMenuProvider(AbstractChestBlockEntity first, AbstractChestBlockEntity second) {
		Container joined = new CompoundContainer(first, second);
		return new MenuProvider() {
			@Override
			public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
				if (first.canOpen(player) && second.canOpen(player)) {
					return DoubleChestMenu.server(syncId, inventory, joined);
				}
				return null;
			}

			@Override
			public Component getDisplayName() {
				// A custom name on either half titles the whole double, like vanilla.
				if (first.hasCustomName()) {
					return first.getDisplayName();
				}
				return second.hasCustomName() ? second.getDisplayName() : defaultDoubleName();
			}
		};
	}

	// --- automation: the combined inventory, visible to hoppers/droppers/crafters on both loaders ---

	/**
	 * {@code HopperBlockEntity.getBlockContainer} consults this interface before its block-entity
	 * branch (and, on NeoForge, before the item capability), so this is what makes a double chest one
	 * inventory for the vanilla hopper, dropper and crafter without a mixin. For a single chest it
	 * answers the same slots the block-entity branch used to.
	 */
	@Override
	public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
		Container container = combinedContainer(state, level, pos);
		return new AllSidesContainer(container != null ? container : new SimpleContainer(0));
	}

	/**
	 * A plain chest exposed through the {@link WorldlyContainer} contract: every slot on every face,
	 * no per-face filtering — the same semantics the block-entity {@code Container} branch gave.
	 */
	static final class AllSidesContainer implements WorldlyContainer {
		private final Container backing;
		private final int[] allSlots;

		AllSidesContainer(Container backing) {
			this.backing = backing;
			this.allSlots = new int[backing.getContainerSize()];
			for (int slot = 0; slot < allSlots.length; slot++) {
				allSlots[slot] = slot;
			}
		}

		@Override
		public int[] getSlotsForFace(Direction side) {
			return allSlots;
		}

		@Override
		public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
			return backing.canPlaceItem(slot, stack);
		}

		@Override
		public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
			return true;
		}

		@Override
		public int getContainerSize() {
			return backing.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return backing.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return backing.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return backing.removeItem(slot, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return backing.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			backing.setItem(slot, stack);
		}

		@Override
		public int getMaxStackSize() {
			return backing.getMaxStackSize();
		}

		@Override
		public void setChanged() {
			backing.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return backing.stillValid(player);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return backing.canPlaceItem(slot, stack);
		}

		@Override
		public void clearContent() {
			backing.clearContent();
		}
	}

	// --- comparator (MOD-391: parity fix for all tiers, doubles read as one) ---

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromContainer(combinedContainer(state, level, pos));
	}

	/** Comparators next to a destroyed half must re-read the halved container. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	// --- shared tickers (moved up from the tier blocks — identical across tiers) ---

	/** Client-only lid interpolation; the server path is the scheduled {@link #tick} below. */
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (!level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof AbstractChestBlockEntity chest) {
				chest.tickLid();
			}
		};
	}

	/**
	 * Scheduled-tick handler: re-evaluates who has the chest open on the server. The opener counter
	 * reschedules this tick every 5 ticks while anyone has the GUI open.
	 */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof AbstractChestBlockEntity chest) {
			chest.recheckOpen();
		}
	}

	/**
	 * A chest is a pure container with no energy port — a cable must not draw an arm toward it
	 * (MOD-038). Identical for all tiers, so it lives here.
	 */
	@Override
	public boolean isCableConnectable() {
		return false;
	}
}

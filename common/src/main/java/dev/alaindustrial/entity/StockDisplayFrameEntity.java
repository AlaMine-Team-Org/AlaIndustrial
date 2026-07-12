package dev.alaindustrial.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stock Display Frame (MOD-066) — an {@link ItemFrame} subclass that hangs on any container block
 * and shows the item count inside it as floating text below the frame (see
 * {@code docs/blocks/utility/stock_display_frame.md}).
 *
 * <p><b>Count rule:</b> a filter item in the frame counts only matching stacks
 * ({@link ItemStack#isSameItemSameComponents}); an empty frame counts everything. Only top-level
 * {@link Container} slots are summed — no recursion into shulkers/bundles. Double chests resolve
 * through {@link ChestBlock#getContainer} so either half shows the combined total.
 *
 * <p><b>Sync:</b> the count lives in {@link SynchedEntityData} ({@code DATA_COUNT}); dirty entity
 * data reaches clients immediately regardless of the type's {@code updateInterval} (verified against
 * {@code ServerEntity.sendChanges} 26.2). {@code -1} means "no container behind the frame" and the
 * renderer draws nothing. The count is not persisted — it is rescanned after load.
 *
 * <p><b>Inherited vanilla behaviour deliberately replaced:</b> right-click sets/removes the filter
 * instead of rotating the displayed item ({@link #interact}), the frame drops the mod's own item
 * ({@link #getFrameItemStack} — the same single seam vanilla {@code GlowItemFrame} uses), and the
 * comparator signal reflects container fullness instead of item rotation ({@link #getAnalogOutput}).
 */
public class StockDisplayFrameEntity extends ItemFrame {
	/** Synced stock count; {@link #NO_CONTAINER} when there is no container behind the frame. */
	private static final EntityDataAccessor<Integer> DATA_COUNT =
			SynchedEntityData.defineId(StockDisplayFrameEntity.class, EntityDataSerializers.INT);
	public static final int NO_CONTAINER = -1;

	/** Ticks until the next container scan; starts at 0 so a fresh/loaded frame scans immediately. */
	private int scanCooldown;

	public StockDisplayFrameEntity(EntityType<? extends StockDisplayFrameEntity> type, Level level) {
		super(type, level);
	}

	public StockDisplayFrameEntity(EntityType<? extends StockDisplayFrameEntity> type, Level level,
			BlockPos pos, Direction direction) {
		super(type, level, pos, direction);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_COUNT, NO_CONTAINER);
	}

	/** The synced stock count for the client renderer; {@link #NO_CONTAINER} hides the text. */
	public int getStockCount() {
		return this.getEntityData().get(DATA_COUNT);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved() && --this.scanCooldown <= 0) {
			this.scanCooldown = Math.max(1, Config.stockFrameScanIntervalTicks);
			int count = this.countContainerBehind(serverLevel);
			if (count != this.getStockCount()) {
				this.getEntityData().set(DATA_COUNT, count);
			}
		}
	}

	/**
	 * Sum the container behind the frame, or {@link #NO_CONTAINER}. The chunk-loaded guard matters at
	 * chunk borders: the frame's chunk can be ticking while the supporting block's chunk is not, and
	 * reading it would force a synchronous chunk load every scan.
	 */
	private int countContainerBehind(ServerLevel level) {
		BlockPos target = this.getPos().relative(this.getDirection().getOpposite());
		if (!level.isLoaded(target)) {
			return this.getStockCount(); // keep the last value rather than flickering to "no container"
		}
		Container container = resolveContainer(level, target);
		if (container == null) {
			return NO_CONTAINER;
		}
		ItemStack filter = this.getItem();
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (filter.isEmpty() || ItemStack.isSameItemSameComponents(stack, filter)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * The {@link Container} at {@code target}, or null. Chests go through
	 * {@link ChestBlock#getContainer} so a double chest is counted as one combined container from
	 * either half; everything else (barrel, shulker box, iron chest, third-party containers) is
	 * matched by its block entity implementing {@link Container}.
	 */
	private static @Nullable Container resolveContainer(ServerLevel level, BlockPos target) {
		BlockState state = level.getBlockState(target);
		if (state.getBlock() instanceof ChestBlock chest) {
			return ChestBlock.getContainer(chest, state, level, target, false);
		}
		BlockEntity be = level.getBlockEntity(target);
		return be instanceof Container container ? container : null;
	}

	/**
	 * Filter handling instead of the vanilla item-rotation branch: empty frame + held item sets the
	 * filter (maps rejected — filtering maps in a chest is meaningless and drags in vanilla map-frame
	 * tracking); a filled frame hands the filter back to the player. Vanilla's rotate branch (and its
	 * rotate sound) is intentionally gone — rotation stays 0 forever on this frame.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		boolean frameHasItem = !this.getItem().isEmpty();
		boolean hasHeldItem = !held.isEmpty();
		// Vanilla guards on the private `fixed` flag here; this frame never sets Fixed itself (only
		// external NBT editing could), so the guard is intentionally omitted.
		if (player.level().isClientSide()) {
			return !frameHasItem && !hasHeldItem ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		if (!frameHasItem) {
			if (!hasHeldItem || this.isRemoved() || this.getFramedMapId(held) != null) {
				return InteractionResult.PASS;
			}
			this.setItem(held);
			this.gameEvent(GameEvent.BLOCK_CHANGE, player);
			held.consume(1, player);
			// Re-scan right away so the number switches to the new filter without the interval lag.
			this.scanCooldown = 0;
			return InteractionResult.SUCCESS;
		}
		// Filled frame: return the filter to the player instead of rotating it.
		ItemStack filter = this.getItem().copy();
		this.setItem(ItemStack.EMPTY);
		this.playSound(this.getRemoveItemSound(), 1.0F, 1.0F);
		this.gameEvent(GameEvent.BLOCK_CHANGE, player);
		if (!player.getInventory().add(filter)) {
			player.drop(filter, false);
		}
		this.scanCooldown = 0;
		return InteractionResult.SUCCESS;
	}

	/** Break/pick-block yield the mod's frame item — the same single seam vanilla GlowItemFrame uses. */
	@Override
	protected ItemStack getFrameItemStack() {
		return new ItemStack(ModContent.STOCK_DISPLAY_FRAME_ITEM.get());
	}

	/**
	 * Comparator output = container fullness (vanilla container-fullness curve), not the vanilla
	 * frame's rotation signal — rotation is always 0 on this frame, which would otherwise pin the
	 * signal to a meaningless constant 1 while a filter is present.
	 */
	@Override
	public int getAnalogOutput() {
		if (this.level() instanceof ServerLevel serverLevel) {
			BlockPos target = this.getPos().relative(this.getDirection().getOpposite());
			if (serverLevel.isLoaded(target)) {
				Container container = resolveContainer(serverLevel, target);
				return AbstractContainerMenu.getRedstoneSignalFromContainer(container);
			}
		}
		return 0;
	}
}

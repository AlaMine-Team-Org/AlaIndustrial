package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * Silver Chest block entity — the tier above the iron chest: 45 slots (one row more than the iron
 * chest's 36 → 45), built on the vanilla {@link BaseContainerBlockEntity} spine just like
 * {@link IronChestBlockEntity}. Pure container: no energy buffer, no processing, no machine tick
 * logic — it reuses the vanilla persistence + menu-provider implementation and only customises the
 * inventory size, the display name and the menu it opens.
 *
 * <p>Lid animation: the silver chest renders as a real 3D chest model (see
 * {@code SilverChestBlockEntityRenderer}) whose lid lifts on open, identical to the iron chest.
 *
 * <p>Sounds: the silver chest reuses the iron chest's open/close samples
 * ({@link ModSounds#IRON_CHEST_OPEN} / {@link ModSounds#IRON_CHEST_CLOSE}) — the metallic chest
 * sound is shared across both tiers, matching how vanilla uses one wooden-chest sample for every
 * wood variant. No dedicated {@code silver_chest_open/close} sound event exists.
 */
public class SilverChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
	/** One row more than the iron chest (36 → 45): 5 rows of 9 slots. */
	public static final int CONTAINER_SIZE = 45;
	/** Block event id the server sends when the open count changes; {@link #triggerEvent} reads it. */
	private static final int EVENT_SET_OPEN_COUNT = 1;

	private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
	private final ChestLidController chestLidController = new ChestLidController();
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			// Reuse the iron chest's open sound — no dedicated silver_chest sample.
			playSound(level, pos, ModSounds.IRON_CHEST_OPEN.get());
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			// Reuse the iron chest's close sound — no dedicated silver_chest sample.
			playSound(level, pos, ModSounds.IRON_CHEST_CLOSE.get());
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
			// Broadcast to the client so ChestLidController lifts the lid (triggerEvent handles id 1).
			level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, newCount);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof SilverChestMenu menu && menu.getContainer() == SilverChestBlockEntity.this;
		}
	};

	public SilverChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SILVER_CHEST_BE.get(), pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.alaindustrial.silver_chest");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return CONTAINER_SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new SilverChestMenu(syncId, playerInventory, this);
	}

	// --- lid animation (26.2 LidBlockEntity + ChestLidController) ---

	@Override
	public float getOpenNess(float partialTicks) {
		return chestLidController.getOpenness(partialTicks);
	}

	/**
	 * Server→client block event: id {@code 1} carries the new open count. Feeds it into the lid
	 * controller so the lid starts lifting (count {@code > 0}) or lowering ({@code 0}).
	 */
	@Override
	public boolean triggerEvent(int id, int arg) {
		if (id == EVENT_SET_OPEN_COUNT) {
			chestLidController.shouldBeOpen(arg > 0);
			return true;
		}
		return super.triggerEvent(id, arg);
	}

	/** Client ticker — drives the {@link ChestLidController} interpolation each client tick. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, SilverChestBlockEntity entity) {
		entity.chestLidController.tickLid();
	}

	/**
	 * Play an open/close sound at the chest's centre. Same volume/pitch profile as the iron chest
	 * (volume 0.42, BLOCKS source, slight random pitch 0.9–1.0).
	 */
	private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		float pitch = level.getRandom().nextFloat() * 0.1F + 0.9F;
		level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.42F, pitch);
	}

	/** Re-evaluate who has the chest open (scheduled-tick callback on the server). */
	public void recheckOpen() {
		if (!remove) {
			openersCounter.recheckOpeners(level, worldPosition, getBlockState());
		}
	}

	// --- opener counting (Container contract hooks) ---

	@Override
	public void startOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			openersCounter.incrementOpeners(user.getLivingEntity(), level, worldPosition,
					getBlockState(), user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			openersCounter.decrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return openersCounter.getEntitiesWithContainerOpen(level, worldPosition);
	}

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	public boolean canOpen(Player player) {
		// No lock code by default; BaseContainerBlockEntity.canOpen already checks the lock + the
		// "not blocked by a block/cat on top" rule via the chest block. The silver chest has no lock,
		// so this stays a simple "is still valid?" check.
		return super.canOpen(player);
	}
}

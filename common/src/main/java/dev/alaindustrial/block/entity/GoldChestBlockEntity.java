package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.menu.GoldChestMenu;
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
 * Gold Chest block entity — the tier above the silver chest: 54 slots (one row more than the silver
 * chest's 45 → 54), built on the vanilla {@link BaseContainerBlockEntity} spine just like
 * {@link IronChestBlockEntity} / {@link SilverChestBlockEntity}. Pure container: no energy buffer,
 * no processing, no machine tick logic.
 *
 * <p>Lid animation: the gold chest renders as a real 3D chest model (see
 * {@code GoldChestBlockEntityRenderer}) whose lid lifts on open, identical to the iron/silver chests.
 *
 * <p>Sounds: the gold chest reuses the iron chest's open/close samples
 * ({@link ModSounds#IRON_CHEST_OPEN} / {@link ModSounds#IRON_CHEST_CLOSE}) — the metallic chest
 * sound is shared across all tiers, matching how vanilla uses one wooden-chest sample for every
 * wood variant.
 */
public class GoldChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
	/** One row more than the silver chest (45 → 54): 6 rows of 9 slots. */
	public static final int CONTAINER_SIZE = 54;
	/** Block event id the server sends when the open count changes; {@link #triggerEvent} reads it. */
	private static final int EVENT_SET_OPEN_COUNT = 1;

	private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
	private final ChestLidController chestLidController = new ChestLidController();
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			// Reuse the iron chest's open sound — no dedicated gold_chest sample.
			playSound(level, pos, ModSounds.IRON_CHEST_OPEN.get());
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			// Reuse the iron chest's close sound — no dedicated gold_chest sample.
			playSound(level, pos, ModSounds.IRON_CHEST_CLOSE.get());
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
			// Broadcast to the client so ChestLidController lifts the lid (triggerEvent handles id 1).
			level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, newCount);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof GoldChestMenu menu && menu.getContainer() == GoldChestBlockEntity.this;
		}
	};

	public GoldChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GOLD_CHEST_BE.get(), pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.alaindustrial.gold_chest");
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
		return new GoldChestMenu(syncId, playerInventory, this);
	}

	// --- lid animation (26.2 LidBlockEntity + ChestLidController) ---

	@Override
	public float getOpenNess(float partialTicks) {
		return chestLidController.getOpenness(partialTicks);
	}

	@Override
	public boolean triggerEvent(int id, int arg) {
		if (id == EVENT_SET_OPEN_COUNT) {
			chestLidController.shouldBeOpen(arg > 0);
			return true;
		}
		return super.triggerEvent(id, arg);
	}

	/** Client ticker — drives the {@link ChestLidController} interpolation each client tick. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, GoldChestBlockEntity entity) {
		entity.chestLidController.tickLid();
	}

	/**
	 * Play an open/close sound at the chest's centre. Same volume/pitch profile as the iron/silver
	 * chests (volume 0.42, BLOCKS source, slight random pitch 0.9–1.0).
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
		return super.canOpen(player);
	}
}

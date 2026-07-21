package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.AbstractChestMenu;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared spine for the mod's tiered storage chests (iron 36 → silver 45 → gold 54 slots). Every tier
 * is a pure container built on the vanilla {@link BaseContainerBlockEntity} rather than on the mod's
 * energy-bound {@link MachineBlockEntity}: no energy buffer, no processing, no machine tick logic, so
 * the vanilla persistence + menu-provider implementation is reused as-is.
 *
 * <p>The tiers differ only in inventory size, display name and the menu class they open — everything
 * else (lid animation, opener counting, sounds, persistence) is identical, so it lives here. A tier
 * subclass is therefore a constructor plus a {@code createMenu} override.
 *
 * <p><b>Lid animation.</b> Each chest renders as a real 3D chest model whose lid lifts on open, like
 * the vanilla chest. This class contributes the moving parts shared with {@code ChestBlockEntity} on
 * 26.2: a {@link ChestLidController} that interpolates the lid angle, a {@link ContainerOpenersCounter}
 * that tracks how many players have the GUI open, and the {@link LidBlockEntity} interface the
 * renderer reads openness from.
 *
 * <p><b>Sounds.</b> All tiers share the iron chest's open/close samples
 * ({@link ModSounds#IRON_CHEST_OPEN} / {@link ModSounds#IRON_CHEST_CLOSE}) — the metallic chest sound
 * is tier-independent, matching how vanilla reuses one wooden-chest sample across every wood variant.
 * No dedicated silver/gold sample exists.
 */
public abstract class AbstractChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
	/** Block event id the server sends when the open count changes; {@link #triggerEvent} reads it. */
	private static final int EVENT_SET_OPEN_COUNT = 1;
	/**
	 * Quieter than the vanilla chest (0.5) because the iron-chest sample is bright/sharp at full
	 * volume.
	 */
	private static final float SOUND_VOLUME = 0.42F;
	/** Lowest pitch of the randomised open/close pitch, so repeated opens don't sound flat. */
	private static final float SOUND_PITCH_BASE = 0.9F;
	/** Width of the random pitch window above {@link #SOUND_PITCH_BASE} (0.9–1.0). */
	private static final float SOUND_PITCH_SPREAD = 0.1F;

	private final int containerSize;
	private final String nameTranslationKey;
	private NonNullList<ItemStack> items;
	private final ChestLidController chestLidController = new ChestLidController();
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, ModSounds.IRON_CHEST_OPEN.get());
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, ModSounds.IRON_CHEST_CLOSE.get());
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
			// Broadcast to the client so ChestLidController lifts the lid (triggerEvent handles id 1).
			level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, newCount);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof AbstractChestMenu menu
					&& menu.getContainer() == AbstractChestBlockEntity.this;
		}
	};

	protected AbstractChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			int containerSize, String nameTranslationKey) {
		super(type, pos, state);
		this.containerSize = containerSize;
		this.nameTranslationKey = nameTranslationKey;
		this.items = NonNullList.withSize(containerSize, ItemStack.EMPTY);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable(nameTranslationKey);
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
		return containerSize;
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

	/**
	 * Client-tick lid interpolation. Each tier exposes its own {@code lidAnimateTick} static entry
	 * point because {@code BlockEntityTicker<T>} pins the concrete type at the block's {@code getTicker};
	 * those one-line entry points all delegate here.
	 */
	protected void tickLid() {
		chestLidController.tickLid();
	}

	/** Play an open/close sound at the chest's centre: BLOCKS channel, slight random pitch. */
	private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		float pitch = level.getRandom().nextFloat() * SOUND_PITCH_SPREAD + SOUND_PITCH_BASE;
		level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, SOUND_VOLUME, pitch);
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
		items = NonNullList.withSize(containerSize, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	public boolean canOpen(Player player) {
		// No lock code on any tier; BaseContainerBlockEntity.canOpen already checks the lock and the
		// "not blocked by a block/cat on top" rule via the chest block, so this stays a validity check.
		return super.canOpen(player);
	}
}

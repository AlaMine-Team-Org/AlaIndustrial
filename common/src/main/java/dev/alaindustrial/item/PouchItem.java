package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Battery Pouch (MOD-052) — the mod's first powered item: a bundle-like carrier with an EU lock.
 * Contents live in {@link ModDataComponents#POUCH_CONTENTS} ({@link PouchContents}, 128 weight),
 * charge in {@link ModDataComponents#POUCH_ENERGY} via {@link ItemEnergy} (2000 EU). While the
 * pouch holds items in a player inventory it drains {@link Config#lvPouchDrainPerSecond} EU/s;
 * at 0 EU both insert and extract are refused (the "lock") until it is recharged in the Battery
 * Box charge slot.
 *
 * <p>The stack-on-stack overrides run on BOTH sides (client prediction): they are deterministic
 * over network-synchronized components and mutate unconditionally, like the vanilla bundle — the
 * menu sync reconciles any divergence. {@link #use} mutates server-side only (loose hand action,
 * no slot/menu prediction to keep consistent).
 */
public class PouchItem extends Item {

	public PouchItem(Properties properties) {
		super(properties);
	}

	// --- extraction: right-click in air -> whole top stack (LIFO, Q-EXT-1) back into inventory ---

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack pouch = player.getItemInHand(hand);
		PouchContents contents = contentsOf(pouch);
		if (contents.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (locked(pouch)) {
			playFail(player);
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel) {
			PouchContents.RemoveResult removed = contents.removeTop();
			setContents(pouch, removed.contents());
			player.getInventory().placeItemBackInInventory(removed.removed());
		}
		playRemove(player);
		return InteractionResult.SUCCESS;
	}

	// --- pouch held on cursor, right-clicked onto a slot ---

	@Override
	public boolean overrideStackedOnOther(ItemStack pouch, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
			return false;
		}
		PouchContents contents = contentsOf(pouch);
		ItemStack slotStack = slot.getItem();
		if (slotStack.isEmpty()) {
			// empty slot: drop the top stack into it
			if (contents.isEmpty()) {
				return false;
			}
			if (locked(pouch)) {
				playFail(player);
				return true;
			}
			PouchContents.RemoveResult removed = contents.removeTop();
			ItemStack rejected = slot.safeInsert(removed.removed());
			PouchContents result = removed.contents();
			if (!rejected.isEmpty()) {
				// slot refused part of it — the freed weight always fits back
				result = result.insert(rejected).contents();
			}
			setContents(pouch, result);
			playRemove(player);
			return true;
		}
		// occupied slot: pull the slot's stack into the pouch
		if (slotStack.getItem() instanceof PouchItem) {
			return false;
		}
		if (locked(pouch)) {
			playFail(player);
			return true;
		}
		int acceptable = Math.min(slotStack.getCount(), contents.room() / PouchContents.weightOf(slotStack));
		if (acceptable <= 0) {
			playFail(player);
			return true;
		}
		ItemStack taken = slot.safeTake(slotStack.getCount(), acceptable, player);
		if (taken.isEmpty()) {
			return false;
		}
		PouchContents.InsertResult inserted = contents.insert(taken);
		setContents(pouch, inserted.contents());
		if (!inserted.leftover().isEmpty()) {
			slot.safeInsert(inserted.leftover());
		}
		playInsert(player);
		return true;
	}

	// --- pouch sitting in a slot, another stack right-clicked onto it ---

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack pouch, ItemStack other, Slot slot, ClickAction action,
			Player player, SlotAccess access) {
		if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
			return false;
		}
		PouchContents contents = contentsOf(pouch);
		if (other.isEmpty()) {
			// empty cursor: take the top stack onto the cursor
			if (contents.isEmpty()) {
				return false;
			}
			if (locked(pouch)) {
				playFail(player);
				return true;
			}
			PouchContents.RemoveResult removed = contents.removeTop();
			if (access.set(removed.removed())) {
				setContents(pouch, removed.contents());
				playRemove(player);
			}
			return true;
		}
		// cursor stack: insert it
		if (other.getItem() instanceof PouchItem) {
			return false;
		}
		if (locked(pouch)) {
			playFail(player);
			return true;
		}
		PouchContents.InsertResult inserted = contents.insert(other);
		if (inserted.inserted() <= 0) {
			playFail(player);
			return true;
		}
		if (access.set(inserted.leftover())) {
			setContents(pouch, inserted.contents());
			playInsert(player);
		}
		return true;
	}

	// --- passive drain: 1 EU/s while carried by a player with items inside (design §3.5) ---

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof Player)) {
			return;
		}
		if (level.getGameTime() % 20 == 0) {
			drainStep(stack, entity);
		}
	}

	/**
	 * One drain step — what {@link #inventoryTick} applies once per second. Drains only while the
	 * pouch is charged AND holds items (an empty or dead pouch never writes the component — no
	 * pointless stack resyncs), and never for a creative or spectating {@code owner}, whose EU is free
	 * (MOD-081 — the rule lives in {@link ItemEnergy#spend}). Separated from the game-time gate so
	 * gametests can drive it deterministically. Returns whether EU was drained.
	 */
	public static boolean drainStep(ItemStack stack, @Nullable Entity owner) {
		if (ItemEnergy.get(stack) <= 0 || contentsOf(stack).isEmpty() || ItemEnergy.free(owner)) {
			return false;
		}
		ItemEnergy.spend(stack, Config.lvPouchDrainPerSecond, owner);
		return true;
	}

	// --- item bar shows the EU charge in the LV tier colour (fill is in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		long capacity = ItemEnergy.capacity(stack);
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH, MAX_BAR_WIDTH * ItemEnergy.get(stack) / capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyTier.LV.color();
	}

	/** Bundle-style visual tooltip (grid + weight bar) whenever the pouch holds anything. */
	@Override
	public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
		PouchContents contents = contentsOf(stack);
		return contents.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(new PouchTooltip(contents));
	}

	// --- helpers ---

	/** Contents component with the absent-means-empty default (loaders can't preset components). */
	public static PouchContents contentsOf(ItemStack stack) {
		PouchContents contents = stack.get(ModDataComponents.POUCH_CONTENTS.get());
		return contents == null ? PouchContents.EMPTY : contents;
	}

	/** Write contents back; empty contents remove the component (drained == crafted-fresh). */
	public static void setContents(ItemStack stack, PouchContents contents) {
		if (contents.isEmpty()) {
			stack.remove(ModDataComponents.POUCH_CONTENTS.get());
		} else {
			stack.set(ModDataComponents.POUCH_CONTENTS.get(), contents);
		}
	}

	/** The lock: every insert/extract requires charge (design §3.3/§3.4). */
	private static boolean locked(ItemStack pouch) {
		return ItemEnergy.get(pouch) <= 0;
	}

	private static void playInsert(Player player) {
		player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
	}

	private static void playRemove(Player player) {
		player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
	}

	private static void playFail(Player player) {
		player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
	}
}

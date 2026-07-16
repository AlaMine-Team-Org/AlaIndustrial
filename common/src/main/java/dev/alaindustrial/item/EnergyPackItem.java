package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Energy Pack (MOD-065) — a worn LV energy buffer: {@link Config#energyPackBuffer} EU carried in the
 * chest slot, charged in the Battery Box charge slot and handed out to the powered items the player
 * carries — the mod's own (the Battery Pouch, MOD-052; the Electric Drill, MOD-079) and, since MOD-084,
 * any other mod's item that exposes the loader's item energy capability. Unlike the pouch it has no
 * passive drain: EU only leaves the pack when something actually takes it.
 *
 * <p>It is <b>not</b> armor. Built without {@code ArmorMaterial}/{@code Properties.humanoidArmor},
 * which would wire in durability ({@code MAX_DAMAGE} — the pack would break), enchantability and a
 * repair tag; the equipment properties come from the {@code EQUIPPABLE} component alone
 * ({@code Equippable.builder(CHEST).setDamageOnHurt(false)}, see the loader registries), so the pack
 * is unbreakable and cannot be enchanted. It grants a token 2 armor points via an attribute modifier
 * — occupying the chestplate slot has to cost the player something, and that trade-off is the point.
 *
 * <p>Charge lives in the shared {@code pouch_energy} component through {@link ItemEnergy} — the same
 * buffer helper every powered item uses; the pack registers its own capacity there.
 */
public class EnergyPackItem extends Item {

	/**
	 * Visual asset key for the worn pack — a mod-namespaced {@link ResourceKey} into the
	 * {@code minecraft:equipment_asset} registry, resolved by the client to
	 * {@code assets/alaindustrial/equipment/energy_pack.json}. Built by hand, not via
	 * {@code EquipmentAssets.createId(...)}, which hardcodes the {@code minecraft} namespace
	 * (see the note in {@link ModArmorMaterials}).
	 */
	public static final ResourceKey<EquipmentAsset> ENERGY_PACK_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("energy_pack"));

	/**
	 * The drained look — a red indicator light and pale cells instead of the charged gold ones. The
	 * worn model is picked by whichever asset the stack's {@code EQUIPPABLE} component points at, so
	 * swapping the key here is what makes a dead pack visibly dead on the player's back.
	 */
	public static final ResourceKey<EquipmentAsset> ENERGY_PACK_OFF_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("energy_pack_off"));

	/** Armor points the pack grants while worn — a token amount, well below leather. */
	public static final double ARMOR_POINTS = 2.0;

	/**
	 * Slot index for a charge target that has no vanilla {@link Inventory} index: the stack on the cursor
	 * and the 2×2 crafting grid (MOD-082). Those two live in menu containers, not in the inventory list.
	 *
	 * <p>Consequence for MOD-084: the mod's own items are charged there as usual (they go through
	 * {@link ItemEnergy}, which needs no slot), but <b>another mod's</b> item is not — both loaders' item
	 * energy capabilities are looked up per inventory slot ({@code ContainerItemContext.ofPlayerSlot} /
	 * {@code ItemAccess.forPlayerSlot}), and there is no index to look up here. A foreign tool held on the
	 * cursor for a moment therefore waits until it is put back in a slot, which is the same second or two
	 * it takes to drop it. Cursor-scoped contexts do exist on both loaders and could lift this later; it
	 * is not worth a menu-aware code path for the one tick an item spends on the cursor.
	 */
	private static final int NO_SLOT = -1;

	public EnergyPackItem(Properties properties) {
		super(properties);
	}

	// --- worn look follows the charge (MOD-065) ---

	/**
	 * Point the stack's {@code EQUIPPABLE} at the charged or the drained asset, following its EU.
	 * Called from {@link ItemEnergy#set} — the single place a pack's charge ever changes — so the
	 * worn model can never drift out of sync with the number in the tooltip.
	 *
	 * <p>Both states are written explicitly — a drained pack does NOT simply drop the component.
	 * {@code ItemStack.remove} on a component the item declares in its properties does not "fall back
	 * to the default", it records a removal on top of it: the pack would end up with no
	 * {@code EQUIPPABLE} at all and stop being wearable. (A gametest caught exactly that.) The write
	 * is skipped when the asset is already the right one, so a pack charging or draining across many
	 * steps only touches this component on the two ticks its state actually flips — and the check
	 * itself only reads the current asset key, it does not build a throw-away {@link Equippable}.
	 */
	public static void refreshWornAsset(ItemStack stack, long eu) {
		ResourceKey<EquipmentAsset> wanted = eu > 0 ? ENERGY_PACK_ASSET : ENERGY_PACK_OFF_ASSET;
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		if (current == null || current.assetId().orElse(null) != wanted) {
			stack.set(DataComponents.EQUIPPABLE, equippable(wanted));
		}
	}

	/** The pack's equippable definition for one of the two visual assets. */
	private static Equippable equippable(ResourceKey<EquipmentAsset> asset) {
		return Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
				.setAsset(asset)
				.setDamageOnHurt(false)
				.build();
	}

	/**
	 * The pack's equipment properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — that is the only difference).
	 *
	 * <p>Deliberately NOT {@code humanoidArmor(ArmorMaterial, ArmorType)}: that helper also wires
	 * durability, enchantability and a repair tag, which would make the pack a breakable, enchantable
	 * chestplate. Here {@code EQUIPPABLE} alone makes it wearable, {@code setDamageOnHurt(false)} keeps
	 * incoming damage away from it, and a lone {@link net.minecraft.world.entity.ai.attributes.Attributes#ARMOR}
	 * modifier grants the token protection. No {@code MAX_DAMAGE} component means it never breaks; no
	 * {@code ENCHANTABLE} component means the enchanting table and the anvil ignore it.
	 *
	 * <p>The default asset is the <b>drained</b> one: a pack comes out of the crafting grid empty, and
	 * {@link #refreshWornAsset} swaps in the charged asset the moment it holds EU.
	 */
	public static Properties equipmentProperties(Properties properties) {
		return properties
				.stacksTo(1)
				.component(DataComponents.EQUIPPABLE, equippable(ENERGY_PACK_OFF_ASSET))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ARMOR,
								new AttributeModifier(Industrialization.id("energy_pack_armor"), ARMOR_POINTS,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.CHEST)
						.build());
	}

	// --- transfer: worn pack tops up the powered items the player carries (design §3) ---

	/**
	 * Runs only for a worn pack ({@code slot == CHEST}; the equipment tick passes the slot the stack
	 * sits in). Carried in a bag or held in hand the pack does nothing — it is a device you wear.
	 *
	 * <p>Transfer runs once a second in a batch of {@code energyPackOutputRate × 20} EU rather than a
	 * per-tick trickle: every EU write touches a data component and resyncs the stack to the client,
	 * and a batch does the same work with 1/20th of the churn (the pouch's passive drain, the only
	 * other item-energy tick in the mod, is paced the same way).
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot != EquipmentSlot.CHEST || !(entity instanceof Player player)) {
			return;
		}
		// Self-heal the worn look. refreshWornAsset normally runs from ItemEnergy.set, but a pack can
		// arrive already charged without ever passing through it — /give with a pouch_energy component,
		// a loot table, a datapack recipe — and would then be worn with the dead texture forever (a
		// charged pack that never has anything to charge writes nothing, so nothing would fix it). This
		// is a key comparison per tick; it writes only when the asset actually disagrees with the EU.
		refreshWornAsset(stack, ItemEnergy.get(stack));
		if (level.getGameTime() % 20 == 0) {
			chargeStep(stack, player);
		}
	}

	/**
	 * One transfer step — what {@link #inventoryTick} applies once per second. Hands out up to
	 * {@code energyPackOutputRate × 20} EU across the player's carried consumers, in slot order
	 * (hotbar first, then the main inventory, then the offhand, then the two places a stack can sit
	 * while the inventory screen is open — the cursor and the 2×2 crafting grid, MOD-082); each
	 * consumer takes what it has room for and the rest of the budget moves on. No round-robin: with a
	 * whole second's worth of EU per step every consumer fills within a few steps anyway, and
	 * remembering "who was next" would mean persisting a cursor on the stack.
	 *
	 * <p>Separated from the game-time gate so gametests can drive it deterministically. Returns the EU
	 * actually moved (0 = nothing was written, and the components stay untouched).
	 */
	public static long chargeStep(ItemStack pack, Player player) {
		long budget = Math.min((long) Config.energyPackOutputRate * 20L, ItemEnergy.get(pack));
		if (budget <= 0) {
			return 0L;
		}
		long moved = 0L;
		// Main inventory (hotbar 0..8 first, so the item in hand is served first) — the offhand is NOT
		// part of this list in 26.2 (it lives in the entity's equipment), hence the separate pass below.
		// The list index doubles as the vanilla Inventory slot index, which is what the cross-mod bridge
		// needs to address the slot (both loaders' item capabilities are slot-scoped, not stack-scoped).
		NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < items.size() && budget > 0; i++) {
			long sent = give(player, i, items.get(i), budget);
			budget -= sent;
			moved += sent;
		}
		if (budget > 0) {
			long sent = give(player, Inventory.SLOT_OFFHAND, player.getItemBySlot(EquipmentSlot.OFFHAND), budget);
			budget -= sent;
			moved += sent;
		}
		if (budget > 0) {
			long sent = chargeOpenScreen(player, budget);
			budget -= sent;
			moved += sent;
		}
		// The pack pays once for the whole step, not once per consumer: each write to its charge is a
		// component write and a stack resync, and with several pouches that would be several of them
		// for one logical transfer. Creative keeps the charge — the debit is dropped inside
		// ItemEnergy.spend, the consumers are filled either way (MOD-081).
		if (moved > 0) {
			ItemEnergy.spend(pack, moved, player);
		}
		return moved;
	}

	/**
	 * The two spots a powered item can occupy while the player has a screen open, neither of which is
	 * part of the inventory list above (MOD-082): the stack held on the cursor, and the inventory's own
	 * 2×2 crafting grid. Both are physically on the player, so a worn pack tops them up like any other
	 * carried item; without this a pouch would visibly stop charging the moment it was picked up.
	 *
	 * <p>Only these two — <b>not</b> the slots of whatever container is open. Those belong to a chest or
	 * a machine in the world, and a pack must not reach into them.
	 *
	 * <p>Both write through to the client on their own: the cursor is resynced by
	 * {@code AbstractContainerMenu.broadcastChanges → synchronizeCarriedToRemote} (a component write
	 * makes {@code ItemStack.matches} fail, which is exactly what triggers the resend), and the crafting
	 * grid can only hold items while the inventory screen itself is open — closing it runs
	 * {@code InventoryMenu.removed → clearContainer}, which hands the items back — so its slots are
	 * being broadcast whenever there is anything in them to charge.
	 */
	private static long chargeOpenScreen(Player player, long budget) {
		long moved = give(player, NO_SLOT, player.containerMenu.getCarried(), budget);
		budget -= moved;
		CraftingContainer grid = player.inventoryMenu.getCraftSlots();
		for (int i = 0; i < grid.getContainerSize() && budget > 0; i++) {
			long sent = give(player, NO_SLOT, grid.getItem(i), budget);
			budget -= sent;
			moved += sent;
		}
		return moved;
	}

	/**
	 * Fill the stack in one inventory slot with up to {@code budget} EU and return how much it took (the
	 * pack is debited for the total by the caller). Nothing is written when the transfer would be 0 — a
	 * full consumer, or a non-powered item, must not cost a component write and a stack resync.
	 *
	 * <p>Items in {@link ModTags.Items#NO_AUTO_CHARGE} are skipped, which is how packs stay out of each
	 * other's way: a charger that charges chargers ping-pongs energy between two of them and drains the
	 * wearer for nothing (a bug other mods have shipped and fixed). Before MOD-084 that rule was an
	 * {@code instanceof EnergyPackItem} check; a tag says the same thing about foreign chargers, which
	 * {@code instanceof} cannot see.
	 *
	 * <p>The mod's own powered items go through {@link ItemEnergy} directly, and only everything else
	 * falls through to {@link ItemEnergyBridge} — a mod item is never worth the cost of opening a
	 * capability lookup and a transaction, and this keeps their behaviour byte-for-byte what it was.
	 *
	 * <p>{@code slot} is the vanilla {@link Inventory} index of the target, or {@link #NO_SLOT} when it
	 * has none. Both loaders' item energy capabilities are scoped to a <i>slot</i>, not to a stack, so a
	 * target without an index can only be served through {@link ItemEnergy} — see {@link #NO_SLOT}.
	 */
	private static long give(Player player, int slot, ItemStack target, long budget) {
		if (target.isEmpty() || target.is(ModTags.Items.NO_AUTO_CHARGE)) {
			return 0L;
		}
		if (ItemEnergy.capacity(target) > 0) {
			long move = Math.min(ItemEnergy.room(target), budget);
			if (move <= 0) {
				return 0L;
			}
			ItemEnergy.add(target, move);
			return move;
		}
		if (slot == NO_SLOT) {
			return 0L;
		}
		// Not one of ours — offer it to the loader's item energy API (a foreign FE tool, battery, ...).
		return ItemEnergyBridge.get().chargeSlot(player, slot, budget);
	}

	// --- item bar shows the EU charge in the LV tier colour (the numbers are in the tooltip) ---

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
}

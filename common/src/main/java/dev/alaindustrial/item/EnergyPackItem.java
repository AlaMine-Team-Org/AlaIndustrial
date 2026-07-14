package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.EnergyTier;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Energy Pack (MOD-065) — a worn LV energy buffer: {@link Config#energyPackBuffer} EU carried in the
 * chest slot, charged in the Battery Box charge slot and handed out to the powered items the player
 * carries (today the Battery Pouch, MOD-052). Unlike the pouch it has no passive drain: EU only
 * leaves the pack when something actually takes it.
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
	 * (hotbar first, then the main inventory, then the offhand); each consumer takes what it has room
	 * for and the rest of the budget moves on. No round-robin: with a whole second's worth of EU per
	 * step every consumer fills within a few steps anyway, and remembering "who was next" would mean
	 * persisting a cursor on the stack.
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
		NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < items.size() && budget > 0; i++) {
			long sent = give(items.get(i), budget);
			budget -= sent;
			moved += sent;
		}
		if (budget > 0) {
			moved += give(player.getItemBySlot(EquipmentSlot.OFFHAND), budget);
		}
		// The pack pays once for the whole step, not once per consumer: each write to its charge is a
		// component write and a stack resync, and with several pouches that would be several of them
		// for one logical transfer.
		if (moved > 0) {
			ItemEnergy.add(pack, -moved);
		}
		return moved;
	}

	/**
	 * Fill one carried stack with up to {@code budget} EU and return how much it took (the pack is
	 * debited for the total by the caller). Nothing is written when the transfer would be 0 — a full
	 * consumer, or a non-powered item, must not cost a component write and a stack resync.
	 *
	 * <p>Other packs are excluded on purpose: a charger that charges chargers ping-pongs energy between
	 * two of them and drains the wearer for nothing (a bug other mods have shipped and fixed).
	 */
	private static long give(ItemStack target, long budget) {
		if (target.isEmpty() || target.getItem() instanceof EnergyPackItem) {
			return 0L;
		}
		long move = Math.min(ItemEnergy.room(target), budget);
		if (move <= 0) {
			return 0L;
		}
		ItemEnergy.add(target, move);
		return move;
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

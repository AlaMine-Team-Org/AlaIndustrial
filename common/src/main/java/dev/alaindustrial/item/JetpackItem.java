package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jetpack (MOD-148) — a worn EU flight device: {@link Config#jetpackBuffer} EU in the chest slot,
 * charged like the rest of the powered-item family (Battery Box charge slot, worn Energy Pack,
 * foreign chargers via MOD-084). Holding jump while airborne burns {@link Config#jetpackEuPerTick}
 * EU/tick and lifts the player; with no charge left (or above {@link Config#jetpackMaxY}) the same
 * held jump turns into a powerless glide — a damped fall that never deals fall damage. Releasing
 * jump mid-air is a normal, damaging fall: the safety is the engine, not the garment.
 *
 * <p>The work is split by authority. Player motion is client-authoritative in 26.2, so the actual
 * thrust/glide velocity change happens on the client ({@code dev.alaindustrial.client.JetpackFlight});
 * the server sees the same held-jump state through the vanilla input sync
 * ({@code ServerPlayer.getLastClientInput()}) and independently burns the EU, resets the fall
 * distance and emits the exhaust — no custom packet, both sides gate on the same
 * {@link #isPowered}/{@code jump}/airborne conditions.
 *
 * <p>Like the Energy Pack it is <b>not</b> armor: no {@code ArmorMaterial}, no durability, no
 * enchantments — {@code EQUIPPABLE} alone makes it wearable and a lone attribute modifier grants
 * {@link #ARMOR_POINTS} (a point under iron: it is a flight device that also shields, not a
 * chestplate that also flies). Charge lives in the shared {@code pouch_energy} component through
 * {@link ItemEnergy}.
 */
public class JetpackItem extends Item {

	/** Worn look while the jetpack holds charge — see {@link EnergyPackItem#ENERGY_PACK_ASSET} for
	 * why the key is built by hand. */
	public static final ResourceKey<EquipmentAsset> JETPACK_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("jetpack"));

	/** The drained look — dark nozzles, red indicator. Swapped by {@link #refreshWornAsset} from the
	 * single place charge changes ({@link ItemEnergy#set}), like the Energy Pack's. */
	public static final ResourceKey<EquipmentAsset> JETPACK_OFF_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("jetpack_off"));

	/** Armor points while worn — one under an iron chestplate (6): "iron or slightly weaker". */
	public static final double ARMOR_POINTS = 5.0;

	/**
	 * Players whose engine has thrusted at least once during the CURRENT airborne stretch
	 * (server side; cleared the tick they touch ground). The powerless glide is a safety net for an
	 * engine that cut out mid-flight — a jetpack that never fired this flight gives no glide and no
	 * fall protection, so wearing an empty jetpack falls exactly like wearing nothing (playtest
	 * feedback: an empty pack must not "levitate"). Transient by design: not persisted, a relog
	 * mid-air simply loses the protection.
	 */
	private static final Set<UUID> THRUSTED_THIS_FLIGHT = ConcurrentHashMap.newKeySet();

	public JetpackItem(Properties properties) {
		super(properties);
	}

	// --- worn look follows the charge (same contract as the Energy Pack) ---

	/**
	 * Point the stack's {@code EQUIPPABLE} at the lit or the drained asset, following its EU. Both
	 * states are written explicitly — removing the component would make the jetpack unwearable, not
	 * "default" (see {@link EnergyPackItem#refreshWornAsset}). Writes only on an actual flip.
	 */
	public static void refreshWornAsset(ItemStack stack, long eu) {
		ResourceKey<EquipmentAsset> wanted = eu > 0 ? JETPACK_ASSET : JETPACK_OFF_ASSET;
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		if (current == null || current.assetId().orElse(null) != wanted) {
			stack.set(DataComponents.EQUIPPABLE, equippable(wanted));
		}
	}

	private static Equippable equippable(ResourceKey<EquipmentAsset> asset) {
		return Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
				.setAsset(asset)
				.setDamageOnHurt(false)
				.build();
	}

	/**
	 * The jetpack's equipment properties, applied identically by both loaders. Deliberately not
	 * {@code humanoidArmor(...)} — same reasoning as {@link EnergyPackItem#equipmentProperties}: no
	 * durability (it must not break mid-flight), no enchantability; the default asset is the drained
	 * one because a fresh jetpack comes out of the grid empty.
	 */
	public static Properties equipmentProperties(Properties properties) {
		return properties
				.stacksTo(1)
				.component(DataComponents.EQUIPPABLE, equippable(JETPACK_OFF_ASSET))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ARMOR,
								new AttributeModifier(Industrialization.id("jetpack_armor"), ARMOR_POINTS,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.CHEST)
						.build());
	}

	// --- server side: EU burn, fall-damage safety, exhaust ---

	/**
	 * Whether the engine may thrust right now: any charge at all, and under the altitude ceiling.
	 * Deliberately {@code > 0}, not {@code >= jetpackEuPerTick}: the last tick of a nearly-empty
	 * jetpack burns whatever remains (the spend clamps at 0), so the buffer always drains to a clean
	 * zero — a 15–20 EU tail would otherwise sit forever below the per-tick cost, showing 1% that can
	 * never be used (playtest feedback). The client checks the same predicate before applying lift
	 * (the component is network-synchronized), so the two sides flip together give or take one sync.
	 */
	public static boolean isPowered(ItemStack stack, Player player) {
		return ItemEnergy.get(stack) > 0 && player.getY() < Config.jetpackMaxY;
	}

	/**
	 * Runs only worn ({@code slot == CHEST}); in 26.2 the equipment tick is server-only. The held
	 * jump is read from the vanilla input sync — the one movement key the server already knows.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot != EquipmentSlot.CHEST || !(entity instanceof ServerPlayer player)) {
			return;
		}
		// Self-heal the worn look (a /give with a preset charge never passes through ItemEnergy.set).
		refreshWornAsset(stack, ItemEnergy.get(stack));
		serverFlightTick(stack, level, player, player.getLastClientInput().jump());
	}

	/**
	 * One server tick of the flight logic, with the input state passed in so gametests can drive it
	 * deterministically. On the ground the airborne-thrust session ends (and nothing else happens —
	 * a held jump there is a vanilla jump). While jump is held airborne: a powered engine burns up to
	 * {@link Config#jetpackEuPerTick} EU (the spend clamps, so the last tick takes the remainder and
	 * the buffer hits a clean 0; creative burns nothing — {@link ItemEnergy#spend}) and the exhaust
	 * plays; the fall distance is zeroed every such tick. With no thrust available the held jump only
	 * glides — zeroed fall included — if the engine actually fired during THIS airborne stretch: the
	 * glide is the safety net for a mid-flight cutout, not a free feather-fall for an empty backpack.
	 * Releasing jump stops the reset and the fall accumulates from that point — a plain, damaging
	 * fall by the player's own choice.
	 */
	public static void serverFlightTick(ItemStack stack, ServerLevel level, Player player, boolean jumpHeld) {
		if (player.onGround()) {
			THRUSTED_THIS_FLIGHT.remove(player.getUUID());
			JetpackLight.extinguish(level, player);
			return;
		}
		if (!jumpHeld) {
			// Engine idle in the air (no input) — drop the flight glow; the glide has its own branch.
			JetpackLight.extinguish(level, player);
			return;
		}
		if (isPowered(stack, player)) {
			ItemEnergy.spend(stack, Math.min(Config.jetpackEuPerTick, ItemEnergy.get(stack)), player);
			THRUSTED_THIS_FLIGHT.add(player.getUUID());
			player.resetFallDistance();
			exhaust(level, player);
			// Torch-like glow follows the engine — only while it actually thrusts (see JetpackLight).
			// Stamp with the server tick counter (never null, unlike a level's game time via overworld()),
			// the same clock the per-tick sweep reads, so a live light is never swept mid-flight.
			JetpackLight.ignite(level, player, level.getServer().getTickCount());
		} else if (THRUSTED_THIS_FLIGHT.contains(player.getUUID())) {
			// Glide after a mid-flight cutout: no thrust, no cost, no glow, but no fall damage either.
			player.resetFallDistance();
			JetpackLight.extinguish(level, player);
		} else {
			// Empty jetpack that never fired this flight: no thrust, no glow, a normal fall.
			JetpackLight.extinguish(level, player);
		}
	}

	/**
	 * Exhaust under the player's back: a puff of smoke every thrust tick and a soft hiss every eight
	 * — enough to read as an engine without a dedicated looping sound (that polish is future work).
	 */
	private static void exhaust(ServerLevel level, Player player) {
		level.sendParticles(ParticleTypes.SMOKE,
				player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
		level.sendParticles(ParticleTypes.SMALL_FLAME,
				player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		if (level.getGameTime() % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.05f, 1.8f);
		}
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

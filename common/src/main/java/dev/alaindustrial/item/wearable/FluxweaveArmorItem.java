package dev.alaindustrial.item.wearable;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.material.ModArmorMaterials;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Fluxweave armour (MOD-127) — the mod's first full EU armour set: one class carrying its
 * {@link ArmorType}, registered four times, rather than four near-identical classes.
 *
 * <p><b>Two layers, not two competing designs.</b> The base properties come from
 * {@code humanoidArmor(FLUXWEAVE, type)} exactly like tempered iron, so the set has ordinary defense,
 * toughness, enchantability and a repair tag. On top of that, this class rewrites two components on
 * the stack as its charge changes — the same trick {@link EnergyPackItem#refreshWornAsset} plays with
 * {@code EQUIPPABLE}:
 *
 * <ul>
 * <li>{@code EQUIPPABLE} — swaps the worn texture between the lit and the dead look, and pins
 * {@code setDamageOnHurt(false)} in both, so durability is never spent and the suit cannot break.</li>
 * <li>{@code ATTRIBUTE_MODIFIERS} — switches the active bonuses on and off with the charge.</li>
 * </ul>
 *
 * <p><b>Why attributes and not potion effects.</b> Every bonus here is an attribute, so vanilla applies
 * and removes them itself when the piece is equipped or its components change: no particles, no HUD
 * icons, no clash with real potions, and no per-tick bonus code at all. The chain that makes this work
 * is {@code LivingEntity.tick() → detectEquipmentUpdates() → collectEquipmentChanges()}, which runs
 * server-side every tick and compares stacks by component <em>value</em>.
 *
 * <p><b>Three rules that bite if broken</b> (all verified against the 26.2 bytecode):
 * <ol>
 * <li>The rewritten component must <b>include the material's own modifiers</b>. {@code humanoidArmor}
 * put ARMOR, ARMOR_TOUGHNESS and KNOCKBACK_RESISTANCE in there; replacing the component wholesale
 * would strip a drained suit of its base protection. Hence {@link #baseAttributes} starts from
 * {@code material.createAttributes(type)} and only ever <em>adds</em>.</li>
 * <li>Every modifier needs a <b>unique {@link Identifier}</b>. {@code AttributeInstance} keys them in
 * one map with no per-slot split, so two pieces sharing an id would overwrite each other.</li>
 * <li>Write <b>only on an actual change</b>. Each detected change removes and reapplies every modifier,
 * re-runs enchantment location effects, sends a {@code ClientboundSetEquipmentPacket} to every tracking
 * player, and on NeoForge posts {@code LivingEquipmentChangeEvent} to other mods. Per-tick rewriting
 * would spam all of that, so {@link #refreshWorn} compares before it writes.</li>
 * </ol>
 */
public class FluxweaveArmorItem extends Item {

	/** Charged look — the conductor tracks glow gold. Declared by the material as its default asset. */
	public static final ResourceKey<EquipmentAsset> FLUXWEAVE_ASSET = ModArmorMaterials.FLUXWEAVE_ASSET;

	/** Drained look — the same weave gone dull. */
	public static final ResourceKey<EquipmentAsset> FLUXWEAVE_OFF_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("fluxweave_off"));

	private final ArmorType type;

	public FluxweaveArmorItem(Properties properties, ArmorType type) {
		super(properties);
		this.type = type;
	}

	public ArmorType armorType() {
		return type;
	}

	/** Base properties: ordinary armour first, then the charge-driven layer on top. */
	public static Properties equipmentProperties(Properties properties, ArmorType type) {
		return properties
				.humanoidArmor(ModArmorMaterials.FLUXWEAVE, type)
				// Start life drained: a freshly crafted suit holds no EU, and refreshWorn swaps both
				// components across the moment it does.
				.component(DataComponents.EQUIPPABLE, equippable(type, FLUXWEAVE_OFF_ASSET))
				.attributes(attributesFor(type, false, false));
	}

	// --- the charge-driven layer -------------------------------------------------------------------

	/**
	 * Point the stack's {@code EQUIPPABLE} and {@code ATTRIBUTE_MODIFIERS} at the charged or the drained
	 * set, following its EU. Called from {@link ItemEnergy#set} — the single place a powered item's
	 * charge ever changes — so the worn look and the active bonuses can never drift from the number in
	 * the tooltip.
	 *
	 * <p>Both states are written explicitly; a drained piece does <b>not</b> simply drop the components.
	 * {@code ItemStack.remove} on a component the item declares in its properties does not fall back to
	 * the default, it records a removal on top of it — the piece would end up unwearable and unarmoured.
	 * (A gametest caught exactly that on the Energy Pack.)
	 *
	 * <p>Writes are skipped when the components already say the right thing, so a suit charging or
	 * draining across many steps only touches them on the two ticks its state actually flips.
	 */
	public static void refreshWorn(ItemStack stack, long eu) {
		if (!(stack.getItem() instanceof FluxweaveArmorItem piece)) {
			return;
		}
		boolean charged = eu > 0;
		ArmorType type = piece.armorType();

		ResourceKey<EquipmentAsset> wanted = charged ? FLUXWEAVE_ASSET : FLUXWEAVE_OFF_ASSET;
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		if (current == null || current.assetId().orElse(null) != wanted) {
			stack.set(DataComponents.EQUIPPABLE, equippable(type, wanted));
		}

		ItemAttributeModifiers wantedAttributes = attributesFor(type, charged, isStepAssistEnabled(stack));
		if (!wantedAttributes.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, wantedAttributes);
		}
	}

	/** The piece's equippable definition for one of the two visual assets. */
	private static Equippable equippable(ArmorType type, ResourceKey<EquipmentAsset> asset) {
		return Equippable.builder(type.getSlot())
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(asset)
				// Incoming damage never touches the suit: wear is paid in EU, not durability.
				.setDamageOnHurt(false)
				.build();
	}

	/**
	 * The material's own modifiers — ARMOR, ARMOR_TOUGHNESS, KNOCKBACK_RESISTANCE for this slot. Every
	 * variant is built on top of this, never instead of it, so a flat suit keeps its base protection.
	 */
	private static ItemAttributeModifiers baseAttributes(ArmorType type) {
		return ModArmorMaterials.FLUXWEAVE.createAttributes(type);
	}

	/** Full modifier set for a slot in the given charge state. */
	private static ItemAttributeModifiers attributesFor(ArmorType type, boolean charged,
			boolean stepAssist) {
		ItemAttributeModifiers attributes = baseAttributes(type);
		if (!charged) {
			return attributes;
		}
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
		return switch (type) {
			// Helmet: the water kit. OXYGEN_BONUS is Respiration's own mechanic — a chance per tick not
			// to spend air, NOT unlimited breathing — and swim efficiency makes the dive practical.
			case HELMET -> attributes
					.withModifierAdded(Attributes.OXYGEN_BONUS,
							modifier("fluxweave_oxygen", Config.fluxweaveOxygenBonus,
									AttributeModifier.Operation.ADD_VALUE), group)
					.withModifierAdded(Attributes.WATER_MOVEMENT_EFFICIENCY,
							modifier("fluxweave_swim", Config.fluxweaveSwimEfficiency / 100.0,
									AttributeModifier.Operation.ADD_VALUE), group);
			// Chestplate: soak the big hits. There is no "damage reduction" attribute in 26.2 — toughness
			// is what actually blunts heavy blows, and knockback resistance keeps the wearer planted.
			case CHESTPLATE -> attributes
					.withModifierAdded(Attributes.ARMOR_TOUGHNESS,
							modifier("fluxweave_toughness", Config.fluxweaveChargedToughness,
									AttributeModifier.Operation.ADD_VALUE), group)
					.withModifierAdded(Attributes.KNOCKBACK_RESISTANCE,
							modifier("fluxweave_knockback", Config.fluxweaveKnockbackResistance / 100.0,
									AttributeModifier.Operation.ADD_VALUE), group);
			// Leggings: run speed is passive; the step assist is opt-in and rides the stack's own flag.
			case LEGGINGS -> {
				ItemAttributeModifiers legs = attributes
						.withModifierAdded(Attributes.MOVEMENT_SPEED,
								modifier("fluxweave_speed", Config.fluxweaveRunSpeedPercent / 100.0,
										AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), group);
				yield stepAssist
						? legs.withModifierAdded(Attributes.STEP_HEIGHT,
								modifier("fluxweave_step", Config.fluxweaveStepHeightBonus / 100.0,
										AttributeModifier.Operation.ADD_VALUE), group)
						: legs;
			}
			// Boots: soften the landing, never cancel it. A multiplier of -0.5 halves the damage; the
			// clamp keeps a config edit from reaching zero, which would make falling free.
			case BOOTS -> attributes
					.withModifierAdded(Attributes.FALL_DAMAGE_MULTIPLIER,
							modifier("fluxweave_fall", -fallReductionFraction(),
									AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), group);
			default -> attributes;
		};
	}

	/**
	 * How much of the fall damage the boots absorb, as a fraction. Clamped to at most 0.9: cancelling
	 * fall damage outright trivialises a core survival rule, so no config value may reach 1.0.
	 */
	public static double fallReductionFraction() {
		int percent = Math.max(0, Math.min(90, Config.fluxweaveFallDamageReductionPercent));
		return percent / 100.0;
	}

	private static AttributeModifier modifier(String id, double amount, AttributeModifier.Operation op) {
		return new AttributeModifier(Industrialization.id(id), amount, op);
	}

	// --- upkeep -------------------------------------------------------------------------------------

	/**
	 * Charged pieces burn a trickle of EU while worn. Batched to once a second rather than per tick:
	 * every EU write touches a data component and resyncs the stack to the client, and a batch does the
	 * same work with 1/20th of the churn (the Energy Pack and the pouch are paced the same way).
	 *
	 * <p>Also self-heals the two charge-driven components. {@link #refreshWorn} normally runs from
	 * {@link ItemEnergy#set}, but a piece can arrive already charged without ever passing through it —
	 * {@code /give} with a {@code pouch_energy} component, a loot table, a datapack recipe — and would
	 * then be worn lit but with no bonuses, forever.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		// EquipmentSlot is null for a piece merely sitting in the inventory (Inventory.tick passes null
		// for every slot but the selected hotbar one), so this guard is a null check as much as a
		// worn check — dereferencing slot first would NPE every tick on armour in a backpack.
		if (slot == null || slot != armorType().getSlot()) {
			return;
		}
		long charge = ItemEnergy.get(stack);
		refreshWorn(stack, charge);
		if (charge <= 0 || level.getGameTime() % 20 != 0) {
			return;
		}
		secondStep(stack, entity);
	}

	/**
	 * One second's worth of work for a worn, charged piece: pay the upkeep, and — on the helmet only —
	 * run the set bonus.
	 *
	 * <p>Separated from the game-time gate in {@link #inventoryTick} so gametests can drive it
	 * deterministically, the same reason {@link EnergyPackItem#chargeStep} is its own method. A test
	 * looping over {@code inventoryTick} cannot work here: the game clock does not advance inside one
	 * server tick, so the {@code % 20} gate would fire on every iteration or on none.
	 */
	public static void secondStep(ItemStack stack, Entity wearer) {
		if (!(stack.getItem() instanceof FluxweaveArmorItem piece)) {
			return;
		}
		ItemEnergy.spend(stack, Config.fluxweaveUpkeepEuPerSecond, wearer);
		if (piece.armorType() == ArmorType.HELMET
				&& wearer instanceof net.minecraft.world.entity.LivingEntity living) {
			trySetBonus(stack, living);
		}
	}

	// --- step assist: an opt-in flag carried by the trousers themselves ---

	/**
	 * Whether the step assist is switched on for this piece. <b>Absent means off</b>, the inverse of the
	 * magnet's flag (MOD-132): auto-stepping changes how movement feels, so it waits to be asked for.
	 *
	 * <p>The flag lives on the stack, not on the player. Persistence is then free, it travels with the
	 * trousers between inventories and worlds, and neither loader needs a per-player storage mechanism.
	 */
	public static boolean isStepAssistEnabled(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModDataComponents.STEP_ASSIST_ENABLED.get()));
	}

	/**
	 * Flip the flag and rebuild the attribute set so the change takes effect on the same tick. Storing
	 * the "off" state as an absent component keeps a switched-off pair component-identical to a freshly
	 * crafted one, so the two stack together instead of looking subtly different.
	 */
	public static void setStepAssistEnabled(ItemStack stack, boolean enabled) {
		if (enabled) {
			stack.set(ModDataComponents.STEP_ASSIST_ENABLED.get(), true);
		} else {
			stack.remove(ModDataComponents.STEP_ASSIST_ENABLED.get());
		}
		refreshWorn(stack, ItemEnergy.get(stack));
	}

	/** Toggle the assist on the worn leggings; returns the new state, or empty if none are worn. */
	public static java.util.Optional<Boolean> toggleStepAssist(net.minecraft.world.entity.LivingEntity wearer) {
		ItemStack legs = wearer.getItemBySlot(EquipmentSlot.LEGS);
		if (!(legs.getItem() instanceof FluxweaveArmorItem piece) || piece.armorType() != ArmorType.LEGGINGS) {
			return java.util.Optional.empty();
		}
		boolean next = !isStepAssistEnabled(legs);
		setStepAssistEnabled(legs, next);
		return java.util.Optional.of(next);
	}

	// --- set bonus ----------------------------------------------------------------------------------

	/**
	 * The 4/4 bonus: a slow regeneration that only runs at less than full health, paid for in EU.
	 *
	 * <p>Computed in the <b>helmet's</b> tick and nowhere else. All four pieces tick independently
	 * (the equipment EnumMap is walked per slot), so doing this in each of them would heal four times
	 * over and charge four times the EU. The helmet is the right one to own it because it ticks last of
	 * the four — FEET, LEGS, CHEST, HEAD by ordinal — so the per-slot bonuses are already applied when
	 * it runs, and taking the helmet off ends the set bonus by construction.
	 */
	private static void trySetBonus(ItemStack helmet, net.minecraft.world.entity.LivingEntity wearer) {
		if (wearer.getHealth() >= wearer.getMaxHealth()) {
			return;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = wearer.getItemBySlot(slot);
			// Every piece must be present AND charged: a flat piece contributes nothing, so a set kept
			// alive by three charged pieces and one dead one does not quietly still heal.
			if (!(worn.getItem() instanceof FluxweaveArmorItem) || ItemEnergy.get(worn) <= 0) {
				return;
			}
		}
		if (ItemEnergy.get(helmet) < Config.fluxweaveRegenEuPerHeal) {
			return;
		}
		wearer.heal(1.0f);
		ItemEnergy.spend(helmet, Config.fluxweaveRegenEuPerHeal, wearer);
	}

	// --- tooltip: how much charge is left, and whether the assist is on ---

	/**
	 * Charge line plus, on the leggings, the step-assist state.
	 *
	 * <p>The percentage is passed as an already-formatted string rather than as a number with a literal
	 * {@code %} in the translation. A literal percent sign inside a format string has to be escaped, and
	 * getting that wrong breaks the line in one locale while looking fine in another — the kind of bug
	 * {@code lang_check} cannot see, because the placeholder signature would still match.
	 *
	 * <p>The assist state is shown only on the trousers that actually carry the flag; repeating it on
	 * all four pieces would suggest each has its own switch.
	 */
	// MOD-498 — Item#appendHoverText is soft-deprecated by vanilla but is the ONLY hook an item has for
	// its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself overrides it in
	// DiscFragmentItem, HangingEntityItem and SmithingTemplateItem. Data-component TooltipProviders cover
	// data-driven components, not text computed per item from the live charge and the armour slot.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		long charge = ItemEnergy.get(stack);
		long capacity = ItemEnergy.capacity(stack);
		long percent = capacity > 0 ? charge * 100L / capacity : 0L;
		adder.accept(Component.translatable("tooltip.alaindustrial.fluxweave.charge",
				charge, capacity, percent + "%").withStyle(ChatFormatting.GOLD));
		if (armorType() == ArmorType.LEGGINGS) {
			boolean on = isStepAssistEnabled(stack);
			adder.accept(Component.translatable(on
					? "tooltip.alaindustrial.fluxweave.step_assist_on"
					: "tooltip.alaindustrial.fluxweave.step_assist_off")
					.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
	}

	// --- item bar shows the EU charge in the LV tier colour ------------------------------------------

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

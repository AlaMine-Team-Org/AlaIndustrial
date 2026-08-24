package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Electric Saber (MOD-149) — the EU line's first weapon, after four hand tools (drill MOD-079,
 * chainsaw MOD-337, shovel MOD-338, hoe MOD-342). Same contract as its siblings: it runs on EU
 * instead of durability, never breaks, and when it runs flat it degrades rather than switching off.
 *
 * <p>Energy is not re-implemented (project rule 4): the charge lives in the shared
 * {@code pouch_energy} component through {@link ItemEnergy}, which gains one {@code capacity} /
 * {@code inputRate} branch — so the Battery Box charge slot, a worn Energy Pack and the Charging
 * Station all charge the saber with no changes on their side.
 *
 * <h2>Three states, two of them the player's choice</h2>
 * <ul>
 * <li><b>Charged and switched on</b> — 12 damage, 4 blocks of reach,
 * {@link Config#electricSaberEuPerHit} per hit, and a jolt of Slowness on the target.</li>
 * <li><b>Switched off</b> (shift-right-click) — 4 damage, vanilla reach, free.</li>
 * <li><b>Flat</b> — the same plain sword the switched-off blade is, and for the same reason: no
 * energy is flowing.</li>
 * </ul>
 *
 * <h2>Why a hand-built {@code TOOL}, and not {@code Properties.sword(...)}</h2>
 * {@code SwordItem} does not exist in 26.2 at all (nor does {@code PickaxeItem}; {@code AxeItem},
 * {@code HoeItem} and {@code ShovelItem} survive because they carry {@code useOn} behaviour). A
 * vanilla sword is a plain {@link Item} whose properties come from
 * {@code Properties.sword(material, damage, speed)} → {@code ToolMaterial.applySwordProperties},
 * which routes through {@code applyCommonProperties} → {@code durability()} → {@code MAX_DAMAGE}.
 * That is exactly what the "EU only, never breaks" model rules out — the item would become damageable
 * and the bar under its icon would go to vanilla durability. So the {@code TOOL} component is
 * assembled here by hand, mirroring what a diamond sword would have produced, minus the durability.
 *
 * <h2>Why the {@code WEAPON} component is mandatory</h2>
 * It is not decoration: {@code ItemStack.hurtEnemy} returns {@code true} <b>only</b> when the stack
 * has {@code WEAPON}, and {@code Player.itemAttackInteraction} calls {@link #postHurtEnemy} only if
 * it did. Without the component the EU hook would simply never run. The damage-per-attack is 0
 * rather than vanilla's 1 to state the intent ("there is no durability, we pay in EU"); the two are
 * equivalent in effect, because {@code hurtAndBreak} returns immediately for an item with no
 * {@code MAX_DAMAGE}.
 *
 * <p>Consequence worth knowing: {@code Mob.doHurtTarget} and {@code LivingEntity} call
 * {@code hurtEnemy} but never {@code postHurtEnemy}, so a mob that somehow ends up holding a saber
 * swings at full damage for free. Mobs never spawn with this mod's items, and the alternative —
 * draining in {@code hurtEnemy} — would move the debit to a point where it is not yet known whether
 * the hit landed.
 *
 * <h2>Why the damage scale is binary</h2>
 * The charged/flat split is written into {@code ATTRIBUTE_MODIFIERS}, following
 * {@link dev.alaindustrial.item.wearable.FluxweaveArmorItem#refreshWorn}, so the tooltip never lies
 * about what the weapon does. The client's held-item renderer replays the swap animation whenever two
 * stacks differ in any component without {@code ignoreSwapAnimation()} — {@code pouch_energy} has
 * that flag (MOD-052), the vanilla {@code ATTRIBUTE_MODIFIERS} does not. A charge-proportional damage
 * curve would therefore twitch the hand on every hit; one twitch when the saber dies is honest
 * feedback. The attack cooldown is safe either way: {@code Player.tick} resets it on
 * {@code !isSameItem}, i.e. on a different item, not on changed components.
 */
public class ElectricSaberItem extends Item {

	/** Enchantability — the diamond value ({@code ToolMaterial.DIAMOND.enchantmentValue}), like the line. */
	private static final int ENCHANT_VALUE = 10;
	/** Damage modifier while live: 1.0 (player base) + 11.0 → 12 displayed, above netherite's 8. */
	private static final double ATTACK_DAMAGE_LIVE = 11.0;
	/** Damage modifier when flat or switched off: → 4 displayed, a stone sword. Still a weapon. */
	private static final double ATTACK_DAMAGE_IDLE = 3.0;
	/** Attack speed: 4.0 (base) - 2.4 → 1.6 swings/s, a vanilla sword's rhythm. */
	private static final double ATTACK_SPEED = -2.4;
	/** Extra entity reach while live, in blocks: vanilla 3.0 → 4.0. The energy blade is longer. */
	private static final double REACH_BONUS_LIVE = 1.0;
	/** Slowness amplifier 1 = Slowness II. The duration is configurable, the level is not. */
	private static final int SHOCK_AMPLIFIER = 1;
	/** Sparks per powered hit — enough to read as a discharge, few enough not to fog the target. */
	private static final int SPARK_COUNT = 8;

	public ElectricSaberItem(Properties properties) {
		super(properties);
	}

	/**
	 * The saber's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — the only difference).
	 *
	 * <p>Three {@code TOOL} rules, which is what {@code applySwordProperties} would have produced:
	 * cobwebs at 15.0, {@code #sword_instantly_mines} at {@code Float.MAX_VALUE},
	 * {@code #sword_efficient} at 1.5. Two vanilla-sword details are carried over deliberately:
	 * {@code canDestroyBlocksInCreative = false} (a sword in creative punches mobs, not blocks), and
	 * {@code damagePerBlock = 0} so {@code super.mineBlock} never reaches for durability we do not have.
	 * {@code stacksTo(1)} is explicit because we skip {@code durability(...)}, which is where a vanilla
	 * tool's max stack size of 1 normally comes from.
	 *
	 * <p>The starting attributes are the idle set: a freshly crafted saber holds no EU, and
	 * {@link #refreshAttributes} swaps both directions the moment that changes.
	 */
	public static Properties electricSaberProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
				.component(DataComponents.TOOL, new Tool(
						List.of(
								// MOD-498 — wrapAsHolder, not the deprecated intrusive Blocks.COBWEB
							// .builtInRegistryHolder(). Same holder, and it is the route vanilla's own
							// Block#toString takes; HolderSet.direct takes Holder<Block>, so nothing here
							// needed the Holder.Reference subtype the deprecated getter returns.
							// This is the one site that runs at REGISTRATION time, so the precondition is worth
							// naming: wrapAsHolder yields Holder.direct for a value not yet in the registry, and a
							// direct holder matches no tag — a silent empty Tool.Rule. Safe here because reading
							// Blocks.COBWEB forces Blocks.<clinit>, which registers it before the field is non-null.
							Tool.Rule.minesAndDrops(
									HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB)), 15.0f),
								Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
								Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5f)),
						1.0f, /*damagePerBlock*/ 0, /*canDestroyBlocksInCreative*/ false))
				.enchantable(ENCHANT_VALUE)
				// Mandatory: without WEAPON the vanilla attack path never calls postHurtEnemy. Zero
				// damage-per-attack because this item has no durability to spend.
				.component(DataComponents.WEAPON, new Weapon(0))
				.attributes(attributesFor(false));
	}

	// --- the charge-driven layer -------------------------------------------------------------------

	/** Whether the saber would spend EU and hit hard right now: switched on and holding a hit's worth. */
	public static boolean isLive(ItemStack stack) {
		return isEnabled(stack) && ItemEnergy.get(stack) >= Config.electricSaberEuPerHit;
	}

	/**
	 * Point the stack's {@code ATTRIBUTE_MODIFIERS} at the live or the idle set. Called from
	 * {@link ItemEnergy#set} — the single place a powered item's charge ever changes — and from
	 * {@link #setEnabled}, so damage, attack speed and reach can never drift from the state the
	 * tooltip shows.
	 *
	 * <p>Both states are written explicitly; the idle one does <b>not</b> simply drop the component.
	 * {@code ItemStack.remove} on a component the item declares in its properties records a removal on
	 * top of the default rather than falling back to it — the saber would end up with no attack
	 * attributes at all. (A gametest caught exactly that on the Energy Pack.)
	 *
	 * <p>The write is skipped when the component already says the right thing, so a saber charging or
	 * draining across many steps only touches it on the tick its state actually flips — each write
	 * resyncs the stack to the client and replays the held-item swap animation.
	 */
	public static void refreshAttributes(ItemStack stack, long eu) {
		if (!(stack.getItem() instanceof ElectricSaberItem)) {
			return;
		}
		boolean live = isEnabled(stack) && eu >= Config.electricSaberEuPerHit;
		ItemAttributeModifiers wanted = attributesFor(live);
		if (!wanted.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, wanted);
		}
	}

	/**
	 * The full modifier set for one of the two states. Reach is a modifier on
	 * {@code ENTITY_INTERACTION_RANGE} rather than an {@code ATTACK_RANGE} component: vanilla builds
	 * the component from that very attribute when none is present ({@code AttackRange.defaultFor}), and
	 * both halves of the game read the same source — the client picks its target through
	 * {@code LocalPlayer.raycastHitResult} and the server validates through
	 * {@code getAttackRangeWith} — so one modifier moves both, and there is no second component whose
	 * state could disagree with this one.
	 */
	private static ItemAttributeModifiers attributesFor(boolean live) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
						new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
								live ? ATTACK_DAMAGE_LIVE : ATTACK_DAMAGE_IDLE,
								AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED,
						new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
								AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND);
		if (live) {
			builder.add(Attributes.ENTITY_INTERACTION_RANGE,
					new AttributeModifier(Industrialization.id("electric_saber_reach"), REACH_BONUS_LIVE,
							AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND);
		}
		return builder.build();
	}

	// --- on/off switch: shift-right-click, absent component = on ---

	/**
	 * Whether the saber is switched on. <b>Absent means on</b>, exactly like the Electromagnet's flag
	 * (MOD-132): a freshly crafted saber works out of the box, and switching it off stores {@code false},
	 * so a switched-off saber and a new one stay distinguishable while two new ones stay identical.
	 */
	public static boolean isEnabled(ItemStack stack) {
		Boolean value = stack.get(ModDataComponents.SABER_ACTIVE.get());
		return value == null || value;
	}

	/** Write the on/off flag and rebuild the attribute set so the change lands on the same tick. */
	public static void setEnabled(ItemStack stack, boolean enabled) {
		if (enabled) {
			stack.remove(ModDataComponents.SABER_ACTIVE.get());
		} else {
			stack.set(ModDataComponents.SABER_ACTIVE.get(), false);
		}
		refreshAttributes(stack, ItemEnergy.get(stack));
	}

	/**
	 * Shift-right-click toggles the blade; a plain right-click passes through so the saber never gets
	 * in the way of other interactions. The flag is written server-side only — the client picks the new
	 * component up from the synced stack — and the metallic copper-bulb click is the same sound the
	 * Electromagnet uses for its own switch.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		boolean nowEnabled = !isEnabled(stack);
		if (level instanceof ServerLevel) {
			setEnabled(stack, nowEnabled);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable(nowEnabled
								? "item.alaindustrial.electric_saber.toggled_on"
								: "item.alaindustrial.electric_saber.toggled_off")
								.withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
						true);
			}
			level.playSound(null, player.blockPosition(),
					nowEnabled ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
					SoundSource.PLAYERS, 0.7f, 1.4f);
		}
		return InteractionResult.SUCCESS;
	}

	// --- the hit: spend EU, jolt the target ---

	/**
	 * Charges the swing and discharges into the target. Reached only through
	 * {@code Player.itemAttackInteraction}, i.e. server-side, after the damage actually landed, and only
	 * because this item carries {@code WEAPON} (see the class javadoc).
	 *
	 * <p>The live check happens before the debit, so the last powered swing costs exactly one hit's
	 * worth and the next one is already the idle sword — {@link ItemEnergy#spend} drops the debit for a
	 * creative or spectating attacker (MOD-081), and in that case the saber stays live at whatever
	 * charge it had, which is the same deal every other powered item gives them.
	 */
	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!isLive(stack)) {
			return;
		}
		ItemEnergy.spend(stack, Config.electricSaberEuPerHit, attacker);
		shock(target, attacker);
	}

	/**
	 * The discharge: a short Slowness on the target plus a burst of sparks around it. Split out so a
	 * gametest can assert the effect without reproducing the whole attack path, and gated on a positive
	 * duration so {@code electricSaberShockSeconds = 0} leaves a pure damage weapon.
	 *
	 * <p>The attacker is passed as the effect's source, which is what vanilla uses to attribute the
	 * effect (mob-vs-player kill credit, {@code entity_effect} advancement triggers).
	 */
	public static void shock(LivingEntity target, LivingEntity attacker) {
		int seconds = Config.electricSaberShockSeconds;
		if (seconds <= 0) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, seconds * 20, SHOCK_AMPLIFIER), attacker);
		if (target.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					target.getX(), target.getY(0.6), target.getZ(),
					SPARK_COUNT, 0.25, 0.35, 0.25, 0.02);
		}
	}

	// --- self-heal: a saber can arrive charged without ever passing through ItemEnergy.set ---

	/**
	 * Keeps the attribute set honest for stacks this class never saw charged: {@code /give} with a
	 * {@code pouch_energy} component, a loot table, a datapack recipe. Without this they would look lit
	 * and hit like a stone sword forever.
	 *
	 * <p>Only the selected hotbar slot is checked. {@code Inventory.tick} passes {@code null} for every
	 * other slot, so this is a null guard as much as a scope guard, and it keeps the comparison off the
	 * dozens of stacks in a backpack. {@link #refreshAttributes} writes only on a real change, so the
	 * normal case is one component comparison per tick and nothing else.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot == null || slot != EquipmentSlot.MAINHAND) {
			return;
		}
		refreshAttributes(stack, ItemEnergy.get(stack));
	}

	// --- item bar shows the EU charge in the LV tier colour (numbers are in the tooltip) ---

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

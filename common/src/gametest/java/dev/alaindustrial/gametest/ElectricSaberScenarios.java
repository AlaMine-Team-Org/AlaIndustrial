package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electric Saber (MOD-149, suite TC-SABER-001). Wrapped by the
 * Fabric {@code ElectricSaberGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane via {@code NeoForgeGameTests}, so both loaders exercise the SAME logic. Numbers come from
 * {@link Config} — the balance source of truth.
 *
 * <h2>Why the hit is driven through {@code postHurtEnemy} and not {@code player.attack(entity)}</h2>
 * A gametest mock player has no {@code connection}, and vanilla's damage path dereferences it
 * ({@code connection.hasClientLoaded()}), so {@code attack} dies with an internal error — the same wall
 * that shaped the cable-shock tests (MOD-279). Calling {@code ItemStack.postHurtEnemy} reproduces the
 * exact step vanilla takes after a landed hit, without touching the network half.
 *
 * <p>That shortcut skips one thing vanilla would have checked, and {@link #fun07TagsAndEnchants}
 * covers it explicitly: {@code hurtEnemy} must return {@code true}, which is true only while the item
 * carries {@code WEAPON}. Without that assertion a forgotten component would leave every test here
 * green while the saber silently spent no EU in a real game.
 */
public final class ElectricSaberScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElectricSaberScenarios::fun01ChargeInBatteryBox, "saber_charge_in_battery_box")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun01_chargeInBatteryBox").ticks(20, 80),
				RosterEntry.of(ElectricSaberScenarios::fun02DrainOnHit, "saber_drain_on_hit")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun02_drainOnHit").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun03NoDrainBelowCost, "saber_no_drain_below_cost")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun03_noDrainBelowCost").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun04AttributesFollowCharge, "saber_attributes_follow_charge")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun04_attributesFollowCharge").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun05SwitchedOffSpendsNothing,
								"saber_switched_off_spends_nothing")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun05_switchedOffSpendsNothing").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun06CreativeSpendsNothing, "saber_creative_spends_nothing")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun06_creativeSpendsNothing").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun07TagsAndEnchants, "saber_tags_and_enchants")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun07_tagsAndEnchants").ticks(20, 40),
				RosterEntry.of(ElectricSaberScenarios::fun08ShockOnlyWhenLive, "saber_shock_only_when_live")
						.fabricId("ElectricSaberGameTest", "tcSaber001Fun08_shockOnlyWhenLive").ticks(20, 40));

		private Roster() {}
	}

	private ElectricSaberScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);
	private static final BlockPos TARGET = new BlockPos(2, 2, 2);
	/** The convention (c:) melee-weapon identity tag — built by hand (not the alaindustrial namespace). */
	private static final TagKey<Item> C_MELEE_WEAPON =
			TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/melee_weapon"));

	private static ItemStack saber(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_SABER.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A cow to swing at: a passive LivingEntity, so nothing fights back mid-test. */
	private static LivingEntity spawnTarget(GameTestHelper helper) {
		return helper.spawn(EntityTypes.COW, TARGET);
	}

	/** Runs the post-hit step vanilla runs after a landed swing — see the class javadoc. */
	private static void hit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.postHurtEnemy(target, attacker);
	}

	private static double damageOf(ItemStack stack) {
		return modifierValue(stack, Attributes.ATTACK_DAMAGE);
	}

	private static double reachBonusOf(ItemStack stack) {
		return modifierValue(stack, Attributes.ENTITY_INTERACTION_RANGE);
	}

	/** Summed ADD_VALUE modifier for an attribute in the stack's current component; 0 when absent. */
	private static double modifierValue(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return 0.0;
		}
		double total = 0.0;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().equals(attribute)
					&& entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}

	private static BatteryBoxBlockEntity placeBox(GameTestHelper helper) {
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity be = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (be == null) {
			helper.fail("battery_box block entity missing");
		}
		return be;
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/**
	 * FUN01: the saber is accepted by the Battery Box charge slot (both the menu's client-side
	 * {@code mayPlace} and the server-side {@code canPlaceItem}) and charges there at
	 * {@code min(tier ceiling, its own intake)} — the whole "no changes needed on the charger's side"
	 * promise of {@link ItemEnergy}.
	 *
	 * @implements TC-SABER-001-FUN01 — the saber is accepted by the Battery Box charge slot (both
	 *     filters) and charges there at min(LV ceiling, its intake rate).
	 */
	public static void fun01ChargeInBatteryBox(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(saber(0))) {
			helper.fail("the charge slot must accept the saber (client prediction)");
		}
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, saber(0))) {
			helper.fail("the server-side filter must accept the saber too");
		}

		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, saber(0));
		box.serverTick(helper.getLevel(), box.getBlockPos(), helper.getLevel().getBlockState(box.getBlockPos()));
		long expected = Math.min(EnergyTier.LV.maxVoltage(), ToolConfig.electricSaberInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, saber intake) = " + expected + " EU, got " + gained);
		}
		helper.succeed();
	}

	/**
	 * FUN02: a landed hit with a live saber drains exactly one hit's worth of EU.
	 *
	 * @implements TC-SABER-001-FUN02 — a landed hit with a live saber drains exactly one hit's worth of EU.
	 */
	public static void fun02DrainOnHit(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		LivingEntity target = spawnTarget(helper);
		ItemStack saber = saber(ToolConfig.electricSaberBuffer);

		hit(saber, target, player);
		long expected = ToolConfig.electricSaberBuffer - ToolConfig.electricSaberEuPerHit;
		if (ItemEnergy.get(saber) != expected) {
			helper.fail("one hit must drain exactly electricSaberEuPerHit; expected " + expected
					+ ", left " + ItemEnergy.get(saber));
		}
		helper.succeed();
	}

	/**
	 * FUN03: below the per-hit cost nothing is spent and nothing goes negative — the saber has already
	 * degraded to a plain sword, and a plain sword costs no energy.
	 *
	 * @implements TC-SABER-001-FUN03 — below the per-hit cost the saber spends nothing and never goes negative.
	 */
	public static void fun03NoDrainBelowCost(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		LivingEntity target = spawnTarget(helper);
		long below = ToolConfig.electricSaberEuPerHit - 1;
		ItemStack saber = saber(below);

		hit(saber, target, player);
		if (ItemEnergy.get(saber) != below) {
			helper.fail("a saber below the per-hit cost must spend nothing, got " + ItemEnergy.get(saber));
		}
		helper.succeed();
	}

	/**
	 * FUN04: the attribute set follows the charge in BOTH directions — damage and reach step up when the
	 * saber crosses the per-hit threshold and step back down when it falls under it. This is the whole
	 * mechanism behind "the tooltip never lies", so it is asserted on the component rather than on a
	 * damage number observed in combat.
	 *
	 * @implements TC-SABER-001-FUN04 — damage and reach modifiers follow the charge across the per-hit
	 *     threshold in both directions.
	 */
	public static void fun04AttributesFollowCharge(GameTestHelper helper) {
		ItemStack saber = saber(0);
		double flatDamage = damageOf(saber);
		if (reachBonusOf(saber) != 0.0) {
			helper.fail("a flat saber must carry no reach bonus, got " + reachBonusOf(saber));
		}

		ItemEnergy.set(saber, ToolConfig.electricSaberEuPerHit);
		double liveDamage = damageOf(saber);
		if (liveDamage <= flatDamage) {
			helper.fail("a live saber must hit harder than a flat one: " + liveDamage + " vs " + flatDamage);
		}
		if (reachBonusOf(saber) <= 0.0) {
			helper.fail("a live saber must carry a positive reach bonus, got " + reachBonusOf(saber));
		}

		// …and back down again: one EU below the threshold is a plain sword once more.
		ItemEnergy.set(saber, ToolConfig.electricSaberEuPerHit - 1);
		if (damageOf(saber) != flatDamage) {
			helper.fail("dropping under the per-hit cost must restore the flat damage, got " + damageOf(saber));
		}
		if (reachBonusOf(saber) != 0.0) {
			helper.fail("dropping under the per-hit cost must drop the reach bonus, got " + reachBonusOf(saber));
		}
		helper.succeed();
	}

	/**
	 * FUN05: a switched-off saber is inert even on a full buffer — no EU spent, no damage bonus, no
	 * reach bonus. The flag is the half of the state the player controls, and it must win over charge.
	 *
	 * @implements TC-SABER-001-FUN05 — a switched-off saber spends no EU and carries no bonuses even on a
	 *     full buffer; switching back on restores them immediately.
	 */
	public static void fun05SwitchedOffSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		LivingEntity target = spawnTarget(helper);
		ItemStack saber = saber(ToolConfig.electricSaberBuffer);
		double liveDamage = damageOf(saber);

		ElectricSaberItem.setEnabled(saber, false);
		if (ElectricSaberItem.isEnabled(saber)) {
			helper.fail("setEnabled(false) must switch the blade off");
		}
		if (damageOf(saber) >= liveDamage) {
			helper.fail("a switched-off saber must lose the damage bonus, got " + damageOf(saber));
		}
		if (reachBonusOf(saber) != 0.0) {
			helper.fail("a switched-off saber must lose the reach bonus, got " + reachBonusOf(saber));
		}

		hit(saber, target, player);
		if (ItemEnergy.get(saber) != ToolConfig.electricSaberBuffer) {
			helper.fail("a switched-off saber must spend no EU, left " + ItemEnergy.get(saber));
		}

		// Switching back on restores everything on the same call — no tick of lag.
		ElectricSaberItem.setEnabled(saber, true);
		if (damageOf(saber) != liveDamage) {
			helper.fail("switching back on must restore the damage bonus, got " + damageOf(saber));
		}
		helper.succeed();
	}

	/**
	 * FUN06: a creative attacker spends nothing — EU is tool wear, and creative does not wear tools (MOD-081).
	 *
	 * @implements TC-SABER-001-FUN06 — a creative attacker spends no charge (MOD-081).
	 */
	public static void fun06CreativeSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.getAbilities().instabuild = true;
		LivingEntity target = spawnTarget(helper);
		ItemStack saber = saber(ToolConfig.electricSaberBuffer);

		hit(saber, target, player);
		if (ItemEnergy.get(saber) != ToolConfig.electricSaberBuffer) {
			helper.fail("a creative attacker must spend no EU, left " + ItemEnergy.get(saber));
		}
		helper.succeed();
	}

	/**
	 * FUN07: identity and the WEAPON contract. The saber sits in {@code #minecraft:swords} (which is what
	 * gives it the sweep attack and, transitively, every melee enchantment) and in
	 * {@code #c:tools/melee_weapon}; it takes the sword enchantments at a table; and — the assertion the
	 * shortcut in the other cases depends on — {@code hurtEnemy} returns {@code true}, which happens only
	 * while {@code DataComponents.WEAPON} is present. Without the component vanilla would never call
	 * {@code postHurtEnemy} and the saber would spend nothing in a real game.
	 *
	 * @implements TC-SABER-001-FUN07 — sword identity tags, melee enchantments, and the WEAPON contract
	 *     that makes the EU hook run at all.
	 */
	public static void fun07TagsAndEnchants(GameTestHelper helper) {
		ItemStack saber = saber(ToolConfig.electricSaberBuffer);
		assertInTag(helper, saber, ItemTags.SWORDS, "#minecraft:swords");
		assertInTag(helper, saber, C_MELEE_WEAPON, "#c:tools/melee_weapon");
		if (saber.get(DataComponents.WEAPON) == null) {
			helper.fail("the saber must carry the WEAPON component");
		}

		ServerPlayer player = survivalPlayer(helper);
		LivingEntity target = spawnTarget(helper);
		if (!saber.hurtEnemy(target, player)) {
			helper.fail("hurtEnemy must report the hit as a weapon hit — otherwise vanilla never calls "
					+ "postHurtEnemy and no EU is ever spent");
		}

		ServerLevel level = helper.getLevel();
		assertCanEnchant(helper, enchant(level, Enchantments.SHARPNESS), saber, "sharpness");
		assertCanEnchant(helper, enchant(level, Enchantments.LOOTING), saber, "looting");
		assertCanEnchant(helper, enchant(level, Enchantments.SWEEPING_EDGE), saber, "sweeping_edge");
		if (!saber.isEnchantable()) {
			helper.fail("the saber must be enchantable at the table");
		}
		helper.succeed();
	}

	/**
	 * FUN08: the discharge lands only on a live hit. A charged, switched-on saber leaves Slowness on the
	 * target; a flat one leaves the target clean — the effect is a property of the powered swing, not of
	 * the weapon.
	 *
	 * @implements TC-SABER-001-FUN08 — the electric discharge lands on a live hit and on nothing else.
	 */
	public static void fun08ShockOnlyWhenLive(GameTestHelper helper) {
		if (ToolConfig.electricSaberShockSeconds <= 0) {
			helper.fail("this case assumes the shock is enabled (electricSaberShockSeconds > 0)");
		}
		ServerPlayer player = survivalPlayer(helper);

		LivingEntity shocked = spawnTarget(helper);
		hit(saber(ToolConfig.electricSaberBuffer), shocked, player);
		if (!shocked.hasEffect(MobEffects.SLOWNESS)) {
			helper.fail("a live hit must leave Slowness on the target");
		}

		LivingEntity untouched = helper.spawn(EntityTypes.COW, new BlockPos(4, 2, 2));
		hit(saber(ToolConfig.electricSaberEuPerHit - 1), untouched, player);
		if (untouched.hasEffect(MobEffects.SLOWNESS)) {
			helper.fail("a flat saber must not shock anything");
		}
		helper.succeed();
	}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	private static Holder<Enchantment> enchant(ServerLevel level,
			net.minecraft.resources.ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void assertInTag(GameTestHelper helper, ItemStack stack, TagKey<Item> tag, String tagName) {
		if (!stack.is(h -> h.is(tag))) {
			helper.fail("electric_saber is not in " + tagName + " (membership tag missing)");
		}
	}

	// MOD-498 — Enchantment#canEnchant(ItemStack) is deprecated by NeoForge only; vanilla leaves it plain.
	// The replacement NeoForge names, ItemStack#supportsEnchantment(Holder), is a NeoForge extension that
	// does not exist in vanilla, and this scenario lives in common/ and is replayed by the Fabric lane too,
	// so it has to ask the vanilla way.
	@SuppressWarnings("deprecation")
	private static void assertCanEnchant(GameTestHelper helper, Holder<Enchantment> enchantment,
			ItemStack stack, String name) {
		if (!enchantment.value().canEnchant(stack)) {
			helper.fail(name + " rejected electric_saber — not in the enchantment's supported_items");
		}
	}
}

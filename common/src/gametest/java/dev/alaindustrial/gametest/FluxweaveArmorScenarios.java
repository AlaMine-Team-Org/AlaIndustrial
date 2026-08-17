package dev.alaindustrial.gametest;

import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Fluxweave armour set (MOD-127, suite TC-FLUX-001).
 *
 * <p>These exist to pin down the failure modes the design calls dangerous, because none of them is a
 * compile error and several would look fine in a screenshot:
 *
 * <ul>
 * <li><b>A drained suit must keep its base protection.</b> The bonuses live in the same component the
 * material's ARMOR/TOUGHNESS modifiers do, so a rewrite that replaced instead of extended would strip
 * a flat suit bare — and the player would only find out by dying.</li>
 * <li><b>Step assist must stay off until asked for</b>, and toggling must move the attribute, not just
 * the flag.</li>
 * <li><b>The set bonus must not multiply.</b> Four pieces tick independently; a per-piece
 * implementation heals and charges four times over, which reads as generous rather than as a bug.</li>
 * </ul>
 *
 * <p><b>Why these read the stack and not the wearer.</b> Applying modifiers is vanilla's job —
 * {@code detectEquipmentUpdates} does it on a real player every tick — and the L2 mock player does not
 * run that machinery. An earlier revision of this suite asserted on {@code player.getAttributeValue}
 * and reported 0 armour for a set that works perfectly in game: it was measuring the harness, not the
 * mod. Worse, its "a dead piece stops the healing" case passed for the wrong reason, because nothing
 * healed there at all. What is actually ours is the content of the component, so that is what is
 * asserted, and the set-bonus cases drive the piece's own tick directly and carry a control leg.
 */
public final class FluxweaveArmorScenarios {

	private FluxweaveArmorScenarios() {
	}

	private static final long AMPLE_EU = 10_000L;

	private static ItemStack piece(java.util.function.Supplier<net.minecraft.world.item.Item> which, long eu) {
		ItemStack stack = new ItemStack(which.get());
		ItemEnergy.set(stack, eu); // routes through refreshWorn, exactly as in play
		return stack;
	}

	/** Dress the player in a full set at {@code eu} charge each. */
	private static void wearSet(ServerPlayer player, long eu) {
		player.setItemSlot(EquipmentSlot.HEAD, piece(ModContent.FLUXWEAVE_HELMET, eu));
		player.setItemSlot(EquipmentSlot.CHEST, piece(ModContent.FLUXWEAVE_CHESTPLATE, eu));
		player.setItemSlot(EquipmentSlot.LEGS, piece(ModContent.FLUXWEAVE_LEGGINGS, eu));
		player.setItemSlot(EquipmentSlot.FEET, piece(ModContent.FLUXWEAVE_BOOTS, eu));
	}

	/** Total amount this piece declares for {@code attribute}, or empty when it declares none. */
	private static Optional<Double> modifierAmount(ItemStack stack, Holder<Attribute> attribute) {
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return Optional.empty();
		}
		return modifiers.modifiers().stream()
				.filter(entry -> entry.attribute().equals(attribute))
				.map(entry -> entry.modifier().amount())
				.reduce(Double::sum);
	}

	/**
	 * Drive {@code seconds} of one worn piece's once-a-second step. Calling the step rather than
	 * {@code inventoryTick} is deliberate: the game clock does not advance inside a single server tick,
	 * so looping over the tick would hit its {@code % 20} gate every time or never. It is also the
	 * tighter test for the set bonus, since it proves the bonus lives in the helmet rather than in
	 * whichever piece happens to tick.
	 */
	private static void driveWorn(GameTestHelper helper, ServerPlayer player, EquipmentSlot slot, int seconds) {
		for (int i = 0; i < seconds; i++) {
			FluxweaveArmorItem.secondStep(player.getItemBySlot(slot), player);
		}
	}

	// ── REG01: a flat suit is still armour ───────────────────────────────────────────────────────────

	/** The load-bearing invariant: charge switches bonuses on, it does not switch protection off. */
	public static void reg01DrainedSetKeepsBaseProtection(GameTestHelper helper) {
		ItemStack chest = piece(ModContent.FLUXWEAVE_CHESTPLATE, 0);
		double armor = modifierAmount(chest, Attributes.ARMOR).orElse(0.0);
		if (armor < 6.0) {
			helper.fail("a drained chestplate declares only " + armor + " armour; the material declares 6");
			return;
		}
		if (modifierAmount(chest, Attributes.ARMOR_TOUGHNESS).orElse(0.0) < 1.0) {
			helper.fail("a drained chestplate lost the material's toughness");
			return;
		}
		helper.succeed();
	}

	// ── FUN01: charging switches the bonuses on, without dropping the base set ──────────────────────

	public static void fun01ChargeSwitchesBonusesOn(GameTestHelper helper) {
		ItemStack helmet = piece(ModContent.FLUXWEAVE_HELMET, 0);
		if (modifierAmount(helmet, Attributes.OXYGEN_BONUS).isPresent()) {
			helper.fail("a drained helmet must grant no oxygen bonus");
			return;
		}
		ItemEnergy.set(helmet, AMPLE_EU);
		if (modifierAmount(helmet, Attributes.OXYGEN_BONUS).orElse(0.0) <= 0.0) {
			helper.fail("a charged helmet declares no oxygen bonus");
			return;
		}
		// Charging must ADD to the material's set, never replace it.
		if (modifierAmount(helmet, Attributes.ARMOR).orElse(0.0) < 2.0) {
			helper.fail("charging the helmet dropped the material's armour modifier");
			return;
		}
		ItemStack legs = piece(ModContent.FLUXWEAVE_LEGGINGS, AMPLE_EU);
		if (modifierAmount(legs, Attributes.MOVEMENT_SPEED).orElse(0.0) <= 0.0) {
			helper.fail("charged leggings declare no run-speed bonus");
			return;
		}
		helper.succeed();
	}

	// ── CON01: falls are softened, never cancelled ──────────────────────────────────────────────────

	public static void con01BootsSoftenButNeverCancelFalls(GameTestHelper helper) {
		ItemStack boots = piece(ModContent.FLUXWEAVE_BOOTS, AMPLE_EU);
		double fall = modifierAmount(boots, Attributes.FALL_DAMAGE_MULTIPLIER).orElse(0.0);
		if (fall >= 0.0) {
			helper.fail("charged boots do not soften falls: modifier " + fall);
			return;
		}
		// ADD_MULTIPLIED_TOTAL of -1.0 would zero the multiplier and make falling free.
		if (fall <= -1.0) {
			helper.fail("charged boots cancel fall damage outright (" + fall + "); they must only soften");
			return;
		}
		helper.succeed();
	}

	// ── FUN02: the step assist is opt-in ────────────────────────────────────────────────────────────

	public static void fun02StepAssistIsOptIn(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		wearSet(player, AMPLE_EU);
		ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
		if (FluxweaveArmorItem.isStepAssistEnabled(legs)) {
			helper.fail("step assist must start switched off");
			return;
		}
		if (modifierAmount(legs, Attributes.STEP_HEIGHT).isPresent()) {
			helper.fail("step height is granted before the assist was switched on");
			return;
		}

		FluxweaveArmorItem.toggleStepAssist(player);
		legs = player.getItemBySlot(EquipmentSlot.LEGS);
		if (!FluxweaveArmorItem.isStepAssistEnabled(legs)) {
			helper.fail("toggling did not set the flag");
			return;
		}
		if (modifierAmount(legs, Attributes.STEP_HEIGHT).orElse(0.0) <= 0.0) {
			helper.fail("the flag flipped but no step-height modifier was added");
			return;
		}

		FluxweaveArmorItem.toggleStepAssist(player);
		legs = player.getItemBySlot(EquipmentSlot.LEGS);
		if (FluxweaveArmorItem.isStepAssistEnabled(legs)
				|| modifierAmount(legs, Attributes.STEP_HEIGHT).isPresent()) {
			helper.fail("toggling off left the assist applied");
			return;
		}
		helper.succeed();
	}

	// ── CON02: the set bonus is paid for once, not four times ───────────────────────────────────────

	public static void con02SetBonusAppliesOncePerSecond(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		wearSet(player, AMPLE_EU);
		player.setHealth(player.getMaxHealth() - 4.0f);

		float before = player.getHealth();
		driveWorn(helper, player, EquipmentSlot.HEAD, 1);
		float healed = player.getHealth() - before;
		if (healed <= 0.0f) {
			helper.fail("a charged 4/4 set below full health did not heal");
			return;
		}
		if (healed > 2.0f) {
			helper.fail("healed " + healed + " hp in one second — the bonus is applying more than once");
			return;
		}

		// The other three pieces must contribute nothing: ticking them adds no further healing.
		float afterHelmet = player.getHealth();
		driveWorn(helper, player, EquipmentSlot.CHEST, 1);
		driveWorn(helper, player, EquipmentSlot.LEGS, 1);
		driveWorn(helper, player, EquipmentSlot.FEET, 1);
		if (player.getHealth() > afterHelmet) {
			helper.fail("a non-helmet piece also healed — the set bonus is duplicated across slots");
			return;
		}
		helper.succeed();
	}

	// ── CON03: a dead piece breaks the set ──────────────────────────────────────────────────────────

	public static void con03DeadPieceBreaksTheSet(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		wearSet(player, AMPLE_EU);
		player.setItemSlot(EquipmentSlot.FEET, piece(ModContent.FLUXWEAVE_BOOTS, 0));
		player.setHealth(player.getMaxHealth() - 4.0f);

		float before = player.getHealth();
		driveWorn(helper, player, EquipmentSlot.HEAD, 2);
		if (player.getHealth() > before) {
			helper.fail("the set bonus healed with a drained piece worn");
			return;
		}
		// Guard against a vacuous pass: the same drive with a full set MUST heal, so this can only go
		// green because the dead boot broke the set, not because nothing ever heals in this harness.
		player.setItemSlot(EquipmentSlot.FEET, piece(ModContent.FLUXWEAVE_BOOTS, AMPLE_EU));
		float control = player.getHealth();
		driveWorn(helper, player, EquipmentSlot.HEAD, 1);
		if (player.getHealth() <= control) {
			helper.fail("the control case did not heal either — this test proves nothing");
			return;
		}
		helper.succeed();
	}

	// ── REG02: the worn look follows the charge ─────────────────────────────────────────────────────

	public static void reg02WornAssetFollowsCharge(GameTestHelper helper) {
		ItemStack helmet = piece(ModContent.FLUXWEAVE_HELMET, 0);
		var drained = helmet.get(DataComponents.EQUIPPABLE);
		if (drained == null || drained.assetId().orElse(null) != FluxweaveArmorItem.FLUXWEAVE_OFF_ASSET) {
			helper.fail("a flat helmet does not carry the drained asset");
			return;
		}
		ItemEnergy.set(helmet, AMPLE_EU);
		var charged = helmet.get(DataComponents.EQUIPPABLE);
		if (charged == null || charged.assetId().orElse(null) != FluxweaveArmorItem.FLUXWEAVE_ASSET) {
			helper.fail("a charged helmet does not carry the lit asset");
			return;
		}
		if (charged.damageOnHurt()) {
			helper.fail("the suit must never take durability damage from hits");
			return;
		}
		ItemEnergy.set(helmet, 0);
		if (helmet.get(DataComponents.EQUIPPABLE) == null) {
			helper.fail("draining removed the EQUIPPABLE component — the piece is no longer wearable");
			return;
		}
		helper.succeed();
	}
}

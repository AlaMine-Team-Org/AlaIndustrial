package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.ElectricBowItem;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electric Bow (MOD-363, suite TC-BOW-001). Wrapped by the
 * Fabric {@code ElectricBowGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests}, so both loaders exercise the SAME logic. Numbers come from {@link Config}
 * and from the constants on {@link ElectricBowItem} — the balance source of truth.
 *
 * <h2>How a shot is fired here</h2>
 * The release is driven through {@code ElectricBowItem.releaseUsing} with the remaining-ticks value a
 * real draw of N ticks would leave, i.e. the exact call {@code LivingEntity.releaseUsingItem} makes when
 * the player lets go. Everything after it is vanilla's own path: the arrow is found and consumed, the
 * power is computed, the arrow entity is spawned into the level. The test then measures that entity —
 * its launch speed and its crit flag — which is what the player actually gets, not a number the bow
 * claims about itself.
 *
 * <p>Every arrow is discarded right after it is measured: it leaves at 60–70 blocks per second in the
 * mock player's facing, and a live arrow crossing into a neighbouring test's structure would be a flaky
 * failure somewhere else.
 */
public final class ElectricBowScenarios {

	private ElectricBowScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);
	private static final TagKey<Item> C_BOW =
			TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/bow"));
	private static final TagKey<Item> C_RANGED_WEAPON =
			TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/ranged_weapon"));
	/** Vanilla's full-draw launch speed, blocks per tick: power 1.0 × 3.0 ({@code BowItem.releaseUsing}). */
	private static final double VANILLA_FULL_SPEED = 3.0;
	/**
	 * Tolerance on a measured launch speed. Spread adds a random offset of at most ±0.0172 per axis to
	 * the unit direction before it is scaled, which moves the length by well under 2 %; 3 % keeps the
	 * test deterministic without letting a wrong multiplier (±15 %) through.
	 */
	private static final double SPEED_TOLERANCE = 0.03;

	private static ItemStack bow(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_BOW.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A survival archer holding {@code bow} in the main hand with {@code arrows} arrows in the pack. */
	private static ServerPlayer archer(GameTestHelper helper, ItemStack bow, int arrows) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		if (arrows > 0) {
			player.getInventory().add(new ItemStack(Items.ARROW, arrows));
		}
		return player;
	}

	/** Lets go of the bow after {@code ticksHeld} ticks of drawing — the call vanilla makes on release. */
	private static boolean release(ItemStack bow, ServerLevel level, Player player, int ticksHeld) {
		int remaining = bow.getUseDuration(player) - ticksHeld;
		return bow.getItem().releaseUsing(bow, level, player, remaining);
	}

	/** The arrows {@code owner} has in the air, discarded as they are collected (see class javadoc). */
	private static List<AbstractArrow> takeArrows(GameTestHelper helper, Player owner) {
		List<AbstractArrow> arrows = List.copyOf(helper.getLevel().getEntities(
				EntityTypeTest.forClass(AbstractArrow.class), arrow -> arrow.getOwner() == owner));
		arrows.forEach(AbstractArrow::discard);
		return arrows;
	}

	private static AbstractArrow onlyArrow(GameTestHelper helper, Player owner, String what) {
		List<AbstractArrow> arrows = takeArrows(helper, owner);
		if (arrows.size() != 1) {
			helper.fail(what + ": expected exactly one arrow in flight, found " + arrows.size());
		}
		return arrows.getFirst();
	}

	private static void assertSpeed(GameTestHelper helper, AbstractArrow arrow, double expected, String what) {
		double speed = arrow.getDeltaMovement().length();
		if (Math.abs(speed - expected) > expected * SPEED_TOLERANCE) {
			helper.fail(what + ": launch speed " + speed + " b/t, expected " + expected + " ±"
					+ (int) (SPEED_TOLERANCE * 100) + "%");
		}
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/**
	 * FUN01: the bow is accepted by the Battery Box charge slot (both the menu's client-side
	 * {@code mayPlace} and the server-side {@code canPlaceItem}) and charges there at
	 * {@code min(tier ceiling, its own intake)} — no change on the charger's side.
	 */
	public static void fun01ChargeInBatteryBox(GameTestHelper helper) {
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity box = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery_box block entity missing");
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(bow(0))) {
			helper.fail("the charge slot must accept the bow (client prediction)");
		}
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, bow(0))) {
			helper.fail("the server-side filter must accept the bow too");
		}

		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, bow(0));
		box.serverTick(helper.getLevel(), box.getBlockPos(), helper.getLevel().getBlockState(box.getBlockPos()));
		long expected = Math.min(EnergyTier.LV.maxVoltage(), Config.electricBowInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, bow intake) = " + expected + " EU, got " + gained);
		}
		helper.succeed();
	}

	/**
	 * FUN02: a charged bow drawn for {@link ElectricBowItem#LIVE_DRAW_TICKS} fires a full-power shot —
	 * crit, launched at vanilla's full speed × {@link ElectricBowItem#LIVE_VELOCITY_MULTIPLIER} — consumes
	 * one arrow, and pays exactly one shot's worth of EU.
	 */
	public static void fun02LiveShotIsFasterAndPays(GameTestHelper helper) {
		ItemStack bow = bow(Config.electricBowBuffer);
		ServerPlayer player = archer(helper, bow, 8);

		if (!release(bow, helper.getLevel(), player, ElectricBowItem.LIVE_DRAW_TICKS)) {
			helper.fail("a charged bow with arrows, drawn " + ElectricBowItem.LIVE_DRAW_TICKS + " ticks, must fire");
		}
		AbstractArrow arrow = onlyArrow(helper, player, "live shot");
		if (!arrow.isCritArrow()) {
			helper.fail("a charged bow is fully drawn after " + ElectricBowItem.LIVE_DRAW_TICKS
					+ " ticks, so the arrow must be a crit");
		}
		assertSpeed(helper, arrow, VANILLA_FULL_SPEED * ElectricBowItem.LIVE_VELOCITY_MULTIPLIER, "live shot");
		// The line above follows the constant, so on its own it would stay green if the constant were
		// quietly set to 1.0 (a mutation run proved it). This one does not: whatever the multiplier, a
		// powered shot has to leave measurably faster than the best a vanilla bow can do.
		double speed = arrow.getDeltaMovement().length();
		if (speed <= VANILLA_FULL_SPEED * (1 + SPEED_TOLERANCE)) {
			helper.fail("a powered shot must leave faster than a vanilla full draw (" + VANILLA_FULL_SPEED
					+ " b/t), got " + speed);
		}
		long expected = Config.electricBowBuffer - Config.electricBowEuPerShot;
		if (ItemEnergy.get(bow) != expected) {
			helper.fail("one shot must drain exactly electricBowEuPerShot; expected " + expected
					+ ", left " + ItemEnergy.get(bow));
		}
		if (player.getInventory().countItem(Items.ARROW) != 7) {
			helper.fail("a powered shot still consumes its arrow; arrows left "
					+ player.getInventory().countItem(Items.ARROW));
		}
		helper.succeed();
	}

	/**
	 * FUN03: below one shot's worth the bow is NOT a free vanilla bow — it would never need charging if
	 * it were. A full vanilla-length draw still spends the arrow and costs no EU, but the arrow leaves at
	 * {@link ElectricBowItem#FLAT_LAUNCH_SPEED} and is no crit. The second assertion on speed does not
	 * follow the constant: whatever it is set to, a flat shot must stay far below even a half-drawn
	 * vanilla shot, or the penalty is gone.
	 */
	public static void fun03FlatShotFallsShort(GameTestHelper helper) {
		long flat = Config.electricBowEuPerShot - 1;
		ItemStack bow = bow(flat);
		ServerPlayer player = archer(helper, bow, 8);

		if (!release(bow, helper.getLevel(), player, BowItem.MAX_DRAW_DURATION)) {
			helper.fail("a flat bow with arrows still fires — the arrow just falls short");
		}
		AbstractArrow arrow = onlyArrow(helper, player, "flat full-draw shot");
		if (arrow.isCritArrow()) {
			helper.fail("a flat bow must never fire a crit, even at full draw");
		}
		assertSpeed(helper, arrow, ElectricBowItem.FLAT_LAUNCH_SPEED, "flat full-draw shot");
		double speed = arrow.getDeltaMovement().length();
		double halfDrawVanilla = VANILLA_FULL_SPEED * BowItem.getPowerForTime(BowItem.MAX_DRAW_DURATION / 2);
		if (speed >= halfDrawVanilla / 3) {
			helper.fail("a flat shot must be far weaker than even a half-drawn vanilla shot ("
					+ halfDrawVanilla + " b/t), got " + speed);
		}
		if (player.getInventory().countItem(Items.ARROW) != 7) {
			helper.fail("a flat shot still spends its arrow; arrows left "
					+ player.getInventory().countItem(Items.ARROW));
		}
		if (ItemEnergy.get(bow) != flat) {
			helper.fail("a flat bow must spend no EU, left " + ItemEnergy.get(bow));
		}
		helper.succeed();
	}

	/**
	 * FUN04: the {@code electric_bow_charged} flag — what the client draws the lit textures, the draw
	 * frames and the zoom from — follows the charge across the per-shot price in both directions,
	 * including on the shot that takes the bow under it.
	 */
	public static void fun04ChargedFlagFollowsCharge(GameTestHelper helper) {
		ItemStack bow = bow(0);
		if (ElectricBowItem.showsCharged(bow) || bow.has(ModDataComponents.ELECTRIC_BOW_CHARGED.get())) {
			helper.fail("an empty bow must not show as charged");
		}
		ItemEnergy.set(bow, Config.electricBowEuPerShot);
		if (!ElectricBowItem.showsCharged(bow)) {
			helper.fail("a bow holding exactly one shot must show as charged");
		}
		if (ElectricBowItem.drawTicks(bow) != ElectricBowItem.LIVE_DRAW_TICKS) {
			helper.fail("a charged bow must report the live draw time, got " + ElectricBowItem.drawTicks(bow));
		}

		// The last powered shot: fired live, and it takes the flag down with it.
		ServerPlayer player = archer(helper, bow, 4);
		release(bow, helper.getLevel(), player, ElectricBowItem.LIVE_DRAW_TICKS);
		AbstractArrow arrow = onlyArrow(helper, player, "last powered shot");
		assertSpeed(helper, arrow, VANILLA_FULL_SPEED * ElectricBowItem.LIVE_VELOCITY_MULTIPLIER, "last powered shot");
		if (ElectricBowItem.showsCharged(bow)) {
			helper.fail("the shot that spends the last powered charge must turn the bow flat");
		}
		if (ElectricBowItem.drawTicks(bow) != BowItem.MAX_DRAW_DURATION) {
			helper.fail("a flat bow must report vanilla's draw time, got " + ElectricBowItem.drawTicks(bow));
		}
		helper.succeed();
	}

	/** FUN05: a creative archer spends nothing — EU is tool wear, and creative does not wear tools (MOD-081). */
	public static void fun05CreativeSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.getAbilities().instabuild = true;
		ItemStack bow = bow(Config.electricBowBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);

		if (!release(bow, helper.getLevel(), player, ElectricBowItem.LIVE_DRAW_TICKS)) {
			helper.fail("a creative archer must be able to fire without arrows");
		}
		takeArrows(helper, player);
		if (ItemEnergy.get(bow) != Config.electricBowBuffer) {
			helper.fail("a creative archer must spend no EU, left " + ItemEnergy.get(bow));
		}
		helper.succeed();
	}

	/**
	 * FUN06: EU is not ammunition. A charged bow in a survival hand with no arrows refuses to start
	 * drawing, fires nothing on release and spends nothing.
	 */
	public static void fun06NoArrowNoShot(GameTestHelper helper) {
		ItemStack bow = bow(Config.electricBowBuffer);
		ServerPlayer player = archer(helper, bow, 0);

		InteractionResult use = bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		if (use != InteractionResult.FAIL) {
			helper.fail("with no arrows the bow must refuse to draw, got " + use);
		}
		if (release(bow, helper.getLevel(), player, ElectricBowItem.LIVE_DRAW_TICKS)) {
			helper.fail("with no arrows the release must fire nothing");
		}
		if (!takeArrows(helper, player).isEmpty()) {
			helper.fail("an arrow appeared without ammunition");
		}
		if (ItemEnergy.get(bow) != Config.electricBowBuffer) {
			helper.fail("a shot that never happened must cost nothing, left " + ItemEnergy.get(bow));
		}
		helper.succeed();
	}

	/**
	 * FUN07: a release too short to fire (under vanilla's 0.1 power) costs nothing even on a charged
	 * bow — the debit sits behind vanilla's own refusal, not in front of it.
	 */
	public static void fun07UnderDrawnReleaseCostsNothing(GameTestHelper helper) {
		ItemStack bow = bow(Config.electricBowBuffer);
		ServerPlayer player = archer(helper, bow, 8);

		if (release(bow, helper.getLevel(), player, 1)) {
			helper.fail("a one-tick draw must not fire, even charged");
		}
		if (!takeArrows(helper, player).isEmpty()) {
			helper.fail("a one-tick draw spawned an arrow");
		}
		if (ItemEnergy.get(bow) != Config.electricBowBuffer || player.getInventory().countItem(Items.ARROW) != 8) {
			helper.fail("an under-drawn release must cost neither EU nor an arrow");
		}
		helper.succeed();
	}

	/**
	 * FUN08: identity and enchantments. The bow sits in {@code #minecraft:enchantable/bow} (Power,
	 * Punch, Flame, Infinity) and in {@code #c:tools/bow} / {@code #c:tools/ranged_weapon}; it takes the
	 * bow enchantments and NOT Unbreaking or Mending, which would do nothing on a bow without durability.
	 */
	public static void fun08TagsAndEnchants(GameTestHelper helper) {
		ItemStack bow = bow(Config.electricBowBuffer);
		assertInTag(helper, bow, ItemTags.BOW_ENCHANTABLE, "#minecraft:enchantable/bow");
		assertInTag(helper, bow, C_BOW, "#c:tools/bow");
		assertInTag(helper, bow, C_RANGED_WEAPON, "#c:tools/ranged_weapon");
		if (!bow.isEnchantable()) {
			helper.fail("the bow must be enchantable at the table");
		}
		ServerLevel level = helper.getLevel();
		for (ResourceKey<Enchantment> key : List.of(Enchantments.POWER, Enchantments.PUNCH,
				Enchantments.FLAME, Enchantments.INFINITY)) {
			if (!canEnchant(enchant(level, key), bow)) {
				helper.fail(key.identifier() + " rejected electric_bow — not in the enchantment's supported_items");
			}
		}
		for (ResourceKey<Enchantment> key : List.of(Enchantments.UNBREAKING, Enchantments.MENDING)) {
			if (canEnchant(enchant(level, key), bow)) {
				helper.fail(key.identifier() + " accepted electric_bow — useless on an item without durability");
			}
		}
		helper.succeed();
	}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	private static Holder<Enchantment> enchant(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void assertInTag(GameTestHelper helper, ItemStack stack, TagKey<Item> tag, String tagName) {
		if (!stack.is(h -> h.is(tag))) {
			helper.fail("electric_bow is not in " + tagName + " (membership tag missing)");
		}
	}

	// MOD-498 — Enchantment#canEnchant(ItemStack) is deprecated by NeoForge only; vanilla leaves it plain.
	// This scenario lives in common/ and is replayed by the Fabric lane too, so it has to ask the vanilla way.
	@SuppressWarnings("deprecation")
	private static boolean canEnchant(Holder<Enchantment> enchantment, ItemStack stack) {
		return enchantment.value().canEnchant(stack);
	}
}

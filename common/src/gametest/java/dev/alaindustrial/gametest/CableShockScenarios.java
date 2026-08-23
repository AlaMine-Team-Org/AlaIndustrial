package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;
import static dev.alaindustrial.gametest.AlaGameTestHelper.detachedSurvivalPlayer;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.energy.ShockGuardMaterial;
import dev.alaindustrial.core.energy.ShockInsulation;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Loader-neutral MOD-260 acceptance scenarios, invoked by both GameTest lanes. */
public final class CableShockScenarios {
	private static final BlockPos GENERATOR = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos BOX = new BlockPos(3, 2, 1);
	/** Directly under {@link #CABLE} — where MOD-279's stand and a DOWN connection compete for space. */
	private static final BlockPos BELOW_CABLE = new BlockPos(2, 1, 1);

	private CableShockScenarios() {
	}

	/** Bare needs committed flow; insulation, config-off and stopped flow are all safe. */
	public static void energizedBareOnly(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);

		buildLine(helper, ModContent.COPPER_CABLE.get());
		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		var cableState = helper.getBlockState(CABLE);
		var insideShape = cableState.getEntityInsideCollisionShape(
				helper.getLevel(), helper.absolutePos(CABLE), player);
		var solidShape = cableState.getCollisionShape(
				helper.getLevel(), helper.absolutePos(CABLE));
		if (insideShape.isEmpty() || Shapes.equal(insideShape, Shapes.block())) {
			helper.fail("cable contact shape was empty or expanded to a full block");
			return;
		}
		if (!Shapes.joinIsNotEmpty(insideShape, solidShape, BooleanOp.ONLY_FIRST)) {
			helper.fail("cable contact shape has no reachable shell outside solid collision");
			return;
		}
		if (bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			helper.fail("unenergized bare cable was marked hazardous");
			return;
		}

		energize(helper);
		if (!bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			CableBlockEntity segment = helper.getBlockEntity(CABLE, CableBlockEntity.class);
			helper.fail("energized bare shock eligibility was rejected; energized="
					+ (segment != null && segment.isEnergizedForShock()));
			return;
		}
		// Resolve the data-driven type in the live server registry; failure here catches a missing or
		// malformed data/alaindustrial/damage_type/electric_shock.json on either loader.
		ModDamageTypes.electricShock(helper.getLevel());

		player.invulnerableTime = 0;
		boolean shockEnabledBeforeTest = Config.bareCableShockEnabled;
		Config.bareCableShockEnabled = false;
		try {
			if (bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
				helper.fail("config-off cable remained hazardous");
				return;
			}
		} finally {
			Config.bareCableShockEnabled = shockEnabledBeforeTest;
		}

		helper.setBlock(CABLE, Blocks.AIR);
		helper.setBlock(CABLE, ModContent.INSULATED_COPPER_CABLE.get());
		tick(helper, be(helper, CABLE));
		NetworkManager.tickAll(helper.getLevel());
		if (!(be(helper, CABLE) instanceof CableBlockEntity insulatedSegment)
				|| !insulatedSegment.isEnergizedForShock()) {
			helper.fail("insulated fixture was not carrying energy");
			return;
		}
		CableBlock insulated = (CableBlock) ModContent.INSULATED_COPPER_CABLE.get();
		if (insulated.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			helper.fail("energized insulated cable was marked hazardous");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-269: the hazard reaches past the wire model, stops at solid cover, and collapses back to
	 * MOD-260's contact-only rule when the radius is configured to zero.
	 *
	 * <p>Asserts {@link CableBlockEntity#isWithinShockReach} rather than health loss: the reach rule is
	 * what this task added, while whether a reachable player is actually hurt is MOD-260's contract and
	 * is already pinned by {@link #energizedBareOnly}. Testing health here would re-test that and add a
	 * dependency on the invulnerability window's timing.
	 */
	public static void proximityRadiusRespectsCoverAndConfig(GameTestHelper helper) {
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		BlockPos cable = helper.absolutePos(CABLE);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);

		BlockPos beside = CABLE.offset(0, 0, 1);
		BlockPos twoAway = CABLE.offset(0, 0, 2);
		double radiusBefore = Config.bareCableShockProximityRadius;
		try {
			// Beside the cable, not intersecting its 6px model: out of reach before MOD-269, in reach now.
			Config.bareCableShockProximityRadius = 0.5;
			snapToCentre(helper, player, beside);
			if (!CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("player one cell from an energized bare cable was out of shock reach");
				return;
			}

			// Same spot, radius off: the reach rule must vanish entirely, not merely shrink.
			Config.bareCableShockProximityRadius = 0.0;
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("radius 0 still reported reach; MOD-260 contact-only behaviour was not restored");
				return;
			}

			// The shipped 0.5 also keeps the hazard off anyone a full cell further out.
			Config.bareCableShockProximityRadius = 0.5;
			snapToCentre(helper, player, twoAway);
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("radius 0.5 reached two cells; the default is meant to be arm's length");
				return;
			}

			// Cover only becomes reachable once an operator widens the radius: at 0.5 the player is
			// already flush against the cable's own cell, leaving no room for a block in between. Widen
			// it, then assert the clear-line rule at a fixed distance — reachable without cover, not
			// reachable with it. Comparing the same position both ways is what proves the difference is
			// the cover and not the distance.
			Config.bareCableShockProximityRadius = 2.0;
			snapToCentre(helper, player, twoAway);
			if (!CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("widened radius did not reach an uncovered player two cells away");
				return;
			}
			helper.setBlock(beside, Blocks.STONE);
			snapToCentre(helper, player, twoAway);
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("solid cover between cable and player did not block the shock");
				return;
			}
		} finally {
			Config.bareCableShockProximityRadius = radiusBefore;
			helper.setBlock(beside, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * Places the mock player at the centre of {@code relative}. Uses {@code setPos} rather than
	 * {@code snapTo}: the latter notifies the client through {@code connection}, which a mock player
	 * created by {@code makeMockServerPlayer} does not have.
	 */
	private static void snapToCentre(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		BlockPos abs = helper.absolutePos(relative);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
	}

	/** A segment with retained EU becomes safe once no transfer has occurred for more than one tick. */
	public static void retainedBufferIsSafe(GameTestHelper helper) {
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		CableBlockEntity cable = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (cable == null || cable.getEnergyStorage().getAmount() <= 0 || !cable.isEnergizedForShock()) {
			helper.fail("fixture cable was not energized with a retained segment buffer");
			return;
		}

		helper.setBlock(GENERATOR, Blocks.AIR);
		helper.setBlock(BOX, Blocks.AIR);
		helper.runAfterDelay(3, () -> {
			if (cable.getEnergyStorage().getAmount() <= 0) {
				helper.fail("fixture drained its retained EU, so the residual-buffer guard was not exercised");
				return;
			}
			if (cable.isEnergizedForShock()) {
				helper.fail("cable remained hazardous without a committed transfer");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * MOD-279: an insulating stand gates the shock, and a blocked shock opens a contact window so the
	 * chance is per contact rather than re-rolled every tick.
	 *
	 * <p>Driven at the two <b>deterministic</b> ends of the chance range (0 = always blocks, 1 = never
	 * blocks) rather than by sampling a middle value. A statistical assertion would need a seeded
	 * {@code RandomSource} the world does not hand out, and would trade a precise contract for a flaky
	 * one; the ladder's actual numbers are pinned by the L1 {@code ShockGuardMaterialTest} instead.
	 *
	 * <p>Asserts {@link CableBlock#passesShockGuard} rather than {@link CableBlock#tryShockPlayer} for
	 * the same reason {@link #energizedBareOnly} asserts eligibility rather than health loss: the
	 * landing path calls {@code hurtServer}, which notifies the player's {@code connection} — and a
	 * mock player created by {@code makeMockServerPlayer} has none.
	 */
	public static void shockGuardGatesShockAndOpensGraceWindow(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		BlockPos cablePos = helper.absolutePos(CABLE);
		CableBlockEntity cable = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (cable == null) {
			helper.fail("fixture cable had no block entity");
			return;
		}

		double woodBefore = Config.shockGuardWoodHitChance;
		int graceBefore = Config.shockGuardGraceTicks;
		try {
			// No stand: the MOD-260 contract is untouched — the guard never intercepts a bare segment.
			player.invulnerableTime = 0;
			if (!bare.passesShockGuard(helper.getLevel(), cablePos, player)) {
				helper.fail("bare cable without a stand was intercepted; MOD-260 regressed");
				return;
			}
			if (player.invulnerableTime != 0) {
				helper.fail("the no-stand path must not touch the invulnerability window");
				return;
			}

			cable.setShockGuard(Blocks.OAK_PLANKS);
			if (cable.shockGuard() != ShockGuardMaterial.WOOD) {
				helper.fail("stand did not persist on the block entity, or its category was misderived");
				return;
			}
			// Standing on the cable is what the chance governs, so put the player there for the rolls
			// below; the side/below immunity is asserted separately by shockGuardShieldsFromTheSide.
			standOnCable(helper, player);

			// A stand that blocks everything must stop the shock AND leave a contact window behind, so the
			// next tick of contact does not immediately roll again.
			Config.shockGuardWoodHitChance = 0.0;
			Config.shockGuardGraceTicks = 7;
			player.invulnerableTime = 0;
			if (bare.passesShockGuard(helper.getLevel(), cablePos, player)) {
				helper.fail("a stand with hit chance 0 still let the shock through");
				return;
			}
			if (player.invulnerableTime != 7) {
				helper.fail("blocked shock left no grace window; invulnerableTime=" + player.invulnerableTime);
				return;
			}
			// Still inside that window: eligibility itself must now say no, which is what stops the reroll.
			if (bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
				helper.fail("grace window did not suppress the next contact tick");
				return;
			}

			// A stand that blocks nothing must behave exactly like no stand at all.
			Config.shockGuardWoodHitChance = 1.0;
			player.invulnerableTime = 0;
			if (!bare.passesShockGuard(helper.getLevel(), cablePos, player)) {
				helper.fail("a stand with hit chance 1 wrongly absorbed the shock");
				return;
			}
		} finally {
			Config.shockGuardWoodHitChance = woodBefore;
			Config.shockGuardGraceTicks = graceBefore;
		}
		helper.succeed();
	}

	/**
	 * MOD-279: a stand shields anyone beside or under the wire outright, and only the player standing
	 * on top of the cable is still at risk. This is the rule that makes a stood run safe to walk past,
	 * and it must hold for the MOD-269 proximity sweep as well as for direct contact — which is why it
	 * lives in {@link CableBlock#shouldShockPlayer} rather than in the damage path.
	 */
	public static void shockGuardShieldsFromTheSide(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		BlockPos cablePos = helper.absolutePos(CABLE);
		CableBlockEntity cable = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (cable == null) {
			helper.fail("fixture cable had no block entity");
			return;
		}

		// Baseline: with no stand, a player at the cable's own level is eligible — MOD-260 behaviour.
		snapToCentre(helper, player, CABLE);
		player.invulnerableTime = 0;
		if (!bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("bare cable did not reach a player at its own level; MOD-260 regressed");
			return;
		}

		cable.setShockGuard(Blocks.OAK_PLANKS);

		// Beside/below the wire the stand is between them: no shock at all, whatever the chance says.
		player.invulnerableTime = 0;
		if (bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("a stand did not shield a player standing at the cable's own level");
			return;
		}

		// On top of the wire the player is above the plate and still eligible.
		standOnCable(helper, player);
		player.invulnerableTime = 0;
		if (!bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("a stand wrongly shielded a player standing on top of the cable");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-466: a worn insulated set protects against a bare cable, by the piece, and pays for it in
	 * durability — and blocking a shock opens a contact window so the cost is per contact rather than
	 * per tick.
	 *
	 * <p><b>This one can drive the real damage path, unlike its MOD-279 neighbours.</b> A full set
	 * reduces the hit to zero, so {@link CableBlock#tryShockPlayer} returns before {@code hurtServer} —
	 * the exact call that needs a {@code connection} a mock player does not have. That makes the
	 * end-to-end behaviour (wear, grace window) assertable here rather than only its predicate. The
	 * partial cases stop at {@link CableBlock#insulatedShockDamage}, which is side-effect-free, for the
	 * same reason the rest of this file does.
	 *
	 * <p>The control case is the point of the test, not decoration: without it, a mechanic that
	 * reduced <em>every</em> shock to zero would pass every remaining assertion.
	 */
	public static void insulatedSetProtectsByThePieceAndWearsOut(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		BlockPos cablePos = helper.absolutePos(CABLE);
		float raw = CableType.COPPER.shockDamage();

		// The fixture has to be a live hazard, or every "no damage" assertion below is vacuous.
		snapToCentre(helper, player, CABLE);
		player.invulnerableTime = 0;
		if (!bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("fixture cable was not a hazard; nothing below would prove anything");
			return;
		}

		// Control: a bare player takes the full hit, and nothing wears out.
		if (CableBlock.wornInsulatingPieces(player) != 0) {
			helper.fail("a naked mock player was counted as wearing insulation");
			return;
		}
		if (CableBlock.insulatedShockDamage(raw, player) != raw) {
			helper.fail("an unprotected player's shock was reduced; expected " + raw
					+ " got " + CableBlock.insulatedShockDamage(raw, player));
			return;
		}

		wearInsulatedSet(player);
		if (CableBlock.wornInsulatingPieces(player) != 4) {
			helper.fail("a full insulated set counted as " + CableBlock.wornInsulatingPieces(player)
					+ " pieces; is #alaindustrial:shock_insulating populated?");
			return;
		}
		if (CableBlock.insulatedShockDamage(raw, player) != 0.0f) {
			helper.fail("a full set left " + CableBlock.insulatedShockDamage(raw, player)
					+ " damage behind; it must stop the hit outright");
			return;
		}

		// The real path: no damage lands, the suit is billed, and a contact window opens. Without that
		// window both hazard paths re-enter next tick and the set is charged twenty times a second.
		player.invulnerableTime = 0;
		if (bare.tryShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("a full set still let the shock land");
			return;
		}
		if (player.invulnerableTime != Config.shockGuardGraceTicks) {
			helper.fail("an absorbed shock left no contact window; invulnerableTime="
					+ player.invulnerableTime);
			return;
		}
		int helmetWear = player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue();
		if (helmetWear != ShockInsulation.wearFor(raw, Config.bareCableShockInsulationDamagePerDurability)) {
			helper.fail("the set was billed " + helmetWear + " durability for an LV shock; expected "
					+ ShockInsulation.wearFor(raw, Config.bareCableShockInsulationDamagePerDurability));
			return;
		}
		// Still inside the window: eligibility itself now says no, which is what stops the re-entry.
		if (bare.shouldShockPlayer(helper.getLevel(), cablePos, player)) {
			helper.fail("the contact window did not suppress the next tick of contact");
			return;
		}

		// Three pieces are worth three quarters of a set, not nothing — the whole point of per-piece.
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		if (CableBlock.wornInsulatingPieces(player) != 3) {
			helper.fail("removing the helmet did not drop the worn count");
			return;
		}
		float partial = CableBlock.insulatedShockDamage(raw, player);
		if (partial <= 0.0f || partial >= raw) {
			helper.fail("a three-piece set gave " + partial + " damage; expected strictly between 0 and "
					+ raw);
			return;
		}
		helper.succeed();
	}

	/** Dresses the player in all four parts of the insulated set. */
	private static void wearInsulatedSet(ServerPlayer player) {
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.INSULATED_HELMET.get()));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.INSULATED_CHESTPLATE.get()));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.INSULATED_LEGGINGS.get()));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.INSULATED_BOOTS.get()));
	}

	/** Puts the player on top of the cable, where a stand no longer shields them. */
	private static void standOnCable(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(CABLE);
		player.setPos(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
	}

	/**
	 * MOD-279 placement rules: planks install a stand on a bare segment and cost an item, while an
	 * insulated grade (no hazard left to reduce) and a downward-connected segment (the stand's own
	 * volume is taken) both refuse without consuming anything.
	 */
	public static void shockGuardInstallRules(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);
		buildLine(helper, ModContent.COPPER_CABLE.get());

		// A non-oak species proves the exact block is stored, not a canonical stand-in for its category.
		if (!installStand(helper, player, CABLE, new ItemStack(Items.BIRCH_PLANKS, 2))) {
			helper.fail("planks did not install a stand on a bare cable");
			return;
		}
		if (player.getMainHandItem().getCount() != 1) {
			helper.fail("installing a stand did not consume exactly one item; left="
					+ player.getMainHandItem().getCount());
			return;
		}
		CableBlockEntity stood = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (stood == null || stood.shockGuardBlock() != Blocks.BIRCH_PLANKS) {
			helper.fail("the installed stand did not keep the exact block the player used");
			return;
		}

		// Empty hand takes it back off and returns the item.
		if (!removeStand(helper, player, CABLE)) {
			helper.fail("empty-handed use did not remove the stand");
			return;
		}

		// Logs and dyed wool are stands too, not just planks.
		if (!installStand(helper, player, CABLE, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 1))
				|| !removeStand(helper, player, CABLE)) {
			helper.fail("a stripped log was not accepted as a wooden stand");
			return;
		}
		if (!installStand(helper, player, CABLE, new ItemStack(Items.WOOL.blue(), 1))
				|| !removeStand(helper, player, CABLE)) {
			helper.fail("dyed wool was not accepted as a stand");
			return;
		}
		if (!installStand(helper, player, CABLE, new ItemStack(Items.STAINED_GLASS.lime(), 1))
				|| !removeStand(helper, player, CABLE)) {
			helper.fail("stained glass was not accepted as a stand");
			return;
		}

		// An insulated grade has no shock to reduce, so a stand there would be a no-op decoration.
		helper.setBlock(CABLE, Blocks.AIR);
		helper.setBlock(CABLE, ModContent.INSULATED_COPPER_CABLE.get());
		if (installStand(helper, player, CABLE, new ItemStack(Items.OAK_PLANKS, 2))) {
			helper.fail("insulated cable accepted an insulating stand");
			return;
		}
		if (player.getMainHandItem().getCount() != 2) {
			helper.fail("a refused install still consumed an item");
			return;
		}

		// A segment wired downward has no room: the DOWN arm fills the same cell floor.
		helper.setBlock(CABLE, Blocks.AIR);
		helper.setBlock(CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(BELOW_CABLE, ModContent.COPPER_CABLE.get());
		if (!helper.getBlockState(CABLE).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN))) {
			helper.fail("fixture did not form the downward connection the rule is about");
			return;
		}
		if (installStand(helper, player, CABLE, new ItemStack(Items.OAK_PLANKS, 2))) {
			helper.fail("a downward-connected segment accepted a stand");
			return;
		}
		helper.succeed();
	}

	/** MOD-279: a connection forming under an existing stand pops it back out as an item. */
	public static void shockGuardPopsWhenDownConnectionAppears(GameTestHelper helper) {
		ServerPlayer player = detachedSurvivalPlayer(helper);
		buildLine(helper, ModContent.COPPER_CABLE.get());
		if (!installStand(helper, player, CABLE, new ItemStack(Items.OAK_PLANKS, 1))) {
			helper.fail("fixture stand was not installed");
			return;
		}

		helper.setBlock(BELOW_CABLE, ModContent.COPPER_CABLE.get());
		tick(helper, be(helper, CABLE));

		CableBlockEntity cable = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (cable == null || cable.shockGuard().isPresent()) {
			helper.fail("stand survived a downward connection claiming its space");
			return;
		}
		helper.succeed();
	}

	/**
	 * Runs the real right-click path ({@code BlockState.useItemOn}, the same entry vanilla uses) with
	 * {@code held} in the player's main hand, and answers whether a stand ended up installed.
	 */
	private static boolean installStand(GameTestHelper helper, ServerPlayer player, BlockPos relative,
			ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos abs = helper.absolutePos(relative);
		helper.getBlockState(relative).useItemOn(player.getMainHandItem(), helper.getLevel(), player,
				InteractionHand.MAIN_HAND, hitOn(abs));
		return helper.getBlockEntity(relative, CableBlockEntity.class) instanceof CableBlockEntity cable
				&& cable.shockGuard().isPresent();
	}

	/** Empty-handed right-click, answering whether the stand is gone afterwards. */
	private static boolean removeStand(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockPos abs = helper.absolutePos(relative);
		helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player, hitOn(abs));
		return helper.getBlockEntity(relative, CableBlockEntity.class) instanceof CableBlockEntity cable
				&& !cable.shockGuard().isPresent();
	}

	private static BlockHitResult hitOn(BlockPos abs) {
		return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
	}

	private static void buildLine(GameTestHelper helper, Block cable) {
		helper.setBlock(GENERATOR, ModContent.GENERATOR.get());
		helper.setBlock(CABLE, cable);
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		tick(helper, be(helper, GENERATOR));
		tick(helper, be(helper, CABLE));
		tick(helper, be(helper, BOX));
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().setAmountUntracked(0);
			generator.setChanged();
		}
		if (be(helper, BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(0);
			box.setChanged();
		}
	}

	private static void energize(GameTestHelper helper) {
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().setAmountUntracked(Config.generatorBuffer);
			generator.setChanged();
		}
		for (int i = 0; i < 3; i++) {
			NetworkManager.tickAll(helper.getLevel());
		}
	}

}

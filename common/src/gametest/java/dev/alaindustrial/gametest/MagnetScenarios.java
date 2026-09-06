package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import dev.alaindustrial.item.tool.MagnetTier;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electromagnet (MOD-132, suite TC-MAGNET-001). Same pattern as
 * {@link ElectricDrillScenarios}/{@link EnergyPackScenarios}: plain {@code GameTestHelper} bodies wrapped
 * by the Fabric {@code MagnetGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * via {@code NeoForgeGameTests} — both loaders exercise the SAME pull logic.
 *
 * <p>The pull is driven by calling {@link MagnetItem#pullSingle} on a single, <em>detached</em>
 * {@link ItemEntity} (constructed relative to the player but never added to the world). This is the
 * per-item core of {@code magnetStep} without the world scan — deliberately so: on a shared gametest
 * server the live scan's radius would pick up the dropped items of neighbouring tests running a few
 * blocks away (and a freshly {@code addFreshEntity}-ed item is not reliably indexed for a same-tick
 * scan), which made count/EU assertions flaky. A detached entity is visible only to this test, so every
 * assertion is deterministic. Numbers come from {@link Config} (magnetBuffer, magnetEuPerItem,
 * magnetRange), the balance source of truth.
 */
public final class MagnetScenarios {

	private MagnetScenarios() {}

	private static ItemStack magnet(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTROMAGNET.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * A loose iron-ingot drop {@code (dx, dy, dz)} from the player, with the given pickup delay —
	 * <em>detached</em> (never added to the world), so only this test can see or move it.
	 */
	private static ItemEntity dropNear(GameTestHelper helper, ServerPlayer player, double dx, double dy, double dz,
			int pickupDelay) {
		Vec3 p = player.position();
		ItemEntity item = new ItemEntity(helper.getLevel(), p.x + dx, p.y + dy, p.z + dz,
				new ItemStack(Items.IRON_INGOT));
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(pickupDelay);
		return item;
	}

	/**
	 * TC-MAGNET-001-FUN01 — a charged, enabled magnet pulls a nearby drop toward the player
	 *     (velocity points at the player) and spends exactly magnetEuPerItem.
	 */
	public static void fun01PullsNearbyDrop(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (!MagnetItem.pullSingle(magnet, player, item)) {
			helper.fail("a charged, enabled magnet must pull a nearby drop");
		}
		if (item.getDeltaMovement().x >= 0.0) {
			helper.fail("pulled item must accelerate toward the player (-x), got dx=" + item.getDeltaMovement().x);
		}
		long expected = Config.magnetBuffer - Config.magnetEuPerItem;
		if (ItemEnergy.get(magnet) != expected) {
			helper.fail("pulling one item must spend magnetEuPerItem; left " + ItemEnergy.get(magnet)
					+ ", expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN02 — a flat magnet (0 EU) pulls nothing and the drop keeps zero velocity.
	 */
	public static void fun02FlatMagnetInert(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(0);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a flat magnet must not move anything");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN03 — a disabled magnet (toggled off) pulls nothing even while charged.
	 */
	public static void fun03DisabledMagnetInert(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);
		MagnetItem.setEnabled(magnet, false);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a disabled magnet must not move anything");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("a disabled magnet must spend no EU");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN04 — a drop still on its pickup delay (a fresh Q-drop) is left alone, so
	 *     the magnet does not instantly suck back what was just thrown.
	 */
	public static void fun04RespectsPickupDelay(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 40);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("an item on pickup delay must not be pulled");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("an item on pickup delay must cost no EU");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN05 — the spherical range refinement: a drop at a cube corner (dx=dz=range−0.4, so
	 *     distance ≈ √2·(range−0.4) &gt; range) sits inside the broad-phase {@code inflate(range)} cube but
	 *     outside the sphere, and must NOT be pulled or charged. A control drop the same axis-distance
	 *     straight ahead IS inside the sphere and IS pulled — proving it is the sphere trim
	 *     ({@code canPull}'s {@code distanceToSqr}), which a cube-only test would leave unexercised.
	 */
	public static void fun05OutOfRangeIgnored(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		double corner = Config.magnetRange - 0.4; // inside inflate(range) cube, outside the sphere (√2·corner > range)
		ItemEntity outside = dropNear(helper, player, corner, 0.5, corner, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (MagnetItem.pullSingle(magnet, player, outside) || outside.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a drop inside the AABB but outside the range sphere must not be pulled");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("an out-of-sphere drop must cost no EU");
		}
		// Control: a drop the same axis-distance straight ahead is inside the sphere and IS pulled.
		ItemEntity inside = dropNear(helper, player, corner, 0.5, 0.0, 0);
		if (!MagnetItem.pullSingle(magnet, player, inside)) {
			helper.fail("a drop within the range sphere must still be pulled (control)");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN06 — the toggle entry point {@link MagnetItem#use}: a plain (non-sneak) right-click
	 *     passes through and leaves the magnet enabled; a sneak right-click flips it off, and again back on.
	 *     Guards the {@code isShiftKeyDown} gate and the server-side mutation that {@code per01} (static
	 *     helpers only) does not exercise.
	 */
	public static void fun06ToggleViaUse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		ItemStack magnet = magnet(Config.magnetBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, magnet);

		player.setShiftKeyDown(false);
		InteractionResult plain = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (plain != InteractionResult.PASS || !MagnetItem.isEnabled(magnet)) {
			helper.fail("a non-sneak use must pass through and leave the magnet enabled");
		}

		player.setShiftKeyDown(true);
		InteractionResult off = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (off != InteractionResult.SUCCESS || MagnetItem.isEnabled(magnet)) {
			helper.fail("a sneak use must toggle the magnet off");
		}

		InteractionResult on = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (on != InteractionResult.SUCCESS || !MagnetItem.isEnabled(magnet)) {
			helper.fail("a second sneak use must toggle the magnet back on");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-PER01 — the on/off flag round-trips: enabled is the absent-component default,
	 *     disabling stores it, re-enabling removes it (stacks stay component-identical to a fresh magnet).
	 */
	public static void per01ToggleRoundTrip(GameTestHelper helper) {
		ItemStack magnet = magnet(Config.magnetBuffer);
		if (!MagnetItem.isEnabled(magnet) || magnet.has(ModDataComponents.MAGNET_ENABLED.get())) {
			helper.fail("a fresh magnet must read enabled with no component present");
		}
		MagnetItem.setEnabled(magnet, false);
		if (MagnetItem.isEnabled(magnet) || !Boolean.FALSE.equals(magnet.get(ModDataComponents.MAGNET_ENABLED.get()))) {
			helper.fail("disabling must store false and read disabled");
		}
		MagnetItem.setEnabled(magnet, true);
		if (!MagnetItem.isEnabled(magnet) || magnet.has(ModDataComponents.MAGNET_ENABLED.get())) {
			helper.fail("re-enabling must remove the component (back to the fresh default)");
		}
		helper.succeed();
	}

	// --- MOD-580: the advanced grade -------------------------------------------------------------

	/**
	 * TC-MAGNET-002-FUN01 — the advanced grade reaches further than the basic one.
	 *
	 * <p>Asserts the RELATION, not the number: a config edit that raises the basic reach past the
	 * advanced one is exactly the regression worth catching, and a test pinned to "9" would go on
	 * passing through it.
	 */
	public static void tcMagnet002Fun01_advancedReachesFurther(GameTestHelper helper) {
		int basic = MagnetTier.BASIC.range();
		int advanced = MagnetTier.ADVANCED.range();
		if (advanced <= basic) {
			helper.fail("the advanced magnet must reach further than the basic one: "
					+ advanced + " vs " + basic);
		}
		if (MagnetTier.BASIC.pullsExperience()) {
			helper.fail("experience is the advanced grade's own mechanic; the basic one must not pull it");
		}
		if (!MagnetTier.ADVANCED.pullsExperience()) {
			helper.fail("the advanced grade must pull experience");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-002-FUN02 — the recipe takes a magnet in ANY state: charged, flat, switched off.
	 *
	 * <p>The owner asked for this by name, and it is worth a test rather than a promise. It holds today
	 * because {@code Ingredient.test} is {@code input.is(values)} — item identity only, components not
	 * compared — but that is vanilla's decision, not ours: a component-aware ingredient added here later
	 * would silently start rejecting the charged magnet a player actually carries, which is the ONLY
	 * kind they ever have.
	 */
	public static void tcMagnet002Fun02_recipeTakesAnyMagnetState(GameTestHelper helper) {
		ItemStack full = magnet(Config.magnetBuffer);
		assertCraftsInto(helper, full, "a fully charged magnet");

		ItemStack flat = magnet(0);
		assertCraftsInto(helper, flat, "a flat magnet");

		ItemStack off = magnet(Config.magnetBuffer / 2);
		MagnetItem.setEnabled(off, false);
		assertCraftsInto(helper, off, "a half-charged magnet that is switched off");

		helper.succeed();
	}

	/** Puts {@code magnet} in the middle of the tier-2 grid and asserts the advanced magnet comes out. */
	private static void assertCraftsInto(GameTestHelper helper, ItemStack magnet, String label) {
		List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
		grid.set(0, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		grid.set(1, new ItemStack(ModContent.COPPER_COIL.get()));
		grid.set(2, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		grid.set(3, new ItemStack(ModContent.ADVANCED_CIRCUIT.get()));
		grid.set(4, magnet);
		grid.set(5, new ItemStack(ModContent.ADVANCED_CIRCUIT.get()));
		grid.set(6, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		grid.set(7, new ItemStack(ModContent.ENERGY_CRYSTAL.get()));
		grid.set(8, new ItemStack(ModContent.ELECTRUM_REINFORCED_PLATE.get()));
		assertCraft(helper, grid, ModContent.ELECTROMAGNET_ADVANCED.get(), label);
	}

	private static void assertCraft(GameTestHelper helper, List<ItemStack> grid, ItemLike expected,
			String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail("the tier-2 recipe did not resolve with " + label);
			return;
		}
		ItemStack output = recipe.value().assemble(input);
		if (!output.is(expected.asItem())) {
			helper.fail("with " + label + " the grid produced " + output + " instead of the advanced magnet");
		}
	}

	/**
	 * TC-MAGNET-002-CON01 — the tooltip must describe THIS grade, not the one whose keys were typed in.
	 *
	 * <p>Found in play: the advanced magnet's tooltip said "radius 5 blocks" while it pulled from nine,
	 * and called itself an item magnet while it collected experience — the lines were built from the
	 * literal {@code item.alaindustrial.electromagnet.*} prefix and from {@code Config.magnetRange},
	 * both of which belong to the BASIC grade. The keys now come from the item's own description id,
	 * which is why this test asserts on the id rather than on any text.
	 */
	public static void tcMagnet002Con01_tooltipKeysFollowTheItem(GameTestHelper helper) {
		ItemStack advanced = new ItemStack(ModContent.ELECTROMAGNET_ADVANCED.get());
		String base = advanced.getItem().getDescriptionId();

		List<TranslatableContents> lines = tooltipLines(advanced);
		if (lines.isEmpty()) {
			helper.fail("the advanced magnet produced no tooltip at all");
			return;
		}
		for (TranslatableContents line : lines) {
			if (!line.getKey().startsWith(base + ".")) {
				helper.fail("the advanced magnet's tooltip borrows another item's key: " + line.getKey());
				return;
			}
		}

		TranslatableContents desc = lines.stream()
				.filter(line -> line.getKey().equals(base + ".desc"))
				.findFirst().orElse(null);
		if (desc == null) {
			helper.fail("the advanced magnet's tooltip has no description line");
			return;
		}
		Object shown = desc.getArgs().length > 0 ? desc.getArgs()[0] : null;
		if (!Integer.valueOf(MagnetTier.ADVANCED.range()).equals(shown)) {
			helper.fail("the tooltip advertises radius " + shown + " while the magnet pulls from "
					+ MagnetTier.ADVANCED.range());
		}

		boolean mentionsExperience = lines.stream()
				.anyMatch(line -> line.getKey().equals(base + ".experience"));
		if (!mentionsExperience) {
			helper.fail("a grade that pulls experience must say so in its tooltip");
		}
		helper.succeed();
	}

	/**
	 * The item's OWN tooltip lines, as translatable contents.
	 *
	 * <p>{@code Item#appendHoverText} is soft-deprecated by vanilla but is the only hook that yields
	 * what the item computes for itself — the same reasoning already written down on
	 * {@code AssemblerScenarios#tooltipLine}.
	 */
	@SuppressWarnings("deprecation")
	private static List<TranslatableContents> tooltipLines(ItemStack stack) {
		List<Component> raw = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT,
				raw::add, TooltipFlag.NORMAL);
		List<TranslatableContents> out = new ArrayList<>();
		for (Component line : raw) {
			if (line.getContents() instanceof TranslatableContents translatable) {
				out.add(translatable);
			}
		}
		return out;
	}

	/**
	 * TC-MAGNET-002-FUN03 — the advanced grade actually moves an experience orb, and only outside the
	 * ring vanilla already covers.
	 *
	 * <p>Driven on a detached orb for the same reason {@link MagnetItem#pullSingle} is: the live scan
	 * finds targets in the world, and on a shared gametest server one test's orbs drift into another's
	 * radius.
	 */
	public static void tcMagnet002Fun03_pullsExperienceBeyondVanillaReach(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack magnet = new ItemStack(ModContent.ELECTROMAGNET_ADVANCED.get());
		ItemEnergy.set(magnet, MagnetTier.ADVANCED.buffer());

		// Halfway between vanilla's ring and the magnet's edge. Not "reach + 1": the distance is measured
		// from a point at half the player's eye height, so an orb placed at exactly the edge horizontally
		// lands just OUTSIDE the sphere and the test would fail on its own geometry.
		double far = (Config.magnetVanillaOrbReach + MagnetTier.ADVANCED.range()) / 2.0;
		ExperienceOrb outside = new ExperienceOrb(player.level(),
				player.getX() + far, player.getY(), player.getZ(), 1);
		outside.setDeltaMovement(Vec3.ZERO);
		if (!MagnetItem.pullOrb(magnet, player, outside)) {
			helper.fail("an orb beyond vanilla's own reach must be pulled by the advanced magnet");
		}
		if (outside.getDeltaMovement().x >= 0) {
			helper.fail("the pulled orb must move back toward the player, not away from them");
		}

		ExperienceOrb inside = new ExperienceOrb(player.level(),
				player.getX() + 1.0, player.getY(), player.getZ(), 1);
		if (MagnetItem.pullOrb(magnet, player, inside)) {
			helper.fail("an orb vanilla already collects must cost the player no EU");
		}

		ItemStack basic = magnet(Config.magnetBuffer);
		if (MagnetItem.pullOrb(basic, player, outside)) {
			helper.fail("experience is the advanced grade's own mechanic; the basic magnet must not pull it");
		}
		helper.succeed();
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import java.util.List;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electric Drill (MOD-079, suite TC-DRILL-001). Same pattern as
 * {@link EnergyPackScenarios}/{@link TemperedIronToolScenarios}: plain {@code GameTestHelper} bodies
 * wrapped by the Fabric {@code ElectricDrillGameTest} suite and registered on the NeoForge
 * {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders exercise the SAME logic.
 *
 * <p>Numbers come from {@link Config} (electricDrillBuffer, electricDrillEuPerBlock,
 * electricDrillInputRate) — the balance source of truth. The drill's mining behaviour is driven by
 * calling {@link Item#getDestroySpeed}/{@link Item#mineBlock} directly, so every case is deterministic.
 */
public final class ElectricDrillScenarios {

	private ElectricDrillScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);
	private static final BlockPos ORE = new BlockPos(1, 2, 3);
	/** The convention (c:) mining-tool identity tag — built by hand (not the alaindustrial namespace). */
	private static final TagKey<Item> C_MINING_TOOL =
			TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/mining_tool"));

	private static ItemStack drill(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_DRILL.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** Simulates a right-click of the drill's main-hand stack on the top face of {@code target}. */
	private static InteractionResult useOnBlock(GameTestHelper helper, ServerPlayer player, BlockPos target) {
		return useOnFace(helper, player, target, Direction.UP);
	}

	/**
	 * Same, but on an arbitrary face — {@code DOWN} is what MOD-398 needs: the placement cell then sits
	 * <i>below</i> the clicked block, where a torch has nothing to stand on.
	 */
	private static InteractionResult useOnFace(GameTestHelper helper, ServerPlayer player, BlockPos target,
			Direction face) {
		BlockPos abs = helper.absolutePos(target);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
		return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	private static BatteryBoxBlockEntity placeBox(GameTestHelper helper) {
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity be = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (be == null) {
			helper.fail("battery_box block entity missing");
		}
		return be;
	}

	private static void tickBox(GameTestHelper helper, BatteryBoxBlockEntity be, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(),
					helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/**
	 * FUN01: the drill is accepted by the Battery Box charge slot (both the menu's client-side
	 * {@code mayPlace} and the server-side {@code canPlaceItem}, matching NEG03 for the pack) and then
	 * charges there, capped by its own intake rate.
	 */
	public static void fun01ChargeInBatteryBox(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(drill(0))) {
			helper.fail("the charge slot must accept the drill (client prediction)");
		}
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, drill(0))) {
			helper.fail("the server-side filter must accept the drill too");
		}

		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, drill(0));
		tickBox(helper, box, 1);
		long expected = Math.min(EnergyTier.LV.maxVoltage(), Config.electricDrillInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, drill intake) = " + expected + " EU, got " + gained);
		}
		helper.succeed();
	}

	/** FUN02: mining a hard block with a charged drill drains exactly one block's worth of EU. */
	public static void fun02DrainOnMineBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(ORE);
		helper.setBlock(ORE, Blocks.STONE);
		BlockState state = level.getBlockState(abs);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack drill = drill(Config.electricDrillBuffer);

		drill.getItem().mineBlock(drill, level, state, abs, player);
		if (ItemEnergy.get(drill) != Config.electricDrillBuffer - Config.electricDrillEuPerBlock) {
			helper.fail("mining one block must drain exactly electricDrillEuPerBlock, left " + ItemEnergy.get(drill));
		}
		helper.succeed();
	}

	/** FUN03: a drill that can't afford a block mines it for free — no EU is spent (and none goes negative). */
	public static void fun03NoDrainBelowCost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(ORE);
		helper.setBlock(ORE, Blocks.STONE);
		BlockState state = level.getBlockState(abs);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long below = Config.electricDrillEuPerBlock - 1;
		ItemStack drill = drill(below);

		drill.getItem().mineBlock(drill, level, state, abs, player);
		if (ItemEnergy.get(drill) != below) {
			helper.fail("a drill below the per-block cost must spend nothing, got " + ItemEnergy.get(drill));
		}
		helper.succeed();
	}

	/**
	 * FUN04: speed and drops. A charged drill mines at 8.5 (a touch above diamond) on pickaxe blocks; a flat
	 * one falls back to EXACTLY hand speed (1.0f) — the strict value matters, because
	 * {@code Player.getDestroySpeed} only adds the Efficiency bonus above 1.0f, so anything higher would
	 * revive the enchantment on an empty drill. Drops (mining level) follow the TOOL component either
	 * way and match a vanilla diamond pickaxe exactly: stone, obsidian, diamond ore AND ancient debris
	 * are all correctly mined (diamond is the top mining tier — {@code #incorrect_for_diamond_tool} is
	 * empty in vanilla, so the deny rule only guards modded above-diamond blocks). A shovel block (dirt)
	 * is the negative: not in {@code #mineable/pickaxe}, so it is not the drill's correct tool.
	 */
	public static void fun04SpeedAndDrops(GameTestHelper helper) {
		Item drillItem = ModContent.ELECTRIC_DRILL.get();
		BlockState stone = Blocks.STONE.defaultBlockState();

		float charged = drillItem.getDestroySpeed(drill(Config.electricDrillBuffer), stone);
		if (charged != 8.5f) {
			helper.fail("a charged drill must mine stone at 8.5 (a touch above diamond), got " + charged);
		}
		float flat = drillItem.getDestroySpeed(drill(0), stone);
		if (flat != 1.0f) {
			helper.fail("a flat drill must mine at exactly hand speed 1.0 (Efficiency gate), got " + flat);
		}

		ItemStack charge = drill(Config.electricDrillBuffer);
		assertCorrect(helper, charge, Blocks.STONE.defaultBlockState(), "stone", true);
		assertCorrect(helper, charge, Blocks.OBSIDIAN.defaultBlockState(), "obsidian", true);
		assertCorrect(helper, charge, Blocks.DIAMOND_ORE.defaultBlockState(), "diamond_ore", true);
		// Diamond tier is the ceiling — a diamond pickaxe correctly mines ancient debris, so the drill does too.
		assertCorrect(helper, charge, Blocks.ANCIENT_DEBRIS.defaultBlockState(), "ancient_debris", true);
		// Negative: dirt is a shovel block, not in #mineable/pickaxe.
		assertCorrect(helper, charge, Blocks.DIRT.defaultBlockState(), "dirt", false);
		// Drops are kept even when flat (mining level lives in the TOOL component, not the charge).
		assertCorrect(helper, drill(0), Blocks.STONE.defaultBlockState(), "stone (flat)", true);
		helper.succeed();
	}

	/** FUN05: instant-break blocks (zero hardness) never cost EU, mirroring vanilla's durability gate. */
	public static void fun05NoDrainOnZeroHardness(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(ORE);
		helper.setBlock(ORE, Blocks.TORCH);
		BlockState state = level.getBlockState(abs);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack drill = drill(Config.electricDrillBuffer);

		drill.getItem().mineBlock(drill, level, state, abs, player);
		if (ItemEnergy.get(drill) != Config.electricDrillBuffer) {
			helper.fail("a zero-hardness block must not cost EU, left " + ItemEnergy.get(drill));
		}
		helper.succeed();
	}

	/**
	 * FUN06: the drill carries the pickaxe identity tags — {@code #minecraft:pickaxes} (which the
	 * enchantable tag chain resolves through), {@code #minecraft:cluster_max_harvestables} (max amethyst
	 * drop, a direct list not reached via #pickaxes), and {@code #c:tools/mining_tool} (cross-mod). It is
	 * enchantable with the mining/durability enchantments a diamond pickaxe accepts.
	 */
	public static void fun06TagsAndEnchants(GameTestHelper helper) {
		ItemStack drill = drill(0);
		assertInTag(helper, drill, ItemTags.PICKAXES, "#minecraft:pickaxes");
		assertInTag(helper, drill, ItemTags.CLUSTER_MAX_HARVESTABLES, "#minecraft:cluster_max_harvestables");
		assertInTag(helper, drill, C_MINING_TOOL, "#c:tools/mining_tool");

		ServerLevel level = helper.getLevel();
		assertCanEnchant(helper, enchant(level, Enchantments.EFFICIENCY), drill, "efficiency");
		assertCanEnchant(helper, enchant(level, Enchantments.FORTUNE), drill, "fortune");
		assertCanEnchant(helper, enchant(level, Enchantments.SILK_TOUCH), drill, "silk_touch");
		if (!drill.isEnchantable()) {
			helper.fail("the drill must be enchantable at the table (ENCHANTABLE component present, unenchanted)");
		}
		helper.succeed();
	}

	/**
	 * FUN07: right-clicking a block with the drill (MOD-089) places a torch pulled from the inventory,
	 * consumes one torch, and drains {@code electricDrillTorchEuCost} EU. The torch lands on the block
	 * above the floor (floor-torch orientation, vanilla {@code canSurvive}); the click is on the floor
	 * block's top face so the placement target is the air above it.
	 */
	public static void fun07PlaceTorchFromInventory(GameTestHelper helper) {
		// A stone floor block to click on; the torch places in the air above it.
		BlockPos floor = new BlockPos(1, 2, 2);
		BlockPos torchAt = floor.above();
		helper.setBlock(floor, Blocks.STONE);

		ServerPlayer player = survivalPlayer(helper);
		ItemStack drillStack = drill(Config.electricDrillBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, drillStack);
		// 8 vanilla torches in hotbar slot 1 — slot 0 holds the drill (the selected main-hand slot),
		// so the torches must live elsewhere or they would overwrite the drill.
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));

		InteractionResult result = useOnBlock(helper, player, floor);
		if (result != InteractionResult.SUCCESS) {
			helper.fail("right-click with torches in inventory must return SUCCESS, got " + result);
		}
		helper.assertBlockPresent(Blocks.TORCH, torchAt);

		// One torch consumed from the inventory, and the EU cost drained from the drill.
		if (player.getInventory().countItem(Items.TORCH) != 7) {
			helper.fail("one torch must be consumed (7 left), got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		if (ItemEnergy.get(drillStack) != Config.electricDrillBuffer - Config.electricDrillTorchEuCost) {
			helper.fail("placing a torch must drain electricDrillTorchEuCost ("
					+ Config.electricDrillTorchEuCost + "), left " + ItemEnergy.get(drillStack));
		}
		helper.succeed();
	}

	/**
	 * FUN08: when the inventory holds BOTH torch kinds, the drill places the enriched uranium torch first
	 * (the advanced, waterlog-safe torch), not the vanilla one. Asserts the placed block is the uranium
	 * standing torch and that the uranium stack — not the vanilla stack — was decremented.
	 */
	public static void fun08TorchPriorityUranium(GameTestHelper helper) {
		BlockPos floor = new BlockPos(1, 2, 2);
		BlockPos torchAt = floor.above();
		helper.setBlock(floor, Blocks.STONE);

		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, drill(Config.electricDrillBuffer));
		// Both torch kinds in the inventory; slot 0 holds the drill (selected main-hand slot), so the
		// torches go in slots 1 and 2. Slot order does not matter — priority is uranium-first by item.
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));
		player.getInventory().setItem(2, new ItemStack(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get(), 4));

		InteractionResult result = useOnBlock(helper, player, floor);
		if (result != InteractionResult.SUCCESS) {
			helper.fail("right-click with both torch kinds must place the uranium torch, got " + result);
		}
		// Uranium standing torch placed (not the vanilla one), uranium stack decremented, vanilla untouched.
		helper.assertBlockPresent(ModContent.ENRICHED_URANIUM_TORCH.get(), torchAt);
		if (player.getInventory().countItem(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()) != 3) {
			helper.fail("the uranium torch stack must be the one consumed (3 left), got "
					+ player.getInventory().countItem(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()));
		}
		if (player.getInventory().countItem(Items.TORCH) != 8) {
			helper.fail("the vanilla torch stack must be untouched (8 left) when uranium is available, got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		helper.succeed();
	}

	/**
	 * FUN09: regression for replaceable-block placement (the b1573697 block-compare bug). When the player
	 * clicks the side of a REPLACEABLE block — {@code Blocks.SHORT_GRASS} (tall grass) stands in the air on
	 * its own cell — {@code BlockPlaceContext} flips {@code replaceClicked} and {@code BlockItem.place} puts
	 * the torch at the clicked cell itself, NOT at {@code clicked.relative(face)}. A naive
	 * "compare getBlockState(clicked.relative(face)) before/after" success check then sees no change and
	 * skips the inventory decrement + EU drain, giving a free torch. This case must still drain EU and
	 * consume a torch, proving {@code consumesAction()} (not block-compare) is the success gate.
	 *
	 * <p>Setup mirrors FUN07 but the click target is a grass tuft standing on dirt; the torch replaces the
	 * grass at the same cell, so the torch ends up at the click position (not above it).
	 */
	public static void fun09PlaceTorchOnReplaceableBlock(GameTestHelper helper) {
		// A dirt floor with a tall-grass tuft on top — grass is replaceable, so vanilla places the torch
		// at the grass cell (torchAt) via the replaceClicked path, not at grass.above().
		BlockPos dirt = new BlockPos(1, 2, 2);
		BlockPos grass = dirt.above();
		BlockPos torchAt = grass; // replaceClicked → torch lands in the grass cell
		helper.setBlock(dirt, Blocks.DIRT);
		helper.setBlock(grass, Blocks.SHORT_GRASS);

		ServerPlayer player = survivalPlayer(helper);
		ItemStack drillStack = drill(Config.electricDrillBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, drillStack);
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));

		// Click the grass (its side) — BlockPlaceContext will treat it as replaceable.
		InteractionResult result = useOnBlock(helper, player, grass);
		if (result != InteractionResult.SUCCESS) {
			helper.fail("placing a torch by clicking a replaceable block must return SUCCESS, got " + result);
		}
		helper.assertBlockPresent(Blocks.TORCH, torchAt);

		// The replaceable-block path must STILL consume a torch and drain EU — this is exactly what the
		// old block-compare-against-wrong-pos gate got wrong (free torch + no drain).
		if (player.getInventory().countItem(Items.TORCH) != 7) {
			helper.fail("replaceable-block placement must still consume a torch (7 left), got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		if (ItemEnergy.get(drillStack) != Config.electricDrillBuffer - Config.electricDrillTorchEuCost) {
			helper.fail("replaceable-block placement must still drain electricDrillTorchEuCost ("
					+ Config.electricDrillTorchEuCost + "), left " + ItemEnergy.get(drillStack)
					+ " — the b1573697 block-compare bug returned here");
		}
		helper.succeed();
	}

	// ── NEG — negative ───────────────────────────────────────────────────────────────────────────

	/**
	 * NEG01 (MOD-097): a drill charged below {@code electricDrillTorchEuCost} refuses to place a torch
	 * instead of giving a free one. The right-click returns {@code CONSUME} (the click is eaten, so no
	 * off-hand fallback fires), the target cell stays air, no torch leaves the inventory, and the drill's
	 * charge is untouched. This is the exact behaviour FUN07 asserts for a charged drill, inverted.
	 */
	public static void neg01TorchRefusedBelowCost(GameTestHelper helper) {
		BlockPos floor = new BlockPos(1, 2, 2);
		BlockPos torchAt = floor.above();
		helper.setBlock(floor, Blocks.STONE);

		ServerPlayer player = survivalPlayer(helper);
		long belowCost = Config.electricDrillTorchEuCost - 1;
		ItemStack drillStack = drill(belowCost);
		player.setItemInHand(InteractionHand.MAIN_HAND, drillStack);
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));

		InteractionResult result = useOnBlock(helper, player, floor);
		if (result != InteractionResult.CONSUME) {
			helper.fail("a drill below the torch cost must refuse with CONSUME, got " + result);
		}
		// Nothing placed: the cell above the clicked floor is still air.
		helper.assertBlockPresent(Blocks.AIR, torchAt);
		if (player.getInventory().countItem(Items.TORCH) != 8) {
			helper.fail("a refused placement must consume no torch (8 left), got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		if (ItemEnergy.get(drillStack) != belowCost) {
			helper.fail("a refused placement must not touch the drill's charge, left "
					+ ItemEnergy.get(drillStack));
		}
		helper.succeed();
	}

	/**
	 * NEG02 (MOD-398): a flat drill clicking a spot where <b>no</b> torch could ever stand answers
	 * {@code PASS} — it neither shouts "not enough charge" at a player who was not placing a torch nor
	 * swallows the click the off-hand item was meant to get.
	 *
	 * <p>The shipped defect this pins: the MOD-097 charge gate used to run before anything looked at the
	 * target cell, so a flat drill with torches in the bag returned {@code CONSUME} — an
	 * {@code InteractionResult.Success}, i.e. "handled" — for a right-click on anything at all. Asserting
	 * on the result <b>is</b> the off-hand assertion: that value is what
	 * {@code ServerPlayerGameMode.useItemOn} hands back to the interaction chain, and {@code PASS} is the
	 * only one that lets the chain continue. Run against the pre-MOD-398 code the first check fails.
	 *
	 * <p>The second half is the guard that makes the fix honest rather than merely green: the SAME click
	 * is repeated with a <b>fully charged</b> drill, which takes the real vanilla {@code place()} path. Its
	 * answer must be {@code PASS} too. That is what pins the dry probe ({@code wouldPlaceTorch}) to
	 * vanilla's own verdict — if the two ever drift apart, the flat drill would start refusing spots a
	 * charged drill happily lights, and this half goes red. Its counterpart lives one test up:
	 * {@link #neg01TorchRefusedBelowCost} clicks a spot that IS valid and requires {@code CONSUME} there,
	 * so the fix cannot degenerate into "always PASS".
	 *
	 * <p>Geometry: a single stone block floating in the rig's air, clicked on its <b>bottom</b> face. The
	 * placement cell is the air below it — nothing under it for a standing torch, nothing beside it for a
	 * wall torch.
	 */
	public static void neg02FlatDrillOnUnplaceableSpotDoesNotSwallowClick(GameTestHelper helper) {
		BlockPos ceiling = new BlockPos(1, 4, 2);
		BlockPos torchAt = ceiling.below();
		helper.setBlock(ceiling, Blocks.STONE);
		// The cells a torch could have attached to must be empty, or the spot would be placeable after all.
		helper.assertBlockPresent(Blocks.AIR, torchAt);
		helper.assertBlockPresent(Blocks.AIR, torchAt.below());

		ServerPlayer player = survivalPlayer(helper);
		ItemStack flatDrill = drill(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, flatDrill);
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 8));

		InteractionResult flat = useOnFace(helper, player, ceiling, Direction.DOWN);
		if (flat != InteractionResult.PASS) {
			helper.fail("a flat drill clicking a spot no torch can occupy must return PASS so the off-hand "
					+ "still runs, got " + flat);
		}
		helper.assertBlockPresent(Blocks.AIR, torchAt);
		if (player.getInventory().countItem(Items.TORCH) != 8) {
			helper.fail("nothing was placed, so no torch may leave the inventory (8 left), got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		if (ItemEnergy.get(flatDrill) != 0) {
			helper.fail("fixture error: the drill under test must be flat");
		}

		// Same click, charged drill: vanilla's own place() must refuse this spot too. This is the assertion
		// that keeps the dry probe honest — it fails the moment the two answers disagree.
		ItemStack chargedDrill = drill(Config.electricDrillBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, chargedDrill);

		InteractionResult charged = useOnFace(helper, player, ceiling, Direction.DOWN);
		if (charged != InteractionResult.PASS) {
			helper.fail("a charged drill must reach the same verdict on the same spot (PASS), got " + charged);
		}
		helper.assertBlockPresent(Blocks.AIR, torchAt);
		if (player.getInventory().countItem(Items.TORCH) != 8) {
			helper.fail("a refused placement must consume no torch (8 left), got "
					+ player.getInventory().countItem(Items.TORCH));
		}
		if (ItemEnergy.get(chargedDrill) != Config.electricDrillBuffer) {
			helper.fail("a refused placement must not drain the drill, left " + ItemEnergy.get(chargedDrill));
		}
		helper.succeed();
	}

	// ── MOD-321 — diamond-tipped upgrade ─────────────────────────────────────────────────────────

	/**
	 * FUN10 (MOD-321): the upgrade digs faster than the base drill, keeps the base drill's mining tier,
	 * and falls back to the same exact hand speed when flat.
	 *
	 * <p>The speed assertion is deliberately doubled: an absolute check against 10.0 pins the balance
	 * number, and a relative check against the base drill's own {@code getDestroySpeed} makes the test
	 * fail if someone later raises the base drill to match — a bare {@code == 10.0f} would stay green
	 * while the upgrade silently stopped being an upgrade.
	 */
	public static void fun10DiamondTipSpeedAndTier(GameTestHelper helper) {
		Item tip = ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get();
		BlockState stone = Blocks.STONE.defaultBlockState();

		float charged = tip.getDestroySpeed(diamondTipDrill(Config.electricDrillBuffer), stone);
		if (charged != 10.0f) {
			helper.fail("a charged diamond-tipped drill must mine stone at 10.0, got " + charged);
		}
		float baseSpeed = ModContent.ELECTRIC_DRILL.get()
				.getDestroySpeed(drill(Config.electricDrillBuffer), stone);
		if (!(charged > baseSpeed)) {
			helper.fail("the upgrade must out-dig the base drill, got " + charged + " vs base " + baseSpeed);
		}
		float flat = tip.getDestroySpeed(diamondTipDrill(0), stone);
		if (flat != 1.0f) {
			helper.fail("a flat diamond-tipped drill must mine at exactly hand speed 1.0, got " + flat);
		}

		// The tier is unchanged: same correct-for-drops answers as the base drill, including the dirt negative.
		ItemStack charge = diamondTipDrill(Config.electricDrillBuffer);
		assertCorrect(helper, charge, Blocks.OBSIDIAN.defaultBlockState(), "obsidian", true);
		assertCorrect(helper, charge, Blocks.ANCIENT_DEBRIS.defaultBlockState(), "ancient_debris", true);
		assertCorrect(helper, charge, Blocks.DIRT.defaultBlockState(), "dirt", false);
		helper.succeed();
	}

	/**
	 * FUN11 (MOD-321): sneak + right-click toggles Silk Touch mode, and the mode actually changes what
	 * the block drops.
	 *
	 * <p>Both directions are asserted against real loot tables via {@link Block#getDrops}: silk mode must
	 * yield the iron ore <b>block</b>, normal mode must yield <b>raw iron</b>. That pairing is what makes
	 * the test able to fail — asserting only the silk branch would stay green even if the toggle got stuck
	 * on and quietly broke ore doubling, which is the exact regression this feature was designed to avoid.
	 * A non-sneaking click is checked too, so the toggle cannot start firing on every plain right-click.
	 */
	public static void fun11DiamondTipSilkToggle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		ItemStack stack = diamondTipDrill(Config.electricDrillBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		// A freshly crafted drill starts in normal mode — ore keeps feeding the Macerator out of the box.
		if (ElectricDrillDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a fresh diamond-tipped drill must start in normal (non-silk) mode");
		}

		// Plain right-click must not toggle: the control is sneak-gated.
		player.setShiftKeyDown(false);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (ElectricDrillDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a non-sneaking right-click must not toggle Silk Touch mode");
		}

		player.setShiftKeyDown(true);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (!ElectricDrillDiamondTipItem.isSilkMode(stack)) {
			helper.fail("sneak + right-click must switch Silk Touch mode on");
		}

		BlockPos abs = helper.absolutePos(ORE);
		helper.setBlock(ORE, Blocks.IRON_ORE);
		BlockState ore = level.getBlockState(abs);

		if (!dropsContain(level, ore, abs, player, Blocks.IRON_ORE.asItem())) {
			helper.fail("in Silk Touch mode iron ore must drop as the ore block");
		}
		if (dropsContain(level, ore, abs, player, Items.RAW_IRON)) {
			helper.fail("in Silk Touch mode iron ore must NOT drop raw iron");
		}

		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (ElectricDrillDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a second sneak + right-click must switch Silk Touch mode back off");
		}
		if (!dropsContain(level, ore, abs, player, Items.RAW_IRON)) {
			helper.fail("in normal mode iron ore must drop raw iron (ore doubling must keep working)");
		}
		if (dropsContain(level, ore, abs, player, Blocks.IRON_ORE.asItem())) {
			helper.fail("in normal mode iron ore must NOT drop as the ore block");
		}
		helper.succeed();
	}

	/**
	 * FUN12 (MOD-321): the upgrade recipe accepts a base drill in <b>any</b> state — fresh, charged,
	 * half-charged, enchanted — because that is the drill the player actually owns by the time they can
	 * afford the upgrade.
	 *
	 * <p>Reported from play-testing: "a charged drill does not fit the craft". A vanilla {@code Ingredient}
	 * is a {@code HolderSet<Item>} and matches by item alone, so components should be irrelevant — this
	 * test exists to prove that claim against the real {@code RecipeManager} rather than trust it, and to
	 * lock the behaviour in if anything ever swaps the plain ingredient for a component-sensitive one.
	 */
	public static void fun12UpgradeRecipeAcceptsAnyDrillState(GameTestHelper helper) {
		assertUpgradeCraftable(helper, drill(0), "empty drill");
		assertUpgradeCraftable(helper, drill(Config.electricDrillBuffer), "fully charged drill");
		assertUpgradeCraftable(helper, drill(Config.electricDrillBuffer / 3), "part-charged drill");

		ItemStack enchanted = drill(Config.electricDrillBuffer);
		Holder<Enchantment> efficiency = enchant(helper.getLevel(), Enchantments.EFFICIENCY);
		EnchantmentHelper.updateEnchantments(enchanted, mutable -> mutable.set(efficiency, 3));
		assertUpgradeCraftable(helper, enchanted, "enchanted charged drill");

		// The other half of the story (play-test report "the craft does not work"): laying the grid out by
		// hand goes through RecipeManager (asserted above), but the recipe-book / auto-fill button goes
		// through StackedItemContents, which silently ignores any stack failing
		// Inventory.isUsableForCrafting = !damaged && !enchanted && !custom-named. A merely CHARGED drill
		// passes that gate, so auto-fill must be able to place it; an ENCHANTED one cannot, and that is
		// vanilla behaviour for every item, not something this recipe can opt out of.
		assertAutoFillCanCraft(helper, drill(Config.electricDrillBuffer), true, "charged drill");
		assertAutoFillCanCraft(helper, drill(0), true, "empty drill");
		assertAutoFillCanCraft(helper, enchanted, false, "enchanted drill (vanilla refuses to auto-fill)");

		// Closest possible simulation of the player at a bench: a real CraftingMenu over a real player
		// inventory, items dropped into the real grid slots, result read from the real result slot.
		assertBenchCraft(helper, drill(Config.electricDrillBuffer), "charged drill at a real bench");
		assertBenchCraft(helper, enchanted, "enchanted charged drill at a real bench");

		helper.succeed();
	}

	/**
	 * Drives an actual {@link CraftingMenu} the way a player does: put the nine stacks into the real grid
	 * slots, let {@code slotsChanged} resolve the recipe, then read the real result slot. This is a
	 * strictly stronger check than {@link #assertUpgradeCraftable} — it would catch a menu-level problem
	 * that direct {@code RecipeManager} lookups cannot see.
	 */
	private static void assertBenchCraft(GameTestHelper helper, ItemStack drillStack, String label) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper);
		CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
				ContainerLevelAccess.create(level, helper.absolutePos(BOX)));

		List<ItemStack> grid = List.of(
				new ItemStack(ModContent.DIAMOND_DUST.get()), new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.INVAR_INGOT.get()), drillStack, new ItemStack(ModContent.INVAR_INGOT.get()),
				ItemStack.EMPTY, new ItemStack(ModContent.SILVER_GEAR.get()), ItemStack.EMPTY);
		List<Slot> inputs = menu.getInputGridSlots();
		for (int i = 0; i < grid.size(); i++) {
			inputs.get(i).set(grid.get(i).copy());
		}
		menu.slotsChanged(inputs.get(0).container);

		ItemStack result = menu.getResultSlot().getItem();
		if (!result.is(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get())) {
			helper.fail("the bench result slot stayed " + result + " with a " + label
					+ "; expected the diamond-tipped drill");
		}
	}

	/**
	 * Asserts whether the vanilla recipe-book/auto-fill path can place the upgrade recipe when the player
	 * carries {@code drillStack} plus the other ingredients. This is the code REI's "+" button drives, and
	 * it is a different gate from {@link #assertUpgradeCraftable} — hence both are asserted.
	 */
	private static void assertAutoFillCanCraft(GameTestHelper helper, ItemStack drillStack,
			boolean expected, String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, List.of(
				new ItemStack(ModContent.DIAMOND_DUST.get()), new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.INVAR_INGOT.get()), drill(0), new ItemStack(ModContent.INVAR_INGOT.get()),
				ItemStack.EMPTY, new ItemStack(ModContent.SILVER_GEAR.get()), ItemStack.EMPTY));
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail("could not resolve the upgrade recipe while testing auto-fill");
			return;
		}

		StackedItemContents contents = new StackedItemContents();
		contents.accountSimpleStack(new ItemStack(ModContent.DIAMOND_DUST.get(), 3));
		contents.accountSimpleStack(new ItemStack(ModContent.INVAR_INGOT.get(), 2));
		contents.accountSimpleStack(new ItemStack(ModContent.SILVER_GEAR.get(), 1));
		contents.accountSimpleStack(drillStack);

		boolean actual = contents.canCraft(recipe.value(), null);
		if (actual != expected) {
			helper.fail("auto-fill with a " + label + ": expected canCraft=" + expected + ", got " + actual);
		}
	}

	/** Lays out the MOD-321 upgrade recipe with {@code drillStack} in the centre and asserts it resolves. */
	private static void assertUpgradeCraftable(GameTestHelper helper, ItemStack drillStack, String label) {
		ItemStack dust = new ItemStack(ModContent.DIAMOND_DUST.get());
		ItemStack invar = new ItemStack(ModContent.INVAR_INGOT.get());
		ItemStack gear = new ItemStack(ModContent.SILVER_GEAR.get());
		List<ItemStack> grid = List.of(
				dust.copy(), dust.copy(), dust.copy(),
				invar.copy(), drillStack, invar.copy(),
				ItemStack.EMPTY, gear.copy(), ItemStack.EMPTY);

		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail("the diamond-tip upgrade recipe did not resolve with a " + label
					+ " — the base drill must be accepted in any state");
			return;
		}
		ItemStack output = recipe.value().assemble(input);
		if (!output.is(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get())) {
			helper.fail("with a " + label + " the recipe produced " + output
					+ "; expected the diamond-tipped drill");
		}
	}

	// ── PER — persistence ────────────────────────────────────────────────────────────────────────

	/** PER01: charge survives a stack copy, 0 EU removes the component, and writes clamp at capacity. */
	public static void per01ChargeRoundTrip(GameTestHelper helper) {
		ItemStack drill = drill(1234);
		ItemStack copy = drill.copy();
		if (ItemEnergy.get(copy) != 1234) {
			helper.fail("a copied drill must keep its charge");
		}
		ItemEnergy.set(drill, 0);
		if (!ItemStack.matches(drill, new ItemStack(ModContent.ELECTRIC_DRILL.get()))) {
			helper.fail("a drained drill must be component-identical to a fresh one");
		}
		ItemEnergy.set(drill, Config.electricDrillBuffer + 5000);
		if (ItemEnergy.get(drill) != Config.electricDrillBuffer) {
			helper.fail("the drill buffer must clamp at capacity");
		}
		helper.succeed();
	}

	// ── helpers ────────────────────────────────────────────────────────────────────────────────────

	private static ItemStack diamondTipDrill(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * Whether breaking {@code state} with the player's current main-hand tool yields {@code expected}.
	 * Runs the real loot table (that is where the Silk Touch predicate lives), so this asserts the drop
	 * the player would actually receive rather than the enchantment component in isolation.
	 */
	private static boolean dropsContain(ServerLevel level, BlockState state, BlockPos pos,
			ServerPlayer player, Item expected) {
		for (ItemStack dropped : Block.getDrops(state, level, pos, null, player, player.getMainHandItem())) {
			if (dropped.getItem() == expected) {
				return true;
			}
		}
		return false;
	}

	private static Holder<Enchantment> enchant(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void assertInTag(GameTestHelper helper, ItemStack stack, TagKey<Item> tag, String tagName) {
		if (!stack.is(h -> h.is(tag))) {
			helper.fail("electric_drill is not in " + tagName + " (membership tag missing)");
		}
	}

	// MOD-498 — Enchantment#canEnchant is deprecated by NeoForge only (vanilla does not mark it); the
	// replacement it names, ItemStack#supportsEnchantment(Holder), is NeoForge-only API and does not
	// exist in the vanilla classes this shared scenario is also compiled against for Fabric.
	@SuppressWarnings("deprecation")
	private static void assertCanEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack, String name) {
		if (!enchantment.value().canEnchant(stack)) {
			helper.fail(name + " rejected electric_drill — not in the enchantment's supported_items");
		}
	}

	private static void assertCorrect(GameTestHelper helper, ItemStack drill, BlockState state, String name, boolean expected) {
		if (drill.isCorrectToolForDrops(state) != expected) {
			helper.fail("isCorrectToolForDrops(" + name + ") must be " + expected);
		}
	}
}

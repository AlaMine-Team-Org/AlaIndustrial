package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.item.ItemEnergy;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

	/**
	 * A survival ServerPlayer mock with {@code instabuild} forced off — the {@code makeMockServerPlayerInLevel}
	 * default leaves it on (hardcoded CREATIVE gameMode), which would skip the inventory decrement and make
	 * the torch-placement assertions meaningless. Mirrors {@code ScytheScenarios.makeSurvivalPlayer}.
	 */
	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	/** Simulates a right-click of the drill's main-hand stack on the top face of {@code target}. */
	private static InteractionResult useOnBlock(GameTestHelper helper, ServerPlayer player, BlockPos target) {
		BlockPos abs = helper.absolutePos(target);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
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

		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity();
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

		ServerPlayer player = makeSurvivalPlayer(helper);
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

		ServerPlayer player = makeSurvivalPlayer(helper);
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

		ServerPlayer player = makeSurvivalPlayer(helper);
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

	private static Holder<Enchantment> enchant(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void assertInTag(GameTestHelper helper, ItemStack stack, TagKey<Item> tag, String tagName) {
		if (!stack.is(h -> h.is(tag))) {
			helper.fail("electric_drill is not in " + tagName + " (membership tag missing)");
		}
	}

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

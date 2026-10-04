package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.compat.FurnaceFuel;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Scenarios that exist on ONE Minecraft line only (MOD-705). The class name, its Fabric wrapper
 * {@code LineOnlyGameTest} and the NeoForge {@code LineOnlyRegistrations} are the same on both lines, so
 * the shared registrars ({@code NeoForgeGameTests}, the Fabric {@code *GameTest} wrappers,
 * {@code fabric.mod.json}) stay byte-equal; the content differs per line. On this line (26.3) it holds the
 * checks of mechanisms 26.3 introduced — the block transformer, the {@code douses_campfires} tag — and of
 * reading a world saved by 26.2.
 *
 * <p>Helpers stay with the suite they belong to (package-private there); a body here reaches them by
 * class name, so moving a scenario back into a shared suite is a cut-and-paste.
 */
public final class LineOnlyScenarios {

	private LineOnlyScenarios() {}

	// ── Persistence (MOD-645): a world saved on MC 26.2 opens on 26.3 ───────────────────────────────────────

	/**
	 * @implements R-PER-01 -- hand-built 26.2 tags ({@code {Name[, Properties]}} — the shape
	 *     {@code BlockState.CODEC} wrote before 26.3 replaced it with a string-or-{@code {id}}
	 *     pair) load into the two block entities that persist a BlockState, and a re-save writes
	 *     the 26.3 shape. Nothing here came from the current save path: this is the "a 26.2 world
	 *     opens" guarantee, in the spirit of {@link PersistenceScenarios#mod556_preRefactorSavesStillLoad}.
	 * @covers R-PER-01
	 *
	 * <p>Without the legacy branch of the tolerant codec ({@code LegacyBlockStates}) the decode
	 * fails and the reader's default kicks in: the incubator's dome quietly degrades to plain
	 * glass and a sand-rooted kok-sagyz root forgets its sand growth bonus. The re-save assertion
	 * pins the one-way migration: after one load+save the legacy shape is gone for good, so the
	 * branch never fires again for that block.
	 */
	public static void mod645_mc262BlockStateTagsStillLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PersistenceScenarios.POS);

		// 1. Incubator dome: {Name} only — the on-disk shape of every real 26.2 save (dumped from
		// the dev worlds: no Properties key, glass blocks carry none).
		helper.setBlock(PersistenceScenarios.POS, ModContent.INCUBATOR.get());
		CompoundTag dome = new CompoundTag();
		dome.putString("Name", "minecraft:pink_stained_glass");
		CompoundTag incubatorTag = new CompoundTag();
		incubatorTag.put("DomeSource", dome);
		IncubatorBlockEntity incubator = new IncubatorBlockEntity(abs, level.getBlockState(abs));
		incubator.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, incubatorTag));
		if (!incubator.domeSource().is(Blocks.STAINED_GLASS.pink())) {
			helper.fail("a 26.2 DomeSource tag did not come back: " + incubator.domeSource());
			return;
		}

		// 2. The re-save writes the 26.3 shape: a default state encodes as its plain registry name
		// (a string tag), and the legacy compound is gone for good.
		CompoundTag resaved = incubator.saveCustomOnly(registries);
		if (!"minecraft:pink_stained_glass".equals(resaved.getStringOr("DomeSource", "<not a string>"))) {
			helper.fail("a re-saved dome must write the 26.3 string shape, got: " + resaved.get("DomeSource"));
			return;
		}

		// 3. Kok-sagyz root soil: {Name} only again — sand is the whole point (groundPercent keys
		// the growth bonus on it, and playerDestroy hands the block back).
		BlockPos absB = helper.absolutePos(PersistenceScenarios.POS_B);
		helper.setBlock(PersistenceScenarios.POS_B, ModContent.KOK_SAGYZ_ROOT.get());
		CompoundTag soilTag = new CompoundTag();
		CompoundTag sand = new CompoundTag();
		sand.putString("Name", "minecraft:sand");
		soilTag.put("soil", sand);
		KokSagyzRootBlockEntity sandyRoot = new KokSagyzRootBlockEntity(absB, level.getBlockState(absB));
		sandyRoot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, soilTag));
		if (!sandyRoot.soil().is(Blocks.SAND)) {
			helper.fail("a 26.2 soil tag did not come back: " + sandyRoot.soil());
			return;
		}

		// 4. And with Properties: farmland is a valid rootable soil (SUPPORTS_CROPS) and carries
		// moisture — the legacy branch must apply properties, not just the block name.
		BlockPos absC = helper.absolutePos(PersistenceScenarios.POS_C);
		helper.setBlock(PersistenceScenarios.POS_C, ModContent.KOK_SAGYZ_ROOT.get());
		CompoundTag moistTag = new CompoundTag();
		CompoundTag farmland = new CompoundTag();
		farmland.putString("Name", "minecraft:farmland");
		CompoundTag properties = new CompoundTag();
		properties.putString("moisture", "7");
		farmland.put("Properties", properties);
		moistTag.put("soil", farmland);
		KokSagyzRootBlockEntity moistRoot = new KokSagyzRootBlockEntity(absC, level.getBlockState(absC));
		moistRoot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, moistTag));
		if (!moistRoot.soil().is(Blocks.FARMLAND) || moistRoot.soil().getValue(FarmlandBlock.MOISTURE) != 7) {
			helper.fail("a 26.2 soil tag with Properties lost its properties: " + moistRoot.soil());
			return;
		}
		helper.succeed();
	}

	// ── Geothermal (MOD-226): a lava capsule saved by 26.2 heals its fuel on load ───────────────────────────

	/**
	 * A lava capsule the way 26.2 saved it — fluid component, no {@code cooking_fuel} — must burn the
	 * moment it materialises (owner decision, 2026-09-22). The stack is built exactly the way the disk
	 * codec builds one: the public {@code ItemStack(Holder, int, DataComponentPatch)} constructor with a
	 * patch that carries the fluid and nothing else, which is the byte-level shape of a 26.2 world file.
	 * That constructor is where {@code ItemStackLavaCapsuleHealMixin} lives, so constructing the stack IS
	 * loading it — asserting right after construction asks what any furnace would ask the same tick.
	 *
	 * <p>The water twin must stay inert: the heal answers a component 26.2 capsules cannot carry, it must
	 * not invent fuel for capsules that never had it.
	 */
	public static void fun14CapsuleSavedBy262HealsFuelOnLoad(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity machine = GeothermalLavaInputScenarios.placeGeo(helper);
		if (machine == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		int lavaBucketTime = FurnaceFuel.burnDuration(level, machine, new ItemStack(Items.LAVA_BUCKET));
		if (lavaBucketTime <= 0) {
			// Same floor as FUN04: with the fuel lookup itself broken, both sides of every comparison
			// below would be zero and the test would pass vacuously.
			helper.fail("a vanilla lava bucket resolved to " + lavaBucketTime
					+ " ticks of burn time — the fuel lookup itself is broken, so this test proves nothing");
			return;
		}
		ItemStack lava262 = new ItemStack(
				BuiltInRegistries.ITEM.wrapAsHolder(ModContent.FILLED_VACUUM_CAPSULE.get()),
				1,
				DataComponentPatch.builder()
						.set(ModDataComponents.CAPSULE_FLUID.get(),
								BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))
						.build());
		if (!FurnaceFuel.isFuel(level, lava262)
				|| FurnaceFuel.burnDuration(level, machine, lava262) != lavaBucketTime) {
			helper.fail("a lava capsule saved by 26.2 must burn like a lava bucket (" + lavaBucketTime
					+ ") the moment it loads, got " + FurnaceFuel.isFuel(level, lava262) + "/"
					+ FurnaceFuel.burnDuration(level, machine, lava262));
			return;
		}
		ItemStack water262 = new ItemStack(
				BuiltInRegistries.ITEM.wrapAsHolder(ModContent.FILLED_VACUUM_CAPSULE.get()),
				1,
				DataComponentPatch.builder()
						.set(ModDataComponents.CAPSULE_FLUID.get(),
								BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))
						.build());
		if (FurnaceFuel.isFuel(level, water262) || FurnaceFuel.burnDuration(level, machine, water262) != 0) {
			helper.fail("a water capsule saved by 26.2 must NOT become fuel by healing ("
					+ FurnaceFuel.isFuel(level, water262) + "/"
					+ FurnaceFuel.burnDuration(level, machine, water262) + ")");
			return;
		}
		helper.succeed();
	}

	// ── Electric hoe (MOD-226): the block transformer is the mechanism on 26.3 ──────────────────────────────

	/**
	 * TC-HOE-001-FUN14 (MOD-226) — a hoe whose {@code minecraft:block_transformer} was stripped from the
	 * stack PASSES the click on and spends nothing.
	 *
	 * <p>On 26.3 applicability is a property of the STACK, not of the item class:
	 * {@code RightClickTransform.wouldTill} reads the transformer the stack in hand carries, exactly as
	 * {@code Item.useOn} does. This scenario is the mutation partner of the roster guard — it proves the
	 * electric hoe's gates really key on that component, by removing it from a CHARGED hoe standing on
	 * tillable dirt and demanding the pre-charge applicability gate answer "not mine": PASS (so the
	 * off-hand still runs), the plot untouched, and the buffer exactly where it was. An implementation
	 * that decided applicability from the class, or charged before asking, fails one of the three.
	 */
	public static void fun14HoeWithoutTransformerPassesFree(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		ItemStack stack = ElectricHoeScenarios.hoe(buffer);
		stack.remove(DataComponents.BLOCK_TRANSFORMER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ElectricHoeScenarios.prepareSoil(helper, Blocks.DIRT);
		helper.assertBlockPresent(Blocks.DIRT, ElectricHoeScenarios.SOIL);

		InteractionResult result = ElectricHoeScenarios.useOnSoil(helper, player);

		if (result != InteractionResult.PASS) {
			helper.fail("a hoe with the transformer stripped must PASS the click on, got " + result);
		}
		helper.assertBlockPresent(Blocks.DIRT, ElectricHoeScenarios.SOIL);
		long left = ItemEnergy.get(player.getMainHandItem());
		if (left != buffer) {
			helper.fail("a click the stripped hoe cannot act on must cost nothing, charge went " + buffer
					+ " → " + left);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN15 (MOD-226) — rooted dirt is a hoe conversion too: it becomes plain dirt, drops its
	 * hanging root, and costs the till.
	 *
	 * <p>On 26.2 rooted dirt rode in {@code HoeItem.TILLABLES} beside grass and coarse dirt, and the
	 * delegation carried it for free. On 26.3 it is the hoe transformer's SECOND entry — a separate
	 * rule with its own loot table ({@code minecraft:till/rooted_dirt}) and drop strategy, no air-above
	 * predicate — which makes it the one conversion whose wiring nothing else in this suite exercises:
	 * tilling dirt and grass walks the FIRST entry only. This scenario walks the second.
	 */
	public static void fun15TillsRootedDirtToDirt(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		player.setItemInHand(InteractionHand.MAIN_HAND, ElectricHoeScenarios.hoe(buffer));
		ElectricHoeScenarios.prepareSoil(helper, Blocks.ROOTED_DIRT);
		helper.assertBlockPresent(Blocks.ROOTED_DIRT, ElectricHoeScenarios.SOIL);

		InteractionResult result = ElectricHoeScenarios.useOnSoil(helper, player);

		if (!result.consumesAction()) {
			helper.fail("tilling rooted dirt must be a consumed action, got " + result);
		}
		helper.assertBlockPresent(Blocks.DIRT, ElectricHoeScenarios.SOIL);
		long expected = buffer - ToolConfig.electricHoeTillEuCost;
		long left = ItemEnergy.get(player.getMainHandItem());
		if (left != expected) {
			helper.fail("tilling rooted dirt must drain exactly electricHoeTillEuCost ("
					+ ToolConfig.electricHoeTillEuCost + "), charge went " + buffer + " → " + left
					+ ", expected " + expected);
		}
		// The second entry's own contract: the hanging root is loot of the transform itself, not of the
		// block — dirt does not drop roots when broken.
		helper.assertItemEntityPresent(Items.HANGING_ROOTS, ElectricHoeScenarios.SOIL, 2.0);
		helper.succeed();
	}

	// ── Electric shovel (MOD-226): dousing a campfire is the douses_campfires tag on 26.3 ───────────────────

	/**
	 * TC-SHOVEL-001-FUN15 (MOD-226) — the diamond-tipped upgrade douses a lit campfire like the base
	 * shovel does.
	 *
	 * <p>The mechanism moved under the item in 26.3: dousing no longer lives in {@code ShovelItem.useOn}
	 * (the class is gone) but in {@code CampfireBlock.useItemOn}, which asks the hand stack for the
	 * {@code minecraft:douses_campfires} item tag — a tag vanilla defines as {@code #minecraft:shovels}
	 * and we APPEND both shovels to. That append is the whole contract on 26.3: FUN04 covers the base
	 * shovel, and nothing else anywhere checks the upgrade, so a tag list edited down to the base tool
	 * would silently take dousing away from the diamond tip alone. The click goes through
	 * {@code gameMode.useItemOn} because that is where the block's tag check reads the stack from.
	 */
	public static void fun15DiamondTipDousesLitCampfire(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND,
				ElectricShovelScenarios.diamondTipShovel(ToolConfig.electricShovelBuffer));
		helper.setBlock(ElectricShovelScenarios.GROUND,
				Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.TRUE));
		helper.setBlock(ElectricShovelScenarios.GROUND.above(), Blocks.AIR);

		if (!ElectricShovelScenarios.litAtGround(helper)) {
			helper.fail("fixture error: the campfire must start lit");
		}

		ElectricShovelScenarios.useOnGround(helper, player);

		if (ElectricShovelScenarios.litAtGround(helper)) {
			helper.fail("right-clicking a lit campfire with the diamond-tipped shovel must douse it");
		}
		helper.succeed();
	}

	// ── Electric tools (MOD-226): the baked right-click roster carries its transformer ──────────────────────

	/** Number of right-click tools that must carry a block transformer — the floor guarding a vacuous sweep. */
	private static final int EXPECTED_RIGHT_CLICK_TOOLS = 4;

	/**
	 * TC-ETOOL-001-FUN06 (MOD-226) — every right-click tool of the line carries the block transformer its
	 * domain names, in its BAKED default components.
	 *
	 * <p>Mirrors: LineOnlyGameTest.tcEtool001Fun06_rightClickRosterDeclaresTransformer
	 *
	 * <p>The runtime half of the {@code right-click-tools-declare-block-transformer} rule (MOD-226).
	 *
	 * <p>The text gate in {@code arch_check.py} reads the SOURCE: "a tool with a hand-built TOOL component
	 * declares BLOCK_TRANSFORMER, of the family its mineable tag names". What it cannot see is whether the
	 * declaration survived the registry bake — the component is a <i>delayed</i> one, resolved against the
	 * loaded {@code block_transformer} registry after item registration, and a resolution that never landed
	 * (a renamed datapack key, a bootstrap ordering change) leaves every source-level declaration green and
	 * every stack in the game without a transformer: the hoe and the shovel silently stop tilling and
	 * pathing, exactly the MOD-378/379 defect class, one mechanism later.
	 *
	 * <p>So this body asks the baked item: a freshly created stack of each of the four right-click tools
	 * (base and diamond tip, hoe and shovel) must carry {@code minecraft:block_transformer} in its DEFAULT
	 * components — that is what {@code Item.useOn} reads — and it must resolve to the family key the tool's
	 * domain names. A wrong family is the subtler failure: a shovel re-pointed at the hoe transformer
	 * would till instead of pathing, and every assertion about "its own action" elsewhere in these suites
	 * would then be testing the wrong action.
	 */
	public static void fun06RightClickRosterDeclaresTransformer(GameTestHelper helper) {
		record RightClickTool(String name, Supplier<Item> item, ResourceKey<BlockTransformer> family) {}

		List<RightClickTool> roster = List.of(
				new RightClickTool("electric_hoe", ModContent.ELECTRIC_HOE, BlockTransformers.HOE),
				new RightClickTool("electric_hoe_diamond_tip", ModContent.ELECTRIC_HOE_DIAMOND_TIP,
						BlockTransformers.HOE),
				new RightClickTool("electric_shovel", ModContent.ELECTRIC_SHOVEL, BlockTransformers.SHOVEL),
				new RightClickTool("electric_shovel_diamond_tip", ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP,
						BlockTransformers.SHOVEL));

		if (roster.size() != EXPECTED_RIGHT_CLICK_TOOLS) {
			helper.fail("the right-click line covers " + EXPECTED_RIGHT_CLICK_TOOLS + " tools, the roster holds "
					+ roster.size() + " — a sweep over a shrunken roster proves nothing");
		}

		for (RightClickTool tool : roster) {
			ItemStack fresh = new ItemStack(tool.item().get());
			Holder<BlockTransformer> transformer = fresh.get(DataComponents.BLOCK_TRANSFORMER);
			if (transformer == null) {
				helper.fail(tool.name + " carries no minecraft:block_transformer in its default components — "
						+ "its right-click answers PASS on 26.3: no tilling, no path, no EU, no message "
						+ "(the delayed component never resolved at registry bake)");
			}
			boolean rightFamily = transformer.unwrapKey()
					.map(key -> key == tool.family)
					.orElse(false);
			if (!rightFamily) {
				helper.fail(tool.name + " declares " + transformer.unwrapKey()
						+ " but its domain is the " + tool.family + " family — it would perform the other "
						+ "tool's action");
			}
		}
		helper.succeed();
	}
}

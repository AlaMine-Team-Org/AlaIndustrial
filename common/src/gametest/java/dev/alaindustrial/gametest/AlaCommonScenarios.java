package dev.alaindustrial.gametest;

import dev.alaindustrial.BuildInfo;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.NetworkTickGuard;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * L2 server game tests — the **common-to-all-blocks** layer (RULES.md {@code R-*}). These run in a
 * real {@link net.minecraft.server.level.ServerLevel} and exit non-zero on failure, so a regression
 * fails CI (unlike the legacy logging self-test).
 *
 * <p>Parametric over the whole {@code alaindustrial} block registry — new blocks are covered
 * automatically, no per-block edit (mirrors the legacy {@code BLOCK_STANDARDS} check). Per-block
 * functional suites and integration scenarios come on top of this layer.
 *
 * <p>MOD-310 — loader-neutral bodies; both loader lanes run them.
 *
 * <p>See docs/testing/AUTOMATION-STANDARDS.md (§2 naming, §3 traceability, §4 world conditions).
 */
public final class AlaCommonScenarios {

	private AlaCommonScenarios() {}

	/** Reused single cell inside the test region; placed, asserted, cleared per block. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * R-PHY-01 (common): every registered {@code alaindustrial} block places and breaks without a
	 * crash, and the placed block is actually the one we asked for.
	 *
	 * @implements R-PHY-01 (all blocks) — see docs/testing/RULES.md
	 */
	public static void everyBlockPlacesAndBreaks(GameTestHelper helper) {
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			helper.setBlock(PROBE, block);
			helper.assertBlockPresent(block, PROBE);
			helper.setBlock(PROBE, Blocks.AIR);
			helper.assertBlockNotPresent(block, PROBE);
		}
		helper.succeed();
	}

	/**
	 * MOD-186: {@link NetworkTickGuard} isolates a throwing network tick so a neighbouring mod's
	 * capability throw cannot crash the server tick. Regression gate — if the guard's {@code try/catch}
	 * is removed, the thrown exception propagates out of {@code tickIsolated}/{@code runIsolated} into this
	 * test and fails it. Also asserts the guard is transparent on the happy path (returns the body's value).
	 */
	public static void networkTickGuardIsolatesThrows(GameTestHelper helper) {
		// A throwing EU-tick body is swallowed and reports 0 EU moved (not propagated).
		long moved = NetworkTickGuard.tickIsolated("test", () -> {
			throw new RuntimeException("foreign capability boom at BlockPos{x=1, y=2, z=3}");
		});
		if (moved != 0L) {
			helper.fail("tickIsolated must return 0 when the body throws, got " + moved);
			return;
		}
		// A throwing void tick body (item pipe) is swallowed too.
		try {
			NetworkTickGuard.runIsolated("test", () -> {
				throw new IllegalStateException("foreign item capability boom");
			});
		} catch (Throwable t) {
			helper.fail("runIsolated must swallow the throw, but it propagated: " + t);
			return;
		}
		// Transparent on success: the body's value passes through unchanged.
		long ok = NetworkTickGuard.tickIsolated("test", () -> 42L);
		if (ok != 42L) {
			helper.fail("tickIsolated must return the body's value on success, got " + ok);
			return;
		}
		helper.succeed();
	}

	/**
	 * R-BRK-01 (common): every block, when broken, drops exactly one of itself — no dupe, no loss.
	 * Uses the real loot path ({@link Block#getDrops}), so a broken loot table fails here.
	 *
	 * @implements R-BRK-01 (all blocks) — see docs/testing/RULES.md
	 */
	public static void everyBlockDropsItself(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(PROBE);
		var level = helper.getLevel();
		var miner = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE); // diamond is correct for every block (incl. tier-gated ores, which are skipped below)
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			// Ores follow VANILLA drop semantics (pickaxe → raw material, Silk Touch → the block),
			// so they legitimately do NOT self-drop with a plain pickaxe. R-BRK-01 governs
			// functional/machine blocks; ore blocks are exempt here. See docs/testing/RULES.md.
			if (id.getPath().endsWith("_ore")) {
				continue;
			}
			// The Enriched Uranium Wall Torch (MOD-085) intentionally has NO block item — it drops the
			// STANDING torch via Properties.overrideLootTable (vanilla wallVariant), so block.asItem() is
			// AIR and it cannot "self-drop". Its drop is asserted in EnrichedUraniumTorchGameTest instead.
			if (id.getPath().equals("enriched_uranium_wall_torch")) {
				continue;
			}
			// The Incubator Dome (MOD-118) is likewise never held by the player: it exists only while the
			// multiblock stands, and breaking it hands back the glass the player originally placed
			// (IncubatorDomeBlock.returnGlass), so it has no block item and an empty loot table by design.
			if (id.getPath().equals("incubator_dome")) {
				continue;
			}
			// The Distillation Column's middle/top segments (MOD-251) are placed by the base and have
			// empty loot tables on purpose: breaking ANY segment drops the whole tower as the base's
			// item (with tank contents). The one-drop contract is asserted in the column's own gametest.
			if (id.getPath().equals("distillation_column_middle")
					|| id.getPath().equals("distillation_column_top")) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			// Liquid blocks (MOD-238 oil): like vanilla water/lava, the in-world block form of a fluid
			// has no item and no loot — it is scooped with a bucket, never mined (LiquidBlock#getDrops
			// is empty by design). The bucket round trip is covered by OilGameTest FUN01 instead.
			if (block instanceof net.minecraft.world.level.block.LiquidBlock) {
				continue;
			}
			helper.setBlock(PROBE, block);
			List<ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs,
					level.getBlockEntity(abs), miner, pickaxe);
			long self = drops.stream().filter(s -> s.getItem() == block.asItem()).mapToLong(ItemStack::getCount).sum();
			if (self != 1) {
				helper.fail(id + " dropped " + self + "× itself with a pickaxe (expected 1)");
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * R-BRK-02 + R-BRK-09 (common): a bare hand is NOT a correct tool for a drop (no drop by hand),
	 * while a pickaxe IS — every block is {@code requiresCorrectToolForDrops} + in
	 * {@code minecraft:mineable/pickaxe}. Tool-harvest gating lives on the item (not in
	 * {@code Block.getDrops}), so check it via {@link ItemStack#isCorrectToolForDrops}.
	 *
	 * @implements R-BRK-02 (all blocks) — wrong tool yields no drop
	 */
	public static void everyBlockNoDropByHand(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(PROBE);
		var level = helper.getLevel();
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			// The Enriched Uranium Torch + Wall Torch (MOD-085) are vanilla-behaviour torches: instabreak,
			// broken by hand with NO tool gate (not requiresCorrectToolForDrops, not in mineable/pickaxe).
			// So a bare hand IS a correct tool for them — exempt from R-BRK-02, like the ore tier-gate carve-out.
			if (id.getPath().endsWith("torch")) {
				continue;
			}
			// Irradiated Soil (MOD-471) is dirt that has been poisoned: shovelled, not mined, and gated by
			// no tool at all — exactly like the dirt it decays back into.
			if (id.getPath().equals("irradiated_soil")) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			// Liquid blocks (MOD-238 oil): fluids are never mined — not requiresCorrectToolForDrops,
			// not in mineable/pickaxe, no drops at all (vanilla water/lava behave identically), so
			// neither the hand-negative nor the pickaxe-positive applies.
			if (block instanceof net.minecraft.world.level.block.LiquidBlock) {
				continue;
			}
			// Plants (MOD-280 trellis and any crop after it): vegetation is pulled up by hand,
			// exactly like vanilla wheat or a sapling — never pickaxe-gated. Exempted by CLASS rather
			// than by id so future crops are covered without touching this test again.
			if (block instanceof net.minecraft.world.level.block.VegetationBlock) {
				continue;
			}
			helper.setBlock(PROBE, block);
			var state = level.getBlockState(abs);
			helper.setBlock(PROBE, Blocks.AIR);
			if (ItemStack.EMPTY.isCorrectToolForDrops(state)) {
				helper.fail(id + " counts a bare hand as a correct tool — should need a pickaxe (R-BRK-02)");
			}
			if (!pickaxe.isCorrectToolForDrops(state)) {
				helper.fail(id + " does not accept a pickaxe as a correct tool (R-BRK-09)");
			}
		}
		helper.succeed();
	}

	/**
	 * BLOCK_STANDARDS (common): parametric block-rendering/registration gate. Iterates EVERY block
	 * registered under the {@code alaindustrial} namespace and asserts three universal invariants per
	 * block — no per-block table, so new blocks are covered automatically:
	 * <ol>
	 *   <li><b>Occlusion ⇔ full cube</b> (R-PHY-05): a non-full-cube collision shape MUST have
	 *       {@code canOcclude()==false} (anti-X-ray); a full cube MUST occlude.</li>
	 *   <li><b>Block item</b>: a matching {@link net.minecraft.world.item.BlockItem} is registered
	 *       under the same id.</li>
	 *   <li><b>Loot table</b> (R-BRK-01): the datapack resource
	 *       {@code data/alaindustrial/loot_table/blocks/<id>.json} exists, so the block drops.</li>
	 * </ol>
	 * Ported faithfully from {@code IndustrializationSelfTest.runBlockStandardsCheck}. Aggregates every
	 * offending block into a single {@link GameTestHelper#fail}; succeeds only if all blocks pass all three.
	 *
	 * @implements BLOCK_STANDARDS — covers R-PHY-05 (occlusion), R-BRK-01 (loot), block-item registration
	 */
	public static void blockStandardsAllBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceManager resources = level.getServer().getResourceManager();
		// Side-effect-free loaded probe inside the force-loaded region; shape/occlusion queries read the
		// state's own cached shape, not the world, so nothing is placed here.
		BlockPos probe = helper.absolutePos(PROBE);

		List<String> failures = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			// The Enriched Uranium Wall Torch (MOD-085) intentionally has no block item and no loot table of
			// its own (it mirrors the standing torch via overrideLootTable/overrideDescription), so the
			// block-item + loot invariants below do not apply. The standing torch covers the torch occlusion.
			if (id.getPath().equals("enriched_uranium_wall_torch")) {
				continue;
			}
			// The Incubator Dome (MOD-118) has no block item on purpose — it is placed by the multiblock,
			// not by the player, and returns the original glass when broken. Occlusion is still asserted
			// for it below by the base rule; only the block-item invariant is waived.
			if (id.getPath().equals("incubator_dome")) {
				continue;
			}
			// The Distillation Column's middle/top segments (MOD-251): no block items on purpose — the
			// base's item raises the whole tower; their loot tables are empty (the base drops for all).
			if (id.getPath().equals("distillation_column_middle")
					|| id.getPath().equals("distillation_column_top")) {
				continue;
			}
			// MOD-468: reactor glass is the mod's first TRANSPARENT full cube, and the occlusion rule
			// below cannot express it. "Occlusion == full collision cube" holds for opaque blocks; glass
			// is a full cube that must NOT occlude, or the room's interior would be culled away behind
			// it and the window would show nothing. Vanilla's own glass is built exactly this way
			// (full cube + noOcclusion). Waived for the same reason liquids are, and only for the blocks
			// named here — anything else claiming the exemption has to earn its own line.
			//
			// MOD-505 adds the greenhouse's glazing on identical grounds: a farm walled in glass that
			// occluded would cull away the very crystals it exists to show.
			if (id.getPath().equals("reactor_glass") || id.getPath().equals("crystal_farm_glass")) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			BlockState state = block.defaultBlockState();

			// Liquid blocks (MOD-238 oil): the fluid's block form mirrors vanilla water/lava — no
			// block item (the hand-carried form is the bucket) and no loot table (LiquidBlock#getDrops
			// is empty), so those two invariants are waived. The occlusion invariant still applies
			// below: a liquid is a non-full-cube non-occluder and must stay that way.
			boolean liquid = block instanceof net.minecraft.world.level.block.LiquidBlock;

			// 1. Occlusion <=> full collision cube.
			boolean fullCube = state.isCollisionShapeFullBlock(level, probe)
					&& Block.isShapeFullBlock(state.getCollisionShape(level, probe, CollisionContext.empty()));
			boolean occludes = state.canOcclude();
			boolean occlusionOk = (fullCube == occludes);

			// 2. A BlockItem is registered under the same id (waived for liquids, see above).
			boolean hasItem = liquid || BuiltInRegistries.ITEM.containsKey(id);

			// 3. The loot-table datapack resource exists (waived for liquids, see above).
			boolean hasLoot = liquid;
			if (!liquid && block.getLootTable().isPresent()) {
				Identifier lootId = block.getLootTable().get().identifier();
				Identifier lootResource = Identifier.fromNamespaceAndPath(
						lootId.getNamespace(), "loot_table/" + lootId.getPath() + ".json");
				hasLoot = !resources.getResourceStack(lootResource).isEmpty();
			}

			if (!(occlusionOk && hasItem && hasLoot)) {
				failures.add(String.format(
						"%s: occlusion=%s(fullCube=%s,canOcclude=%s) item=%s loot=%s",
						id, occlusionOk, fullCube, occludes, hasItem, hasLoot));
			}
		}

		if (!failures.isEmpty()) {
			helper.fail("BLOCK_STANDARDS failed for " + failures.size() + " block(s): "
					+ String.join("; ", failures));
		}
		helper.succeed();
	}

	/**
	 * ALA_COMMAND (common): the {@code /ala} command must be registered on the server dispatcher and
	 * {@link BuildInfo#version()} must expose a non-empty version, so build-visibility is verifiable
	 * in-game. Ports the legacy monolith {@code ALA_COMMAND} self-test check.
	 *
	 * @implements ALA_COMMAND (build/command visibility) — see docs/testing/RULES.md
	 */
	public static void alaCommandRegistered(GameTestHelper helper) {
		boolean alaRegistered = helper.getLevel().getServer().getCommands()
				.getDispatcher().getRoot().getChild("ala") != null;
		if (!alaRegistered) {
			helper.fail("/ala command is not registered on the server dispatcher (ALA_COMMAND)");
		}
		String version = BuildInfo.version();
		if (version == null || version.isEmpty()) {
			helper.fail("BuildInfo.version() is null/empty — build version not exposed (ALA_COMMAND)");
		}
		helper.succeed();
	}

	/**
	 * ORE_CONVENTION_TAGS (MOD-114): every ore block/item is exposed through the Fabric+NeoForge
	 * common {@code c:} convention tags, so tag-driven mods (vein miners, ore-processing, unification,
	 * REI/EMI grouping) recognise our materials as ores — parity with vanilla iron/copper.
	 *
	 * <p>Asserts, for each of tin/silver/nickel/sulfur/uranium (stone + deepslate variant):
	 * <ul>
	 *   <li>block in {@code #c:ores} and {@code #c:ores/<metal>};</li>
	 *   <li>stone variant in {@code #c:ores_in_ground/stone}, deepslate variant in {@code .../deepslate};</li>
	 *   <li>block-item in {@code #c:ores}; raw drop in {@code #c:raw_materials(/<metal>)};
	 *       and, for metals, ingot in {@code #c:ingots(/<metal>)}.</li>
	 * </ul>
	 * A missing/typo'd tag JSON breaks membership → this test fails. The tag data lives in
	 * {@code common/}, so the same files back the NeoForge loader (structural parity).
	 *
	 * @implements ORE_CONVENTION_TAGS (MOD-114)
	 */
	public static void oresInConventionTags(GameTestHelper helper) {
		List<String> failures = new ArrayList<>();
		TagKey<Block> cOres = blockTag("ores");
		TagKey<Block> inStone = blockTag("ores_in_ground/stone");
		TagKey<Block> inDeepslate = blockTag("ores_in_ground/deepslate");

		for (String material : new String[] { "tin", "silver", "nickel", "sulfur", "uranium" }) {
			Block stoneOre = ore(material + "_ore");
			Block deepslateOre = ore("deepslate_" + material + "_ore");
			TagKey<Block> perMaterial = blockTag("ores/" + material);

			for (Block b : new Block[] { stoneOre, deepslateOre }) {
				BlockState s = b.defaultBlockState();
				if (!s.is(cOres)) {
					failures.add(blockId(b) + " not in #c:ores");
				}
				if (!s.is(perMaterial)) {
					failures.add(blockId(b) + " not in #c:ores/" + material);
				}
			}
			if (!stoneOre.defaultBlockState().is(inStone)) {
				failures.add(blockId(stoneOre) + " not in #c:ores_in_ground/stone");
			}
			if (!deepslateOre.defaultBlockState().is(inDeepslate)) {
				failures.add(blockId(deepslateOre) + " not in #c:ores_in_ground/deepslate");
			}

			assertItemInTag(failures, material + "_ore", "ores");
			assertItemInTag(failures, "deepslate_" + material + "_ore", "ores");
			assertItemInTag(failures, "raw_" + material, "raw_materials");
			assertItemInTag(failures, "raw_" + material, "raw_materials/" + material);
		}

		// Palladium (MOD-423) is checked apart from the loop above: it is the only ore without a
		// deepslate twin (Nether host rock), so the paired stone/deepslate assertions do not apply.
		// Its ground tag is ores_in_ground/netherrack rather than stone/deepslate.
		Block palladiumOre = ore("palladium_ore");
		BlockState palladiumState = palladiumOre.defaultBlockState();
		if (!palladiumState.is(cOres)) {
			failures.add(blockId(palladiumOre) + " not in #c:ores");
		}
		if (!palladiumState.is(blockTag("ores/palladium"))) {
			failures.add(blockId(palladiumOre) + " not in #c:ores/palladium");
		}
		if (!palladiumState.is(blockTag("ores_in_ground/netherrack"))) {
			failures.add(blockId(palladiumOre) + " not in #c:ores_in_ground/netherrack");
		}
		assertItemInTag(failures, "palladium_ore", "ores");
		assertItemInTag(failures, "raw_palladium", "raw_materials");
		assertItemInTag(failures, "raw_palladium", "raw_materials/palladium");

		// Sulfur is a non-metal: it deliberately has no ingot form.
		for (String metal : new String[] { "tin", "silver", "nickel", "uranium", "palladium" }) {
			assertItemInTag(failures, metal + "_ingot", "ingots");
			assertItemInTag(failures, metal + "_ingot", "ingots/" + metal);
		}

		// Dusts (MOD-114): full processing-chain material tag for unification/grinding mods. Covers
		// the mod's own metals plus dusts of vanilla materials it produces.
		for (String mat : new String[] { "tin", "silver", "nickel", "sulfur", "uranium", "palladium",
				"copper", "iron", "gold", "coal", "diamond", "emerald", "lapis" }) {
			assertItemInTag(failures, mat + "_dust", "dusts");
			assertItemInTag(failures, mat + "_dust", "dusts/" + mat);
		}

		if (!failures.isEmpty()) {
			helper.fail("Ore convention tags missing for " + failures.size() + " entry(ies): "
					+ String.join("; ", failures));
		}
		helper.succeed();
	}

	/**
	 * The test structure this lane runs on must be big enough to contain a scenario rig (MOD-335).
	 *
	 * <p>This is a configuration guard, not a gameplay test, and it is here because the bug it pins
	 * was invisible to every gameplay test: the engine sizes <b>chunk force-loading</b>, the
	 * entity-ticking wait, grid spacing and {@code clearSpaceForStructure} off the STRUCTURE box, not
	 * off what a scenario body writes. The NeoForge lane used {@code minecraft:empty} (1x1x1) while
	 * rigs are up to 5x5, so a rig whose origin landed late in a chunk straddled the border and
	 * dropped its items into a chunk nothing had force-loaded — {@code getEntitiesOfClass} counted
	 * zero and the test failed with "no drop". Because {@code GameTestServer} picks a RANDOM world
	 * origin per run, that surfaced as an unreproducible flake that blocked a release.
	 *
	 * <p>A flake is a poor regression test, so this guard is deterministic: shrink the structure back
	 * and it fails on every run, on both loaders.
	 */
	public static void gametestRigStructureFitsRigs(GameTestHelper helper) {
		double x = helper.getBounds().getXsize();
		double z = helper.getBounds().getZsize();
		if (x < MIN_RIG_STRUCTURE_SIZE || z < MIN_RIG_STRUCTURE_SIZE) {
			helper.fail("this lane's test structure is " + x + "x" + z + ", below the required "
					+ MIN_RIG_STRUCTURE_SIZE + "x" + MIN_RIG_STRUCTURE_SIZE + " — rigs up to 5x5 would"
					+ " straddle a chunk border and their drops would land in a chunk the lane never"
					+ " force-loads (MOD-335). Fabric: @GameTest(structure = ...); NeoForge:"
					+ " NeoForgeGameTests.RIG_STRUCTURE.");
			return;
		}
		helper.succeed();
	}

	/**
	 * Smallest structure that safely holds a rig: the widest rigs that count drops are 5x5
	 * (the scythe / trellis platforms) and 8 is what Fabric's own default template ships.
	 */
	private static final double MIN_RIG_STRUCTURE_SIZE = 8.0;

	/**
	 * MOD-417 — every {@code BlockEntityDef.blockSet()} is unmodifiable, ordered, and is the set the
	 * registered {@code BlockEntityType} actually answers {@code isValid} from.
	 *
	 * <p><b>Why a game test and not a unit test.</b> {@code blockSet()} resolves registry ids, so it
	 * needs live registries; and the invariant is about what reaches the GAME, which means it has to be
	 * checked on both loaders — each builds its {@code BlockEntityType} in its own registration code
	 * (Fabric eagerly, NeoForge inside a deferred supplier).
	 *
	 * <p><b>Why it checks four things and not one.</b> A single "add throws" assertion is satisfied by
	 * several wrong fixes:
	 * <ul>
	 *   <li>{@code Set.copyOf} would be unmodifiable but SALTED — iteration order varies per JVM run —
	 *       so the order assertion is what pins {@code Collections.unmodifiableSet};</li>
	 *   <li>an unmodifiable wrapper that only blocks {@code add} would leave {@code remove}/{@code clear}
	 *       open, so all three are exercised;</li>
	 *   <li>a set that is immutable but never reaches the registry would pass every assertion above,
	 *       so {@code registeredType().isValid(...)} closes the loop against the live type — vanilla's
	 *       {@code isValid} reads the {@code validBlocks} field directly, so agreement here means the
	 *       manifest's set really is the one the game consults.</li>
	 * </ul>
	 *
	 * <p>The roster floor is deliberate: an empty {@code BLOCK_ENTITIES} would make every loop below
	 * vacuous and the test would pass by checking nothing.
	 */
	public static void blockEntityBlockSetsAreImmutable(GameTestHelper helper) {
		if (ContentManifest.BLOCK_ENTITIES.isEmpty()) {
			helper.fail("ContentManifest.BLOCK_ENTITIES is empty — every assertion below would be "
					+ "vacuous, so this counts as a failure, not as 'nothing to check'.");
			return;
		}
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			Set<Block> blocks = def.blockSet();

			List<Block> expected = new ArrayList<>();
			for (String blockId : def.blocks()) {
				expected.add(BuiltInRegistries.BLOCK.getValue(Industrialization.id(blockId)));
			}
			if (!expected.equals(new ArrayList<>(blocks))) {
				helper.fail("BlockEntityDef '" + def.id() + "': blockSet() iterates in " + blocks
						+ " but the manifest declares " + expected + " — the set must keep insertion "
						+ "order (Set.copyOf is salted and would drift between runs).");
				return;
			}

			Block foreign = Blocks.STONE;
			if (!mutationIsRefused(() -> blocks.add(foreign))) {
				helper.fail("BlockEntityDef '" + def.id() + "': blockSet().add(...) succeeded — the set "
						+ "handed to new BlockEntityType<>(factory, blockSet()) is stored by reference "
						+ "and read by isValid(), so a mutable one lets any caller silently change which "
						+ "blocks the type attaches to (MOD-417).");
				return;
			}
			Block first = expected.getFirst();
			if (!mutationIsRefused(() -> blocks.remove(first))) {
				helper.fail("BlockEntityDef '" + def.id() + "': blockSet().remove(...) succeeded — "
						+ "blocking add() alone is not immutability (MOD-417).");
				return;
			}
			if (!mutationIsRefused(blocks::clear)) {
				helper.fail("BlockEntityDef '" + def.id() + "': blockSet().clear() succeeded — "
						+ "blocking add()/remove() alone is not immutability (MOD-417).");
				return;
			}
			if (!expected.equals(new ArrayList<>(blocks))) {
				helper.fail("BlockEntityDef '" + def.id() + "': the refused mutations still changed the "
						+ "set — it now reads " + blocks + " instead of " + expected + ".");
				return;
			}

			BlockEntityType<?> type = def.registeredType();
			for (Block block : expected) {
				if (!type.isValid(block.defaultBlockState())) {
					helper.fail("BlockEntityDef '" + def.id() + "': the REGISTERED BlockEntityType does "
							+ "not accept " + blockId(block) + ", which the manifest lists — the set this "
							+ "test asserted on is not the set the game uses.");
					return;
				}
			}
			if (type.isValid(foreign.defaultBlockState())) {
				helper.fail("BlockEntityDef '" + def.id() + "': the registered BlockEntityType accepts "
						+ blockId(foreign) + ", which the manifest never listed.");
				return;
			}
		}
		helper.succeed();
	}

	/** {@code true} when the mutation was refused with {@link UnsupportedOperationException}. */
	private static boolean mutationIsRefused(Runnable mutation) {
		try {
			mutation.run();
			return false;
		} catch (UnsupportedOperationException expected) {
			return true;
		}
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
	}

	private static Block ore(String path) {
		return BuiltInRegistries.BLOCK.getValue(Industrialization.id(path));
	}

	private static String blockId(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	private static void assertItemInTag(List<String> failures, String itemPath, String tagPath) {
		Item item = BuiltInRegistries.ITEM.getValue(Industrialization.id(itemPath));
		TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", tagPath));
		if (!new ItemStack(item).is(tag)) {
			failures.add(itemPath + " item not in #c:" + tagPath);
		}
	}
}

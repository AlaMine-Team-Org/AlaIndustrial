package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.alaindustrial.BuildInfo;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * L2 server game tests — the **common-to-all-blocks** layer (RULES.md {@code R-*}). These run in a
 * real {@link net.minecraft.server.level.ServerLevel} via {@code ./gradlew runGameTest} and exit
 * non-zero on failure, so a regression fails CI (unlike the legacy logging self-test).
 *
 * <p>Parametric over the whole {@code alaindustrial} block registry — new blocks are covered
 * automatically, no per-block edit (mirrors the legacy {@code BLOCK_STANDARDS} check). Per-block
 * functional suites and integration scenarios come on top of this layer.
 *
 * <p>See docs/testing/AUTOMATION-STANDARDS.md (§2 naming, §3 traceability, §4 world conditions).
 */
public class AlaCommonGameTest {

	/** Reused single cell inside the test region; placed, asserted, cleared per block. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * R-PHY-01 (common): every registered {@code alaindustrial} block places and breaks without a
	 * crash, and the placed block is actually the one we asked for.
	 *
	 * @implements R-PHY-01 (all blocks) — see docs/testing/RULES.md
	 */
	@GameTest
	public void everyBlockPlacesAndBreaks(GameTestHelper helper) {
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
	 * R-BRK-01 (common): every block, when broken, drops exactly one of itself — no dupe, no loss.
	 * Uses the real loot path ({@link Block#getDrops}), so a broken loot table fails here.
	 *
	 * @implements R-BRK-01 (all blocks) — see docs/testing/RULES.md
	 */
	@GameTest
	public void everyBlockDropsItself(GameTestHelper helper) {
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
			Block block = BuiltInRegistries.BLOCK.getValue(id);
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
	@GameTest
	public void everyBlockNoDropByHand(GameTestHelper helper) {
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
			Block block = BuiltInRegistries.BLOCK.getValue(id);
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
	@GameTest
	public void blockStandardsAllBlocks(GameTestHelper helper) {
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
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			BlockState state = block.defaultBlockState();

			// 1. Occlusion <=> full collision cube.
			boolean fullCube = state.isCollisionShapeFullBlock(level, probe)
					&& Block.isShapeFullBlock(state.getCollisionShape(level, probe, CollisionContext.empty()));
			boolean occludes = state.canOcclude();
			boolean occlusionOk = (fullCube == occludes);

			// 2. A BlockItem is registered under the same id.
			boolean hasItem = BuiltInRegistries.ITEM.containsKey(id);

			// 3. The loot-table datapack resource exists.
			boolean hasLoot = false;
			if (block.getLootTable().isPresent()) {
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
	@GameTest
	public void alaCommandRegistered(GameTestHelper helper) {
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

}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.BlockBreakerBlock;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity.RedstoneMode;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity.Status;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral gametests of the Block Breaker (MOD-787), run by both loaders from the roster.
 *
 * <p>The machine faces UP in every rig: it is the first machine with a menu that faces up or down, so
 * the vertical case is the one worth pinning, and the target above it stays inside the arena.
 *
 * <p>The break goes through the loader's fake player ({@code FakePlayers}), so these tests exercise
 * the real path on each loader — the one a land-claim mod can veto — not a shortcut.
 */
public final class BlockBreakerScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockBreakerScenarios::breaksStoneLikeAPlayer, "mod787_breaks_stone_like_a_player")
						.ticks(40, 40),
				RosterEntry.of(BlockBreakerScenarios::higherTierBlockDropsNothing,
						"mod787_higher_tier_block_drops_nothing").ticks(40, 40),
				RosterEntry.of(BlockBreakerScenarios::emptySlotSpendsNothing, "mod787_empty_slot_spends_nothing"),
				RosterEntry.of(BlockBreakerScenarios::leverSwitchesItOff, "mod787_lever_switches_it_off"),
				RosterEntry.of(BlockBreakerScenarios::noOwnerBreaksNothing, "mod787_no_owner_breaks_nothing"),
				RosterEntry.of(BlockBreakerScenarios::slotTakesStoneTierOnly, "mod787_slot_takes_stone_tier_only"),
				RosterEntry.of(BlockBreakerScenarios::workingFaceTakesNoEnergy, "mod787_working_face_takes_no_energy"));

		private Roster() {}
	}

	private BlockBreakerScenarios() {}

	private static final BlockPos BREAKER = new BlockPos(1, 2, 1);
	private static final BlockPos TARGET = BREAKER.above();
	private static final UUID OWNER =
			UUID.nameUUIDFromBytes("mod787-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	/** A powered, owned breaker facing up with {@code tool} in its slot. */
	private static BlockBreakerBlockEntity rig(GameTestHelper helper, ItemStack tool, boolean owned) {
		helper.setBlock(BREAKER, ModContent.BLOCK_BREAKER.get().defaultBlockState()
				.setValue(BlockBreakerBlock.FACING, Direction.UP));
		BlockBreakerBlockEntity breaker = helper.getBlockEntity(BREAKER, BlockBreakerBlockEntity.class);
		breaker.getEnergyStorage().setAmountUntracked(Config.blockBreakerBuffer);
		if (owned) {
			breaker.setOwner(OWNER, "Mod787Owner");
		}
		breaker.setItem(BlockBreakerBlockEntity.TOOL_SLOT, tool);
		return breaker;
	}

	/**
	 * @implements MOD-787-AC02 — a stone pickaxe takes cobblestone the way a player does: the block is
	 *     gone, cobblestone lies where it stood, the pickaxe lost one point of durability, energy was
	 *     spent only while breaking, and the break counts as one operation.
	 */
	public static void breaksStoneLikeAPlayer(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, new ItemStack(Items.STONE_PICKAXE), true);
		helper.setBlock(TARGET, Blocks.COBBLESTONE);
		long before = breaker.getEnergyStorage().getAmount();

		// Cobblestone: hardness 2, stone pickaxe speed 4 -> 4 / 2 / 30 per tick -> 15 ticks (16 if the float
		// sum of fifteen fifteenths lands a hair under one).
		AlaGameTestHelper.drive(breaker, helper, 17);

		helper.assertBlockPresent(Blocks.AIR, TARGET);
		helper.assertItemEntityPresent(Items.COBBLESTONE, TARGET, 2.0);
		ItemStack tool = breaker.getItem(BlockBreakerBlockEntity.TOOL_SLOT);
		helper.assertTrue(tool.is(Items.STONE_PICKAXE) && tool.getDamageValue() == 1,
				"the pickaxe must come back worn by exactly one, got " + tool + " damage " + tool.getDamageValue());
		long spent = before - breaker.getEnergyStorage().getAmount();
		helper.assertTrue(spent == 15L * Config.blockBreakerEuPerTick || spent == 16L * Config.blockBreakerEuPerTick,
				"15 (or 16) breaking ticks at " + Config.blockBreakerEuPerTick
						+ " EU/t and nothing in the pause, spent "
						+ spent);
		helper.assertTrue(breaker.totalItemsProcessed() == 1,
				"one broken block is one operation, got " + breaker.totalItemsProcessed());
		helper.succeed();
	}

	/**
	 * @implements MOD-787-AC02 — diamond ore under a stone pickaxe breaks, slowly, and drops nothing:
	 *     the player's correct-tool rule, not the loot table, decides the drop.
	 */
	public static void higherTierBlockDropsNothing(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, new ItemStack(Items.STONE_PICKAXE), true);
		helper.setBlock(TARGET, Blocks.DIAMOND_ORE);

		// Hardness 3, speed 4, wrong tier -> 4 / 3 / 100 per tick -> 75 ticks.
		AlaGameTestHelper.drive(breaker, helper, 78);

		helper.assertBlockPresent(Blocks.AIR, TARGET);
		helper.assertItemEntityNotPresent(Items.DIAMOND, TARGET, 2.0);
		helper.assertItemEntityNotPresent(Items.DIAMOND_ORE, TARGET, 2.0);
		helper.succeed();
	}

	/** @implements MOD-787-AC01 — no tool: the block stays, nothing is spent, the screen says why. */
	public static void emptySlotSpendsNothing(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, ItemStack.EMPTY, true);
		helper.setBlock(TARGET, Blocks.COBBLESTONE);
		long before = breaker.getEnergyStorage().getAmount();

		AlaGameTestHelper.drive(breaker, helper, 5);

		helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET);
		helper.assertTrue(breaker.getEnergyStorage().getAmount() == before, "an idle breaker must spend nothing");
		helper.assertTrue(statusOf(breaker) == Status.NO_TOOL, "status must be NO_TOOL, got " + statusOf(breaker));
		helper.assertBlockProperty(BREAKER, BlockBreakerBlock.LIT, false);
		helper.succeed();
	}

	/**
	 * @implements MOD-787-AC07 — in "works without a signal" a powered breaker stands still: a lever on
	 *     it is its off switch.
	 */
	public static void leverSwitchesItOff(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, new ItemStack(Items.STONE_PICKAXE), true);
		breaker.setRedstoneMode(RedstoneMode.WITHOUT_SIGNAL);
		helper.setBlock(TARGET, Blocks.COBBLESTONE);
		helper.setBlock(BREAKER.east(), Blocks.REDSTONE_BLOCK);

		AlaGameTestHelper.drive(breaker, helper, 20);

		helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET);
		helper.assertTrue(statusOf(breaker) == Status.REDSTONE, "status must be REDSTONE, got " + statusOf(breaker));
		helper.succeed();
	}

	/** @implements MOD-787-AC05 — a breaker nobody placed has nobody to break as, and breaks nothing. */
	public static void noOwnerBreaksNothing(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, new ItemStack(Items.STONE_PICKAXE), false);
		helper.setBlock(TARGET, Blocks.COBBLESTONE);

		AlaGameTestHelper.drive(breaker, helper, 20);

		helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET);
		helper.assertTrue(statusOf(breaker) == Status.NO_OWNER, "status must be NO_OWNER, got " + statusOf(breaker));
		helper.succeed();
	}

	/** @implements MOD-787-AC02 — the slot takes tools up to stone tier and shears, nothing better. */
	public static void slotTakesStoneTierOnly(GameTestHelper helper) {
		BlockBreakerBlockEntity breaker = rig(helper, ItemStack.EMPTY, true);
		for (var item : List.of(Items.WOODEN_PICKAXE, Items.STONE_AXE, Items.GOLDEN_SHOVEL, Items.STONE_HOE,
				Items.WOODEN_SWORD, Items.SHEARS)) {
			helper.assertTrue(breaker.canPlaceItem(BlockBreakerBlockEntity.TOOL_SLOT, new ItemStack(item)),
					item + " must fit the slot");
		}
		for (var item : List.of(Items.IRON_PICKAXE, Items.DIAMOND_AXE, Items.NETHERITE_SHOVEL, Items.COBBLESTONE,
				ModContent.ELECTRIC_DRILL.get())) {
			helper.assertFalse(breaker.canPlaceItem(BlockBreakerBlockEntity.TOOL_SLOT, new ItemStack(item)),
					item + " must not fit the slot of a first-tier machine");
		}
		helper.succeed();
	}

	/** @implements MOD-787-AC01 — energy comes in on every face but the working one, in all six facings. */
	public static void workingFaceTakesNoEnergy(GameTestHelper helper) {
		for (Direction facing : Direction.values()) {
			helper.setBlock(BREAKER, ModContent.BLOCK_BREAKER.get().defaultBlockState()
					.setValue(BlockBreakerBlock.FACING, facing));
			BlockBreakerBlockEntity breaker = helper.getBlockEntity(BREAKER, BlockBreakerBlockEntity.class);
			for (Direction face : Direction.values()) {
				EnergyRole expected = face == facing ? EnergyRole.NONE : EnergyRole.IN;
				helper.assertTrue(breaker.energyRoleForFace(face) == expected,
						"facing " + facing + ", face " + face + ": expected " + expected);
				helper.assertTrue(((BlockBreakerBlock) ModContent.BLOCK_BREAKER.get())
								.isCableConnectable(breaker.getBlockState(), face) == (face != facing),
						"facing " + facing + ": a cable must attach to every face but the working one, face " + face);
			}
		}
		helper.succeed();
	}

	private static Status statusOf(BlockBreakerBlockEntity breaker) {
		return Status.byOrdinal(breaker.getDataAccess().get(BlockBreakerBlockEntity.Channel.STATUS.ordinal()));
	}
}

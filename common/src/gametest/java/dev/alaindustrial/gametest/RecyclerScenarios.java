package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.MachineRig.AMPLE_EU;
import static dev.alaindustrial.gametest.MachineRig.place;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.block.RecyclerBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * World scenarios for the recycler (MOD-145): the grading rule and the two upkeep gates.

 * <p>Split by mechanic in MOD-717 (TST-2, the {@code ReactorScenarios} pattern): the lanes run the names in
 * {@link MachineScenarios}, which delegates here; a new processing-machine scenario is written in the class of
 * its mechanic and declared in that class's roster.
 */
public final class RecyclerScenarios {

	private RecyclerScenarios() {
	}

	// --- MOD-145: the Recycler -------------------------------------------------------------------
	// The machine's whole point is the grading rule, and none of it can be seen from a recipe file:
	// what these guard is that a one-note batch pays ballast, a mixed one pays rich, and that the two
	// upkeep gates (blades, ash) actually stop the machine rather than being decorative.

	private static RecyclerBlockEntity placeRecycler(GameTestHelper helper, Item blade) {
		RecyclerBlockEntity be = (RecyclerBlockEntity) place(helper, ModContent.RECYCLER.get());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU * 8);
		be.setItem(RecyclerBlockEntity.BLADE_SLOT, new ItemStack(blade));
		return be;
	}

	/** Grinds {@code count} items of {@code stack}, one operation each, with ample power. */
	private static void grind(RecyclerBlockEntity be, GameTestHelper helper, ItemStack stack, int count) {
		for (int i = 0; i < count; i++) {
			be.setItem(RecyclerBlockEntity.INPUT_SLOT, stack.copy());
			be.getEnergyStorage().setAmountUntracked(AMPLE_EU * 8);
			drive(be, helper, Config.recyclerDuration * 3);
		}
	}

	/**
	 * TC-RECYCLER-001-FUN01: sixteen blocks of one kind fill a batch and cast POOR slag.
	 *
	 * @implements TC-RECYCLER-001-FUN01 — sixteen blocks of one kind fill a batch and cast POOR slag.
	 */
	public static void tcRecycler001Fun01_monoBatchCastsPoor(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_DIAMOND.get());
		grind(be, helper, new ItemStack(Items.COBBLESTONE), 16);
		ItemStack out = be.getItem(RecyclerBlockEntity.SLAG_SLOT);
		if (!out.is(ModContent.SLAG_POOR.get())) {
			helper.fail("recycler: a single-fraction batch must cast poor slag, got "
					+ (out.isEmpty() ? "nothing" : out.getItem().toString()));
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-FUN02: a balanced batch of all three fractions casts RICH slag. Deliberately fed
	 * in equal masses — stone (mineral), planks (burnable) and iron ingots (metal) — because the grade
	 * is decided by share, not by count.
	 *
	 * @implements TC-RECYCLER-001-FUN02 — a balanced batch of all three fractions casts RICH slag.
	 */
	public static void tcRecycler001Fun02_mixedBatchCastsRich(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_DIAMOND.get());
		grind(be, helper, new ItemStack(Items.STONE), 6);
		grind(be, helper, new ItemStack(Items.OAK_PLANKS), 6);
		grind(be, helper, new ItemStack(Items.IRON_INGOT), 8);
		ItemStack out = be.getItem(RecyclerBlockEntity.SLAG_SLOT);
		if (!out.is(ModContent.SLAG_RICH.get())) {
			helper.fail("recycler: a three-fraction batch must cast rich slag, got "
					+ (out.isEmpty() ? "nothing" : out.getItem().toString()));
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-CON01: grass block counts as MINERAL, not burnable. A regression guard for the
	 * classifier: grass shares its sound type with leaves, so the fallback used to file a hillside of
	 * dirt as firewood and quietly spoil the player's batch.
	 *
	 * @implements TC-RECYCLER-001-CON01 — grass block grades as MINERAL, not burnable (its sound type is shared with
	 *     leaves).
	 */
	public static void tcRecycler001Con01_grassIsMineral(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_DIAMOND.get());
		grind(be, helper, new ItemStack(Items.GRASS_BLOCK), 16);
		ItemStack out = be.getItem(RecyclerBlockEntity.SLAG_SLOT);
		if (!out.is(ModContent.SLAG_POOR.get())) {
			helper.fail("recycler: grass must grade as one fraction (mineral) and cast poor slag, got "
					+ (out.isEmpty() ? "nothing" : out.getItem().toString()));
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-CON02: without blades the machine does not run at all.
	 *
	 * @implements TC-RECYCLER-001-CON02 — without blades the machine does not run at all.
	 */
	public static void tcRecycler001Con02_noBladesNoWork(GameTestHelper helper) {
		RecyclerBlockEntity be = (RecyclerBlockEntity) place(helper, ModContent.RECYCLER.get());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU * 8);
		be.setItem(RecyclerBlockEntity.INPUT_SLOT, new ItemStack(Items.COBBLESTONE, 16));
		drive(be, helper, Config.recyclerDuration * 4);
		if (be.getItem(RecyclerBlockEntity.INPUT_SLOT).getCount() != 16) {
			helper.fail("recycler: it consumed input with no blades installed");
		}
		if (be.batchMass() != 0) {
			helper.fail("recycler: the batch grew with no blades installed");
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-CON03: a full ash bin stops the machine instead of voiding the ash.
	 *
	 * @implements TC-RECYCLER-001-CON03 — a full ash bin stops the machine instead of voiding the ash.
	 */
	public static void tcRecycler001Con03_fullAshStops(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_IRON.get());
		be.setItem(RecyclerBlockEntity.ASH_SLOT, new ItemStack(ModContent.ASH.get(), 64));
		be.setItem(RecyclerBlockEntity.INPUT_SLOT, new ItemStack(Items.COBBLESTONE, 16));
		drive(be, helper, Config.recyclerDuration * 4);
		if (be.getItem(RecyclerBlockEntity.INPUT_SLOT).getCount() != 16) {
			helper.fail("recycler: it kept grinding with a full ash bin");
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-FUN03: the front panel's lamp count follows the fractions in the batch.
	 *
	 * @implements TC-RECYCLER-001-FUN03 — the front panel's lamp count follows the fractions in the batch.
	 */
	public static void tcRecycler001Fun03_lampsFollowFractions(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_DIAMOND.get());
		grind(be, helper, new ItemStack(Items.STONE), 1);
		assertLamps(helper, be, 1);
		grind(be, helper, new ItemStack(Items.OAK_PLANKS), 1);
		assertLamps(helper, be, 2);
		grind(be, helper, new ItemStack(Items.IRON_INGOT), 1);
		assertLamps(helper, be, 3);
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-CON04: a briquette of another grade already in the output slot must not stop the
	 * machine while the batch is nowhere near casting.
	 *
	 * <p>Three operations, because the defect needed the batch to CHANGE grade under a leftover: stone
	 * leaves it POOR (which matches the poor briquette in the slot), the ingot turns it COMMON, and the
	 * third item is the one the machine used to refuse — reporting "output full" over a slot holding one
	 * item of sixty-four.
	 *
	 * @implements TC-RECYCLER-001-CON04 — a leftover briquette of another grade does not block the batch.
	 */
	public static void tcRecycler001Con04_leftoverBriquetteDoesNotBlock(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_DIAMOND.get());
		be.setItem(RecyclerBlockEntity.SLAG_SLOT, new ItemStack(ModContent.SLAG_POOR.get(), 1));
		grind(be, helper, new ItemStack(Items.STONE), 1);
		grind(be, helper, new ItemStack(Items.IRON_INGOT), 1);
		grind(be, helper, new ItemStack(Items.STONE), 1);
		if (be.batchMass() != 10) {
			helper.fail("recycler: a leftover briquette of another grade blocked the batch — expected"
					+ " mass 10 after three items, got " + be.batchMass());
		}
		helper.succeed();
	}

	/**
	 * TC-RECYCLER-001-CON05: the ash the INSTALLED blades owe must fit before a batch may cast.
	 *
	 * <p>Iron blades leave four ash; a bin holding sixty-two has room for two. The gate used to reserve
	 * the diamond set's two whatever was fitted, so the batch cast anyway and the bin was grown to
	 * sixty-six — {@code addOutput} takes the caller's word for it and does not re-check.
	 *
	 * @implements TC-RECYCLER-001-CON05 — the ash the installed blades owe must fit before a batch casts.
	 */
	public static void tcRecycler001Con05_ashNeverOverstacks(GameTestHelper helper) {
		RecyclerBlockEntity be = placeRecycler(helper, ModContent.RECYCLER_BLADES_IRON.get());
		grind(be, helper, new ItemStack(Items.COBBLESTONE), 15); // mass 60 of the 64 a batch needs
		be.setItem(RecyclerBlockEntity.ASH_SLOT, new ItemStack(ModContent.ASH.get(), 62));
		grind(be, helper, new ItemStack(Items.COBBLESTONE), 1);
		ItemStack ash = be.getItem(RecyclerBlockEntity.ASH_SLOT);
		if (ash.getCount() > ash.getMaxStackSize()) {
			helper.fail("recycler: the ash bin grew past its stack limit to " + ash.getCount());
		}
		if (!be.getItem(RecyclerBlockEntity.SLAG_SLOT).isEmpty()) {
			helper.fail("recycler: it cast a briquette although the ash it owed did not fit");
		}
		helper.succeed();
	}

	private static void assertLamps(GameTestHelper helper, RecyclerBlockEntity be, int expected) {
		int lamps = be.getBlockState().getValue(RecyclerBlock.LAMPS);
		if (lamps != expected) {
			helper.fail("recycler: expected " + expected + " lamp(s) lit, blockstate says " + lamps);
		}
	}
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.item.misc.TownHallPlanSheetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.plan.PlanSheetHints;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.PlanSheetNews;
import dev.alaindustrial.settlement.works.PlanSheetRows;
import dev.alaindustrial.settlement.works.SettlementWorks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * The town hall plan in the core (MOD-859, R92 of MOD-824): the hall's log line its core writes on waking, once per
 * town, and the «Plan sheets» section of the core's «Construction» tab, which lists as found the sheets that lay in
 * the town's working dispatcher and nothing else (ADR-010).
 */
public final class PlanSheetSectionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PlanSheetSectionScenarios::wakingCoreWritesThePlanLineOnce,
						"plan_sheet_waking_core_writes_the_plan_line_once").ticks(40),
				RosterEntry.of(PlanSheetSectionScenarios::sectionListsTheSheetsTheDispatcherSaw,
						"plan_sheet_section_lists_the_sheets_the_dispatcher_saw").ticks(60));

		private Roster() {}
	}

	private PlanSheetSectionScenarios() {}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	private static final String PLAINS = "alaindustrial:plains";
	private static final UUID OWNER = UUID.fromString("00000000-0000-4859-8000-000000000859");

	/**
	 * @implements MOD-859-RULES — a town's core waking writes the hall's log line about the plan; going dark and lit
	 *     again writes no second one, the line comes back from a save, and a town of no style gets none.
	 */
	public static void wakingCoreWritesThePlanLineOnce(GameTestHelper helper) {
		SettlementCoreBlockEntity core = AlaGameTestHelper.place(helper, CORE, ModContent.SETTLEMENT_CORE.get(),
				SettlementCoreBlockEntity.class);
		AlaGameTestHelper.drive(core, helper, 1);
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = core.settlementId();
		helper.assertTrue(town > 0, "control: the core registered no town");
		registry.setStyle(town, PLAINS);
		helper.assertTrue(planLines(registry.workers(), town) == 0, "a dark core must write nothing yet");

		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		helper.assertTrue(core.isActive(), "control: a full buffer did not light the core");
		helper.assertTrue(planLines(registry.workers(), town) == 1,
				"the waking core must write one plan line, the log holds " + planLines(registry.workers(), town));

		charge(core, 0);
		AlaGameTestHelper.drive(core, helper, SettlementConfig.settlementCoreGraceTicks + 1);
		helper.assertTrue(!core.isActive(), "control: the core did not go dark");
		charge(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		helper.assertTrue(core.isActive(), "control: the core did not light again");
		helper.assertTrue(planLines(registry.workers(), town) == 1,
				"lighting again must not write a second plan line, the log holds "
						+ planLines(registry.workers(), town));

		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ShiftWorkerBook back = ShiftWorkerBook.CODEC.parse(ops,
				ShiftWorkerBook.CODEC.encodeStart(ops, registry.workers()).getOrThrow()).getOrThrow();
		helper.assertTrue(planLines(back, town) == 1, "the plan line must come back from a save");
		helper.assertTrue(!PlanSheetNews.coreWoke(helper.getLevel(), town),
				"a town whose log holds the line must not get it again");

		BlockPos far = helper.absolutePos(CORE).offset(0, 0, 40_000);
		int bare = registry.place(helper.getLevel().dimension(), far, 0).settlementId();
		helper.assertTrue(registry.find(bare).map(Settlement::style).orElse("x").isEmpty(),
				"control: a town no structure built has no style");
		helper.assertTrue(!PlanSheetNews.coreWoke(helper.getLevel(), bare) && planLines(registry.workers(), bare) == 0,
				"a town of no style has no sheets and must get no plan line");
		helper.succeed();
	}

	/**
	 * @implements MOD-859-RULES — the «Plan sheets» section lists the three sheets, each with where to look: a sheet of
	 *     the town's style lying in its dispatcher becomes found and stays found when taken out, a sheet of another
	 *     style finds nothing, the hall's plan finds all three; the found sheets come back from a save, and an older
	 *     save without them reads none.
	 */
	public static void sectionListsTheSheetsTheDispatcherSaw(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 0, OWNER, false);
		try {
			TownDispatcherBlockEntity dispatcher = rig.dispatcher();
			helper.assertTrue(dispatcher.status().serves(), "control: the hall's dispatcher must serve, it says "
					+ dispatcher.status());
			assertSection(helper, rig, 0, "before any sheet");

			Identifier plains = Identifier.parse(PLAINS);
			dispatcher.setItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST,
					TownHallPlanSheetItem.of(Industrialization.id("desert"), 1));
			dispatcher.setItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST + 1, TownHallPlanSheetItem.of(plains, 2));
			rig.tickDispatcherAndNests();
			assertSection(helper, rig, 0b010, "with sheet 2 of the style and a desert sheet 1");

			dispatcher.setItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST + 1, ItemStack.EMPTY);
			rig.tickDispatcherAndNests();
			assertSection(helper, rig, 0b010, "after sheet 2 was taken out again");

			dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT, TownDroneScenarios.hallPlan());
			rig.tickDispatcherAndNests();
			assertSection(helper, rig, PlanSheetRules.ALL_FOUND, "with the hall's plan");

			RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
			SettlementWorks all = SettlementRegistry.get(helper.getLevel().getServer()).works();
			SettlementWorks back = SettlementWorks.CODEC.parse(ops, SettlementWorks.CODEC.encodeStart(ops, all)
					.getOrThrow()).getOrThrow();
			helper.assertTrue(back.town(rig.town()).foundSheets() == PlanSheetRules.ALL_FOUND,
					"the found sheets must come back from a save, read " + back.town(rig.town()).foundSheets());
			SettlementWorks older = SettlementWorks.CODEC.parse(ops, parse(
					"{works:[{settlement:5,known:1b,next_job:2}]}").get("works")).getOrThrow();
			helper.assertTrue(older.town(5).foundSheets() == 0, "a save without found sheets must read none");
			helper.succeed();
		} finally {
			rig.release();
		}
	}

	/** The section's rows for the rig's town match {@code mask}, for the owner and for a stranger alike. */
	private static void assertSection(GameTestHelper helper, TownDroneScenarios.Rig rig, int mask, String when) {
		helper.assertTrue(rig.works().foundSheets() == mask,
				"found sheets " + when + ": expected " + mask + ", got " + rig.works().foundSheets());
		Settlement town = SettlementRegistry.get(rig.level().getServer()).find(rig.town()).orElseThrow();
		for (boolean manage : new boolean[] {true, false}) {
			List<ConstructionRow> rows = ConstructionRows.collect(
					new ConstructionRows.Context(rig.level(), town, rig.works(), manage)).stream()
					.filter(row -> row.section().equals(PlanSheetRows.SECTION)).toList();
			helper.assertTrue(rows.equals(PlanSheetRows.rows(mask)),
					"the section " + when + " (manage " + manage + ") reads " + rows);
			for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
				ConstructionRow row = rows.get(sheet - 1);
				helper.assertTrue(row.text().key().equals(PlanSheetHints.hintKey(sheet)) && row.actions() == 0,
						"sheet " + sheet + " must name its place and offer no control: " + row);
			}
		}
	}

	private static void charge(SettlementCoreBlockEntity core, long amount) {
		core.getEnergyStorage().setAmountUntracked(amount);
		core.wake();
	}

	/** How many plan lines town {@code town} has in {@code book}. */
	private static long planLines(ShiftWorkerBook book, int town) {
		return book.log().stream().filter(line -> line.settlement() == town && line.note()
				.map(note -> note.key().equals(PlanSheetHints.JOURNAL_CORE_AWAKE)).orElse(false)).count();
	}
}

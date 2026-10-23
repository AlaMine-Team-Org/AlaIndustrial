package dev.alaindustrial.gametest;

import dev.alaindustrial.client.screen.SettlementCoreScreen;
import dev.alaindustrial.client.screen.works.TownWorksClient;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildingTile;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.NextStepHint;
import dev.alaindustrial.settlement.works.PlanSheetRows;
import dev.alaindustrial.settlement.works.StageRows;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/**
 * The L3 frames of the settlement core's «Construction» tab (MOD-857; owner's round 3 of 0.1.219, R3-3, R3-6), taken by
 * {@link ScreensClientGameTest} right after the core's own frames, on the same opened screen.
 */
final class SettlementCoreWorksShot {

    private static final List<String> SHORT = List.of(
            ConstructionRow.Text.counted(13, "block.minecraft.stone_bricks"),
            ConstructionRow.Text.counted(4, "block.minecraft.glass"));

    private SettlementCoreWorksShot() {
    }

    /**
     * The tab as cards. The rig's core is bare and of no town, so the server answers no request; the rows, the cards'
     * data and the hint are handed to the client as the server would send them to the town's owner, after the tab was
     * opened through the screen's own selector: the hall waiting for materials (its card open by itself, with its
     * stage and its blocked job), three houses — one being built, one with no plan, one to restore — and the intact
     * warehouse.
     */
    static void capture(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof SettlementCoreScreen core) {
                core.selectTab(SettlementCoreScreen.TAB_WORKS);
                List<ConstructionRow> rows = new ArrayList<>();
                rows.add(worksRow("settlement.alaindustrial.building.town_hall", "clearing", 0, 52,
                        ConstructionRow.Tone.NORMAL, Optional.of(new ConstructionRow.Text(
                                ConstructionRows.MISSING_KEYS, List.of("settlement.alaindustrial.building.key.roof",
                                        "settlement.alaindustrial.building.key.drone_pad"), true)),
                        ConstructionRow.Action.ORDER_CANCEL.bit() | ConstructionRow.Action.SCHEMATIC.bit()));
                rows.add(worksRow("settlement.alaindustrial.building.capsule_house.type_1", "repair", 1, 80,
                        ConstructionRow.Tone.NORMAL, Optional.empty(), ConstructionRow.Action.ORDER_CANCEL.bit()));
                rows.add(worksRow("settlement.alaindustrial.building.capsule_house.type_2", "ruin", 2, 70,
                        ConstructionRow.Tone.NORMAL, Optional.of(ConstructionRow.Text.of(
                                ConstructionRows.NO_HOUSE_PLAN, "2")), 0));
                rows.add(worksRow("settlement.alaindustrial.building.capsule_house.type_3", "ruin", 3, 66,
                        ConstructionRow.Tone.NORMAL, Optional.empty(), ConstructionRow.Action.ORDER_RESTORE.bit()
                                | ConstructionRow.Action.SCHEMATIC.bit()));
                rows.add(worksRow("settlement.alaindustrial.building.warehouse", "intact", 4, 100,
                        ConstructionRow.Tone.GOOD, Optional.empty(), 0));
                rows.add(network());
                rows.addAll(PlanSheetRows.rows(0b111));
                rows.add(housePlan(1, true));
                rows.add(housePlan(2, false));
                rows.add(housePlan(3, true));
                rows.add(new ConstructionRow(StageRows.SECTION, ConstructionRow.Text.of(StageRows.LINE,
                        "settlement.alaindustrial.building.town_hall", "2", "5",
                        "settlement.alaindustrial.works.stage.walls"),
                        Optional.of(ConstructionRow.Text.of("settlement.alaindustrial.works.stage_count", "60", "80")),
                        75, ConstructionRow.Tone.BAD,
                        Optional.of(ConstructionRow.Text.of(BlockReason.noMaterials(SHORT))), 0, 0));
                rows.add(new ConstructionRow(ConstructionRows.SECTION_QUEUE, ConstructionRow.Text.of(
                        ConstructionRows.JOB_LINE, "settlement.alaindustrial.works.job.build",
                        "settlement.alaindustrial.building.town_hall"),
                        Optional.of(ConstructionRow.Text.of("settlement.alaindustrial.works.status.blocked")),
                        -1, ConstructionRow.Tone.BAD, Optional.of(ConstructionRow.Text.of(
                                BlockReason.obstructed("block.minecraft.chest", 709, 96, 260))), 7,
                        ConstructionRow.Action.CANCEL.bit() | ConstructionRow.Action.START.bit()));
                List<BuildingTile> tiles = List.of(
                        tile(0, "plains/town_hall", BuildingTile.Group.HALL, BuildingTile.Chip.WAITING, List.of(7)),
                        tile(1, "plains/capsule_house_1", BuildingTile.Group.HOUSE, BuildingTile.Chip.WORKING,
                                List.of()),
                        tile(2, "plains/capsule_house_2", BuildingTile.Group.HOUSE, BuildingTile.Chip.NO_PLAN,
                                List.of()),
                        tile(3, "plains/capsule_house_3", BuildingTile.Group.HOUSE, BuildingTile.Chip.RUIN,
                                List.of()),
                        tile(4, "plains/warehouse", BuildingTile.Group.OTHER, BuildingTile.Chip.DONE, List.of()));
                TownWorksClient.receive(new TownWorksPayload(pos, 3, true,
                        Optional.of(new ConstructionRow.Text(NextStepHint.MATERIALS, SHORT, true)), tiles, rows));
            }
        });
        context.waitFor(mc -> mc.gui.screen() instanceof SettlementCoreScreen core
                && core.selectedTab() == SettlementCoreScreen.TAB_WORKS);
        context.waitTicks(2);
        ShotRecorder.captureScreen("settlement_core", "works", ShotRecorder.rules("R-GUI-03"),
                "Construction tab as cards: a dark hint bar at the top asking for stone bricks and glass in the "
                        + "dispatcher's buffer; a «Town hall» heading over the hall's card — its picture, a red "
                        + "«waiting» chip, 52 %, a barrier button — open below with «Stage 2 of 5: Walls 60/80», a "
                        + "copper bar, the missing materials in red, the blocked build job with its small button and "
                        + "the schematic switch; a «Houses» heading over the house cards (blue «under way», slate "
                        + "«no plan», brown «ruin» with a brick button); every card inside the frame, a scroll bar "
                        + "at the right");
        TownWorksClient.forget();
    }

    /**
     * The tab as a stranger to the town reads it: the hint to find the hall's plan sheets, the hall's card with no
     * order button, the road network's row with its schematic switch, and the plans — sheet 2 found, sheets 1 and 3
     * washed out.
     */
    static void captureSheets(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof SettlementCoreScreen core) {
                core.selectTab(SettlementCoreScreen.TAB_WORKS);
                List<ConstructionRow> rows = new ArrayList<>();
                rows.add(worksRow("settlement.alaindustrial.building.town_hall", "ruin", 0, 41,
                        ConstructionRow.Tone.NORMAL, Optional.empty(), ConstructionRow.Action.SCHEMATIC.bit()));
                rows.add(network());
                rows.addAll(PlanSheetRows.rows(0b010));
                List<BuildingTile> tiles = List.of(
                        tile(0, "plains/town_hall", BuildingTile.Group.HALL, BuildingTile.Chip.RUIN, List.of()));
                TownWorksClient.receive(new TownWorksPayload(pos, 3, false,
                        Optional.of(ConstructionRow.Text.of(NextStepHint.SHEETS, "1")), tiles, rows));
            }
        });
        context.waitFor(mc -> mc.gui.screen() instanceof SettlementCoreScreen core
                && core.selectedTab() == SettlementCoreScreen.TAB_WORKS);
        context.waitTicks(2);
        ShotRecorder.captureScreen("settlement_core", "works_sheets", ShotRecorder.rules("R-GUI-03"),
                "Construction tab as a stranger reads it: the hint to find the town hall's plan sheets, 1 of 3; the "
                        + "hall's card with a brown «ruin» chip and no order button; the road network's row with a "
                        + "map button; under «Plans» the hall's three sheet slots, sheet 2 bright, 1 and 3 washed "
                        + "out, «1 of 3»; the read-only note at the end, all inside the frame");
        TownWorksClient.forget();
    }

    private static ConstructionRow network() {
        return new ConstructionRow(ConstructionRows.SECTION_BUILDINGS,
                ConstructionRow.Text.of(ConstructionRows.ROAD_NETWORK), Optional.empty(), -1,
                ConstructionRow.Tone.NORMAL, Optional.empty(), ConstructionRows.NETWORK_TARGET,
                ConstructionRow.Action.SCHEMATIC.bit());
    }

    private static BuildingTile tile(int index, String icon, BuildingTile.Group group, BuildingTile.Chip chip,
            List<Integer> jobs) {
        return new BuildingTile(index, icon, group, chip, group == BuildingTile.Group.HOUSE
                ? Optional.of(ConstructionRow.Text.of("settlement.alaindustrial.card.no_resident"))
                : Optional.empty(), jobs);
    }

    private static ConstructionRow housePlan(int type, boolean known) {
        return new ConstructionRow(ConstructionRows.SECTION_HOUSE_PLANS,
                ConstructionRow.Text.of("item.alaindustrial.house_plan.typed", String.valueOf(type)),
                Optional.of(ConstructionRow.Text.of(known ? ConstructionRows.PLAN_KNOWN
                        : ConstructionRows.PLAN_UNKNOWN)),
                -1, known ? ConstructionRow.Tone.GOOD : ConstructionRow.Tone.DIM, Optional.empty(), 0, 0);
    }

    private static ConstructionRow worksRow(String name, String state, int target, int percent,
            ConstructionRow.Tone tone, Optional<ConstructionRow.Text> detail, int actions) {
        return new ConstructionRow(ConstructionRows.SECTION_BUILDINGS, ConstructionRow.Text.of(name),
                Optional.of(ConstructionRow.Text.of(ConstructionRows.STATE + state)), percent, tone, detail, target,
                actions);
    }
}

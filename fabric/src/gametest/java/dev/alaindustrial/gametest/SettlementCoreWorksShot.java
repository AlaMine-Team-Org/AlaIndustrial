package dev.alaindustrial.gametest;

import dev.alaindustrial.client.screen.SettlementCoreScreen;
import dev.alaindustrial.client.screen.works.TownWorksClient;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.PlanSheetRows;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/**
 * The L3 frame of the settlement core's «Construction» tab (MOD-857), taken by {@link ScreensClientGameTest} right
 * after the core's own frames, on the same opened screen.
 */
final class SettlementCoreWorksShot {

    private SettlementCoreWorksShot() {
    }

    /**
     * The settlement core's «Construction» tab (MOD-857). The rig's core is bare and of no town, so the server answers
     * no request; the rows are handed to the client as the server would send them to the town's owner — buildings
     * with their states and the queue with a blocked job and its reason — after the tab was opened through the
     * screen's own selector.
     */
    static void capture(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof SettlementCoreScreen core) {
                core.selectTab(SettlementCoreScreen.TAB_WORKS);
                TownWorksClient.receive(new TownWorksPayload(pos, 3, true, List.of(
                        worksRow(ConstructionRows.SECTION_BUILDINGS, "settlement.alaindustrial.building.town_hall",
                                "settlement.alaindustrial.works.state.clearing", 52, ConstructionRow.Tone.NORMAL,
                                Optional.of(new ConstructionRow.Text(ConstructionRows.MISSING_KEYS, List.of(
                                        "settlement.alaindustrial.building.key.roof",
                                        "settlement.alaindustrial.building.key.drone_pad"), true)),
                                ConstructionRow.Action.RAISE_BUILD.bit() | ConstructionRow.Action.SCHEMATIC.bit()),
                        worksRow(ConstructionRows.SECTION_BUILDINGS, "settlement.alaindustrial.building.workshop",
                                "settlement.alaindustrial.works.state.ruin", -1, ConstructionRow.Tone.DIM,
                                Optional.empty(), 0),
                        worksRow(ConstructionRows.SECTION_BUILDINGS, "settlement.alaindustrial.building.warehouse",
                                "settlement.alaindustrial.works.state.intact", 100, ConstructionRow.Tone.GOOD,
                                Optional.empty(), 0),
                        new ConstructionRow(ConstructionRows.SECTION_QUEUE, ConstructionRow.Text.of(
                                ConstructionRows.JOB_LINE, "settlement.alaindustrial.works.job.clear",
                                "settlement.alaindustrial.building.town_hall"),
                                Optional.of(ConstructionRow.Text.of("settlement.alaindustrial.works.status.blocked")),
                                -1, ConstructionRow.Tone.BAD, Optional.of(ConstructionRow.Text.of(
                                        BlockReason.obstructed("block.minecraft.chest"))), 1,
                                ConstructionRow.Action.CANCEL.bit() | ConstructionRow.Action.START.bit()),
                        new ConstructionRow(ConstructionRows.SECTION_QUEUE, ConstructionRow.Text.of(
                                ConstructionRows.JOB_LINE, "settlement.alaindustrial.works.job.build",
                                "settlement.alaindustrial.building.town_hall"),
                                Optional.of(ConstructionRow.Text.of("settlement.alaindustrial.works.status.queued")),
                                -1, ConstructionRow.Tone.NORMAL, Optional.empty(), 2,
                                ConstructionRow.Action.CANCEL.bit()))));
            }
        });
        context.waitFor(mc -> mc.gui.screen() instanceof SettlementCoreScreen core
                && core.selectedTab() == SettlementCoreScreen.TAB_WORKS);
        context.waitTicks(2);
        ShotRecorder.captureScreen("settlement_core", "works", ShotRecorder.rules("R-GUI-03"),
                "Construction tab: the fourth tab open, a heading over the buildings and over the queue, the town "
                        + "hall clearing at 52 % with its missing key points on the line below and a map icon for "
                        + "its schematic beside the brick one, the intact warehouse "
                        + "in green, the blocked clearing job in red with «obstructed by Chest» under it, the "
                        + "owner's small controls at the right edge, every column inside the frame, no energy bar");
        TownWorksClient.forget();
    }

    /**
     * The «Construction» tab with the «Plan sheets» section (MOD-859), as a stranger to the town reads it: sheet 2
     * found in green, sheets 1 and 3 missing in red, each line naming where to look, no controls on any row.
     */
    static void captureSheets(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof SettlementCoreScreen core) {
                core.selectTab(SettlementCoreScreen.TAB_WORKS);
                List<ConstructionRow> rows = new ArrayList<>();
                rows.add(worksRow(ConstructionRows.SECTION_BUILDINGS, "settlement.alaindustrial.building.town_hall",
                        "settlement.alaindustrial.works.state.ruin", 41, ConstructionRow.Tone.NORMAL,
                        Optional.empty(), ConstructionRow.Action.SCHEMATIC.bit()));
                rows.addAll(PlanSheetRows.rows(0b010));
                rows.add(new ConstructionRow(ConstructionRows.SECTION_QUEUE,
                        ConstructionRow.Text.of(ConstructionRows.NO_JOBS), Optional.empty(), -1,
                        ConstructionRow.Tone.DIM, Optional.empty(), 0, 0));
                TownWorksClient.receive(new TownWorksPayload(pos, 3, false, rows));
            }
        });
        context.waitFor(mc -> mc.gui.screen() instanceof SettlementCoreScreen core
                && core.selectedTab() == SettlementCoreScreen.TAB_WORKS);
        context.waitTicks(2);
        ShotRecorder.captureScreen("settlement_core", "works_sheets", ShotRecorder.rules("R-GUI-03"),
                "Construction tab, plan sheets: under the town hall a «Town hall plan sheets» heading and three "
                        + "rows, «Sheet 2: in the ruins of a house» found in green, sheets 1 and 3 missing in red, "
                        + "each naming where to look, the status word at the right inside the frame, no control on "
                        + "a sheet row; the queue says it is empty");
        TownWorksClient.forget();
    }

    private static ConstructionRow worksRow(String section, String name, String state, int percent,
            ConstructionRow.Tone tone, Optional<ConstructionRow.Text> detail, int actions) {
        return new ConstructionRow(section, ConstructionRow.Text.of(name), Optional.of(ConstructionRow.Text.of(state)),
                percent, tone, detail, 0, actions);
    }
}

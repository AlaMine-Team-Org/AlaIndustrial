package dev.alaindustrial.gametest;

import dev.alaindustrial.client.screen.TownDispatcherScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The town dispatcher's tab frames for {@link ScreensClientGameTest} (MOD-858), kept apart to keep that file short. */
final class TownDispatcherShots {

    private TownDispatcherShots() {
    }

    /**
     * The town dispatcher's other three tabs (MOD-858, MOD-860; owner's round 1), opened through the screen's own
     * tab switch. The rig's dispatcher serves no town, so «Buffer» shows its 27 empty slots and the hint under them,
     * «Materials» says nothing is left to build and the journal is empty; the frames guard the panel, the side strip
     * and the wrapped lines, not the numbers.
     */
    static void captureTabs(ClientGameTestContext context) {
        for (TownDispatcherScreen.Tab tab : List.of(TownDispatcherScreen.Tab.BUFFER,
                TownDispatcherScreen.Tab.MATERIALS, TownDispatcherScreen.Tab.JOURNAL)) {
            context.runOnClient(mc -> {
                if (mc.gui.screen() instanceof TownDispatcherScreen dispatcher) {
                    dispatcher.openTab(tab);
                }
            });
            context.waitTicks(2);
            ShotRecorder.captureScreen("town_dispatcher", "tab_" + tab.name().toLowerCase(java.util.Locale.ROOT),
                    ShotRecorder.rules("R-GUI-03"), "Dispatcher tab " + tab.name() + ": the tab is lit in the side "
                            + "strip, the slots of the other tabs are hidden, every line wraps inside the frame");
        }
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof TownDispatcherScreen dispatcher) {
                dispatcher.openTab(TownDispatcherScreen.Tab.TASK);
            }
        });
        // Owner's round 2: the wider window at GUI scale 3, the largest a 1280 x 720 window allows (426 x 240 scaled,
        // the smallest room a container screen gets): the checklist stays in its frame above the inventory.
        context.runOnClient(mc -> {
            mc.getWindow().setGuiScale(mc.getWindow().calculateScale(3, mc.isEnforceUnicode()));
            mc.resizeGui();
        });
        context.waitTicks(2);
        ShotRecorder.captureScreen("town_dispatcher", "tab_task_scale3", ShotRecorder.rules("R-GUI-03"),
                "Dispatcher «Task» at GUI scale 3 (426 x 240): the window and the side strip fit, the checklist is "
                        + "clipped to its frame and never reaches the inventory, «Start» is drawn disabled");
        context.runOnClient(mc -> {
            mc.getWindow().setGuiScale(mc.getWindow().calculateScale(2, mc.isEnforceUnicode()));
            mc.resizeGui();
        });
        context.waitTicks(2);
    }
}

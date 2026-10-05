package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-693 (owner decision 12): the open statistics panel is modal over its footprint. A screen's own
 * control that lies under it neither answers a click nor is drawn over the panel.
 *
 * <p>Each frame drags the panel over a control, clicks the control's centre through
 * {@code Screen#mouseClicked} — the method Minecraft dispatches a real click to — and photographs the
 * result. The Assembler frame also ASSERTS: its Record tab switches the window locally, so a click that
 * leaked through the panel would show as the Record tab being open, and the stand fails on it before the
 * frame is taken. The Mob Repeller's dome toggle is decided by the server, so its frame is the drawing
 * check: the panel covers the eye button and the vessel status line, nothing of either printed on top.
 */
@SuppressWarnings("UnstableApiUsage")
public final class StatsPanelModalStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * Panel offset from its dock. The dock sits {@code PANEL_W (159) + TAB_W (24)} left of the GUI, so
     * this puts the panel's left edge 60 px inside the window and its top edge on the window's top:
     * over the Assembler's Record tab (x 88..168, y 14..30) and the repeller's dome button (x 134..156,
     * y 32..54) and status line (y 60).
     */
    private static final int DRAG_DX = 159 + 24 + 60;
    private static final int DRAG_DY = -MachineMenu.PANEL_Y;

    /** Centre of the Assembler's Record tab and of the repeller's dome button, relative to the window. */
    private static final int RECORD_TAB_X = 88 + 40, RECORD_TAB_Y = 14 + 8;
    private static final int DOME_X = 134 + 11, DOME_Y = 32 + 11;

    private StatsPanelModalStand() {
    }

    public static void shoot(ClientGameTestContext context) {
        int savedDX = AlaClientConfig.statsPanelDX;
        int savedDY = AlaClientConfig.statsPanelDY;
        try {
            shootOne(context, "gui_stats_modal_assembler_tab", ModContent.ASSEMBLER_MENU.get(), "Assembler",
                    12000, RECORD_TAB_X, RECORD_TAB_Y);
            shootOne(context, "gui_stats_modal_mob_repeller_dome", ModContent.MOB_REPELLER_MENU.get(),
                    "Mob Repeller", 4000, DOME_X, DOME_Y);
        } finally {
            AlaClientConfig.statsPanelDX = savedDX;
            AlaClientConfig.statsPanelDY = savedDY;
        }
    }

    private static void shootOne(ClientGameTestContext context, String name, MenuType<?> type,
                                 String displayName, int capacity, int clickX, int clickY) {
        LOG.info("[GUITEST][MOD-693] opening {} (statistics panel dragged over a control, then clicked)", name);
        context.runOnClient(mc -> {
            // Read by the panel controller when the screen lays itself out, so it is set before create.
            AlaClientConfig.statsPanelDX = DRAG_DX;
            AlaClientConfig.statsPanelDY = DRAG_DY;
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(capacity * 3 / 4, capacity, 0, 0);
                if (menu instanceof AssemblerMenu assembler) {
                    assembler.setActiveTab(AssemblerMenu.TAB_WORK);
                }
                menu.toggleStatsPanel();
            }
        });
        awaitMenuScreen(context);
        context.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof MachineScreen<?> screen)) {
                throw new AssertionError("[GUITEST][MOD-693] " + name + ": the machine screen is not open");
            }
            var box = (AbstractContainerScreenAccessor) screen;
            double x = box.alaindustrial$getLeftPos() + clickX;
            double y = box.alaindustrial$getTopPos() + clickY;
            // The rig must really put the panel over the control, or the click below proves nothing.
            boolean covered = false;
            for (Rect2i area : screen.extraGuiAreas()) {
                if (area.getWidth() > 100 && x >= area.getX() && x < area.getX() + area.getWidth()
                        && y >= area.getY() && y < area.getY() + area.getHeight()) {
                    covered = true;
                }
            }
            if (!covered) {
                throw new AssertionError("[GUITEST][MOD-693] " + name + ": the statistics panel does not cover ("
                        + x + ", " + y + "); areas " + screen.extraGuiAreas());
            }
            screen.mouseClicked(new MouseButtonEvent(x, y,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            if (screen.getMenu() instanceof AssemblerMenu assembler
                    && assembler.getActiveTab() != AssemblerMenu.TAB_WORK) {
                throw new AssertionError("[GUITEST][MOD-693] " + name + ": a click on the statistics panel "
                        + "switched the Assembler tab under it (tab " + assembler.getActiveTab() + ")");
            }
        });
        context.waitTicks(1);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-693] screenshot {} -> {}", name, path.toAbsolutePath());
    }
}

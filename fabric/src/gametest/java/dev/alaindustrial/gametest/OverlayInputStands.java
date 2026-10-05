package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.client.screen.SawmillScreen;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
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
 * MOD-716 batch 0 (characterization of CLI-2): what a click on a screen's OWN control does while one of the
 * three {@code MachineScreen} overlays is open.
 *
 * <p>Five screens handle their own clicks — the sawmill's mode buttons, the Assembler's tabs, the mob
 * repeller's dome button, the creative energy source's presets and the reactor controller's tab strip — and
 * each decides by itself whether an open overlay deafens it. They do not agree: the upgrade panel deafens the
 * first four ANYWHERE on the window, but not the reactor's strip; the statistics panel deafens a control only
 * under its footprint, except on the creative source, which defers wholesale. MOD-716 batches 3 and 4 move that
 * decision into the base class, and must reproduce exactly what this stand records.
 *
 * <p>For every subject and every overlay it can open, the stand clicks the control's centre through
 * {@code Screen#mouseClicked} — the method a real click reaches — and records two things: a log line
 * {@code [OVERLAY-INPUT] <frame> consumed=<bool> state=<before> -> <after>} (whether the screen claimed the
 * click, and the client-side state the control changes: the Assembler tab, the reactor page, the overlay
 * flags), and the frame, which shows the drawing order of the control against the overlay. The server-decided
 * controls (sawmill mode, dome, preset) cannot change here — the menu is opened without a block — so for them
 * the {@code consumed} flag and the frame are the record.
 *
 * <p>The statistics panel dragged over the Assembler's Record tab and over the repeller's dome is already
 * photographed by {@link StatsPanelModalStand} (MOD-693); those two situations are reused, not repeated.
 */
@SuppressWarnings("UnstableApiUsage")
public final class OverlayInputStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** The statistics panel's docked left edge relative to the window: {@code PANEL_W 159 + TAB_W 24} left of it. */
    private static final int STATS_DOCK_X = -(159 + 24);

    /** One screen with a control of its own, and the point (window coordinates) that hits the control. */
    private record Subject(String menuId, Supplier<? extends MenuType<?>> type, String title, int capacity,
                           int clickX, int clickY, boolean statsFrameElsewhere) {
    }

    private static final List<Subject> SUBJECTS = List.of(
            // Second mode button (sticks): BUTTON_X0 52 + one BUTTON_SIZE 18, centre +9; BUTTON_Y 48 + 9.
            new Subject("sawmill", ModContent.SAWMILL_MENU, "Sawmill", 4000, 52 + 18 + 9, 48 + 9, false),
            // The Record tab (x 88..168, y 14..30) — its stats-panel case is StatsPanelModalStand's.
            new Subject("assembler", ModContent.ASSEMBLER_MENU, "Assembler", 12000, 88 + 40, 14 + 8, true),
            // The dome button (x 134..156, y 32..54) — its stats-panel case is StatsPanelModalStand's.
            new Subject("mob_repeller", ModContent.MOB_REPELLER_MENU, "Mob Repeller", 4000, 134 + 11, 32 + 11, true),
            // The middle preset (x 62..112, y 44..62).
            new Subject("creative_energy_source", ModContent.CREATIVE_ENERGY_SOURCE_MENU, "Creative Energy Source",
                    4000, 62 + 25, 44 + 9, false),
            // The second tab of the side strip (Room): x = -TAB_W + TAB_OVERLAP .. -TAB_OVERLAP, y 28..56.
            new Subject("reactor_controller", ModContent.REACTOR_CONTROLLER_MENU, "Reactor Controller", 100000,
                    -14, 28 + 14, false));

    private OverlayInputStands() {
    }

    /**
     * Every subject under each overlay it can open.
     *
     * @covers R-GUI-03
     */
    public static void shoot(ClientGameTestContext context) {
        int savedUpgradeDX = AlaClientConfig.upgradePanelDX;
        int savedUpgradeDY = AlaClientConfig.upgradePanelDY;
        int savedStatsDX = AlaClientConfig.statsPanelDX;
        int savedStatsDY = AlaClientConfig.statsPanelDY;
        try {
            for (Subject subject : SUBJECTS) {
                shootOne(context, subject, Overlay.UPGRADES);
                if (!subject.statsFrameElsewhere()) {
                    shootOne(context, subject, Overlay.STATS);
                }
                shootOne(context, subject, Overlay.DRAWER);
            }
            shootSawmillTooltips(context);
        } finally {
            AlaClientConfig.upgradePanelDX = savedUpgradeDX;
            AlaClientConfig.upgradePanelDY = savedUpgradeDY;
            AlaClientConfig.statsPanelDX = savedStatsDX;
            AlaClientConfig.statsPanelDY = savedStatsDY;
        }
    }

    /** The three overlays of {@code MachineScreen}. */
    private enum Overlay {
        /** The upgrade panel, docked where a player first meets it — NOT over the control. */
        UPGRADES("upgrades"),
        /** The statistics panel, dragged so its body covers the control. */
        STATS("stats"),
        /** The battery drawer, opened. */
        DRAWER("drawer");

        private final String tag;

        Overlay(String tag) {
            this.tag = tag;
        }
    }

    private static void shootOne(ClientGameTestContext context, Subject subject, Overlay overlay) {
        String name = "gui_overlay_input_" + subject.menuId() + "_" + overlay.tag;
        LOG.info("[GUITEST][MOD-716] opening {} ({} open, click at {},{})", name, overlay.tag,
                subject.clickX(), subject.clickY());
        boolean[] opened = new boolean[1];
        context.runOnClient(mc -> {
            AlaClientConfig.upgradePanelDX = 0;
            AlaClientConfig.upgradePanelDY = 0;
            // Read by the panel controller when the screen lays itself out, so it is set before create: the
            // panel's left and top edges land 20 px before the click point, so its body covers the control.
            AlaClientConfig.statsPanelDX = overlay == Overlay.STATS ? subject.clickX() - 20 - STATS_DOCK_X : 0;
            AlaClientConfig.statsPanelDY = overlay == Overlay.STATS ? subject.clickY() - 20 - MachineMenu.PANEL_Y : 0;
            MenuScreens.create(subject.type().get(), mc, 0, Component.literal(subject.title()));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(subject.capacity() * 3 / 4, subject.capacity(), 0, 0);
                if (menu instanceof AssemblerMenu assembler) {
                    assembler.setActiveTab(AssemblerMenu.TAB_WORK);
                }
                switch (overlay) {
                    case UPGRADES -> opened[0] = menu.togglePanel() && menu.isPanelOpen();
                    case STATS -> opened[0] = menu.toggleStatsPanel() && menu.isStatsPanelOpen();
                    case DRAWER -> {
                        if (menu.hasBatteryDrawer()) {
                            menu.setBatteryDrawerOpen(true);
                        }
                        opened[0] = menu.isBatteryDrawerOpen();
                    }
                }
            }
        });
        awaitMenuScreen(context);
        if (!opened[0]) {
            // Recorded rather than failed: whether a screen offers the overlay at all is part of what is pinned.
            LOG.info("[OVERLAY-INPUT] {} unavailable: the {} overlay does not open on this screen", name, overlay.tag);
            return;
        }
        context.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof MachineScreen<?> screen)) {
                throw new AssertionError("[GUITEST][MOD-716] " + name + ": the machine screen is not open");
            }
            var box = (AbstractContainerScreenAccessor) screen;
            double x = box.alaindustrial$getLeftPos() + subject.clickX();
            double y = box.alaindustrial$getTopPos() + subject.clickY();
            if (overlay == Overlay.STATS && !covered(screen, x, y)) {
                throw new AssertionError("[GUITEST][MOD-716] " + name + ": the statistics panel does not cover ("
                        + x + ", " + y + "); areas " + screen.extraGuiAreas());
            }
            String before = state(screen);
            boolean consumed = screen.mouseClicked(new MouseButtonEvent(x, y,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            LOG.info("[OVERLAY-INPUT] {} consumed={} state={} -> {}", name, consumed, before, state(screen));
        });
        context.waitTicks(1);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-716] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /** One rig of the sawmill tooltip frames (MOD-738): which overlay is open and where it lies. */
    private enum TooltipRig {
        /** Every overlay closed: the positive control, the button must be lit and the tooltip must show. */
        NONE("none", true),
        /** The upgrade panel open and dragged left so its body covers the button, on an empty spot of the panel. */
        UPGRADES_OVER("upgrades_over", false),
        /** The upgrade panel open where it docks, NOT over the button: the button is deaf all the same. */
        UPGRADES_DOCKED("upgrades_docked", false),
        /** The statistics panel open and dragged over the button. */
        STATS_OVER("stats_over", false);

        private final String tag;
        private final boolean tooltipExpected;

        TooltipRig(String tag, boolean tooltipExpected) {
            this.tag = tag;
            this.tooltipExpected = tooltipExpected;
        }
    }

    /**
     * Upgrade-panel offset that puts its body at x 50..208 of the screen frame (from {@code leftPos}, as
     * {@link Subject#clickX} is) — over the mode buttons.
     */
    private static final int UPGRADES_OVER_DX = -122;

    /**
     * MOD-738: the sawmill's mode tooltip and hover tint obey the rule its click obeys, {@code frameAcceptsInput}.
     * For each rig the stand asks {@link SawmillScreen#litModeAt} (the button the tint lights) and
     * {@link SawmillScreen#modeTooltipAt} at the centre of the second mode button, asserts both answers, logs
     * {@code [OVERLAY-TOOLTIP] <frame> lit=<mode|none> tooltip=<text|none>} and photographs the frame with the
     * cursor there.
     * Called inside {@link #shoot}'s {@code try}, whose {@code finally} restores the shared panel offsets; the
     * cursor is restored here.
     *
     * @covers R-GUI-03
     */
    private static void shootSawmillTooltips(ClientGameTestContext context) {
        Subject sawmill = SUBJECTS.get(0);
        double[] saved = new double[2];
        context.runOnClient(mc -> {
            saved[0] = mc.mouseHandler.xpos();
            saved[1] = mc.mouseHandler.ypos();
        });
        try {
            for (TooltipRig rig : TooltipRig.values()) {
                shootSawmillTooltip(context, sawmill, rig);
            }
        } finally {
            context.getInput().setCursorPos(saved[0], saved[1]);
        }
    }

    private static void shootSawmillTooltip(ClientGameTestContext context, Subject sawmill, TooltipRig rig) {
        String name = "gui_overlay_input_sawmill_tooltip_" + rig.tag;
        LOG.info("[GUITEST][MOD-738] opening {}", name);
        context.runOnClient(mc -> {
            AlaClientConfig.upgradePanelDX = rig == TooltipRig.UPGRADES_OVER ? UPGRADES_OVER_DX : 0;
            AlaClientConfig.upgradePanelDY = 0;
            // The same drag as shootOne's STATS frame: the statistics panel's corner 20 px before the button.
            boolean stats = rig == TooltipRig.STATS_OVER;
            AlaClientConfig.statsPanelDX = stats ? sawmill.clickX() - 20 - STATS_DOCK_X : 0;
            AlaClientConfig.statsPanelDY = stats ? sawmill.clickY() - 20 - MachineMenu.PANEL_Y : 0;
            MenuScreens.create(sawmill.type().get(), mc, 0, Component.literal(sawmill.title()));
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu)) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": the sawmill screen did not open");
            }
            menu.injectTestData(sawmill.capacity() * 3 / 4, sawmill.capacity(), 0, 0);
            switch (rig) {
                case NONE -> { }
                case UPGRADES_OVER, UPGRADES_DOCKED -> menu.togglePanel();
                case STATS_OVER -> menu.toggleStatsPanel();
            }
        });
        awaitMenuScreen(context);
        double[] cursor = new double[2];
        context.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof SawmillScreen screen)) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": the sawmill screen is not open");
            }
            var box = (AbstractContainerScreenAccessor) screen;
            double x = box.alaindustrial$getLeftPos() + sawmill.clickX();
            double y = box.alaindustrial$getTopPos() + sawmill.clickY();
            MachineMenu menu = screen.getMenu();
            boolean upgrades = rig == TooltipRig.UPGRADES_OVER || rig == TooltipRig.UPGRADES_DOCKED;
            if (menu.isPanelOpen() != upgrades || menu.isStatsPanelOpen() != (rig == TooltipRig.STATS_OVER)) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": wrong overlays open: " + state(screen));
            }
            // A rig that does not put the panel where it claims proves nothing: check the footprint first.
            boolean over = rig == TooltipRig.UPGRADES_OVER || rig == TooltipRig.STATS_OVER;
            if (covered(screen, x, y) != over) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": expected the point (" + x + ", " + y + ") "
                        + (over ? "under" : "clear of") + " an open panel; areas " + screen.extraGuiAreas());
            }
            SawmillMode lit = screen.litModeAt(x, y);
            Component tip = screen.modeTooltipAt(x, y);
            LOG.info("[OVERLAY-TOOLTIP] {} lit={} tooltip={}", name, lit == null ? "none" : lit,
                    tip == null ? "none" : tip.getString());
            // The point is the centre of the second button: the tint must light exactly it, or nothing.
            SawmillMode litExpected = rig.tooltipExpected ? SawmillMode.values()[1] : null;
            if (lit != litExpected) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": hover tint lights " + lit
                        + ", expected " + litExpected + " at (" + x + ", " + y + "); " + state(screen));
            }
            if ((tip != null) != rig.tooltipExpected) {
                throw new AssertionError("[GUITEST][MOD-738] " + name + ": mode tooltip "
                        + (rig.tooltipExpected ? "missing" : "shown (" + tip.getString() + ")")
                        + " at (" + x + ", " + y + "); " + state(screen));
            }
            double scale = mc.getWindow().getGuiScale();
            cursor[0] = x * scale;
            cursor[1] = y * scale;
        });
        // The cursor is for the eye only: the frame shows whether a tooltip floats over the panel. Pixels are not
        // compared — a local frame is noisy with the OS cursor.
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(1);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-738] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /** Whether a wide exclusion area (an open panel, not a tab) contains the point. */
    private static boolean covered(MachineScreen<?> screen, double x, double y) {
        for (Rect2i area : screen.extraGuiAreas()) {
            if (area.getWidth() > 100 && x >= area.getX() && x < area.getX() + area.getWidth()
                    && y >= area.getY() && y < area.getY() + area.getHeight()) {
                return true;
            }
        }
        return false;
    }

    /** The client-side state a click on these controls can change, plus the three overlay flags. */
    private static String state(MachineScreen<?> screen) {
        StringBuilder out = new StringBuilder();
        if (screen.getMenu() instanceof MachineMenu menu) {
            out.append("upgrades=").append(menu.isPanelOpen())
                    .append(",stats=").append(menu.isStatsPanelOpen())
                    .append(",drawer=").append(menu.isBatteryDrawerOpen());
            if (menu instanceof AssemblerMenu assembler) {
                out.append(",tab=").append(assembler.getActiveTab());
            }
        }
        if (screen instanceof ReactorControllerScreen reactor) {
            out.append(",page=").append(reactor.selectedPage());
        }
        return out.toString();
    }
}

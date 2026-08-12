package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R-GUI-01 / R-GUI-03 — the machine screens, opened through {@code MenuScreens.create} and fed the
 * exact {@code ContainerData} the state under test needs.
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. {@link #shootGuiScreenshots} is the catalogue;
 * everything below it is the shooter each entry uses. Three subjects are big enough to own a file of
 * their own and are called from here rather than inlined: the water mill's status row
 * ({@link WaterMillGuiStand}), the Assembler window ({@link AssemblerGuiStands}) and the blueprint item
 * icon ({@link BlueprintIconStands}).
 *
 * <p>Note what this file does NOT prove: opening a screen this way skips the block interaction, the
 * server's menu-open packet and the real synced data. That path is {@code ScreensClientGameTest}'s.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MachineGuiStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private MachineGuiStands() {
    }

    /**
     * R-GUI-01: Every machine GUI opens without crash; custom screens (Generator, Geothermal,
     * Macerator) are captured in three states — empty, mid-fill, and full — to verify bar/flame
     * positioning at all fill levels.
     *
     * <p>Capacity=4000 EU, maxProgress=200 ticks mirrors the default balance config values.
     *
     * <p>When adding a new machine: add one {@code shootMenu} line here. That's all.
     *
     * @implements R-GUI-03 - the energy and progress bars match the values behind them
     * @covers R-GUI-01, R-GUI-03
     */
    public static void shootGuiScreenshots(ClientGameTestContext context) {
        final int CAP  = 4000;
        final int BURN = 200;   // maxProgress for fuel-burning machines
        final int TANK = 10000; // maxProgress for geothermal (lavaTicks tank capacity)

        // ── Solar Panel — sun modes + energy fill levels (R-GUI-01, R-GUI-03) ──────
        // State 1: night, empty battery — no sun dot, no energy fill, no evo bar
        shootSolarPanel(context, "gui_solar_panel_night_empty",  0,    8000, 0, 0, 0, 33600);
        // State 2: day direct sun, empty battery — sun dot ON, energy bar empty
        shootSolarPanel(context, "gui_solar_panel_day_empty",    0,    8000, 1, 1, 0, 33600);
        // State 3: day direct sun, 25 % battery
        shootSolarPanel(context, "gui_solar_panel_day_25pct",    2000, 8000, 1, 1, 0, 33600);
        // State 4: day direct sun, 50 % battery
        shootSolarPanel(context, "gui_solar_panel_day_half",     4000, 8000, 1, 1, 0, 33600);
        // State 5: day direct sun, 75 % battery
        shootSolarPanel(context, "gui_solar_panel_day_75pct",    6000, 8000, 1, 1, 0, 33600);
        // State 6: rainy day, full battery — sun dot OFF, 100 % energy
        shootSolarPanel(context, "gui_solar_panel_rain_full",    8000, 8000, 0, 2, 0, 33600);
        // State 7: partial shade, quarter battery — sun dot ON, 25 % energy
        shootSolarPanel(context, "gui_solar_panel_partial",      2000, 8000, 1, 3, 0, 33600);

        // ── Solar Panel — day chip (yellow evo bar) ───────────────────────────────
        // State 8: day, day chip at 50 % evolution — yellow bar half-filled
        shootSolarPanel(context, "gui_solar_panel_evo_day_50pct",  4000, 8000, 1, 1, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);
        // State 9: day, day chip at 100 % evolution — yellow bar full
        shootSolarPanel(context, "gui_solar_panel_evo_day_full",   8000, 8000, 1, 1, 33600, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);

        // ── Solar Panel — night chip (blue evo bar) ───────────────────────────────
        // State 10: night, night chip at 50 % evolution — blue bar half-filled
        shootSolarPanel(context, "gui_solar_panel_evo_night_50pct", 2000, 8000, 0, 0, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 11: night, night chip at 75 % evolution
        shootSolarPanel(context, "gui_solar_panel_evo_night_75pct", 2000, 8000, 0, 0, 25200, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 12: night, night chip evolution complete — full blue bar
        shootSolarPanel(context, "gui_solar_panel_evo_night_full",  2000, 8000, 0, 0, 33600, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 13: day with night chip mid-evolution — sun active + blue bar (cross-mode check)
        shootSolarPanel(context, "gui_solar_panel_evo_night_day",   6000, 8000, 1, 1, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 14: evolution just started (1 tick) — bar must show a minimum 1px so the player gets
        // immediate feedback instead of a blank track for the first ~22 s (proportional floor = 0px).
        shootSolarPanel(context, "gui_solar_panel_evo_day_start",   4000, 8000, 1, 1, 1, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);
        // State 15: same first-tick minimum for the night branch (blue bar, 1px).
        shootSolarPanel(context, "gui_solar_panel_evo_night_start", 2000, 8000, 0, 0, 1, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);

        // ── Water Mill — the status row in every state it can show (MOD-354) ─────────
        WaterMillGuiStand.checkWaterMillStatusRow(context);

        // ── Machines without custom screens (one shot each) ──────────────────────────
        shootMenu(context, "gui_moonlit_solar_panel", ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), "Moonlit Solar Panel");

        // ── MOD-080: upgrade panel open — gear tab + cross panel + mute chip in the active slot ──
        shootMenuWithPanelOpen(context, "gui_macerator_upgrades_open", ModContent.MACERATOR_MENU.get(), "Macerator", CAP, CAP, 0, 0);
        // Dragged over the GUI: proves the panel is a top overlay (GUI slots/text do not bleed over it).
        shootMenuWithPanelOpen(context, "gui_macerator_upgrades_dragged", ModContent.MACERATOR_MENU.get(), "Macerator", CAP, CAP, -120, 60);

        // ── Electric Furnace — three states ──────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_electric_furnace_empty",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                0, CAP, 0, BURN);

        // State 2: smelting — 50 % through, energy at 75 %
        shootMenuWithState(context, "gui_electric_furnace_smelting",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: full — full energy, arrow at max
        shootMenuWithState(context, "gui_electric_furnace_full",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                CAP, CAP, BURN, BURN);

        // ── Sawmill (MOD-215) — three states ─────────────────────────────────────────
        // The machine has its own atlas, its own saw-blade progress sprite and a row of mode buttons
        // under the slots, so it is the one GUI in the mod where the layout itself is the feature —
        // these shots are the regression guard for it (the buttons are drawn in code, not the atlas).
        final int SAW = 80;   // Config.sawmillDuration — one cut at 1.0 speed
        shootMenuWithState(context, "gui_sawmill_empty",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                0, CAP, 0, SAW);
        shootMenuWithState(context, "gui_sawmill_sawing",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                CAP * 3 / 4, CAP, SAW / 2, SAW);
        shootMenuWithState(context, "gui_sawmill_full",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                CAP, CAP, SAW, SAW);

        // ── Assembler (MOD-275) — three states ───────────────────────────────────────
        // The one screen in the mod with ghost cells: nine slots that show items nobody owns, plus a
        // result preview, a Write button drawn in code and a status line. None of that is in the
        // atlas, so these shots are the only visual guard on it.
        final int ASM = 40;   // Config.assemblerDuration — one operation at 1.0 speed
        AssemblerGuiStands.shootAssembler(context, "gui_assembler_empty", 0, CAP, 0, ASM, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_BLUEPRINT, false);
        AssemblerGuiStands.shootAssembler(context, "gui_assembler_working", CAP * 3 / 4, CAP, ASM / 2, ASM, 0,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.READY, true);
        AssemblerGuiStands.shootAssembler(context, "gui_assembler_output_full", CAP, CAP, 0, ASM, 2,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.OUTPUT_FULL, true);
        // Two more states, and they are the reason this stand exists after MOD-275's playtest: a queue
        // of recorded blueprints has to be readable WITHOUT touching anything. State 4 is the machine
        // idle — no layout of the player's own, so the window shows the queued blueprint's, dimmed and
        // blue-framed, with the blue ring naming which slot it came from. State 5 is the same window
        // while the machine works: the ring on the working slot turns MV orange. The blueprints in the
        // queue carry their own products in their icons (sticks vs planks — two different products in
        // one frame, so the icon cannot be a fixed picture).
        AssemblerGuiStands.shootAssemblerQueue(context, "gui_assembler_blueprint_preview", CAP, CAP, 0, ASM, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, true);
        AssemblerGuiStands.shootAssemblerQueue(context, "gui_assembler_blueprint_active", CAP * 3 / 4, CAP, ASM / 2, ASM, 1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.READY, true);
        AssemblerGuiStands.assertBlueprintPreviewInFrame(context, ASM);
        // MOD-275, the tab split: one window, two tabs, and the two jobs must not share screen space.
        // Both tabs are photographed, and the gate proves the hidden one's contents are GONE rather
        // than merely covered — in both directions.
        AssemblerGuiStands.assertHiddenTabIsGone(context);
        // The icon itself (the player's actual complaint): a recorded blueprint has to be readable
        // wherever the item is, not only inside this window. Shot in plain player-inventory slots,
        // which no mod code draws over, so what these frames show is the item model and nothing else.
        BlueprintIconStands.assertBlueprintIconShowsProduct(context);

        // ── Incubator (MOD-118) — three states ───────────────────────────────────────
        // Its screen carries two things no other machine draws: the charge pips under the fuel slot
        // and a status line that explains why nothing is happening. The energy bar sits on the right
        // here — the chip slot occupies the top-left corner the default bar would paint over.
        final int MUT = 300;  // Config.mutationDurationTransform — one transform at 1.0 speed
        shootIncubator(context, "gui_incubator_no_dome", 0, CAP, 0, MUT, -1, 0, 0);
        shootIncubator(context, "gui_incubator_no_chip", CAP, CAP, 0, MUT, -1, 0, 1);
        shootIncubator(context, "gui_incubator_running", CAP * 3 / 4, CAP, MUT / 2, MUT, 0, 2, 1);

        // ── BatteryBox — three states ─────────────────────────────────────────────────────
        shootMenuWithState(context, "gui_battery_box_empty",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                0, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_half",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                CAP / 2, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_full",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                CAP, CAP, 0, 0);
        // MOD-052: fourth state — a half-charged Battery Pouch sitting in the new charge slot, so the
        // reviewer sees the slot niche, the pouch icon and its EU item bar together.
        shootBatteryBoxWithPouch(context, "gui_battery_box_pouch", CAP / 2, CAP);
        shootBatteryBoxWithPack(context, "gui_battery_box_energy_pack", CAP / 2, CAP);

        // ── LV Generator — three states ──────────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_generator_empty",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                0, CAP, 0, BURN);

        // State 2: burning — 50 % fuel remaining, energy building to 50 %
        shootMenuWithState(context, "gui_generator_burning",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                CAP / 2, CAP, BURN / 2, BURN);

        // State 3: full energy — buffer saturated, no active burn
        shootMenuWithState(context, "gui_generator_full",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                CAP, CAP, 0, BURN);

        // ── Geothermal Generator — three states ──────────────────────────────────────
        // State 1: empty — no lava in tank, no energy
        shootMenuWithState(context, "gui_geothermal_empty",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                0, CAP, 0, TANK);

        // State 2: lava tank ~70 %, energy building (~40 %)
        shootMenuWithState(context, "gui_geothermal_mid",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                CAP * 2 / 5, CAP, TANK * 7 / 10, TANK);

        // State 3: full lava tank, full energy buffer
        shootMenuWithState(context, "gui_geothermal_full",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                CAP, CAP, TANK, TANK);

        // ── Macerator — three states ─────────────────────────────────────────────────
        // State 1: empty — no input, no energy
        shootMenuWithState(context, "gui_macerator_empty",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                0, CAP, 0, BURN);

        // State 2: processing — 60 % through, moderate energy
        shootMenuWithState(context, "gui_macerator_processing",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, arrow at max
        shootMenuWithState(context, "gui_macerator_full",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                CAP, CAP, BURN, BURN);

        // ── Extractor — three states ─────────────────────────────────────────────
        // State 1: empty — no input, no energy, chevrons dark
        shootMenuWithState(context, "gui_extractor_empty",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                0, CAP, 0, BURN);

        // State 2: extracting — 60 % through, energy at 75 % (cyan chevrons mid-fill)
        shootMenuWithState(context, "gui_extractor_processing",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, chevrons fully lit
        shootMenuWithState(context, "gui_extractor_full",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                CAP, CAP, BURN, BURN);

        // ── Compressor — three states ────────────────────────────────────────────
        // State 1: idle — no energy, no active compression
        shootMenuWithState(context, "gui_compressor_idle",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                0, CAP, 0, BURN);

        // State 2: compressing — 50 % through, energy at 75 % (arrows mid-way toward center)
        shootMenuWithState(context, "gui_compressor_mid",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: done — full energy, arrows fully converged at center
        shootMenuWithState(context, "gui_compressor_done",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                CAP, CAP, BURN, BURN);
    }

    /** BatteryBox screen with a half-charged Battery Pouch injected into the charge slot (MOD-052). */
    private static void shootBatteryBoxWithPouch(ClientGameTestContext context, String name,
                                                 int energy, int capacity) {
        LOG.info("[GUITEST] opening {} (pouch in charge slot)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.BATTERY_BOX_MENU.get(), mc, 0, Component.literal("BatteryBox"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                ItemStack pouch = new ItemStack(ModItems.BATTERY_POUCH);
                dev.alaindustrial.item.energy.ItemEnergy.set(pouch, dev.alaindustrial.Config.lvPouchBuffer / 2);
                menu.getSlot(0).container.setItem(0, pouch);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * BatteryBox screen with a half-charged Energy Pack in the charge slot (MOD-065). Proves in one
     * frame that the slot accepts the pack at all (the filter is "any item with an EU buffer", not
     * "pouch only") and that the pack's icon + charge bar render in a GUI slot.
     */
    private static void shootBatteryBoxWithPack(ClientGameTestContext context, String name,
                                                int energy, int capacity) {
        LOG.info("[GUITEST] opening {} (energy pack in charge slot)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.BATTERY_BOX_MENU.get(), mc, 0, Component.literal("BatteryBox"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                ItemStack pack = new ItemStack(ModItems.ENERGY_PACK);
                dev.alaindustrial.item.energy.ItemEnergy.set(pack, dev.alaindustrial.Config.energyPackBuffer / 2);
                menu.getSlot(0).container.setItem(0, pack);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Opens the screen, then immediately injects ContainerData so the GUI renders the requested
     * state (energy fill, flame height, arrow width). Works only for screens backed by
     * {@link MachineMenu}; for other types falls back to the plain empty shot.
     */
    private static void shootMenuWithState(ClientGameTestContext context, String name,
                                           MenuType<?> type, String displayName,
                                           int energy, int capacity, int progress, int maxProgress) {
        LOG.info("[GUITEST] opening {} (E={}/{} P={}/{})", name, energy, capacity, progress, maxProgress);
        context.runOnClient(mc -> {
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            // In MC 26.2 the active screen lives in mc.gui.screen(), not mc.screen
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Incubator variant of {@link #shootMenuWithState} (MOD-118): also fills the three channels the
     * machine adds — the mode ordinal ({@code -1} = no chip inserted), the irradiation charge left on
     * the loaded ingot, and whether the dome is in place. Those three drive the charge pips and the
     * status line, which is the whole point of photographing this screen.
     */
    private static void shootIncubator(ClientGameTestContext context, String name,
                                       int energy, int capacity, int progress, int maxProgress,
                                       int mode, int charge, int formed) {
        LOG.info("[GUITEST] opening {} (E={}/{} P={}/{} mode={} charge={} formed={})",
                name, energy, capacity, progress, maxProgress, mode, charge, formed);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.INCUBATOR_MENU.get(), mc, 0, Component.literal("Incubator"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, mode);
                menu.injectTestChannel(5, charge);
                menu.injectTestChannel(6, formed);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Opens the screen bound to {@code type} in {@link MenuScreens} (same code path as the real
     * game) and takes a screenshot. If no screen is registered for this type, {@link
     * MenuScreens#create} does nothing and the screenshot shows an empty world — a clear visual
     * signal that the binding in {@link dev.alaindustrial.IndustrializationClient} is missing.
     */
    private static void shootMenu(ClientGameTestContext context, String name,
                                  MenuType<?> type, String displayName) {
        LOG.info("[GUITEST] opening {}", name);
        context.runOnClient(mc -> MenuScreens.create(type, mc, 0, Component.literal(displayName)));
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * MOD-080: a machine screen with the upgrade panel expanded (gear tab clicked) and a mute chip in
     * the active slot. Confirms in one frame that the gear tab draws, the cross panel blits beside the
     * GUI, the locked slots read as dimmed, and a chip renders in the active slot.
     */
    private static void shootMenuWithPanelOpen(ClientGameTestContext context, String name,
                                               MenuType<?> type, String displayName, int energy, int capacity,
                                               int dragDX, int dragDY) {
        LOG.info("[GUITEST][MOD-080] opening {} (upgrade panel open, drag {},{})", name, dragDX, dragDY);
        context.runOnClient(mc -> {
            // The screen reads the docked/dragged offset from AlaClientConfig in init(); set it before
            // opening, then reset so it does not leak into later shots.
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDX = dragDX;
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDY = dragDY;
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDX = 0;
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDY = 0;
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                menu.togglePanel();
                for (net.minecraft.world.inventory.Slot s : menu.slots) {
                    if (s instanceof MachineMenu.UpgradeSlot) {
                        s.set(new ItemStack(dev.alaindustrial.registry.ModContent.MUTE_CHIP.get()));
                        break;
                    }
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-080] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Opens the Solar Panel screen and injects all six ContainerData channels so the screenshot
     * shows the requested visual state. Covers: energy bar fill, mode square colour (yellow/blue),
     * sun-active dot, and evolution bar.
     *
     * @param energy         stored EU (0..capacity)
     * @param capacity       max EU buffer (use 8000 for LV solar)
     * @param production     EU/t being produced (shown in the production-rate channel)
     * @param mode           sky mode: 0=night, 1=day, 2=weather, 3=partial
     * @param evolveProgress chip ticks accumulated (0..evolveMax)
     * @param evolveMax      chip ticks needed to evolve (Config.solarEvolveTicks, 33600 default)
     */
    private static void shootSolarPanel(ClientGameTestContext context, String name,
                                        int energy, int capacity, int production, int mode,
                                        int evolveProgress, int evolveMax) {
        shootSolarPanel(context, name, energy, capacity, production, mode, evolveProgress, evolveMax, null);
    }

    /**
     * Variant of {@link #shootSolarPanel} that also places {@code chipItem} into the evolution-chip
     * slot (slot 0). Pass {@link ModItems#ALIGNMENT_CHIP_DAY} for a yellow evo bar or
     * {@link ModItems#ALIGNMENT_CHIP_NIGHT} for a blue evo bar. Pass {@code null} to leave the slot
     * empty (same as the no-chip overload).
     *
     * <p>The chip is injected directly into the client-side {@code SimpleContainer} that backs the
     * slot — no server round-trip needed; the rendering reads the slot item synchronously.
     */
    private static void shootSolarPanel(ClientGameTestContext context, String name,
                                        int energy, int capacity, int production, int mode,
                                        int evolveProgress, int evolveMax, Item chipItem) {
        LOG.info("[GUITEST] solar_panel {} (E={}/{} prod={} mode={} evo={}/{} chip={})",
                name, energy, capacity, production, mode, evolveProgress, evolveMax,
                chipItem != null ? chipItem.getDescriptionId() : "none");
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.SOLAR_PANEL_MENU.get(), mc, 0, Component.literal("Solar Panel"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof SolarPanelMenu menu) {
                menu.injectSolarTestData(energy, capacity, production, mode, evolveProgress, evolveMax);
                if (chipItem != null) {
                    // Directly mutate the client-side SimpleContainer that backs slot 0.
                    menu.getSlot(0).container.setItem(0, new ItemStack(chipItem));
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }
}

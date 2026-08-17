package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.visual.ShotGroup;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R-GUI-15 — every menu photographed once in Arabic ({@code ar_sa}), the mod's only RTL locale.
 *
 * <p>MOD-053. The engine shapes and reorders Arabic text itself (all our draws go through
 * {@code Component} → {@code getVisualOrderText()} → {@link net.minecraft.client.resources.language.FormattedBidiReorder}),
 * so what this pass audits is NOT glyph rendering — it is our fixed coordinates: a label anchored
 * for English can overlap a value or clip a title when the string behind it is Arabic. One frame
 * per menu, every menu, in the language that changes every string's width and direction at once.
 *
 * <p>The language is switched the way {@code LanguageSelectScreen} does it —
 * {@code LanguageManager.setSelected} + {@code options.languageCode} + {@code reloadResourcePacks()}
 * (all three symbols verified against the 26.2 jar) — and the wait is on OBSERVED EFFECT, not on the
 * future alone: the probe key must resolve with Arabic letters before any frame is taken, which also
 * proves the mod's own {@code ar_sa.json} loaded rather than just vanilla's.
 *
 * <p>Names are {@code gui_<menu_id>_rtl}: unique across the run, and they count as evidence for the
 * menu in {@code menu_shot_parity_check} (the {@code _rtl} suffix survives its prefix match). The
 * frame passes its group, rule and caption explicitly, so no {@code ShotFamilies} prefix is needed.
 * The double chest is the one menu left out: its combined view needs a real two-block server state
 * that {@code MenuScreens.create} cannot fake, and its title string shares the pattern the four
 * single chests already cover.
 *
 * <p>English is restored afterwards: Fabric runs every client-gametest class in this process, and a
 * lane that leaves {@code ar_sa} on would quietly shoot every later frame in Arabic too.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RtlGuiStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Mid-fill state: energy at 75 %, progress at 50 % — bars, EU numbers and status text all draw. */
    private static final int ENERGY = 3000;
    private static final int CAPACITY = 4000;
    private static final int PROGRESS = 100;
    private static final int MAX_PROGRESS = 200;

    private static final String RTL_CHECKS =
            "RTL (ar_sa): Arabic labels are shaped and right-ordered by the engine; check that no "
                    + "label overlaps a slot or a value and no title clips the panel";

    /** One menu per entry: registry id (frame-name stem), the title's lang key, the menu type. */
    private record MenuEntry(String menuId, String titleKey, Supplier<? extends MenuType<?>> type) {
    }

    private static final List<MenuEntry> CATALOG = List.of(
            new MenuEntry("generator", "block.alaindustrial.generator", dev.alaindustrial.registry.ModContent.GENERATOR_MENU),
            new MenuEntry("macerator", "block.alaindustrial.macerator", dev.alaindustrial.registry.ModContent.MACERATOR_MENU),
            new MenuEntry("solar_panel", "block.alaindustrial.solar_panel", dev.alaindustrial.registry.ModContent.SOLAR_PANEL_MENU),
            new MenuEntry("moonlit_solar_panel", "block.alaindustrial.moonlit_solar_panel", dev.alaindustrial.registry.ModContent.MOONLIT_SOLAR_PANEL_MENU),
            new MenuEntry("daylight_solar_panel", "block.alaindustrial.daylight_solar_panel", dev.alaindustrial.registry.ModContent.DAYLIGHT_SOLAR_PANEL_MENU),
            new MenuEntry("electric_furnace", "block.alaindustrial.electric_furnace", dev.alaindustrial.registry.ModContent.ELECTRIC_FURNACE_MENU),
            new MenuEntry("extractor", "block.alaindustrial.extractor", dev.alaindustrial.registry.ModContent.EXTRACTOR_MENU),
            new MenuEntry("compressor", "block.alaindustrial.compressor", dev.alaindustrial.registry.ModContent.COMPRESSOR_MENU),
            new MenuEntry("component_repair_bench", "block.alaindustrial.component_repair_bench", dev.alaindustrial.registry.ModContent.COMPONENT_REPAIR_BENCH_MENU),
            new MenuEntry("canning_machine", "block.alaindustrial.canning_machine", dev.alaindustrial.registry.ModContent.CANNING_MACHINE_MENU),
            new MenuEntry("sawmill", "block.alaindustrial.sawmill", dev.alaindustrial.registry.ModContent.SAWMILL_MENU),
            new MenuEntry("assembler", "block.alaindustrial.assembler", dev.alaindustrial.registry.ModContent.ASSEMBLER_MENU),
            new MenuEntry("polymerizer", "block.alaindustrial.polymerizer", dev.alaindustrial.registry.ModContent.POLYMERIZER_MENU),
            new MenuEntry("distillation_column", "block.alaindustrial.distillation_column", dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN_MENU),
            new MenuEntry("vulcanizer", "block.alaindustrial.vulcanizer", dev.alaindustrial.registry.ModContent.VULCANIZER_MENU),
            new MenuEntry("electric_heater", "block.alaindustrial.electric_heater", dev.alaindustrial.registry.ModContent.ELECTRIC_HEATER_MENU),
            new MenuEntry("alloy_smelter", "block.alaindustrial.alloy_smelter", dev.alaindustrial.registry.ModContent.ALLOY_SMELTER_MENU),
            new MenuEntry("galvanic_bath", "block.alaindustrial.galvanic_bath", dev.alaindustrial.registry.ModContent.GALVANIC_BATH_MENU),
            new MenuEntry("thermal_centrifuge", "block.alaindustrial.thermal_centrifuge", dev.alaindustrial.registry.ModContent.THERMAL_CENTRIFUGE_MENU),
            new MenuEntry("incubator", "block.alaindustrial.incubator", dev.alaindustrial.registry.ModContent.INCUBATOR_MENU),
            new MenuEntry("battery_box", "block.alaindustrial.battery_box", dev.alaindustrial.registry.ModContent.BATTERY_BOX_MENU),
            new MenuEntry("energy_condenser", "block.alaindustrial.energy_condenser", dev.alaindustrial.registry.ModContent.ENERGY_CONDENSER_MENU),
            new MenuEntry("mob_repeller", "block.alaindustrial.mob_repeller", dev.alaindustrial.registry.ModContent.MOB_REPELLER_MENU),
            new MenuEntry("mob_repeller_mv", "block.alaindustrial.mob_repeller_mv", dev.alaindustrial.registry.ModContent.MOB_REPELLER_MV_MENU),
            new MenuEntry("mob_repeller_hv", "block.alaindustrial.mob_repeller_hv", dev.alaindustrial.registry.ModContent.MOB_REPELLER_HV_MENU),
            new MenuEntry("cesu", "block.alaindustrial.cesu", dev.alaindustrial.registry.ModContent.CESU_MENU),
            new MenuEntry("charge_pad", "block.alaindustrial.charge_pad", dev.alaindustrial.registry.ModContent.CHARGE_PAD_MENU),
            new MenuEntry("teleporter_station", "block.alaindustrial.teleporter", dev.alaindustrial.registry.ModContent.TELEPORTER_STATION_MENU),
            new MenuEntry("teleporter_remote", "item.alaindustrial.teleporter_remote", dev.alaindustrial.registry.ModContent.TELEPORTER_REMOTE_MENU),
            new MenuEntry("geothermal_generator", "block.alaindustrial.geothermal_generator", dev.alaindustrial.registry.ModContent.GEOTHERMAL_GENERATOR_MENU),
            new MenuEntry("pump", "block.alaindustrial.pump", dev.alaindustrial.registry.ModContent.PUMP_MENU),
            new MenuEntry("garden_drone_station", "block.alaindustrial.garden_drone_station", dev.alaindustrial.registry.ModContent.GARDEN_DRONE_STATION_MENU),
            new MenuEntry("water_mill", "block.alaindustrial.water_mill", dev.alaindustrial.registry.ModContent.WATER_MILL_MENU),
            new MenuEntry("wind_mill", "block.alaindustrial.wind_mill", dev.alaindustrial.registry.ModContent.WIND_MILL_MENU),
            new MenuEntry("high_altitude_wind_mill", "block.alaindustrial.high_altitude_wind_mill", dev.alaindustrial.registry.ModContent.HIGH_ALTITUDE_WIND_MILL_MENU),
            new MenuEntry("storm_wind_mill", "block.alaindustrial.storm_wind_mill", dev.alaindustrial.registry.ModContent.STORM_WIND_MILL_MENU),
            new MenuEntry("iron_chest", "block.alaindustrial.iron_chest", dev.alaindustrial.registry.ModContent.IRON_CHEST_MENU),
            new MenuEntry("silver_chest", "block.alaindustrial.silver_chest", dev.alaindustrial.registry.ModContent.SILVER_CHEST_MENU),
            new MenuEntry("gold_chest", "block.alaindustrial.gold_chest", dev.alaindustrial.registry.ModContent.GOLD_CHEST_MENU),
            new MenuEntry("electrum_chest", "block.alaindustrial.electrum_chest", dev.alaindustrial.registry.ModContent.ELECTRUM_CHEST_MENU),
            new MenuEntry("storage_module_3", "block.alaindustrial.storage_module", dev.alaindustrial.registry.ModContent.STORAGE_MODULE_MENU_3),
            new MenuEntry("storage_module_6", "block.alaindustrial.storage_module", dev.alaindustrial.registry.ModContent.STORAGE_MODULE_MENU_6));

    private RtlGuiStands() {
    }

    /**
     * Switches the client to Arabic, photographs the whole menu catalogue, switches back to English.
     *
     * @covers R-GUI-15
     */
    public static void shootArabicScreens(ClientGameTestContext context) {
        switchLanguage(context, "ar_sa");
        try {
            ShotRecorder.locale("ar_sa");
            for (MenuEntry entry : CATALOG) {
                shootRtlMenu(context, entry);
            }
        } finally {
            ShotRecorder.locale("en_us");
            switchLanguage(context, "en_us");
        }
    }

    private static void shootRtlMenu(ClientGameTestContext context, MenuEntry entry) {
        String name = "gui_" + entry.menuId() + "_rtl";
        LOG.info("[GUITEST] opening {} (ar_sa RTL audit)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(entry.type().get(), mc, 0, Component.translatable(entry.titleKey()));
            // In MC 26.2 the active screen lives in mc.gui.screen(), not mc.screen
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof dev.alaindustrial.menu.MachineMenu menu) {
                menu.injectTestData(ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS);
            }
        });
        awaitMenuScreen(context);
        ShotRecorder.capture(name, ShotGroup.GUI, entry.menuId(), List.of("R-GUI-15"), RTL_CHECKS);
    }

    /**
     * Applies a language the way the language screen does, then waits for its EFFECT — the probe
     * key must resolve in the expected script — bounded, because a reload that never lands would
     * otherwise hang the whole lane on an unbounded {@code waitFor}.
     */
    private static void switchLanguage(ClientGameTestContext context, String code) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(mc -> {
            mc.getLanguageManager().setSelected(code);
            mc.options.languageCode = code;
            reload.set(mc.reloadResourcePacks());
        });
        boolean wantArabic = "ar_sa".equals(code);
        for (int i = 0; i < 100; i++) {   // 100 × 5 ticks ≈ 25 s cap on a stuck reload
            CompletableFuture<Void> future = reload.get();
            String probe = context.computeOnClient(
                    mc -> Component.translatable("gui.alaindustrial.stats.title").getString());
            boolean arabic = probe.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
            if (future != null && future.isDone() && arabic == wantArabic) {
                context.waitTicks(2);   // let the reload overlay clear before the next frame
                return;
            }
            context.waitTicks(5);
        }
        throw new AssertionError("[RTL] language switch to '" + code + "' did not land within 25 s — "
                + "the resource reload never finished or the probe key never resolved in the expected "
                + "script. No RTL frame was taken.");
    }
}

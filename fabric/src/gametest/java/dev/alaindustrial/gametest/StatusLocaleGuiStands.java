package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.AssemblerStatus;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnStatus;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterStatus;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorStatus;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.visual.ShotGroup;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-716 batch 0 (characterization of CLI-3): the status line of the six screens that draw it with their
 * own {@code graphics.text} instead of the fitted helper, in a BLOCKING state, under the two longest locales
 * the mod ships ({@code de_de}, {@code ru_ru}).
 *
 * <p>These are the frames a status refactoring can break without any test noticing: the line is drawn at a
 * hand-picked position with no width fitting, so a long translation either fits, wraps, or runs over the
 * frame — and whichever it does today is what MOD-716 batch 5 must reproduce pixel for pixel. A line that
 * overflows today is pinned as it is (a known overflow), not fixed here.
 *
 * <p>The vulcanizer's status line is photographed by MOD-693; it is not repeated. English is restored at the
 * end, because Fabric runs every client-gametest class in this process.
 *
 * <p>One more frame guards a regression rather than a baseline: {@link #OVER_BAR}, the lightning rod's no-tip
 * line in {@code fr_fr} over a half-full energy bar. That line is wider than 122 px and starts inside the bar
 * column, so when the frame text was submitted before the bar (MOD-716 batch 6) the fill covered its first
 * letters; {@code MachineScreen.drawFrameText} now draws it after the bar.
 */
@SuppressWarnings("UnstableApiUsage")
public final class StatusLocaleGuiStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private static final List<String> LOCALES = List.of("de_de", "ru_ru");

    /** Rules and caption of the {@code gui_status_} family in {@code ShotFamilies}. */
    private static final List<String> RULES = List.of("R-GUI-01", "R-GUI-03");

    private static final String CHECKS = "Blocking status line under a long locale (MOD-716): the caption is the "
            + "state the frame is named for, in the frame's language; record whether it fits, wraps or runs "
            + "over the frame — a status refactoring must reproduce exactly this";

    /** One screen in its blocking state: the menu, a title, and the channels that put it there. */
    private record Subject(String menuId, Supplier<? extends MenuType<?>> type, String title,
                           Consumer<MachineMenu> state) {
    }

    private static final int CAP = 4000;

    /** Prefix, rules and caption of the {@code gui_status_over_bar_} family in {@code ShotFamilies}. */
    private static final String OVER_BAR_PREFIX = "gui_status_over_bar_";
    private static final String OVER_BAR_LOCALE = "fr_fr";
    private static final String OVER_BAR_CHECKS = "Status row drawn after the energy bar (MOD-716): the lightning "
            + "rod's no-tip line in fr_fr is readable from its first letter; no part of it is covered by the "
            + "half-full energy bar's fill";

    /** No tip installed (mode channel {@code MODE_NO_TIP}), buffer about half full. */
    private static final Subject OVER_BAR = new Subject("lightning_rod_generator",
            ModContent.LIGHTNING_ROD_GENERATOR_MENU, "Lightning Rod Generator", menu -> {
                menu.injectTestData(CAP / 2, CAP, 0, 0);
                menu.injectTestChannel(LightningRodGeneratorBlockEntity.Channel.MODE.ordinal(),
                        LightningRodGeneratorBlockEntity.MODE_NO_TIP);
            });

    private static final List<Subject> SUBJECTS = List.of(
            new Subject("electric_heater", ModContent.ELECTRIC_HEATER_MENU, "Electric Heater", menu -> {
                menu.injectTestData(CAP, CAP, 0, 0);
                menu.injectTestChannel(ElectricHeaterBlockEntity.Channel.STATUS.ordinal(),
                        ElectricHeaterStatus.NO_CONSUMER.ordinal());
            }),
            new Subject("incubator", ModContent.INCUBATOR_MENU, "Incubator", menu -> {
                menu.injectTestData(CAP, CAP, 0, 300);
                menu.injectTestChannel(4, 0);   // mode: transform chip inserted
                menu.injectTestChannel(5, 2);   // charge left on the loaded ingot
                menu.injectTestChannel(6, 1);   // dome formed
                menu.injectTestChannel(IncubatorBlockEntity.Channel.STATUS.ordinal(),
                        IncubatorStatus.OUTPUT_BLOCKED.ordinal());
            }),
            new Subject("garden_drone_station", ModContent.GARDEN_DRONE_STATION_MENU, "Garden Drone Station", menu -> {
                menu.injectTestData(CAP, CAP, 0, 0);
                menu.injectTestChannel(GardenDroneStationBlockEntity.Channel.STATUS.ordinal(),
                        GardenDroneStatus.NO_RESOURCES.ordinal());
            }),
            new Subject("distillation_column", ModContent.DISTILLATION_COLUMN_MENU, "Distillation Column", menu -> {
                menu.injectTestData(CAP, CAP, 0, 0);
                menu.injectTestChannel(DistillationColumnBlockEntity.Channel.STATUS.ordinal(),
                        DistillationColumnStatus.FUEL_OIL_FULL.ordinal());
            }),
            new Subject("assembler", ModContent.ASSEMBLER_MENU, "Assembler", menu -> {
                menu.injectTestData(CAP, CAP, 0, 40);
                menu.injectTestChannel(4, -1);   // no active queue slot
                menu.injectTestChannel(5, AssemblerStatus.OUTPUT_FULL.ordinal());
                if (menu instanceof AssemblerMenu assembler) {
                    assembler.setActiveTab(AssemblerMenu.TAB_WORK);
                }
            }),
            // An empty vessel slot: the line asks for a vessel and names how many souls the next tier needs.
            new Subject("mob_repeller", ModContent.MOB_REPELLER_MENU, "Mob Repeller",
                    menu -> menu.injectTestData(CAP, CAP, 0, 0)));

    private StatusLocaleGuiStands() {
    }

    /**
     * Every subject under each long locale, the {@link #OVER_BAR} frame in {@code fr_fr}, then English again.
     *
     * @covers R-GUI-01, R-GUI-03
     */
    public static void shoot(ClientGameTestContext context) {
        String english = probe(context);
        try {
            for (String locale : LOCALES) {
                switchLanguage(context, locale, english, false);
                ShotRecorder.locale(locale);
                for (Subject subject : SUBJECTS) {
                    shootOne(context, subject, "gui_status_" + subject.menuId() + "_" + locale, RULES, CHECKS);
                }
            }
            switchLanguage(context, OVER_BAR_LOCALE, english, false);
            ShotRecorder.locale(OVER_BAR_LOCALE);
            shootOne(context, OVER_BAR, OVER_BAR_PREFIX + OVER_BAR.menuId() + "_" + OVER_BAR_LOCALE, RULES,
                    OVER_BAR_CHECKS);
        } finally {
            ShotRecorder.locale("en_us");
            switchLanguage(context, "en_us", english, true);
        }
    }

    private static void shootOne(ClientGameTestContext context, Subject subject, String name, List<String> rules,
                                 String checks) {
        LOG.info("[GUITEST][MOD-716] opening {} (status line)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(subject.type().get(), mc, 0, Component.literal(subject.title()));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                subject.state().accept(menu);
            }
        });
        awaitMenuScreen(context);
        ShotRecorder.capture(name, ShotGroup.GUI, subject.menuId(), rules, checks);
    }

    /** A mod string every locale translates, read in the client's current language. */
    private static String probe(ClientGameTestContext context) {
        return context.computeOnClient(mc -> Component.translatable("gui.alaindustrial.stats.title").getString());
    }

    /**
     * Applies a language the way the language screen does, then waits for its effect: the reload has finished
     * and the probe key reads as English exactly when English was asked for. Bounded, as in {@code RtlGuiStands}.
     */
    private static void switchLanguage(ClientGameTestContext context, String code, String english,
                                       boolean wantEnglish) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(mc -> {
            mc.getLanguageManager().setSelected(code);
            mc.options.languageCode = code;
            reload.set(mc.reloadResourcePacks());
        });
        for (int i = 0; i < 100; i++) {   // 100 x 5 ticks, about 25 s, cap on a stuck reload
            CompletableFuture<Void> future = reload.get();
            boolean isEnglish = english.equals(probe(context));
            if (future != null && future.isDone() && isEnglish == wantEnglish) {
                context.waitTicks(2);   // let the reload overlay clear before the next frame
                return;
            }
            context.waitTicks(5);
        }
        throw new AssertionError("[MOD-716] language switch to '" + code + "' did not land within 25 s; no status"
                + " frame was taken in it");
    }
}

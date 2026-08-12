package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.Industrialization;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R-GUI-04 — the advancement-tab icons, and every other item icon rendered in
 * {@code ItemDisplayContext.GUI}.
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. It needs the in-world player and the server
 * (the tree has to be granted before the icons render at full brightness), which is why it runs
 * inside the world lane rather than with the menu stands.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdvancementScreenStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private AdvancementScreenStand() {
    }

    /**
     * R-GUI-04: Opens the real {@link AdvancementsScreen} and photographs our advancement tab, a
     * vanilla tab, and the return to our tab. The tab icons (raw_tin on ore_hunter, solar_panel
     * on root/solar_power, copper_cable on first_wire/energized_network, battery_box on
     * first_storage, electronic_circuit on first_circuit) are rendered by {@code AdvancementWidget} in
     * {@code ItemDisplayContext.GUI} —
     * the exact path the {@code minecraft:select} gui-icon fix targets. A white square or an
     * off-centre icon here is the regression this suite exists to catch.
     *
     * <p>Also shoots the hotbar with all seven fixed block-items ({@code solar_panel},
     * {@code daylight/moonlit_solar_panel}, and the four cables) so every fixed icon is verified in
     * one frame, not just the three that happen to be advancement icons.
     *
     * <p>The whole tree is granted first so icons render at full brightness (ungranted icons still
     * render, only dimmed).
     */
    public static void shootAdvancementScreens(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Grant our root (ore_hunter) + all descendants so every tab icon renders bright.
        server.runCommand("advancement grant @p from alaindustrial:ore_hunter");
        context.waitTicks(5);

        // ── Our tab ──────────────────────────────────────────────────────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            mc.setScreenAndShow(new AdvancementsScreen(adv));
            AdvancementHolder root = adv.get(Industrialization.id("ore_hunter"));
            if (root != null) {
                adv.setSelectedTab(root, false);
                LOG.info("[GUITEST][R-GUI-04] selected tab {}", root.id());
            } else {
                LOG.warn("[GUITEST][R-GUI-04] alaindustrial:ore_hunter (root) advancement missing on client!");
            }
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_alaindustrial_root -> {}",
                takeCleanScreenshot(context, "adv_alaindustrial_root").toAbsolutePath());

        // ── Transition: switch to a vanilla tab (proves tab navigation) ────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            AdvancementHolder story = adv.get(Identifier.fromNamespaceAndPath("minecraft", "story/root"));
            if (story != null) adv.setSelectedTab(story, false);
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_vanilla_story -> {}",
                takeCleanScreenshot(context, "adv_vanilla_story").toAbsolutePath());

        // ── Back to our tab (icons must survive re-select) ─────────────────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            AdvancementHolder root = adv.get(Industrialization.id("ore_hunter"));
            if (root != null) adv.setSelectedTab(root, false);
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_alaindustrial_return -> {}",
                takeCleanScreenshot(context, "adv_alaindustrial_return").toAbsolutePath());

        // Close the advancements screen.
        context.runOnClient(mc -> mc.setScreenAndShow(null));
        context.waitTicks(3);

        // ── All 7 fixed block-item icons in the hotbar (same gui render context) ───────
        server.runCommand("fill 6 99 17 12 99 23 minecraft:smooth_stone");
        server.runCommand("gamemode creative @p");
        server.runCommand("clear @p");
        server.runCommand("tp @p 9 100 20 180 0");
        String[] fixedItems = {
            "solar_panel", "daylight_solar_panel", "moonlit_solar_panel",
            "copper_cable", "tin_cable", "insulated_copper_cable", "insulated_tin_cable"
        };
        for (String it : fixedItems) server.runCommand("give @p alaindustrial:" + it);
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] hud_fixed_item_icons -> {}",
                takeCleanScreenshot(context, "hud_fixed_item_icons").toAbsolutePath());
    }
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStatus;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mob wheel drive's window (MOD-763) in the three states a player meets: a fresh pig running, a zombie
 * worn out with nothing in the feeder, and an empty wheel.
 *
 * <p>The menu is built client-side and its channels injected, for the reason {@link WaterMillGuiStand} gives:
 * a real drive is a generator that rewrites its channels every tick, and the menu only opens on an assembled
 * 3×3×3 wheel. A client-only menu shows exactly the state under test.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MobWheelGuiStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private MobWheelGuiStand() {
    }

    public static void shootStates(ClientGameTestContext context) {
        shoot(context, "running_pig",
                "A pig runs: the stamina gauge is nearly full and green, the wheel pictogram is lit, the display "
                        + "names the Pig, its speed and the output in EU/t, and the status row reads \"Running\" "
                        + "in the dim colour",
                MobWheelStatus.RUNNING, MobWheelProfile.PIG, 95, 900, 4);
        shoot(context, "exhausted_zombie",
                "A zombie is worn out and the feeder is empty: the gauge is empty, the pictogram grey, the "
                        + "status row reads the exhausted reason in red without leaving the display, and the "
                        + "feeder shows a translucent rotten-flesh hint",
                MobWheelStatus.EXHAUSTED, MobWheelProfile.ZOMBIE, 0, 0, 0);
        shoot(context, "empty",
                "Nobody inside: no species, speed or output lines, the status row reads \"No mob inside\" in "
                        + "red, and the feeder hint cycles through the four staple foods",
                MobWheelStatus.NO_MOB, null, 0, 0, 0);
    }

    private static Path shoot(ClientGameTestContext context, String state, String checks, MobWheelStatus status,
            MobWheelProfile species, int speed, int staminaPermille, int rate) {
        LOG.info("[GUITEST][MOD-763] opening mob_wheel_controller/{}", state);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.MOB_WHEEL_CONTROLLER_MENU.get(), mc, 0,
                    Component.literal("Mob Wheel Drive"));
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)
                    || !(acs.getMenu() instanceof MachineMenu menu)) {
                throw new AssertionError("[GUITEST][MOD-763] MenuScreens.create did not open a machine screen for "
                        + "mob_wheel_controller/" + state + " — the screen binding in MenuScreenManifest is missing.");
            }
            menu.injectTestData(0, 0, speed, status.ordinal());
            menu.injectTestChannel(MobWheelBlockEntity.Channel.RATE.ordinal(), rate);
            menu.injectTestChannel(MobWheelBlockEntity.Channel.STAMINA.ordinal(), staminaPermille);
            menu.injectTestChannel(MobWheelBlockEntity.Channel.SPECIES.ordinal(),
                    species == null ? 0 : species.ordinal() + 1);
        });
        awaitMenuScreen(context);
        Path path = ShotRecorder.captureScreen("mob_wheel_controller", state,
                ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
        LOG.info("[GUITEST][MOD-763] screenshot mob_wheel_controller/{} -> {}", state, path.toAbsolutePath());
        return path;
    }
}

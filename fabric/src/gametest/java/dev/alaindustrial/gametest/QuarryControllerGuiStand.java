package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity.Channel;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The quarry controller's window (MOD-951) in the three states a player meets: digging, stopped for want of a
 * drill bit, and a frame that does not stand.
 *
 * <p>The menu is built client-side and its channels injected, for the reason {@link MobWheelGuiStand} gives: the
 * real window opens only on an assembled frame of four towers, and a client-only menu shows exactly the state under
 * test. A bit and a few stacks of haul are put into the client container, so the frame shows whether the items sit
 * in the wells the atlas paints.
 */
@SuppressWarnings("UnstableApiUsage")
public final class QuarryControllerGuiStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");
    private static final int CAPACITY = 40_000;

    private QuarryControllerGuiStand() {
    }

    public static void shootStates(ClientGameTestContext context) {
        shoot(context, "drilling",
                "Digging: the energy bar is half full, the drill bit sits in its well with a durability bar, the "
                        + "info block reads layer 3 of 24, the layer 40 % dug and \"Drilling\", the drill-bit "
                        + "sprite is half filled, there is no red status row, and the haul fills the first buffer "
                        + "wells exactly",
                QuarryWorkStatus.DRILLING, QuarryFrameScan.Status.FORMED, 20_000, 3, 24, 400, 20, 40, true);
        shoot(context, "no_bit",
                "Stopped for want of a bit: the bit slot shows a translucent drill-bit hint, the layer lines stay, "
                        + "the cycle line and the drill sprite are gone, and the status row reads \"No drill bit\" "
                        + "in red between the energy bar and the right border",
                QuarryWorkStatus.NO_BIT, QuarryFrameScan.Status.FORMED, 40_000, 3, 24, 400, 0, 40, false);
        shoot(context, "not_formed",
                "The frame does not stand: no layer lines, an empty energy bar, and the status row names the "
                        + "frame's first broken rule (\"A path is broken\") in red, not \"Frame not assembled\"",
                QuarryWorkStatus.NOT_FORMED, QuarryFrameScan.Status.PATH_BROKEN, 0, 0, 0, 0, 0, 0, false);
    }

    private static Path shoot(ClientGameTestContext context, String state, String checks, QuarryWorkStatus status,
            QuarryFrameScan.Status frame, int energy, int layer, int layers, int permille, int progress,
            int maxProgress, boolean bit) {
        LOG.info("[GUITEST][MOD-951] opening quarry_controller/{}", state);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.QUARRY_CONTROLLER_MENU.get(), mc, 0, Component.literal("Quarry Controller"));
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)
                    || !(acs.getMenu() instanceof MachineMenu menu)) {
                throw new AssertionError("[GUITEST][MOD-951] MenuScreens.create did not open a machine screen for "
                        + "quarry_controller/" + state + " — the screen binding in MenuScreenManifest is missing.");
            }
            menu.injectTestData(energy, CAPACITY, progress, maxProgress);
            menu.injectTestChannel(Channel.STATUS.ordinal(), status.ordinal());
            menu.injectTestChannel(Channel.FRAME_STATUS.ordinal(), frame.ordinal());
            menu.injectTestChannel(Channel.LAYER.ordinal(), layer);
            menu.injectTestChannel(Channel.LAYERS.ordinal(), layers);
            menu.injectTestChannel(Channel.LAYER_PERMILLE.ordinal(), permille);
            if (bit) {
                ItemStack drill = new ItemStack(ModContent.QUARRY_DRILL_BIT.get());
                drill.setDamageValue(drill.getMaxDamage() * 3 / 10);
                menu.machineSlot(QuarryControllerBlockEntity.BIT_SLOT).set(drill);
            }
            if (layers > 0) {
                int first = QuarryControllerBlockEntity.BUFFER_START;
                menu.machineSlot(first).set(new ItemStack(Items.COBBLESTONE, 64));
                menu.machineSlot(first + 1).set(new ItemStack(Items.COBBLESTONE, 37));
                menu.machineSlot(first + 2).set(new ItemStack(Items.RAW_IRON, 5));
                menu.machineSlot(first + 9).set(new ItemStack(Items.COAL, 12));
                menu.machineSlot(first + 26).set(new ItemStack(Items.DIRT, 64));
            }
        });
        awaitMenuScreen(context);
        Path path = ShotRecorder.captureScreen("quarry_controller", state,
                ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
        LOG.info("[GUITEST][MOD-951] screenshot quarry_controller/{} -> {}", state, path.toAbsolutePath());
        return path;
    }
}

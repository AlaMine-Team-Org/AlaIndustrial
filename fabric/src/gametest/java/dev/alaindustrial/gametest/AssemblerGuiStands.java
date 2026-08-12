package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.differsAt;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Assembler window (MOD-275): its three machine states, the queue of recorded blueprints, the
 * two-tab split and the read-only layout preview.
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. The blueprint <em>item</em> icon — the
 * player's original complaint, which is about the item wherever it is and not about this window —
 * lives next door in {@link BlueprintIconStands}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AssemblerGuiStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * The Assembler window as {@code {leftPos, topPos, imageWidth, imageHeight, guiScaledWidth}}, in
     * GUI-scaled units, captured inside {@code runOnClient} by {@link #shootAssemblerTab}.
     */
    private static int[] assemblerWindowBox;

    private AssemblerGuiStands() {
    }

    /**
     * Assembler variant of {@code shootMenuWithState} (MOD-275): also fills the two channels this
     * machine adds — the queue slot it is working from ({@code -1} = the queue is empty) and the idle
     * reason — and, when {@code withPattern}, lays a real recipe into the ghost grid so the shot shows
     * the cells, the result preview and an enabled Write button rather than an empty frame.
     *
     * <p>The pattern is written straight into the client-side container behind the ghost slots, the
     * same way {@code MachineGuiStands.shootSolarPanel} injects its chip: the screen reads slot
     * contents synchronously, so no server round-trip is needed to photograph the state.
     */
    public static void shootAssembler(ClientGameTestContext context, String name,
                                      int energy, int capacity, int progress, int maxProgress,
                                      int activeSlot,
                                      dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus status,
                                      boolean withPattern) {
        LOG.info("[GUITEST][MOD-275] opening {} (E={}/{} P={}/{} active={} status={} pattern={})",
                name, energy, capacity, progress, maxProgress, activeSlot, status, withPattern);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, activeSlot);
                menu.injectTestChannel(5, status.ordinal());
                selectTab(menu, dev.alaindustrial.menu.AssemblerMenu.TAB_WORK);
                if (withPattern) {
                    // A stick: two planks stacked in the left column — an off-centre layout on purpose,
                    // because that is the case CraftingInput trimming turns into a 1x2 grid. It reaches
                    // the Work tab through the QUEUE, which is what that tab draws its read-only view
                    // from; the editable grid it used to be injected into now lives on the Record tab.
                    menu.slots.get(0).set(blueprint(new int[] {0, 3},
                            net.minecraft.world.item.Items.OAK_PLANKS,
                            new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Assembler window with a queue of <b>recorded</b> blueprints and an untouched authoring grid — the
     * state MOD-275's playtest found unreadable, where the machine visibly worked and the window showed
     * nothing about what it was making.
     *
     * <p>Two different products on purpose (4 sticks in slot 0, 4 planks in slot 1): an icon that showed
     * the same picture for both would be a lie dressed as a feature, and one frame with two unlike icons
     * is what rules it out. {@code recorded=false} shoots the identical rig with blank blueprints, which
     * is the baseline the pixel gate measures against.
     */
    public static Path shootAssemblerQueue(ClientGameTestContext context, String name,
                                           int energy, int capacity, int progress, int maxProgress,
                                           int activeSlot,
                                           dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus status,
                                           boolean recorded) {
        LOG.info("[GUITEST][MOD-275] opening {} (active={} status={} recorded={})",
                name, activeSlot, status, recorded);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, activeSlot);
                menu.injectTestChannel(5, status.ordinal());
                selectTab(menu, dev.alaindustrial.menu.AssemblerMenu.TAB_WORK);
                // Slot 0: two planks in the left column -> 4 sticks. Slot 1: one log -> 4 planks.
                menu.slots.get(0).set(recorded
                        ? blueprint(new int[] {0, 3}, net.minecraft.world.item.Items.OAK_PLANKS,
                                new ItemStack(net.minecraft.world.item.Items.STICK, 4))
                        : new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
                menu.slots.get(1).set(recorded
                        ? blueprint(new int[] {0}, net.minecraft.world.item.Items.OAK_LOG,
                                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4))
                        : new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
            }
        });
        awaitMenuScreen(context);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /** Put the Assembler window on {@code tab}; a no-op for any other machine menu. */
    private static void selectTab(MachineMenu menu, int tab) {
        if (menu instanceof dev.alaindustrial.menu.AssemblerMenu assembler) {
            assembler.setActiveTab(tab);
        }
    }

    /**
     * One Assembler window on one tab, with each group of content independently switchable (MOD-275).
     *
     * <p>Everything else is pinned, so a pair of shots from this method differs by exactly the groups
     * that were toggled between them — which is what makes the tab gate below able to say "this content
     * is not in this frame" rather than merely "the two frames look different".
     *
     * @param tab       {@code TAB_WORK} or {@code TAB_RECORD}
     * @param fillGrid  lay a recipe into the authoring grid and its result preview (Record-tab content)
     * @param fillBlank put a blank blueprint in the Record tab's slot (Record-tab content)
     * @param fillQueue put two recorded blueprints in the queue (Work-tab content)
     */
    private static Path shootAssemblerTab(ClientGameTestContext context, String name, int tab,
                                          boolean fillGrid, boolean fillBlank, boolean fillQueue) {
        final int CAP = 12000;   // Config.assemblerBuffer
        final int ASM = 40;      // Config.assemblerDuration
        LOG.info("[GUITEST][MOD-275] opening {} (tab={} grid={} blank={} queue={})",
                name, tab, fillGrid, fillBlank, fillQueue);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(CAP * 3 / 4, CAP, ASM / 2, ASM);
                menu.injectTestChannel(4, fillQueue ? 0 : -1);
                menu.injectTestChannel(5, dev.alaindustrial.block.entity.AssemblerBlockEntity
                        .AssemblerStatus.READY.ordinal());
                selectTab(menu, tab);
                var box = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
                assemblerWindowBox = new int[] {
                        box.alaindustrial$getLeftPos(), box.alaindustrial$getTopPos(),
                        box.alaindustrial$getImageWidth(), box.alaindustrial$getImageHeight(),
                        mc.getWindow().getGuiScaledWidth(),
                };
                if (fillGrid) {
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.GRID_SLOT_START)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.GRID_SLOT_START + 4)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.RESULT_SLOT_INDEX)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK));
                }
                if (fillBlank) {
                    menu.slots.get(dev.alaindustrial.block.entity.AssemblerBlockEntity.BLANK_SLOT)
                            .set(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
                }
                if (fillQueue) {
                    menu.slots.get(0).set(blueprint(new int[] {0, 3},
                            net.minecraft.world.item.Items.OAK_PLANKS,
                            new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
                    menu.slots.get(1).set(blueprint(new int[] {0},
                            net.minecraft.world.item.Items.OAK_LOG,
                            new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4)));
                }
            }
        });
        context.waitTicks(5);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * The gate that says the hidden tab's contents are <b>gone</b>, not merely covered (MOD-275).
     *
     * <p>Hiding a slot in Minecraft is one boolean — {@code Slot#isActive} — and getting it wrong looks
     * exactly like getting it right in a single screenshot: the frame is drawn from a different atlas
     * either way, and a slot that is still painted underneath, or an item drawn over the wrong band, is
     * a couple of hundred pixels nobody notices. So this measures each tab's content twice, once with it
     * present and once absent, and asserts <em>both</em> directions:
     *
     * <ul>
     *   <li>on its own tab that content moves pixels (otherwise the injection is doing nothing and the
     *       zero below would be vacuous — this is the control);</li>
     *   <li>on the other tab the identical injection moves <b>no</b> pixels beyond the frame-to-frame
     *       noise floor.</li>
     * </ul>
     *
     * <p>Run for both tabs, so neither one is trusted on the strength of the other.
     */
    public static void assertHiddenTabIsGone(ClientGameTestContext context) {
        int work = dev.alaindustrial.menu.AssemblerMenu.TAB_WORK;
        int record = dev.alaindustrial.menu.AssemblerMenu.TAB_RECORD;

        // Noise floor: the same state, twice.
        Path noiseA = shootAssemblerTab(context, "gui_assembler_tab_noise_a", work, false, false, false);
        Path noiseB = shootAssemblerTab(context, "gui_assembler_tab_noise_b", work, false, false, false);
        int noise = differingPixelsInWindow(noiseA, noiseB);

        // Record-tab content: visible on Record, absent from Work.
        Path recOn = shootAssemblerTab(context, "gui_assembler_tab_record_filled", record, true, true, false);
        Path recOff = shootAssemblerTab(context, "gui_assembler_tab_record_empty", record, false, false, false);
        Path workRecOn = shootAssemblerTab(context, "gui_assembler_tab_work_recdata", work, true, true, false);
        Path workRecOff = shootAssemblerTab(context, "gui_assembler_tab_work_nodata", work, false, false, false);

        // Work-tab content: visible on Work, absent from Record.
        Path workOn = shootAssemblerTab(context, "gui_assembler_tab_work_filled", work, false, false, true);
        Path workOff = workRecOff;
        Path recWorkOn = shootAssemblerTab(context, "gui_assembler_tab_record_workdata", record, false, false, true);
        Path recWorkOff = recOff;

        assertTabIsolation("Record", differingPixelsInWindow(recOn, recOff),
                differingPixelsInWindow(workRecOn, workRecOff), noise, recOn, workRecOn);
        assertTabIsolation("Work", differingPixelsInWindow(workOn, workOff),
                differingPixelsInWindow(recWorkOn, recWorkOff), noise, workOn, recWorkOn);
    }

    /**
     * Pixels that differ inside the Assembler window, and nowhere else.
     *
     * <p>The whole screen is the wrong canvas for this measurement, and measuring it taught that the
     * hard way: the recipe viewer's item panel down the right-hand edge re-renders its item models every
     * frame and drifts by a few dozen pixels between two identical shots. Cropping to the window is not
     * a way of hiding an inconvenient signal — it is the only region either tab can draw in, so a
     * difference outside it cannot be the thing under test, and a noise floor big enough to absorb the
     * viewer would also absorb a genuinely leaking slot.
     */
    private static int differingPixelsInWindow(Path first, Path second) {
        ShotRecorder.markComparedFiles(first, second);
        if (assemblerWindowBox == null) {
            throw new AssertionError("[GUITEST][MOD-275] the Assembler window was never measured");
        }
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST][MOD-275] could not decode " + first + " / " + second);
            }
            // Screenshots are in physical pixels, the box in GUI-scaled units.
            int scale = Math.max(1, a.getWidth() / assemblerWindowBox[4]);
            int x0 = assemblerWindowBox[0] * scale;
            int y0 = assemblerWindowBox[1] * scale;
            int x1 = Math.min(a.getWidth(), x0 + assemblerWindowBox[2] * scale);
            int y1 = Math.min(a.getHeight(), y0 + assemblerWindowBox[3] * scale);
            int differing = 0;
            for (int y = Math.max(0, y0); y < y1; y++) {
                for (int x = Math.max(0, x0); x < x1; x++) {
                    if (differsAt(a, b, x, y)) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read screenshots for the tab gate", e);
        }
    }

    /** One direction of {@link #assertHiddenTabIsGone}: seen on its own tab, unseen on the other. */
    private static void assertTabIsolation(String owner,
                                           int onOwnTab, int onOtherTab, int noise,
                                           Path ownShot, Path otherShot) {
        LOG.info("[GUITEST][MOD-275] {} content: {} px on its own tab, {} px on the other (noise {})",
                owner, onOwnTab, onOtherTab, noise);
        int required = Math.max(4 * noise, 200);
        if (onOwnTab < required) {
            throw new AssertionError("[GUITEST][MOD-275] the " + owner + " tab's own content changed only "
                    + onOwnTab + " px (noise " + noise + ", required > " + required + ") — the shots are "
                    + "not injecting anything, so the isolation check below would be vacuous. See "
                    + ownShot.getFileName() + ".");
        }
        // Zero, not "small": inside the window there is nothing to be noisy about, and the noise floor
        // measured above is the evidence for that rather than a budget to spend.
        int tolerated = noise;
        if (onOtherTab > tolerated) {
            throw new AssertionError("[GUITEST][MOD-275] " + owner + "-tab content moved " + onOtherTab
                    + " px on the OTHER tab (noise floor " + tolerated + ") — a hidden slot is still being "
                    + "drawn, or its item is bleeding into the visible band. See " + otherShot.getFileName()
                    + ".");
        }
    }

    /**
     * A recorded blueprint: {@code item} in each of {@code cells}, carrying {@code result} as its product.
     *
     * <p>Public because {@link BlueprintIconStands} builds the same stacks to photograph the ITEM rather
     * than this window — one factory, so the two lanes cannot drift into shooting different blueprints.
     */
    public static ItemStack blueprint(int[] cells, net.minecraft.world.item.Item item, ItemStack result) {
        java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(
                dev.alaindustrial.item.assembler.BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));
        for (int cell : cells) {
            grid.set(cell, new ItemStack(item));
        }
        return dev.alaindustrial.item.assembler.AssemblyBlueprintItem.record(
                new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
                dev.alaindustrial.item.assembler.BlueprintPattern.of(grid), result);
    }

    /**
     * Pixel gate for MOD-275's read-only preview: a screenshot of a window is not evidence that anything
     * was drawn in it.
     *
     * <p>Same window twice — once with recorded blueprints in the queue, once with blanks. Everything
     * else is identical, so the only thing that can move a pixel is the new drawing: the borrowed layout
     * in the ghost cells, the blue frame around it, the result preview and the two icons. A run where
     * the preview silently stopped rendering shows a delta of nearly zero here and fails, instead of
     * producing a perfectly valid, perfectly empty frame for a reviewer to sign off.
     */
    public static void assertBlueprintPreviewInFrame(ClientGameTestContext context, int maxProgress) {
        final int CAP = 12000;   // Config.assemblerBuffer
        Path recorded = shootAssemblerQueue(context, "gui_assembler_bp_gate_recorded", CAP, CAP, 0,
                maxProgress, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, true);
        Path blankA = shootAssemblerQueue(context, "gui_assembler_bp_gate_blank_a", CAP, CAP, 0,
                maxProgress, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, false);
        context.waitTicks(3);
        Path blankB = takeCleanScreenshot(context, "gui_assembler_bp_gate_blank_b");

        int previewDelta = differingPixels(recorded, blankA);
        int noise = differingPixels(blankA, blankB);
        // A static GUI has essentially no frame-to-frame noise, so the floor is what matters: the two
        // layout items, the result preview, the frame and the icons cover well over 400 px even at the
        // smallest GUI scale this harness runs at.
        int required = Math.max(4 * noise, 400);
        LOG.info("[GUITEST][MOD-275] preview pixel gate: delta={} px, noise={} px, required>{}",
                previewDelta, noise, required);
        if (previewDelta < required) {
            throw new AssertionError("[GUITEST][MOD-275] recording the queued blueprints changed only "
                    + previewDelta + " px (noise " + noise + ", required > " + required + ") — the "
                    + "read-only layout preview and the blueprint icons are NOT in the captured frame. "
                    + "Compare " + recorded.getFileName() + " with " + blankA.getFileName() + ".");
        }
    }
}

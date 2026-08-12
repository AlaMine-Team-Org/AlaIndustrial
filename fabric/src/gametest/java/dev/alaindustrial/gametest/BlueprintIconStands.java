package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.differsAt;
import static dev.alaindustrial.gametest.VisualStandSupport.differsFrom;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.visual.PixelMath;
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
 * The blueprint ITEM icon (MOD-275 and its follow-ups): drawn at all, follows the recorded recipe,
 * stays inside the sheet's frame, sits in the middle of it, is lit like the item it copies, and
 * survives out of the GUI into the player's hand.
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. The Assembler <em>window</em> is next door in
 * {@link AssemblerGuiStands}; this file is about the item wherever it is, which was the player's
 * actual complaint — a recorded blueprint looked exactly like a blank one in the hotbar, on the
 * ground and in a chest.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlueprintIconStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * The three icon slots of the last {@link #shootBlueprintIcons} call, in GUI coordinates
     * ({@code x, y} of the 16×16 icon), plus the GUI-scaled screen width the shot was taken at.
     * Written inside the client task, read by {@link #assertDiffStaysInsideIconInteriors}.
     */
    private static int[][] iconSlotBoxes;
    private static int iconGuiScaledWidth;

    /**
     * The recorded sheet the icon is drawn on. Read straight off the mod's own resources, so the gate
     * below re-measures itself if the art ever changes instead of pinning colours as constants.
     */
    private static final String RECORDED_SHEET_TEXTURE =
            "/assets/alaindustrial/textures/item/assembly_blueprint_recorded.png";

    /**
     * How far the product's centre may sit from the icon's centre, in sprite texels (MOD-275).
     *
     * <p>Three quarters of a texel. The residual it has to tolerate is the product sprite's own
     * off-centre artwork — a vanilla stick's opaque pixels run 2..14 of its 16×16 sheet, so its visual
     * middle is 8.5, not 8.0, and shrunk to 10/16 that puts its centre of mass 0.38 texels low and
     * right; an oak-planks cube measures 0.01. A placement error, by contrast, moves in whole texels:
     * the smallest wrong translation the JSON can carry (1/16 off) displaces the product by a full
     * texel and fails.
     */
    private static final double ICON_CENTRE_TOLERANCE_TEXELS = 0.75;

    /**
     * How far the composed product's top-to-side shading contrast may drift from the same item's own.
     *
     * <p>Mean brightness is NOT the instrument here, and that is worth recording: measured on the two
     * rigs, the composed cube's mean luminance came out 106 under the wrong rig and 88 under the right
     * one — <em>brighter</em> when broken. The flat-sprite rig does not dim a block, it relights it, and
     * what the playtest saw as "dark grey" was the top face going dark while a side face went white. So
     * the gate compares the shape of the shading, not its level: top band against bottom band. The two
     * rigs put that ratio 45 % apart (1.24 against 1.82), and two sizes of the same cube agree to a few
     * percent, so a fifth of the way between them separates them with room on both sides.
     */
    private static final double ICON_SHADING_TOLERANCE = 0.20;

    /**
     * How far the composed product's aspect ratio may drift from the same item's own (MOD-275 follow-up).
     *
     * <p>This is the gate for "cropped", and it exists because none of the placement gates could see it.
     * The bounding box only says the product stays inside the frame — a cut-down product is smaller, so
     * it passes more easily. The centre of mass only says it sits in the middle — the sheet bisects the
     * product symmetrically, so a bisected cube is still dead centre. Both stayed green while the icon
     * showed a flat-topped stump. Shape is the thing neither measures: the icon draws the product with a
     * uniform scale, so its silhouette must have the same proportions as the item drawn on its own.
     * Measured, the bisected cube came out 18×17 device px against a whole one's 18×20 — 0.94 against
     * 1.11, 15 % apart — while the two whole shapes agree to a few percent across a 10-vs-16 texel size
     * difference.
     */
    private static final double ICON_SILHOUETTE_TOLERANCE = 0.08;

    private BlueprintIconStands() {
    }

    /**
     * Photographs three blueprint icons side by side in ordinary player-inventory slots and gates them
     * on pixels (MOD-275).
     *
     * <p>The player's complaint was about the <b>item</b>, not the machine window: a recorded blueprint
     * looked exactly like a blank one in the hotbar, on the ground and in a chest. The fix replaces the
     * grid motif in the middle of the icon with the recipe's real product, through a custom item-model
     * type ({@code alaindustrial:blueprint_result}). Player-inventory slots are used on purpose — the
     * Assembler draws its own decoration over its queue slots, so a shot taken there could pass on the
     * strength of that decoration alone.
     *
     * <p>Two gates, because "something changed" and "the right thing changed" are different claims:
     * <ul>
     *   <li><b>Drawn at all</b> — three recorded blueprints against three blanks. A registration that
     *       silently fell back to the plain model, or a composite that rendered nothing, shows a delta
     *       of roughly zero here and fails instead of producing a valid, empty-looking frame.
     *   <li><b>Reflects the recipe</b> — three sticks-blueprints against three planks-blueprints. Same
     *       component, same model, different product: a static "recorded" texture pretending to be
     *       dynamic passes the first gate and dies on this one.
     * </ul>
     */
    public static void assertBlueprintIconShowsProduct(ClientGameTestContext context) {
        ItemStack blank = new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get());
        // Two planks in the left column -> 4 sticks (a flat, mostly transparent icon), and one log ->
        // 4 planks (a full cube). Two very different shapes, so the second gate cannot be satisfied by
        // a tint difference.
        ItemStack sticks = AssemblerGuiStands.blueprint(new int[] {0, 3},
                net.minecraft.world.item.Items.OAK_PLANKS,
                new ItemStack(net.minecraft.world.item.Items.STICK, 4));
        ItemStack planks = AssemblerGuiStands.blueprint(new int[] {0},
                net.minecraft.world.item.Items.OAK_LOG,
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));

        // The reviewer's frame: blank, sticks and planks in one row, so "these are three different
        // pieces of paper" is a claim a human can check in one glance.
        shootBlueprintIcons(context, "gui_blueprint_icon_states", blank, sticks, planks);

        Path recorded = shootBlueprintIcons(context, "gui_blueprint_icon_gate_sticks",
                sticks, sticks, sticks);
        Path blankA = shootBlueprintIcons(context, "gui_blueprint_icon_gate_blank_a",
                blank, blank, blank);
        context.waitTicks(3);
        Path blankB = takeCleanScreenshot(context, "gui_blueprint_icon_gate_blank_b");
        Path otherProduct = shootBlueprintIcons(context, "gui_blueprint_icon_gate_planks",
                planks, planks, planks);
        // The lighting stand: the same block, once composed into a blueprint and once as itself, in two
        // adjacent slots of the same frame — so the comparison below is free of every other variable.
        Path lighting = shootBlueprintIcons(context, "gui_blueprint_icon_gate_lighting",
                planks, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), blank);
        // Leave the slots as we found them: every later shot in this suite renders the same inventory.
        Path cleared = shootBlueprintIcons(context, "gui_blueprint_icon_cleared",
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        // Out of the GUI entirely. The item-model definition carries no display-context switch, so the
        // product should follow the item into the hand — unlike vanilla's bundle, which restricts its
        // composed item to "when": "gui". Photographed rather than assumed.
        // Gated on two *products*, not on recorded-vs-blank. Blank and recorded blueprints also differ
        // by their sheet texture (the grid motif is erased on a recorded one), so a recorded-vs-blank
        // delta out here stays large even when the product is drawn in the GUI only — measured, not
        // assumed: restricting the product to "when": "gui" left that comparison at 52 000 px. Sticks
        // against planks shares the sheet, so anything this gate sees is the product.
        shootBlueprintInHand(context, "world_blueprint_in_hand_blank", blank);
        Path handSticks = shootBlueprintInHand(context, "world_blueprint_in_hand_sticks", sticks);
        Path handPlanks = shootBlueprintInHand(context, "world_blueprint_in_hand_planks", planks);
        // The baseline goes through a full swap cycle (sticks -> empty -> sticks), not two consecutive
        // frames: a world frame drifts between shots (sky, equip animation), and a baseline that skipped
        // the swap would understate the noise and make the gate vacuous.
        shootBlueprintInHand(context, "world_blueprint_in_hand_empty", ItemStack.EMPTY);
        Path handSticksAgain = shootBlueprintInHand(context, "world_blueprint_in_hand_sticks_again", sticks);
        shootBlueprintInHand(context, "world_blueprint_in_hand_cleared", ItemStack.EMPTY);

        int drawnDelta = differingPixels(recorded, blankA);
        int noise = differingPixels(blankA, blankB);
        int productDelta = differingPixels(recorded, otherProduct);
        int handDelta = differingPixels(handSticks, handPlanks);
        int handNoise = differingPixels(handSticks, handSticksAgain);
        // Three icons at 16x16 GUI px, gui scale 2, with the product filling the middle 10x10 of each:
        // ~1200 px of the frame belong to the three products. The floors below are a quarter of that,
        // so a partial render still fails while driver dithering never flakes a healthy one.
        int drawnRequired = Math.max(4 * noise, 300);
        int productRequired = Math.max(4 * noise, 200);
        LOG.info("[GUITEST][MOD-275] icon pixel gates: recorded-vs-blank={} px (>{}), "
                + "sticks-vs-planks={} px (>{}), noise={} px",
                drawnDelta, drawnRequired, productDelta, productRequired, noise);
        if (drawnDelta < drawnRequired) {
            throw new AssertionError("[GUITEST][MOD-275] recording a blueprint changed only "
                    + drawnDelta + " px of its icon (noise " + noise + ", required > " + drawnRequired
                    + ") — the product is NOT drawn into the item model. Either the "
                    + "alaindustrial:blueprint_result model type is not registered on this loader, or "
                    + "items/assembly_blueprint.json no longer selects it. Compare "
                    + recorded.getFileName() + " with " + blankA.getFileName() + ".");
        }
        if (productDelta < productRequired) {
            throw new AssertionError("[GUITEST][MOD-275] a sticks blueprint and a planks blueprint "
                    + "render " + productDelta + " px apart (noise " + noise + ", required > "
                    + productRequired + ") — the icon does not follow the recorded recipe. Compare "
                    + recorded.getFileName() + " with " + otherProduct.getFileName() + ".");
        }
        // "Inside the frame" is an acceptance criterion, not a matter of taste: the sheet's dark border
        // ring and its highlight row have to survive, or the icon stops reading as a blueprint at all.
        // Every pixel the recording changed must therefore land inside the sheet's interior.
        assertDiffStaysInsideIconInteriors(recorded, blankA);
        // Inside the frame is not the same claim as in the MIDDLE of it: everything above stays green
        // with the product shoved into a corner of the field. Both a flat product and a block one are
        // measured against the sheet's own texture (MOD-275 follow-up).
        assertProductCentredInIcon(recorded, "sticks");
        assertProductCentredInIcon(otherProduct, "planks");
        // Placement is not the same claim as LIGHTING: every gate above stayed green for a release while
        // the composed block rendered dark grey, because they all count pixels or measure where they are.
        assertProductLitLikeTheItemItself(lighting, cleared);
        // The held item is a fraction of the frame and the sky moves behind it, so this gate is
        // measured against its own noise baseline rather than a fixed floor.
        int handRequired = Math.max(4 * handNoise, 4000);
        LOG.info("[GUITEST][MOD-275] in-hand pixel gate: delta={} px (>{}), noise={} px",
                handDelta, handRequired, handNoise);
        if (handDelta < handRequired) {
            throw new AssertionError("[GUITEST][MOD-275] a sticks blueprint and a planks blueprint look "
                    + handDelta + " px apart in the player's hand (noise " + handNoise + ", required > "
                    + handRequired + ") — the product reaches the icon in a GUI slot but not in the "
                    + "world, so the item definition has picked up a display-context restriction. "
                    + "Compare " + handSticks.getFileName() + " with " + handPlanks.getFileName() + ".");
        }
    }

    /**
     * Fails unless every pixel that differs between the two frames lies inside one of the three icons'
     * <em>interiors</em> — the 12×12 field the sheet's border ring encloses.
     *
     * <p>The pixel-count gates above prove something was drawn and that it follows the recipe; neither
     * would notice a product drawn at full size, spilling over the frame and turning the blueprint into
     * "an item with a blue outline". This is the check for the size and placement, and it is exact:
     * the border ring (icon column/row 1) and the highlight and shadow rows (2 and 13) must come out
     * byte-identical to the blank sheet's.
     */
    private static void assertDiffStaysInsideIconInteriors(Path recorded, Path blank) {
        if (iconSlotBoxes == null) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        try {
            BufferedImage a = ImageIO.read(recorded.toFile());
            BufferedImage b = ImageIO.read(blank.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST][MOD-275] could not decode " + recorded + " / " + blank);
            }
            // The screenshot is the raw framebuffer; the GUI is drawn at an integer scale over it.
            int scale = a.getWidth() / iconGuiScaledWidth;
            // Icon interior: columns/rows 2..13 of the 16×16 sprite. 1 is the border ring, 0 is the
            // one-pixel margin outside it.
            final int INSET = 2;
            final int INTERIOR = 12;
            // Scan the icons and their immediate surroundings only. The rest of the frame carries the
            // world behind the window, whose few drifting pixels have nothing to say about whether the
            // product stayed inside its frame — and a whole-frame scan would fail on them.
            final int MARGIN = 8;
            int scanX0 = Integer.MAX_VALUE;
            int scanY0 = Integer.MAX_VALUE;
            int scanX1 = Integer.MIN_VALUE;
            int scanY1 = Integer.MIN_VALUE;
            for (int[] box : iconSlotBoxes) {
                scanX0 = Math.min(scanX0, (box[0] - MARGIN) * scale);
                scanY0 = Math.min(scanY0, (box[1] - MARGIN) * scale);
                scanX1 = Math.max(scanX1, (box[0] + 16 + MARGIN) * scale);
                scanY1 = Math.max(scanY1, (box[1] + 16 + MARGIN) * scale);
            }
            scanX0 = Math.max(0, scanX0);
            scanY0 = Math.max(0, scanY0);
            scanX1 = Math.min(a.getWidth(), scanX1);
            scanY1 = Math.min(a.getHeight(), scanY1);
            int outside = 0;
            int firstX = -1;
            int firstY = -1;
            for (int y = scanY0; y < scanY1; y++) {
                for (int x = scanX0; x < scanX1; x++) {
                    if (!differsAt(a, b, x, y)) {
                        continue;
                    }
                    boolean inside = false;
                    for (int[] box : iconSlotBoxes) {
                        int x0 = (box[0] + INSET) * scale;
                        int y0 = (box[1] + INSET) * scale;
                        if (x >= x0 && x < x0 + INTERIOR * scale && y >= y0 && y < y0 + INTERIOR * scale) {
                            inside = true;
                            break;
                        }
                    }
                    if (!inside) {
                        if (firstX < 0) {
                            firstX = x;
                            firstY = y;
                        }
                        outside++;
                    }
                }
            }
            LOG.info("[GUITEST][MOD-275] icon containment gate: {} px changed outside the sheet interiors",
                    outside);
            if (outside > 0) {
                throw new AssertionError("[GUITEST][MOD-275] recording a blueprint changed " + outside
                        + " px outside the sheets' interiors (first at " + firstX + "," + firstY
                        + ") — the product overflows the frame instead of sitting inside it. Check the "
                        + "scale in the minecraft:composite transformation of items/assembly_blueprint.json. "
                        + "Compare " + recorded.getFileName() + " with " + blank.getFileName() + ".");
            }
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read the icon screenshots", e);
        }
    }

    /** The recorded sheet, read off the mod's own resources so the gates re-measure instead of pinning. */
    private static BufferedImage readRecordedSheet() {
        BufferedImage sheet;
        try (java.io.InputStream in =
                     BlueprintIconStands.class.getResourceAsStream(RECORDED_SHEET_TEXTURE)) {
            if (in == null) {
                throw new AssertionError("[GUITEST][MOD-275] " + RECORDED_SHEET_TEXTURE
                        + " is not on the classpath — the sheet texture moved or was renamed.");
            }
            sheet = ImageIO.read(in);
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + RECORDED_SHEET_TEXTURE, e);
        }
        if (sheet == null || sheet.getWidth() != 16 || sheet.getHeight() != 16) {
            throw new AssertionError("[GUITEST][MOD-275] the recorded sheet is expected to be a 16×16 "
                    + "sprite; the gates' texel arithmetic does not hold otherwise.");
        }
        return sheet;
    }

    /**
     * Fails unless the product sits in the MIDDLE of the sheet, not merely somewhere inside it
     * (MOD-275 follow-up — the playtest complaint was "it is not centred", which every earlier gate
     * passed happily).
     *
     * <p>Method: the recorded blueprint is a flat 16×16 sprite drawn at an integer GUI scale, so every
     * pixel of the icon that is <em>not</em> the sheet's own texel is a pixel of the product. Compared
     * against the texture rather than against a blank blueprint on purpose — a blank carries a
     * different sheet (the 3×3 grid motif), so a recorded-vs-blank diff is contaminated by the erased
     * grid, which is itself centred and would drag any centre-of-mass measurement back towards the
     * middle no matter where the product went.
     *
     * <p>Two measurements, because they fail on different mistakes. The bounding box says the product
     * stays inside the frame — a centre test alone passes a product that is centred but overflows.
     * The <em>centre of mass</em> says it sits in the middle — a box test alone passes a product
     * shoved off to one side, and the box's own centre is the wrong instrument for that: an isometric
     * block's silhouette narrows to a single vertex top and bottom, and whether that sliver lands on a
     * sample point shifts the box by half a texel for reasons that have nothing to do with placement.
     * The centre of mass of the same shape is stable to a hundredth of a texel.
     */
    private static void assertProductCentredInIcon(Path frame, String label) {
        if (iconSlotBoxes == null) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        BufferedImage sheet = readRecordedSheet();
        BufferedImage shot;
        try {
            shot = ImageIO.read(frame.toFile());
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + frame, e);
        }
        if (shot == null) {
            throw new AssertionError("[GUITEST][MOD-275] could not decode " + frame);
        }
        int scale = shot.getWidth() / iconGuiScaledWidth;
        for (int slot = 0; slot < iconSlotBoxes.length; slot++) {
            int[] box = iconSlotBoxes[slot];
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            int count = 0;
            double sumX = 0.0;
            double sumY = 0.0;
            for (int ty = 0; ty < 16; ty++) {
                for (int tx = 0; tx < 16; tx++) {
                    int expected = sheet.getRGB(tx, ty);
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            int x = (box[0] + tx) * scale + sx;
                            int y = (box[1] + ty) * scale + sy;
                            if (!differsFrom(shot, x, y, expected)) {
                                continue;
                            }
                            count++;
                            // Sub-texel bounds: the pixel covers [u, u + 1/scale) of the sprite.
                            double u = tx + (double) sx / scale;
                            double v = ty + (double) sy / scale;
                            minX = Math.min(minX, u);
                            minY = Math.min(minY, v);
                            maxX = Math.max(maxX, u + 1.0 / scale);
                            maxY = Math.max(maxY, v + 1.0 / scale);
                            sumX += u + 0.5 / scale;
                            sumY += v + 0.5 / scale;
                        }
                    }
                }
            }
            // A stick is the thinnest product this suite photographs and still covers ~55 pixels at
            // scale 2; a floor of 16 says "something is drawn" without pinning either product's shape.
            if (count < 16) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " carries "
                        + count + " px that differ from the bare sheet — the product is not drawn at "
                        + "all. Compare " + frame.getFileName() + " with " + RECORDED_SHEET_TEXTURE + ".");
            }
            double centreX = sumX / count;
            double centreY = sumY / count;
            LOG.info("[GUITEST][MOD-275] icon {} of {}: {} px, box x[{}, {}] y[{}, {}], "
                            + "centre of mass ({}, {})",
                    slot, label, count, minX, maxX, minY, maxY, centreX, centreY);
            // Columns/rows 2..13 are the field the border ring encloses; the product must not reach the
            // ring itself. Same acceptance criterion as the containment gate, restated per product so
            // this method is a complete statement about placement on its own.
            if (minX < 2.0 || minY < 2.0 || maxX > 14.0 || maxY > 14.0) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " draws the "
                        + "product over texels x[" + minX + ", " + maxX + "] y[" + minY + ", " + maxY
                        + "] — outside the sheet's 2..14 interior. Check the scale in the "
                        + "minecraft:composite transformation of items/assembly_blueprint.json.");
            }
            double offX = centreX - 8.0;
            double offY = centreY - 8.0;
            if (Math.abs(offX) > ICON_CENTRE_TOLERANCE_TEXELS
                    || Math.abs(offY) > ICON_CENTRE_TOLERANCE_TEXELS) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " puts the "
                        + "product's centre of mass at (" + centreX + ", " + centreY + ") texels instead "
                        + "of (8, 8) — off by (" + offX + ", " + offY + "), tolerance "
                        + ICON_CENTRE_TOLERANCE_TEXELS + ". The sheet's frame is symmetric, so the blue "
                        + "field's centre IS the icon's centre; a layer scaled by s is centred only when "
                        + "the minecraft:composite translates it by (1 - s) / 2 on every axis. Check the "
                        + "translation in items/assembly_blueprint.json against " + frame.getFileName() + ".");
            }
        }
    }

    /**
     * Fails unless a block product composed into a blueprint is as brightly lit as the same block drawn
     * as an ordinary item one slot to its right (MOD-275 follow-up).
     *
     * <p><b>Why this gate had to be added.</b> Every other icon gate in this file — pixel deltas,
     * containment, bounding box, centre of mass — is a statement about <em>where</em> pixels are. All of
     * them stayed green through an entire release in which the product rendered dark grey, because a
     * dark cube occupies exactly the same texels as a lit one. Brightness needed its own instrument.
     *
     * <p><b>Method.</b> One frame, two adjacent slots: a planks blueprint in slot 0 and a plain oak
     * planks stack in slot 1. Slot 0's product is every pixel that differs from the bare sheet texture
     * (the same identification {@link #assertProductCentredInIcon} uses); slot 1's item is every pixel
     * that differs from the same slot in the all-empty frame. Each shape is then split into a top band
     * (its cube's upward face) and a bottom band (its side faces), and the two <em>contrasts</em> are
     * compared. Measuring the reference in the SAME frame is the point — it cancels the GUI scale, the
     * window background and any driver gamma, so the only thing left between the two numbers is the rig.
     */
    private static void assertProductLitLikeTheItemItself(Path frame, Path emptyFrame) {
        if (iconSlotBoxes == null || iconSlotBoxes.length < 2) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        BufferedImage sheet = readRecordedSheet();
        BufferedImage shot;
        BufferedImage empty;
        try {
            shot = ImageIO.read(frame.toFile());
            empty = ImageIO.read(emptyFrame.toFile());
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + frame + " / " + emptyFrame, e);
        }
        if (shot == null || empty == null) {
            throw new AssertionError("[GUITEST][MOD-275] could not decode " + frame + " / " + emptyFrame);
        }
        int scale = shot.getWidth() / iconGuiScaledWidth;
        IconShape composedShape = measureShape(shot, iconSlotBoxes[0], scale, sheet, null,
                "composed product");
        IconShape plainShape = measureShape(shot, iconSlotBoxes[1], scale, null, empty, "plain item");
        double aspectDrift = composedShape.aspect() / plainShape.aspect() - 1.0;
        LOG.info("[GUITEST][MOD-275] icon silhouette gate: composed {}x{} (aspect {}), "
                        + "plain {}x{} (aspect {}), drift={} (|.| < {})",
                composedShape.width(), composedShape.height(), composedShape.aspect(),
                plainShape.width(), plainShape.height(), plainShape.aspect(),
                aspectDrift, ICON_SILHOUETTE_TOLERANCE);
        if (Math.abs(aspectDrift) > ICON_SILHOUETTE_TOLERANCE) {
            throw new AssertionError("[GUITEST][MOD-275] the composed product's silhouette is "
                    + composedShape.width() + "x" + composedShape.height() + " px (aspect "
                    + composedShape.aspect() + ") while the same item drawn on its own is "
                    + plainShape.width() + "x" + plainShape.height() + " (aspect " + plainShape.aspect()
                    + ", drift " + aspectDrift + ", tolerance " + ICON_SILHOUETTE_TOLERANCE + ") — the "
                    + "product is not the whole shape. A composite scale cannot do this: it is uniform. "
                    + "What can is the sheet, if the product intersects it — everything behind the "
                    + "paper's plane is occluded and the silhouette comes back cut off. See "
                    + "BlueprintResultItemModel#onTheSheet. Compare " + frame.getFileName() + ".");
        }
        double composed = composedShape.contrast();
        double plain = plainShape.contrast();
        double drift = composed / plain - 1.0;
        LOG.info("[GUITEST][MOD-275] icon lighting gate: top/bottom contrast composed={}, plain={}, "
                + "drift={} (|.| < {})", composed, plain, drift, ICON_SHADING_TOLERANCE);
        if (Math.abs(drift) > ICON_SHADING_TOLERANCE) {
            throw new AssertionError("[GUITEST][MOD-275] a block product composed into a blueprint is "
                    + "shaded with a top-to-side contrast of " + composed + " while the same block as a "
                    + "plain item measures " + plain + " in the same frame (drift " + drift
                    + ", tolerance " + ICON_SHADING_TOLERANCE + ") — the GUI is lighting the whole icon "
                    + "with the wrong rig. ItemStackRenderState.usesBlockLight() answers for layer 0 "
                    + "only, so the paper decides the rig for the product; see BlueprintResultItemModel. "
                    + "Compare " + frame.getFileName() + " with " + emptyFrame.getFileName() + ".");
        }
    }

    /**
     * A drawn shape's silhouette in device pixels plus its top-to-bottom shading contrast.
     *
     * <p>Both numbers describe the same set of pixels and are measured in one pass, because both gates
     * ask about the same shape: is it whole, and is it lit like the item it is a copy of.
     */
    private record IconShape(int width, int height, double contrast) {
        double aspect() {
            return (double) height / width;
        }
    }

    /**
     * Measures the shape a slot draws: its bounding box, and the mean luminance of its top third over
     * that of its bottom half.
     *
     * <p>The shape is whatever differs from the reference: a 16×16 sprite passed as {@code sheet}, or
     * the same slot of another frame passed as {@code background}. Bands are taken as fractions of the
     * shape's own bounding box, so the measurement does not care how large the shape is — which is the
     * whole point, since the two shapes compared here are 10 and 16 texels across.
     */
    private static IconShape measureShape(BufferedImage shot, int[] box, int scale,
                                          BufferedImage sheet, BufferedImage background, String label) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int x = (box[0] + tx) * scale + sx;
                        int y = (box[1] + ty) * scale + sy;
                        int expected = sheet != null ? sheet.getRGB(tx, ty) : background.getRGB(x, y);
                        if (!differsFrom(shot, x, y, expected)) {
                            continue;
                        }
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        count++;
                    }
                }
            }
        }
        if (count < 32) {
            throw new AssertionError("[GUITEST][MOD-275] the lighting stand drew " + count + " px of "
                    + label + " — that slot is empty, so the shading comparison would be meaningless.");
        }
        double height = maxY - minY + 1;
        double topEdge = minY + 0.35 * height;
        double bottomEdge = minY + 0.55 * height;
        double topSum = 0.0;
        double bottomSum = 0.0;
        int topCount = 0;
        int bottomCount = 0;
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int x = (box[0] + tx) * scale + sx;
                        int y = (box[1] + ty) * scale + sy;
                        int expected = sheet != null ? sheet.getRGB(tx, ty) : background.getRGB(x, y);
                        if (!differsFrom(shot, x, y, expected)) {
                            continue;
                        }
                        if (y < topEdge) {
                            topSum += luminance(shot.getRGB(x, y));
                            topCount++;
                        } else if (y >= bottomEdge) {
                            bottomSum += luminance(shot.getRGB(x, y));
                            bottomCount++;
                        }
                    }
                }
            }
        }
        if (topCount == 0 || bottomCount == 0 || bottomSum <= 0.0) {
            throw new AssertionError("[GUITEST][MOD-275] could not band " + label + " into a top and a "
                    + "bottom (" + topCount + " / " + bottomCount + " px) — the shape is degenerate.");
        }
        return new IconShape(maxX - minX + 1, maxY - minY + 1,
                (topSum / topCount) / (bottomSum / bottomCount));
    }

    /** Rec. 709 luminance of an ARGB pixel. */
    private static double luminance(int argb) {
        return PixelMath.luminance(argb);
    }

    /** Closes any open screen, puts {@code held} in the selected hotbar slot and shoots the world. */
    private static Path shootBlueprintInHand(ClientGameTestContext context, String name,
                                             ItemStack held) {
        LOG.info("[GUITEST][MOD-275] in hand {} ({})", name, held.getItem());
        context.runOnClient(mc -> {
            mc.setScreenAndShow(null);
            mc.player.getInventory().setSelectedSlot(0);
            mc.player.getInventory().setItem(0, held.copy());
        });
        // Swapping the held item plays the equip animation; wait it out or the frames differ for a
        // reason that has nothing to do with the icon.
        context.waitTicks(20);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * Puts three stacks into the first three player-inventory slots of an open Assembler window and
     * shoots it. The slots are found by their container rather than by index: the Assembler appends its
     * ghost-grid slots after the base menu's groups, so the player rows do not sit at a fixed offset.
     */
    private static Path shootBlueprintIcons(ClientGameTestContext context, String name,
                                            ItemStack left, ItemStack middle, ItemStack right) {
        LOG.info("[GUITEST][MOD-275] icon row {} ({} | {} | {})", name,
                left.getItem(), middle.getItem(), right.getItem());
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(0, 12000, 0, 40);
                menu.injectTestChannel(4, -1);
                menu.injectTestChannel(5, dev.alaindustrial.block.entity.AssemblerBlockEntity
                        .AssemblerStatus.NO_BLUEPRINT.ordinal());
                java.util.List<net.minecraft.world.inventory.Slot> player = menu.slots.stream()
                        .filter(s -> s.container instanceof net.minecraft.world.entity.player.Inventory)
                        .toList();
                if (player.size() < 3) {
                    throw new AssertionError("[GUITEST][MOD-275] the Assembler menu exposes only "
                            + player.size() + " player-inventory slots — the icon stand needs three.");
                }
                player.get(0).set(left.copy());
                player.get(1).set(middle.copy());
                player.get(2).set(right.copy());
                // Remember where the three icons landed, so a gate can say "inside the frame" in pixels.
                // leftPos/topPos are protected in 26.2 — read through the accessor the mod already has.
                var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
                int guiLeft = pos.alaindustrial$getLeftPos();
                int guiTop = pos.alaindustrial$getTopPos();
                iconSlotBoxes = new int[][] {
                        {guiLeft + player.get(0).x, guiTop + player.get(0).y},
                        {guiLeft + player.get(1).x, guiTop + player.get(1).y},
                        {guiLeft + player.get(2).x, guiTop + player.get(2).y},
                };
                iconGuiScaledWidth = mc.getWindow().getGuiScaledWidth();
            }
        });
        context.waitTicks(5);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }
}

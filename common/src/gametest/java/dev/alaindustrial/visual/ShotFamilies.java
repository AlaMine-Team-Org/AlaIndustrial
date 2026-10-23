package dev.alaindustrial.visual;

import java.util.List;

/**
 * What a frame is FOR, derived from its name.
 *
 * <p><b>The hole this closes.</b> MOD-362 gave new frames metadata — the group they belong to, the
 * {@code R-*} rules they serve, and one sentence telling a reviewer what to look at. The frames written
 * before it kept going through a helper that passed {@code null, List.of(), ""}. Counted in a real
 * nightly manifest: <b>148 of 236 frames carried no rule and no caption</b> — 63 % of the suite. In the
 * gallery such a frame is a picture with a grey badge and an empty line under it, and a month later
 * nobody can say whether it is showing a defect or the intended look.
 *
 * <p>Naming them one by one was never an option: most of those 148 are captured inside loops from a
 * variable, so there is no literal to annotate. What they do have is a naming convention that has held
 * for the life of the suite — {@code wmill_*} is the water mill's renderer, {@code con_cable_*} is
 * cable connection geometry, {@code gui_*} is a machine screen. This maps that convention to metadata
 * once, so a frame gets its rule from the family it belongs to rather than from whoever remembered.
 *
 * <p>Minecraft-free on purpose: the mapping is string data, so L1 tests cover it without a client.
 */
public final class ShotFamilies {

    /**
     * The metadata one family of frames carries.
     *
     * @param group  what kind of thing is being photographed
     * @param rules   {@code R-*} ids from {@code docs/testing/RULES.md} the frame serves
     * @param checks  one sentence telling a human reviewer what to look at
     */
    public record Family(ShotGroup group, List<String> rules, String checks) {

        public Family {
            if (group == null) {
                throw new IllegalArgumentException("a family needs a group");
            }
            if (rules == null || rules.isEmpty()) {
                throw new IllegalArgumentException("a family needs at least one R-* rule — a frame that "
                        + "serves no rule is a picture, not a check");
            }
            if (checks == null || checks.isBlank()) {
                throw new IllegalArgumentException("a family needs a reviewer caption");
            }
            rules = List.copyOf(rules);
        }
    }

    /**
     * Prefix → family, longest prefix wins.
     *
     * <p>Order in this list is NOT significance order — {@link #of} picks the longest matching prefix,
     * so {@code gui_solar_panel_} beats {@code gui_} regardless of where either sits here. That is what
     * lets a family be refined later without re-sorting the table.
     */
    private static final List<Object[]> TABLE = List.of(
            new Object[] {"network_overlay_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Network analyzer trace: tubes, joint cubes and sparks follow the scanned cable line")},
            new Object[] {"hall_schematic_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Town hall schematic (MOD-857): in hall_schematic_on every gap of the half-built wall holds the "
                            + "block that belongs there as its own textured model, translucent and slightly blue "
                            + "(stone bricks, glass, oak stairs facing the camera along the top), and in front of the "
                            + "wall a moss carpet, a spruce door of two cells, a chest and a double chest drawn as "
                            + "chests with lids and latches, not as boxes of planks; the real blocks between "
                            + "them unchanged and not hidden; the _off frames are the same view without the ghosts")},
            new Object[] {"root_inspect_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "One and two root segments appear through original soil only while crouching")},
            // ── Screens ──────────────────────────────────────────────────────────────────────
            new Object[] {"gui_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Machine screen: the energy and progress bars match the numbers behind them, "
                            + "nothing is clipped and no label overlaps a slot")},
            new Object[] {"gui_macerator_stats_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Statistics panel (MOD-125): docked left of the GUI with nothing printed over it, "
                            + "every row inside the frame, the close cross centred on its plate, and the "
                            + "no-chip state saying a chip is needed instead of showing an empty box")},
            new Object[] {"gui_stats_modal_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Statistics panel dragged over a screen's own control and clicked there (MOD-693): the "
                            + "panel is drawn above the control with nothing of it printed on top, and the "
                            + "window under it did not react — the Assembler still shows its Work tab")},
            new Object[] {"gui_overlay_input_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "A click on the screen's own control while an overlay is open (MOD-716): the log line "
                            + "[OVERLAY-INPUT] of this frame says whether the screen claimed the click and what "
                            + "changed; the frame shows the control and the overlay in their drawing order — a "
                            + "refactoring of the overlays must reproduce both exactly")},
            new Object[] {"gui_status_", new Family(ShotGroup.GUI, List.of("R-GUI-01", "R-GUI-03"),
                    "Blocking status line under a long locale (MOD-716): the caption names the state the "
                            + "frame is labelled with, in the frame's language; whether it fits, wraps or runs "
                            + "over the frame today is the baseline a status refactoring must reproduce")},
            new Object[] {"gui_status_over_bar_", new Family(ShotGroup.GUI, List.of("R-GUI-01", "R-GUI-03"),
                    "Status row drawn after the energy bar (MOD-716): a label that starts inside the bar "
                            + "column is readable from its first letter — the bar's fill covers none of it")},
            new Object[] {"gui_solar_panel_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Solar panel readout: output and buffer match the light level the scene is set to "
                            + "(day / night / rain / partial shade)")},
            new Object[] {"gui_storage_warehouse_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Warehouse capacity grows with the module count: the slot grid and the capacity line "
                            + "both follow the number of modules attached")},
            new Object[] {"gui_blueprint_icon_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "A recorded blueprint's icon shows its PRODUCT, not a generic blueprint sprite")},
            new Object[] {"gui_assembler_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Assembler: the pattern grid, the recorded blueprint and the progress bar agree with "
                            + "each other and with the tab that is open")},
            new Object[] {"gui_reactor_controller_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Reactor controller (MOD-617): the tab strip sits on the panel's left edge with the "
                            + "open tab merged into the frame, the status chip names the state the frame is "
                            + "labelled with, the heat marks and their numbers do not collide, no row runs "
                            + "into its neighbour, and the advice box says what to do without clipping")},
            new Object[] {"gui_settlement_core_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Settlement core (MOD-831, MOD-842): the energy bar on the left, the readout beside it "
                            + "(power state, radius, houses, workers, upkeep, owner; no tier row) inside the "
                            + "frame, no inventory rows, an unpowered unowned core saying in red that it has no "
                            + "power, the show-borders eye under the bar (MOD-843), and below it, across the full "
                            + "width, how to become the owner, wrapped rather than running past the right border")},
            new Object[] {"gui_settlement_core_roster", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Settlement core, «Shift workers» tab (MOD-854): the three tabs down the panel's left edge with "
                            + "the second one merged into the frame, the tab's name as the heading, one row per worker "
                            + "- number, name, level, status - each column inside its width, no energy bar")},
            new Object[] {"gui_settlement_core_works", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Settlement core, «Construction» tab (MOD-857): the four tabs down the panel's left edge with the "
                            + "fourth one merged into the frame, the tab's name as the heading, a heading over the "
                            + "buildings and over the queue, each row's name, state and percent inside their columns, "
                            + "a blocked job in red with its reason on the line below, the owner's small controls at "
                            + "the right edge inside the frame, no energy bar")},
            new Object[] {"gui_settlement_core_works_sheets", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Settlement core, «Construction» tab, plan sheets (MOD-859): a heading over the three sheets of "
                            + "the town hall plan, each row naming where to look, a found sheet in green and a missing "
                            + "one in red with its status word at the right inside the frame, no control on a sheet "
                            + "row, no energy bar")},
            new Object[] {"gui_shift_worker_card_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Shift worker card (MOD-854): the dark header with the number and name, the three tab buttons "
                            + "and close at the right, the open tab greyed; the worker in its frame on the left with "
                            + "the battery bar, charge and health under it; on the right the open tab's modules, every "
                            + "row inside its card, a «+» only on a working stat's row, the two inactive stats grey "
                            + "with their caption; a dead record grey under its stamp with no buttons")},
            new Object[] {"gui_reception_capsule_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Reception capsule (MOD-833, MOD-844): a tall window; a 3D shift-worker head beside the "
                            + "workers and EU rows, a full-width progress bar, then one row per material - its slot "
                            + "with a faded hint, the material's name (wrapped, never overlapping) and an 'in slot / "
                            + "needed' count right-aligned in red - with the energy bar beside the rows, the "
                            + "player's inventory below, and a bare capsule with no core saying in red on its status "
                            + "row why it waits, the text inside the frame")},
            new Object[] {"gui_town_dispatcher_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Town dispatcher (MOD-858, owner's rounds 1 and 2): a 256 wide window, the side tab strip "
                            + "down the left edge; the title and the status row on lines of their own, never "
                            + "overlapping; on «Task» the plan row (three sheet slots with faded hints, the vanilla "
                            + "«assemble» button, the plan slot, «Start» drawn disabled while anything is missing) "
                            + "and, in a framed card above the inventory, the four-step checklist with a tick or a "
                            + "cross and a wrapped hint each, clipped to the card; on «Buffer» the 27 slots; on "
                            + "«Materials» item icons with «have/need»; on «Journal» wrapped lines; all text inside "
                            + "the frame; the player's inventory below and no light line under the hotbar")},
            new Object[] {"gui_skill_tree_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Skill wheel (MOD-483): the four branch captions stay pinned in their corners with "
                            + "their icons, no label crosses a node or the profile column, the board is "
                            + "clipped to its own panel at every zoom, and the hub reads as the centre")},
            new Object[] {"gui_guide_book_record", new Family(ShotGroup.GUI, List.of("R-GUI-01", "R-GUI-03"),
                    "Guide book welcome card (MOD-513): the ALA INDUSTRIAL GUIDE heading, then "
                            + "'Record detected:' followed by a letter, a hyphen and three digits — the record the "
                            + "server sent this player, never the {record} token or the '...' pending mark — "
                            + "then the four remaining archive lines, each on its own, nothing clipped at the "
                            + "panel edge")},

            // ── Block faces and world views ──────────────────────────────────────────────────
            new Object[] {"world_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "World view: every block in frame renders with its own textures on the right faces")},
            new Object[] {"world_blueprint_in_hand_", new Family(ShotGroup.ITEM, List.of("R-VIS-05"),
                    "Blueprint held in hand: the item model reflects what is recorded on it")},
            new Object[] {"gen_face_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Generator faces: front / back / sides / top are each their own texture and none is "
                            + "mirrored or rotated wrongly")},
            new Object[] {"solar_face_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Solar panel faces: the cell pattern is on top, the casing on the sides")},
            new Object[] {"panel_neighbour", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Panels standing next to each other: no seam artefact and no z-fighting where two "
                            + "of them meet")},
            new Object[] {"vis_", new Family(ShotGroup.WORLD, List.of("R-VIS-01"),
                    "Idle vs active: the working block visibly differs from the same block at rest")},
            new Object[] {"con_cable_", new Family(ShotGroup.WORLD, List.of("R-CON-03", "R-VIS-12"),
                    "Cable connection model: arms point at the neighbours that are really there, and the "
                            + "multipart joins have no gaps")},
            new Object[] {"storage_", new Family(ShotGroup.WORLD, List.of("R-VIS-04"),
                    "Storage modules side by side: the seam between neighbours renders as one continuous "
                            + "wall, without a doubled or missing edge")},

            // ── Block-entity renderers ──────────────────────────────────────────────────────
            new Object[] {"shift_worker_", new Family(ShotGroup.WORLD, List.of("R-VIS-13"),
                    "Shift workers (MOD-832), left to right open M1, window F1, open M3, window F4: from the"
                            + " front the classic and slim bodies, the faces readable (M3 and F4 too), the window's"
                            + " glass over the face; from behind the balloon with the number in two lines readable,"
                            + " the curls above the plate and not over it, no z-fighting on the back; _empty is the"
                            + " same view without them")},
            new Object[] {"capsule_", new Family(ShotGroup.BER, List.of("R-VIS-04"),
                    "Teleporter capsule: the glass of the door and of the fixed facets is drawn, seen from "
                            + "inside looking ahead and looking down, and from outside")},
            new Object[] {"capsule_water_", new Family(ShotGroup.BER, List.of("R-VIS-04"),
                    "Water behind the shut capsule door (MOD-777): in capsule_water_behind_door the pool ahead "
                            + "shows a water surface through the door glass; capsule_water_drained is the same "
                            + "view of the empty black basin, and the two must differ across the pool")},
            new Object[] {"wmill_",new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Water mill wheel: the renderer draws the wheel, it sits in the housing and it is "
                            + "not clipped by the block's own model")},
            new Object[] {"windmill_", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Wind mill rotor: the blades are drawn, centred on the hub and do not clip the mast")},
            new Object[] {"incubator_", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Incubator dome: the glass dome and its contents are drawn above the base, not "
                            + "floating or sunk into it")},
            new Object[] {"ber_quads_", new Family(ShotGroup.BER, List.of("R-VIS-04"),
                    "Hand-built block-entity quads (MOD-716): the water surface inside the tank, the "
                            + "airlock panel closed and open, the glass stand under the bare cable and the "
                            + "stock display frame with its ingot — every face present, shaded as in the "
                            + "previous run, none inside out or missing")},
            new Object[] {"condenser_crystal", new Family(ShotGroup.BER, List.of("R-VIS-01"),
                    "Energy condenser crystal: the crystal is drawn once a clot is banked and is "
                            + "absent below tier I — its presence IS the readout that an item is "
                            + "waiting in the slot")},
            new Object[] {"centrifuge_rotor", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Thermal centrifuge rotor: the basket is drawn inside the housing and moves between "
                            + "the two spinning frames, while the two stopped frames are identical; the "
                            + "dark spindle and its green core show between the vanes, and nothing pokes "
                            + "through the frame. The _side frame is the same rotor through the left "
                            + "window — front and side must frame it alike")},
            new Object[] {"chest_crumbling", new Family(ShotGroup.BER, List.of("R-VIS-04"),
                    "Break cracks on a renderer-only block (MOD-703): the iron chest in chest_crumbling is "
                            + "covered by the stage-9 crack pattern on its lid and front, following the lid's "
                            + "shape; the two _clean frames show the same chest with no cracks at all")},

            // ── Items ───────────────────────────────────────────────────────────────────────
            new Object[] {"item_", new Family(ShotGroup.ITEM, List.of("R-VIS-05"),
                    "Item model: the same item across the render transforms a player sees it in")},
            new Object[] {"worn_", new Family(ShotGroup.ITEM, List.of("R-VIS-05"),
                    "Worn on the body: the armour model sits on the player, front and back")},
            new Object[] {"hud_", new Family(ShotGroup.ITEM, List.of("R-GUI-03"),
                    "Hotbar charge HUD: the bar under the item matches its stored EU and does not drift "
                            + "off the icon")},
            new Object[] {"hud_stack_", new Family(ShotGroup.ITEM, List.of("R-GUI-03"),
                    "HUD layer stack (MOD-706): the teleport fade darkens the world evenly and the Energy "
                            + "Pack readout, registered after it, stays legible on top; the vanilla hotbar "
                            + "and hearts still draw")},

            // ── Advancements ────────────────────────────────────────────────────────────────
            new Object[] {"adv_", new Family(ShotGroup.ADVANCEMENT, List.of("R-ADV-03"),
                    "Advancement tree: the tab renders, the parent chain runs from simple to advanced "
                            + "and every icon resolves")},

            // ── Locale sweep ────────────────────────────────────────────────────────────────
            new Object[] {"locale_", new Family(ShotGroup.LOCALE, List.of("R-GUI-01"),
                    "The same screen under a long locale: labels are not clipped and do not spill "
                            + "outside the window frame")});

    private ShotFamilies() {
    }

    /**
     * The family a frame name belongs to, or {@code null} when the name matches nothing.
     *
     * <p>Longest prefix wins, so a family can be refined ({@code gui_} → {@code gui_assembler_})
     * without touching the entries around it.
     *
     * <p>Returning {@code null} rather than a bland default is the point: the caller turns it into a
     * failure that names the frame. A default would quietly restore exactly the state this class
     * exists to end — frames with no rule, indistinguishable from frames that have one.
     */
    public static Family of(String frameName) {
        if (frameName == null) {
            return null;
        }
        Family best = null;
        int bestLength = -1;
        for (Object[] row : TABLE) {
            String prefix = (String) row[0];
            if (frameName.startsWith(prefix) && prefix.length() > bestLength) {
                best = (Family) row[1];
                bestLength = prefix.length();
            }
        }
        return best;
    }

    /** Every prefix in the table, for a failure message that can list what IS known. */
    public static List<String> knownPrefixes() {
        return TABLE.stream().map(row -> (String) row[0]).sorted().toList();
    }
}

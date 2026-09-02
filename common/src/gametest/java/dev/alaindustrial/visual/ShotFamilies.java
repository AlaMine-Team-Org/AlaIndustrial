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
            // ── Screens ──────────────────────────────────────────────────────────────────────
            new Object[] {"gui_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Machine screen: the energy and progress bars match the numbers behind them, "
                            + "nothing is clipped and no label overlaps a slot")},
            new Object[] {"gui_macerator_stats_", new Family(ShotGroup.GUI, List.of("R-GUI-03"),
                    "Statistics panel (MOD-125): docked left of the GUI with nothing printed over it, "
                            + "every row inside the frame, the close cross centred on its plate, and the "
                            + "no-chip state saying a chip is needed instead of showing an empty box")},
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
            new Object[] {"wmill_", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Water mill wheel: the renderer draws the wheel, it sits in the housing and it is "
                            + "not clipped by the block's own model")},
            new Object[] {"windmill_", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Wind mill rotor: the blades are drawn, centred on the hub and do not clip the mast")},
            new Object[] {"incubator_", new Family(ShotGroup.BER, List.of("R-VIS-01", "R-VIS-04"),
                    "Incubator dome: the glass dome and its contents are drawn above the base, not "
                            + "floating or sunk into it")},
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

            // ── Items ───────────────────────────────────────────────────────────────────────
            new Object[] {"item_", new Family(ShotGroup.ITEM, List.of("R-VIS-05"),
                    "Item model: the same item across the render transforms a player sees it in")},
            new Object[] {"worn_", new Family(ShotGroup.ITEM, List.of("R-VIS-05"),
                    "Worn on the body: the armour model sits on the player, front and back")},
            new Object[] {"hud_", new Family(ShotGroup.ITEM, List.of("R-GUI-03"),
                    "Hotbar charge HUD: the bar under the item matches its stored EU and does not drift "
                            + "off the icon")},

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

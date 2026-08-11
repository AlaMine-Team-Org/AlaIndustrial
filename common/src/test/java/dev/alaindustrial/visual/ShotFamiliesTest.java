package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Frame names taken from a real nightly manifest, not invented for the test.
 *
 * <p>The list below is the set of prefixes that carried NO rule in the 236-frame run of 2026-08-11 —
 * the exact gap this table exists to close. Testing against made-up names would prove the lookup
 * works and say nothing about whether it covers the suite.
 */
class ShotFamiliesTest {

    /** One real frame name per family that used to go unannotated. */
    private static final List<String> REAL_UNANNOTATED_FRAMES = List.of(
            "adv_alaindustrial_root",
            "con_cable_east_arm",
            "gen_face_front",
            "gui_compressor_idle",
            "gui_solar_panel_day_25pct",
            "gui_storage_warehouse_3_modules",
            "gui_assembler_blueprint_preview",
            "gui_blueprint_icon_gate_sticks",
            "hud_fixed_item_icons",
            "incubator_ber_item",
            "panel_neighbour_top",
            "solar_face_iso",
            "storage_seams_row_4_iso",
            "vis_furnace_active",
            "windmill_t2_storm_norotor_a",
            "wmill_ber_wheel",
            "world_cables",
            "world_blueprint_in_hand_sticks",
            "worn_energy_pack_back");

    @Test
    @DisplayName("every frame family that shipped without a rule now resolves to one")
    void everyRealFrameResolves() {
        for (String name : REAL_UNANNOTATED_FRAMES) {
            ShotFamilies.Family family = ShotFamilies.of(name);
            assertNotNull(family, "no family for the real frame '" + name + "' — it would capture with "
                    + "no rule and no caption, which is the state this table exists to end");
            assertFalse(family.rules().isEmpty(), name + " resolved to a family with no rule");
            assertFalse(family.checks().isBlank(), name + " resolved to a family with no caption");
        }
    }

    @Test
    @DisplayName("an unknown name resolves to nothing rather than to a bland default")
    void unknownNameHasNoFamily() {
        // Negative control for the test above: if `of` returned a catch-all, that test would pass for
        // any input at all and would be measuring nothing.
        assertNull(ShotFamilies.of("totally_new_kind_of_frame"));
        assertNull(ShotFamilies.of(""));
        assertNull(ShotFamilies.of(null));
    }

    @Test
    @DisplayName("the longest matching prefix wins, so a family can be refined")
    void longestPrefixWins() {
        ShotFamilies.Family generic = ShotFamilies.of("gui_compressor_idle");
        ShotFamilies.Family specific = ShotFamilies.of("gui_storage_warehouse_2_modules");
        assertNotNull(generic);
        assertNotNull(specific);
        assertFalse(generic.checks().equals(specific.checks()),
                "gui_storage_warehouse_ must beat gui_ — otherwise refining a family does nothing");
    }

    @Test
    @DisplayName("a screen frame is not filed under the world group and vice versa")
    void groupsAreNotAllTheSame() {
        assertEquals(ShotGroup.GUI, ShotFamilies.of("gui_macerator_empty").group());
        assertEquals(ShotGroup.WORLD, ShotFamilies.of("con_cable_corner").group());
        assertEquals(ShotGroup.BER, ShotFamilies.of("wmill_ber_wheel").group());
        assertEquals(ShotGroup.ITEM, ShotFamilies.of("worn_energy_pack_front").group());
        assertEquals(ShotGroup.ADVANCEMENT, ShotFamilies.of("adv_vanilla_story").group());
    }

    @Test
    @DisplayName("an item held in hand is an item frame even though its name starts with world_")
    void heldItemIsAnItemFrame() {
        // world_blueprint_in_hand_* photographs the ITEM model, not the world around it. Filing it
        // under WORLD would put it in the gallery group where nobody looks for item regressions.
        assertEquals(ShotGroup.ITEM, ShotFamilies.of("world_blueprint_in_hand_planks").group());
        assertEquals(ShotGroup.WORLD, ShotFamilies.of("world_blocks").group());
    }

    @Test
    @DisplayName("a family cannot be declared without a rule or a caption")
    void familyRefusesToBeEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFamilies.Family(ShotGroup.GUI, List.of(), "something"));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFamilies.Family(ShotGroup.GUI, List.of("R-GUI-03"), "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFamilies.Family(null, List.of("R-GUI-03"), "something"));
    }

    @Test
    @DisplayName("every rule named in the table looks like a rule id")
    void rulesAreWellFormed() {
        for (String prefix : ShotFamilies.knownPrefixes()) {
            ShotFamilies.Family family = ShotFamilies.of(prefix + "x");
            assertNotNull(family, "prefix '" + prefix + "' does not resolve through of()");
            for (String rule : family.rules()) {
                assertTrue(rule.matches("R-[A-Z]{3}-\\d{2}"),
                        "'" + rule + "' (family " + prefix + ") is not an R-XXX-NN id, so the gallery "
                                + "link to RULES.md would go nowhere");
            }
        }
    }
}

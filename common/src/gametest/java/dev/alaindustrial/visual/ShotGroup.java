package dev.alaindustrial.visual;

/**
 * What a frame photographs. Drives grouping in the review gallery and lets a run be filtered down to
 * the part someone is actually working on.
 */
public enum ShotGroup {

    /** A machine/chest screen. */
    GUI("gui"),
    /** Blocks in the world: faces, active/idle textures, cable connections. */
    WORLD("world"),
    /** A BlockEntityRenderer's own geometry (mill wheels, rotors, the incubator dome). */
    BER("ber"),
    /** The advancement tree and its tab icons. */
    ADVANCEMENT("advancement"),
    /** Items rendered in hand, on the ground, worn, or as an inventory icon. */
    ITEM("item"),
    /** The same screen captured under a non-English locale, where text is longest. */
    LOCALE("locale");

    private final String id;

    ShotGroup(String id) {
        this.id = id;
    }

    /** Lower-case name used in file names, the manifest and the gallery. */
    public String id() {
        return id;
    }
}

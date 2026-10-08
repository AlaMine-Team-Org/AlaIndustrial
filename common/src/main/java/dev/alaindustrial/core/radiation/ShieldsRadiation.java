package dev.alaindustrial.core.radiation;

/**
 * A container ENTITY that stops the radiation of what it carries (MOD-786) — the vehicle counterpart of
 * the shielding chest block and the shielding pouch.
 *
 * <p>Keyed on the entity's own TYPE, like its two siblings, never on a tag: no datapack can hand
 * shielding to another vehicle, and no future boat inherits it by accident. The interface lives in the
 * core so that {@link RadiationVehicles} asks a question of the core rather than naming an entity class
 * (ADR-039).
 */
public interface ShieldsRadiation {

	/** Whether this vehicle's contents are invisible to the radiation field. */
	boolean shieldsRadiation();
}

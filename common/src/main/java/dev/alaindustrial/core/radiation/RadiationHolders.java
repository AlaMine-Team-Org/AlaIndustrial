package dev.alaindustrial.core.radiation;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;

/**
 * Items HELD by an entity (MOD-788): a stack in an item frame, in the hands or armour slots of an armour
 * stand or a mob, and the pockets of a villager, a wandering trader, an allay or a piglin.
 *
 * <p><b>Why this exists.</b> Until MOD-788 the field saw an ingot lying on the floor and stopped seeing it
 * the moment a zombie picked it up: the stack left {@code collectGround} and entered no other collector.
 * A frame on the wall, an armour stand and any mob that grabbed loot were free shielding.
 *
 * <p><b>Two rules, decided by the owner (2026-10-09).</b> What is ON VIEW — the frame's stack, every
 * equipment slot of any living entity — radiates with no ceiling, exactly as the same stack on the floor:
 * otherwise picking a stack up or framing it would lower the hazard again. What is in an INVENTORY
 * ({@link InventoryCarrier}) leaks as a chest does, through {@link RadiationCore#containerLeak}. Both are
 * summed into the one source the entity is.
 *
 * <p><b>The player is not a holder here.</b> Its inventory, both hands and the four armour slots are its
 * carried dose ({@code RadiationSources.carried}, read by {@code RadiationTicker.carriedDose}, and its own
 * {@code carriedSources} for the mobs around it); counting the player here as well would count every
 * carried ingot twice. A player's load doses the player and the mobs near it, not another player.
 *
 * <p><b>Consequences, also decided.</b> A mob holding uranium irradiates itself (its own source sits less
 * than a block from its eyes, as a player's pockets do). A shielding suit worn by the holder stops nothing
 * of what it carries — it protects the wearer from a field, it does not wrap the wearer's load.
 *
 * <p><b>Not covered:</b> an {@code ItemDisplay} entity. Its stack getter is private, so reading it needs an
 * accessor mixin; displays are placed by commands only, and the case is left out on purpose.
 *
 * <p>Asked from the same single entity query as the vehicles ({@link RadiationVehicles#collectVehicles}):
 * no second walk over the entities in the box.
 */
public final class RadiationHolders {

	private RadiationHolders() {
	}

	/** An entity that can hold a radioactive stack: an item frame, or anything alive that is not a player. */
	static boolean holds(Entity entity) {
		return entity instanceof ItemFrame || (entity instanceof LivingEntity && !(entity instanceof Player));
	}

	/** Raw dose of what the entity holds: on view uncapped, the inventory capped like a chest. */
	static int heldStrength(Entity entity) {
		if (entity instanceof ItemFrame frame) {
			return RadiationSources.strengthOf(frame.getItem());
		}
		if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
			return 0;
		}
		int strength = 0;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			strength += RadiationSources.strengthOf(living.getItemBySlot(slot));
		}
		if (entity instanceof InventoryCarrier carrier) {
			// A plain SimpleContainer: no loot table behind it, getItem reads and unpacks nothing (ADR-010).
			SimpleContainer pockets = carrier.getInventory();
			int inside = 0;
			for (int slot = 0; slot < pockets.getContainerSize(); slot++) {
				inside += RadiationSources.strengthOf(pockets.getItem(slot));
			}
			strength += RadiationCore.containerLeak(inside, RadiationConfig.radiationContainerMaxItems,
					RadiationConfig.radiationDoseHighPerItem);
		}
		return strength;
	}
}

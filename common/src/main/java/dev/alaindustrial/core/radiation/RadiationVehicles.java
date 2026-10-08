package dev.alaindustrial.core.radiation;

import dev.alaindustrial.loot.PendingLoot;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Containers that MOVE (MOD-786): chest boats and rafts, chest and hopper minecarts — vanilla, the mod's
 * own boats and any other mod's {@link ContainerEntity} — and donkeys, mules and llamas carrying a chest.
 *
 * <p><b>Why this exists.</b> Until MOD-786 the field saw chest BLOCKS, loose items and carried stacks,
 * and no entity container at all: uranium put in a chest boat or a chest minecart irradiated nobody,
 * which made any vehicle a free, better shielding chest. Each one now radiates exactly as a chest block
 * with the same contents does — the same {@link RadiationCore#containerLeak} ceiling, the same reach.
 * The one exception is a vehicle that says it shields ({@link ShieldsRadiation}): the mod's boat with
 * the shielding chest.
 *
 * <p><b>Reading without opening.</b> A vehicle container's {@code getItem} unpacks a pending loot table
 * with no player in context — the MOD-524 trap again, and a chest minecart in a mineshaft is the very
 * case. So a pending vehicle is skipped ({@link PendingLoot#isPending(ContainerEntity)}) and the
 * contents are read from {@code getItemStacks()}, the plain list. A pack animal has no loot table; its
 * chest is read through {@code getSlot}, which touches nothing.
 *
 * <p><b>One query by predicate, not two by class.</b> {@code getEntitiesOfClass} cannot take an
 * interface, and asking for the two vanilla base classes would miss every other mod's container. The
 * price is that the query walks EVERY entity in a box the size of the reach and type-checks it, where
 * {@code collectGround}'s box of the same size reads only the {@code ItemEntity} bucket of each section.
 *
 * <p><b>Cost.</b> One query per {@code exposureAt}/{@code detectedAt}, which runs once a second per
 * player, per player with a counter, and per convertible mob near a player ({@code RadiationMobs}). Measured
 * (MOD-786 research.md): ~5–11 µs per query with 30 entities in the box, plus one line-of-sight trace per
 * radiating vehicle. A packed mob farm multiplies both — every cow in it is an observer that queries the
 * same crowd. If that ever shows in a profile, collect the vehicles once per sweep over the union of the
 * observers' boxes and keep only the per-observer distance cut here, instead of querying once per observer.
 */
public final class RadiationVehicles {

	private RadiationVehicles() {
	}

	/** Moving containers within the container reach — see {@link RadiationSources#exposureAt}. */
	public static void collectVehicles(ServerLevel level, Vec3 centre, int radius, int groundReach,
			List<RadiationSources.Source> out) {
		if (groundReach <= 0 || RadiationConfig.radiationContainerMaxItems <= 0) {
			return;
		}
		double reach = Math.min(radius, groundReach);
		AABB box = AABB.ofSize(centre, reach * 2, reach * 2, reach * 2);
		for (Entity entity : level.getEntities((Entity) null, box, RadiationVehicles::isExposedCarrier)) {
			// The centre of the box, as for a loose item: an entity's position is the bottom of its box,
			// and a trace to it clips the floor the vehicle stands on.
			Vec3 at = entity.getBoundingBox().getCenter();
			if (at.distanceTo(centre) > reach) {
				continue;
			}
			int strength = RadiationCore.containerLeak(contentsStrength(entity),
					RadiationConfig.radiationContainerMaxItems, RadiationConfig.radiationDoseHighPerItem);
			if (strength > 0) {
				out.add(new RadiationSources.Source(at, strength));
			}
		}
	}

	/** An entity whose cargo radiates: an unshielded container with no pending loot, or a pack chest. */
	private static boolean isExposedCarrier(Entity entity) {
		if (entity instanceof ContainerEntity container) {
			return !(entity instanceof ShieldsRadiation shield && shield.shieldsRadiation())
					&& !PendingLoot.isPending(container);
		}
		return entity instanceof AbstractChestedHorse horse && horse.hasChest();
	}

	/** Raw dose of everything the carrier holds — never through {@code getItem}, see the class doc. */
	private static int contentsStrength(Entity entity) {
		int strength = 0;
		if (entity instanceof ContainerEntity container) {
			for (ItemStack stack : container.getItemStacks()) {
				strength += RadiationSources.strengthOf(stack);
			}
		} else if (entity instanceof AbstractChestedHorse horse) {
			for (int slot = 0; slot < horse.getInventorySize(); slot++) {
				// getInventorySize() is computed from the columns NOW, the inventory was sized when it was last
				// built; if another mod changes the columns on the fly the two disagree, and an index past the
				// real inventory falls through to Entity.getSlot, which returns null. Skip it, never crash the tick.
				SlotAccess access = horse.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET + slot);
				if (access != null) {
					strength += RadiationSources.strengthOf(access.get());
				}
			}
		}
		return strength;
	}
}

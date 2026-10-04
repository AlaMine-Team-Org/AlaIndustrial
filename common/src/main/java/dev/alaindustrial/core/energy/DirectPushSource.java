package dev.alaindustrial.core.energy;

import net.minecraft.core.Direction;

/**
 * An energy block that pushes straight into its neighbours, with no cable between (MOD-715, CORE-3): what
 * {@link DirectAdjacencyDistributor} needs of the source — its buffer, its tier, the role of each face and
 * its storage class. Declared here so the push names no block-entity class.
 */
public interface DirectPushSource extends StorageEndpoint {

	/** The source's own buffer. */
	EnergyBuffer getEnergyStorage();

	/** The tier whose voltage caps one push. */
	EnergyTier getTier();

	/** The role of the face pointing in {@code worldFace}; a face that cannot extract pushes nothing. */
	EnergyRole energyRoleForFace(Direction worldFace);
}

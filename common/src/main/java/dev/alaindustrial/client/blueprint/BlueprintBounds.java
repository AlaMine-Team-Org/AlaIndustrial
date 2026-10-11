package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;

/**
 * The box that holds every block of a blueprint across all its steps (MOD-797). The camera turns around
 * its centre and the picture is scaled to its size once, so the view does not jump between steps.
 *
 * @param centerX centre of the box, in blocks
 * @param radius  half of the box's longest diagonal, in blocks — the size the picture must fit
 */
public record BlueprintBounds(double centerX, double centerY, double centerZ, double radius) {

	/** The bounds of every block on every step of {@code def}. */
	public static BlueprintBounds of(BlueprintDef def) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Step step : def.steps()) {
			for (Placement p : step.blocks()) {
				minX = Math.min(minX, p.x());
				minY = Math.min(minY, p.y());
				minZ = Math.min(minZ, p.z());
				maxX = Math.max(maxX, p.x() + 1);
				maxY = Math.max(maxY, p.y() + 1);
				maxZ = Math.max(maxZ, p.z() + 1);
			}
		}
		double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
		return new BlueprintBounds((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
				Math.sqrt(dx * dx + dy * dy + dz * dz) / 2);
	}
}

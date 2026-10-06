package dev.alaindustrial.block;

import dev.alaindustrial.core.environment.MobWheelLayout;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What an invisible cell of an assembled mob wheel (MOD-763, D1) does to whatever walks into it: a solid cell
 * is a wall of the wheel, the floor cell is the running deck the mob stands on, and the open cell above the
 * gate is the doorway's head room. The shapes are built once here, never inside {@code getShape} (ADR-023).
 */
public enum MobWheelCellShape implements StringRepresentable {
	/** Rim, frame infill or the wheel's top: a full block. */
	SOLID("solid", Shapes.block()),
	/**
	 * The running deck under the mob: its top is the drawn wheel's running surface
	 * ({@link MobWheelLayout#ROTOR_FLOOR_PX}), so a mob walking in or let out stands on the planks.
	 */
	FLOOR("floor", Block.box(0.0, 0.0, 0.0, 16.0, MobWheelLayout.ROTOR_FLOOR_PX, 16.0)),
	/** The doorway above the gate and the head room above the deck: nothing to bump into. */
	OPEN("open", Shapes.empty());

	private final String serializedName;
	private final VoxelShape shape;

	MobWheelCellShape(String serializedName, VoxelShape shape) {
		this.serializedName = serializedName;
		this.shape = shape;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** Collision and outline of a cell of this kind. */
	public VoxelShape shape() {
		return shape;
	}
}

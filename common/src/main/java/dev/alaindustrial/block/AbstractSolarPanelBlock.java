package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared base for the three solar panels ({@link SolarPanelBlock},
 * {@link DaylightSolarPanelBlock}, {@link MoonlitSolarPanelBlock}). They differ only in their
 * block entity + the {@code SolarSky} predicate that drives production, but share the half-slab
 * {@code SHAPE}, the ambient hum (loop + volume) and — crucially for MOD-071 — the cable-connection
 * contract: the {@code UP} face is the daylight/moonlight working surface and is energy-inert (see
 * {@code SolarPanelBlockEntity#energyRoleForFace}: {@code worldFace == UP ? NONE : OUT}), so a cable
 * must not draw a "power flows here" arm toward it. EU is emitted from the other five faces only.
 *
 * <p>Sibling to the MOD-061 {@code FACING}-inert default in {@link HorizontalMachineBlock}, but the
 * panels are not {@code HorizontalMachine}s (they have no {@code FACING} property — they are
 * rotation-symmetric), so they cannot ride the MOD-061 default and need their own per-face override.
 * Lifting the override here (not repeating it in each subclass) mirrors the precedent of
 * {@code HorizontalMachineBlock}/{@code LitMachineBlock}: groups of blocks with a shared energy-face
 * contract declare it once at their shared base.
 *
 * <p>Concrete subclasses supply their codec + block-entity factory + a Pattern-C {@link #isWorking}
 * predicate (lit-less: derived from side-agnostic sky access).
 */
public abstract class AbstractSolarPanelBlock extends AbstractMachineBlock implements MachineHumProvider {

	/** Half-block (8px) collision/outline matching the slab model — keeps occlusion off and shape in sync. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

	protected AbstractSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	// --- Ambient hum (pattern C: lit-less ambient loop, silent when the panel stops producing) ---

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.SOLAR_PANEL_HUM;
	}

	@Override
	public float humVolume() {
		// Quieter than the generator (0.4): solar farms stack many panels, so each is tuned lower.
		return 0.28f;
	}

	/**
	 * Restricts the cable arm to the five non-{@code UP} faces, matching the single-energy-role
	 * contract of {@code SolarPanelBlockEntity#energyRoleForFace}: {@code UP} is the daylight/moonlight
	 * capture surface and emits no EU ({@code EnergyRole.NONE}); the other five faces are generator
	 * outputs. A cable on {@code UP} would otherwise draw a misleading "energy goes here" arm toward a
	 * face through which no EU can ever flow — the same shape as the MOD-061 {@code FACING}-inert
	 * machines and the MOD-038 iron-chest case, here applied per-face to a block that carries no
	 * {@code FACING}. Decided from the fixed direction {@code UP} alone (no blockstate needed) so it is
	 * correct the instant the block is placed, with no block-entity load race.
	 *
	 * <p>MOD-071: lifted from per-block overrides into this shared base — the contract is identical for
	 * all three panels (T1 + the two evolved variants).
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side != Direction.UP; // top is the daylight/moonlit working face — energy-inert
	}
}

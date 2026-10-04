package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.item.ItemPipeNode;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.core.item.PipeTier;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Persistent per-face configuration and lifecycle hook for a passive item-pipe segment.
 *
 * <p>Transport, not a machine (MOD-400): no inventory of its own, no processing progress, no upgrade
 * panel and no owner — the pipe is bufferless and every item it moves is atomically handed from one
 * container to the next by {@link ItemNetworkManager}.
 */
public final class ItemPipeBlockEntity extends EnergyBlockEntity implements ItemPipeNode {
	private int packedFaceModes;
	private boolean registered;
	/** Whether the once-per-load face re-derive has run — see {@link #validateShapeOnce}. */
	private boolean shapeValidated;

	public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ITEM_PIPE_BE.get(), pos, state, EnergyTier.LV, 0, 0, 0);
	}

	/**
	 * Not an energy block (MOD-691): no face is a port. The zero-capacity buffer is scaffolding inherited
	 * with the tick and persistence of {@link EnergyBlockEntity}; left on the default {@code BOTH} role it
	 * made every face a live port, and NeoForge's lookup, which asks {@code energyPort()} directly, turned a
	 * pipe pressed against a cable into a producer and a consumer of that network — one that then never
	 * slept. {@code NONE} makes {@link #energyPort} return {@code null} on both loaders, as the sprinkler's does.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.NONE;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		validateShapeOnce(level, pos);
		return 0;
	}

	/**
	 * Re-derive the six drawn faces once per load (MOD-540, the pattern MOD-061 established for
	 * cables). The blockstate stores what each face draws, including whether its arm drops toward a
	 * half-block neighbour — but the paths that keep it current, {@code getStateForPlacement} and
	 * {@code updateShape}, run on placement and on a neighbour change, never on chunk load. A pipe
	 * laid before this feature existed, or one whose neighbour changed height while its chunk was
	 * unloaded, would therefore keep a stale sleeve for ever. One pass on the first server tick costs
	 * a handful of neighbour reads per segment per load and makes the saved geometry self-correcting.
	 */
	private void validateShapeOnce(Level level, BlockPos pos) {
		if (shapeValidated) {
			return;
		}
		shapeValidated = true;
		ItemPipeBlock.refreshConnections(level, pos);
	}

	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			ItemNetworkManager.register(this);
			registered = true;
		}
	}

	@Override
	public PipeFaceMode faceMode(Direction direction) {
		return PipeFaceMode.values()[(packedFaceModes >>> (direction.ordinal() * 2)) & 3];
	}

	/**
	 * The grade of the block this segment stands in, or {@code null} when that block is not an item pipe
	 * ({@link ItemPipeNode}, MOD-715).
	 */
	@Override
	public PipeTier tier() {
		return getBlockState().getBlock() instanceof ItemPipeBlock pipe ? pipe.tier() : null;
	}

	/** {@link ItemPipeBlock#shouldConnectTo} for this segment's own position ({@link ItemPipeNode}). */
	@Override
	public boolean connects(Direction side) {
		return ItemPipeBlock.shouldConnectTo(level, worldPosition, side);
	}

	/** Advance this face one step along the wrench ladder (MOD-108: neutral → extract → insert → disabled). */
	public void cycleFaceMode(Direction direction) {
		setFaceMode(direction, faceMode(direction).nextInCycle());
	}

	public void setFaceMode(Direction direction, PipeFaceMode mode) {
		int shift = direction.ordinal() * 2;
		int next = (packedFaceModes & ~(3 << shift)) | (mode.ordinal() << shift);
		if (next == packedFaceModes) return;
		packedFaceModes = next;
		setChangedQuietly();
		// Face modes drive a client-only terminal renderer. Persisting alone does not send a BE data
		// packet, so push the update before the client recomputes the static connection model.
		syncBlockEntityToClient();
		if (level instanceof ServerLevel server) {
			ItemNetworkManager.topologyChanged(server, worldPosition);
			ItemPipeBlock.refreshConnections(server, worldPosition);
			ItemPipeBlock.refreshConnections(server, worldPosition.relative(direction));
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("FaceModes", packedFaceModes);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		packedFaceModes = input.getIntOr("FaceModes", 0);
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			ItemNetworkManager.unregister(this);
			registered = false;
		}
		super.setRemoved();
	}
}

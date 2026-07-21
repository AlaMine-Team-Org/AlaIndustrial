package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Iron Chest block — a horizontal-facing storage cube that opens a 36-slot GUI on right-click and
 * spills its contents when broken. Extends {@link HorizontalMachineBlock} so it inherits the
 * {@code facing} placement + the right-click-to-open-menu behaviour from
 * {@link AbstractMachineBlock#useWithoutItem}, but it is <em>not</em> a machine: it has no energy,
 * no {@code lit} state and no server processing ticker.
 *
 * <p>Rendering: unlike a plain cube machine, the iron chest renders as a real 3D chest model with
 * an animated lid (see {@code ChestBlockEntityRenderer}). On 26.2 the chest block keeps the
 * default {@code MODEL} render shape (matching vanilla {@code ChestBlock}, which no longer returns
 * {@code INVISIBLE} — the BER is layered on top of the block pass, not in place of it). The block
 * model itself is a particle-only shell (no visible cube faces), so only the 3D BER geometry is
 * seen. The inherited {@code AbstractMachineBlock} use hook still drives the GUI.
 *
 * <p>Inventory drop on break is handled by vanilla: {@code BlockEntity.preRemoveSideEffects} drops
 * any {@code Container} block entity's contents during {@code LevelChunk.setBlockState}, before the
 * BE is removed — so this block does not override {@code playerWillDestroy} for that. (The inherited
 * {@link AbstractMachineBlock#playerWillDestroy} only covers the mod's {@code MachineBlockEntity},
 * not {@code BaseContainerBlockEntity}, but it is a no-op here because {@code instanceof
 * MachineBlockEntity} is false for the iron chest.)
 *
 * <p>Tickers: on the client, {@link #getTicker} returns
 * {@link IronChestBlockEntity#lidAnimateTick} so the {@code ChestLidController} interpolates the
 * lid angle each tick. On the server, {@link #tick} handles scheduled-tick rechecks of who has
 * the chest open (the opener counter reschedules itself via {@code scheduleTick} while players
 * keep the GUI open).
 */
public class IronChestBlock extends HorizontalMachineBlock {
	public static final MapCodec<IronChestBlock> CODEC = simpleCodec(IronChestBlock::new);
	/**
	 * Collision/outline shape — a 14×14×14 column (AABB {@code box(1,0,1,15,14,15)}: 14 wide/deep,
	 * 14 tall — a squat chest shorter than a full block), matching the vanilla chest's footprint.
	 * {@code Block.column(xz, minY, maxY)} = {@code column(14, 0, 14)} here. This is deliberately NOT
	 * a full cube, and the block's properties also set {@code noOcclusion()}. The two together satisfy
	 * R-PHY-05 (occlusion ⇔ full-cube): non-full-cube shape ⇒ {@code fullCube==false}, and
	 * {@code noOcclusion()} ⇒ {@code canOcclude==false}, so the invariant holds. The
	 * {@code canOcclude==false} is also what actually fixes the X-ray — with an empty occlusion shape,
	 * neighbours keep their inward-facing sides, so the particle-only block model + the 3D chest BER
	 * don't expose a gap into adjacent blocks.
	 */
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 14.0);

	public IronChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Iron chest is a pure 36-slot container with no energy port — its block entity is a vanilla
	 * {@code BaseContainerBlockEntity}, not a {@link dev.alaindustrial.block.entity.MachineBlockEntity}.
	 * A cable must therefore not draw an arm toward it (MOD-038): the inherited
	 * {@code HorizontalMachineBlock → AbstractMachineBlock} typing is there only for the {@code facing}
	 * property and the right-click-to-open-GUI hook, not because the chest is an energy block.
	 */
	@Override
	public boolean isCableConnectable() {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IronChestBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Client only — the lid controller ticks on the client (where the renderer reads it). The
		// server never needs a per-tick callback here; opener-count rechecks arrive via the block's
		// scheduledTick → #tick below. The type guard (createTickerHelper) is replaced by an
		// instanceof check at call time because ModContent suppliers are wildcard-typed.
		if (!level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof IronChestBlockEntity chest) {
				IronChestBlockEntity.lidAnimateTick(lvl, pos, st, chest);
			}
		};
	}

	/**
	 * Scheduled-tick handler: re-evaluates who has the chest open on the server. The opener counter
	 * reschedules this tick every 5 ticks while anyone has the GUI open (see
	 * {@code ContainerOpenersCounter.scheduleRecheck}).
	 */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof IronChestBlockEntity chest) {
			chest.recheckOpen();
		}
	}
}

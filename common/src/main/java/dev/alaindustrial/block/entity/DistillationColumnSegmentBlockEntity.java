package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.DistillationColumnSegmentBlock;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The thin proxy block entity of the Distillation Column's middle and top segments (MOD-251). It
 * holds no state of its own — its whole job is to exist, because both loaders resolve fluid
 * capabilities strictly per-position per-BE-type: without a BE here, a pump against the middle or
 * top segment would find nothing (2026-08-09 audit — there is no block-based capability path in
 * this codebase).
 *
 * <p>Which tank it forwards is decided by the segment's fixed role: the middle (offset 1) exposes
 * the base's <b>oil intake</b>, the top (offset 2) its <b>diesel output</b>. Energy is deliberately
 * NOT forwarded — EU enters the tower only at the base, and the segments' blocks also refuse the
 * cable's visual arm.
 */
public class DistillationColumnSegmentBlockEntity extends BlockEntity implements FluidPortHost {

	public DistillationColumnSegmentBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.DISTILLATION_COLUMN_SEGMENT_BE.get(), pos, state);
	}

	private int offsetToBase() {
		return getBlockState().getBlock() instanceof DistillationColumnSegmentBlock segment
				? segment.offsetToBase() : 1;
	}

	private @Nullable DistillationColumnBlockEntity master() {
		return level != null
				&& level.getBlockEntity(worldPosition.below(offsetToBase())) instanceof DistillationColumnBlockEntity master
				? master : null;
	}

	/**
	 * The base's tank for this segment's role, or {@code null} while the tower is incomplete. The
	 * {@code null}-side contract (Jade/WTHIT/foreign pipes probe without a direction) answers the
	 * segment's own tank — the fluid a player pointing at this very block is asking about.
	 */
	@Override
	public @Nullable FluidPort fluidPort(@Nullable Direction side) {
		DistillationColumnBlockEntity master = master();
		if (master == null) {
			return null;
		}
		return offsetToBase() == 1 ? master.oilPort() : master.dieselPort();
	}
}

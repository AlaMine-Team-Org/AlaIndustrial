package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.ItemStack;

/**
 * Kok sagyz root (MOD-537) — the underground half of the plant {@link KokSagyzBlock} carries above
 * ground. One class, two states: {@code tip=false} is the upper root directly under the flower,
 * {@code tip=true} the root tip one block deeper — the part the harvest is after.
 *
 * <p><b>Digging either root leaves the flower standing (owner round 7).</b> The plant above is
 * never destroyed by work underground — it is a living dandelion whose roots go two blocks down,
 * and a dug root simply grows back through the flower's random tick. The two depths pay
 * differently, which is the whole point of the column: the TIP mints the root item (plus the seed
 * chance), the upper root mints seeds only. {@link #playerDestroy} refills the hole rather than
 * leaving air, because the rubber has to come out of the ground without leaving a hole, and ground
 * under the flower is exactly what the plant needs to root into again.
 *
 * <p><b>The segment remembers the ground it replaced (MOD-584).</b> A non-ticking
 * {@link dev.alaindustrial.block.entity.KokSagyzRootBlockEntity} holds the original BlockState, so
 * a root grown in sand harvests back to sand instead of turning the desert into dirt, and the
 * ordinary chunk model draws that ground rather than a root texture — the root itself is only
 * visible through the crouch inspection. Segments saved before MOD-584 carry no such data and fall
 * back to dirt, which is what they already looked like.
 *
 * <p>Before round 7 taking the upper root destroyed the flower on purpose, to stop players cheesing
 * the payout in the middle of the column. That guard is now unnecessary and was doing harm: the
 * middle already pays no root item (the loot table gates it on {@code tip=true}), so the only thing
 * the murder rule achieved was killing a plant the player was trying to farm.
 *
 * <p><b>The root does NOT need the plant above it (owner round 5).</b> Losing the flower does not
 * uproot what is already in the ground — the root stays put and can still be dug out, exactly like
 * a real root outliving the plant it fed. This block therefore declares no {@code canSurvive} and
 * no {@code updateShape}: it stands on its own like the dirt it is made of. It reverses the round-3
 * rule where breaking the flower folded the whole column back into the ground, which read as the
 * roots teleporting away. Nothing is farmable that way: an orphaned column has no flower to drive
 * {@code randomTick}, so the tip never regrows and the two blocks are a one-off dig.
 */
public class KokSagyzRootBlock extends Block implements EntityBlock {

	public static final MapCodec<KokSagyzRootBlock> CODEC = simpleCodec(KokSagyzRootBlock::new);

	/** {@code false}: the upper root under the flower. {@code true}: the harvestable tip below it. */
	public static final BooleanProperty TIP = BooleanProperty.create("tip");

	public KokSagyzRootBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(TIP, false));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KokSagyzRootBlockEntity(pos, state);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(TIP);
	}

	/** Restore the captured soil after ordinary loot handling, leaving the perennial flower alive. */
	@Override
	public void playerDestroy(net.minecraft.world.level.Level level, Player player, BlockPos pos, BlockState state,
			BlockEntity blockEntity, ItemStack tool) {
		super.playerDestroy(level, player, pos, state, blockEntity, tool);
		level.setBlockAndUpdate(pos, blockEntity instanceof KokSagyzRootBlockEntity root
				? root.soil() : Blocks.DIRT.defaultBlockState());
	}
}

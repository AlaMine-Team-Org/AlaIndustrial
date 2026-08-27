package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity;
import dev.alaindustrial.chat.ModChat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The brain of a crystal farm (MOD-505): it scans the greenhouse around it, drives every seedbed
 * inside, and tells the player what is wrong when the room will not close.
 *
 * <p><b>One ticking block per farm.</b> The seedbeds and the buds growing on them carry no block
 * entity at all — this controller walks the interior and grows them. That is what lets a player fill
 * a 12×12 room with seedbeds without adding 144 ticking objects to the world, and it is why growth
 * happens only inside a sealed room: the room is not a decorative requirement, it is the thing that
 * ticks.
 *
 * <p>The {@code formed} property is the outward half of the answer — the panel lights up when the
 * greenhouse is sealed, so a correct build is readable from across the base without clicking
 * anything. A blockstate rather than a block-entity field, for the same reason the reactor
 * controller does it: the client needs it for the model, and a blockstate is already synced.
 */
public class CrystalFarmControllerBlock extends HorizontalMachineBlock {

	public static final MapCodec<CrystalFarmControllerBlock> CODEC =
			simpleCodec(CrystalFarmControllerBlock::new);

	/** Whether the room around this controller passed its last scan. Drives the lit panel texture. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	public CrystalFarmControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FORMED, false));
	}

	@Override
	protected MapCodec<? extends CrystalFarmControllerBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FORMED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrystalFarmControllerBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Right-clicking the panel reports the room's state in chat.
	 *
	 * <p>A chat line rather than a screen, on purpose: the only thing this controller has to say is
	 * one sentence ("sealed, 6×4×6, 9 seedbeds" or "there is a hole at these coordinates"), and a
	 * whole menu — with the screen, the sync channel and the frame in the shot catalogue that a menu
	 * obliges — would be a lot of machinery around one sentence. Hunting a one-block hole in a 14³
	 * shell by eye is frustration rather than gameplay, so the coordinates are what matters here.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof CrystalFarmControllerBlockEntity brain) {
			// Chat rather than the action bar: the failure lines carry coordinates, which the action
			// bar truncates on a narrow window. Being a chat line is also what earns it the mod's tag —
			// the report shares that column with the server and every other mod (MOD-522).
			serverPlayer.sendSystemMessage(ModChat.line(brain.describeStatus(pos)), false);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Any neighbour change may have opened or closed the shell, so the scan is re-armed at once
	 * rather than waiting out the periodic sweep. The block entity still re-scans on its own timer,
	 * because a shell can also change out of range of this callback — a block broken on the far wall
	 * of a large room is nobody's neighbour here.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide()
				&& level.getBlockEntity(pos) instanceof CrystalFarmControllerBlockEntity controller) {
			controller.requestScan();
		}
	}
}

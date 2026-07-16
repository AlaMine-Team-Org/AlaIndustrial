package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.menu.TeleporterStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Teleporter station (spec: alaindustrial:teleporter) — the HV anchor a Teleporter Remote jumps to
 * (MOD-091). This task ships the station only: it accepts HV on its five working faces, banks EU in
 * an oversized buffer, and remembers its owner and privacy flag. The jump itself (MOD-092) and the
 * GUI (MOD-093) come later, so the station has no menu and no slots yet.
 *
 <p>It stayed hidden from the creative tab and un-craftable until MOD-093 finished the feature —
 * a station that banks EU with no way to spend it is not something to ship. The tab entry, the
 * recipe and its unlock advancement all arrived together.
 */
public class TeleporterBlock extends HorizontalMachineBlock {
	public static final MapCodec<TeleporterBlock> CODEC = simpleCodec(TeleporterBlock::new);

	public TeleporterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TeleporterBlockEntity(pos, state);
	}

	/**
	 * Ownership is decided here, at placement, and belongs to whoever placed the block — every time.
	 * It deliberately does NOT ride along on the dropped item (unlike the EU buffer and the privacy
	 * flag, which do): a station handed to a friend, or looted from someone's base, becomes the new
	 * placer's. Carrying the owner on the item would make a gifted station permanently unusable by
	 * its new holder, and re-assigning on place is the same rule players already know from the
	 * battery box keeping its charge but not its history.
	 *
	 * <p>The name snapshot is taken here too, so the MOD-093 GUI can show "owner: Steve" without a
	 * UUID→name lookup that is unreliable for offline players.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || !(placer instanceof net.minecraft.world.entity.player.Player player)) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof TeleporterBlockEntity station) {
			station.setOwner(player.getUUID(), player.getGameProfile().name());
		}
	}

	/**
	 * Right-click opens the station's screen (MOD-093).
	 *
	 * <p>Opened here rather than through {@code MenuProvider} on the block entity: the base machine
	 * class hands four upgrade slots to every menu-bearing BE ({@code MachineBlockEntity:76}), and a
	 * station is a fund, not a machine — it must stay slotless, or hoppers gain somewhere to push
	 * items nobody can see.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity station)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new TeleporterStationMenu(syncId, inventory, station,
							ContainerLevelAccess.create(level, pos)),
					station.menuTitle()));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * No ticker on purpose. The station has nothing to do per tick: energy delivery is driven by the
	 * network ({@code EnergyNetwork#tick} pushes straight into the buffer through the face port),
	 * not by the consumer's own tick, so registering a ticker would only spin an empty
	 * {@code onServerTick} 20×/s for every loaded station — exactly what the idle-sleep gate (R-29)
	 * exists to avoid. MOD-092 brings back {@code machineTicker(level)} when the jump gives the
	 * station real per-tick work.
	 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return null;
	}
}

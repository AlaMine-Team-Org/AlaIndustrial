package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * The settlement core (MOD-831): a full-cube machine with the {@code lit} state — lit while its town holds its
 * territory, which the block entity flips through {@code updateLit} and the manifest's light chain turns into a
 * glow. Right-click opens its screen (the machine base); the work is in {@link SettlementCoreBlockEntity}.
 *
 * <p><b>Who breaks it (epic decision R36).</b> The block is declared unbreakable, like bedrock — hardness −1 and a
 * bedrock blast resistance — so explosions, the mod's drills and block breaker, and other mods' area tools, which
 * all skip a block of negative hardness, leave it alone. Only {@link #getDestroyProgress} lets it go, and only for
 * the town's owner holding a tool that mines it: any pickaxe, at the pace of a block of {@link #OWNER_HARDNESS}. A
 * creative player breaks it as they break bedrock.
 *
 * <p><b>One core to a town (epic decision R43).</b> A player cannot place a core inside the territory of a town that
 * already has its core ({@link #getStateForPlacement}): the core stays in the hand, and the chat says where the town's
 * core stands. Inside the territory of a town without its core the placed core continues that town.
 *
 * <p>No {@code FACING}: the territory is centred on the core and every face takes EU, like the mob repeller's
 * radial field.
 */
public class SettlementCoreBlock extends AbstractMachineBlock implements HasMachineTooltip {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The hardness the owner mines the core at — a teleporter station's: a few seconds with a wooden pickaxe. */
	public static final float OWNER_HARDNESS = 5.0F;
	/** Vanilla's divisor of the destroy progress with the right tool in hand ({@code BlockBehaviour}, javap 26.3). */
	private static final float CORRECT_TOOL_DIVISOR = 30.0F;

	public SettlementCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SettlementCoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** Nothing for anyone but the town's owner with a pickaxe; for them, a block of {@link #OWNER_HARDNESS}. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SettlementCoreBlockEntity core) || !core.mayBeBrokenBy(player)) {
			return 0.0F;
		}
		return player.getDestroySpeed(state) / OWNER_HARDNESS / CORRECT_TOOL_DIVISOR;
	}

	/**
	 * Refuses a core a player would put inside the territory of a town that already has its core (R43), and tells them
	 * where that core stands. Asked on both sides; only the server knows the towns, so a client may show the block for
	 * a moment until the server's answer puts the spot back, as with any placement the server turns down.
	 */
	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getLevel() instanceof ServerLevel level) {
			Optional<Settlement> town = SettlementRegistry.get(level.getServer())
					.coreAlreadyStands(level.dimension(), context.getClickedPos());
			if (town.isPresent()) {
				if (context.getPlayer() instanceof ServerPlayer player) {
					SettlementCoreBlockEntity.tellCoreStands(player, town.get());
				}
				return null;
			}
		}
		return super.getStateForPlacement(context);
	}

	/** A core placed from an item takes its place in a town at once (R43). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player
				&& level.getBlockEntity(pos) instanceof SettlementCoreBlockEntity core) {
			core.placedBy(player);
		}
	}

	/**
	 * A core gone from the world leaves its town dark until it is placed back (R36). Called only when the block itself
	 * changed — never for the core's own {@code lit} flip — and keyed by position, because the block entity is
	 * already detached here (the {@code TeleporterBlock} hook, MOD-628).
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		SettlementRegistry.get(level.getServer()).coreRemoved(level.dimension(), pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040): the LV upkeep, buffer and territory radius. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_input", ServerBalance::settlementCoreEuPerTickLv)),
				List.of(MachineTooltipSpec.stat("buffer", ServerBalance::settlementCoreBuffer),
						MachineTooltipSpec.plain("range", ServerBalance::settlementRadiusLv)));
	}
}

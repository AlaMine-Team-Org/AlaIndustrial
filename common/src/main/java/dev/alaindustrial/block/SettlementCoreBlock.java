package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.works.HousePlans;
import dev.alaindustrial.settlement.works.TownWorks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
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
 * creative player breaks it as they break bedrock; it drops nothing then, and the town is left without its core all the
 * same — the player is told so in chat ({@link #playerWillDestroy}, MOD-836).
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
	/**
	 * The chat line of the player who takes a town's core out of the world (MOD-836): the town sleeps without it, and a
	 * core placed on its territory carries it on.
	 */
	public static final String REMOVED_MESSAGE_KEY = "settlement.alaindustrial.core.removed";

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

	/** The overlay line of a house plan the town knows already (round 2 of 0.1.219, Q4). */
	public static final String PLAN_ALREADY_KNOWN = "settlement.alaindustrial.house_plan.already_known";
	/** The overlay line of a house plan right-clicked onto the core by anybody but the restorer. */
	public static final String PLAN_NOT_RESTORER = "settlement.alaindustrial.house_plan.not_restorer";

	/**
	 * A house plan of the town's style right-clicked onto the core is learned by the town and used up (round 2 of
	 * 0.1.219, Q4) — by the town's restorer only; a plan the town knows stays in the hand. Any other item: as a
	 * machine.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof HousePlanItem)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		RestorationPlan plan = RestorationPlanItem.planOf(stack);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)
				|| !(level.getBlockEntity(pos) instanceof SettlementCoreBlockEntity core) || plan == null) {
			return InteractionResult.SUCCESS;
		}
		Optional<Settlement> town = core.settlement();
		if (town.isEmpty() || !town.get().isOwnedBy(player.getUUID())) {
			serverPlayer.sendOverlayMessage(Component.translatable(PLAN_NOT_RESTORER));
			return InteractionResult.SUCCESS;
		}
		if (!town.get().style().isEmpty() && !town.get().style().equals(plan.style().toString())) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		TownWorks works = SettlementRegistry.get(server.getServer()).works().town(town.get().id());
		if (HousePlans.knows(works, TownHall.index(works), plan.template())) {
			serverPlayer.sendOverlayMessage(Component.translatable(PLAN_ALREADY_KNOWN));
			return InteractionResult.SUCCESS;
		}
		HallBuildNews.housePlanLearned(server, town.get().id(), plan.template());
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
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
	 * Tells the player taking a town's core out of the world what became of the town (MOD-836, {@link #removalNotice}):
	 * the owner in survival, anyone in creative, where the core drops nothing. Here rather than in the removal hook
	 * below, which no longer knows who broke the block.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			removalNotice(serverLevel, pos).ifPresent(line -> serverPlayer.sendSystemMessage(line, false));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * The chat line for the player taking the core at {@code pos} out of the world (MOD-836): when it is its town's
	 * core, the town is left without one and sleeps, and any core placed on its territory carries it on (R43) — the
	 * owner breaking it in survival and anyone breaking it in creative read the same line. Empty for a second core
	 * (R43) or a core of no town (R53): neither leaves a town without its core. Asked before the block goes, while the
	 * registry still records the core where it stands.
	 */
	public static Optional<Component> removalNotice(ServerLevel level, BlockPos pos) {
		return SettlementRegistry.get(level.getServer()).findAt(level.dimension(), pos)
				.map(town -> ModChat.line(Component.translatable(REMOVED_MESSAGE_KEY)));
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

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}

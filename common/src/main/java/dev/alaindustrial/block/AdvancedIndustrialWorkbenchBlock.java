package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.AdvancedIndustrialWorkbenchBlockEntity;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.OptionalInt;

/**
 * Advanced Industrial Workbench (MOD-810), the second tier of the crafting bench: a 3x3 grid that keeps its
 * items, is shared by everyone who opens it, answers to pipes and hoppers, and reads on a comparator.
 *
 * <p>Right-click opens the bench's own menu ({@code AdvancedIndustrialWorkbenchMenu}). As on the first tier,
 * when the bench has a recipe on record and the grid is EMPTY the window opens with that recipe drawn in as
 * vanilla's ghost items; with anything in the grid there is no ghost - the grid is the player's now.
 *
 * <p>This is not a villager workstation: the profession's point of interest stays the first tier only. The
 * bench's contents are dropped by its block entity ({@code preRemoveSideEffects}), the one hook that still has
 * the entity attached on every removal path.
 */
public class AdvancedIndustrialWorkbenchBlock extends Block implements EntityBlock, HasMachineTooltip {

	public AdvancedIndustrialWorkbenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AdvancedIndustrialWorkbenchBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof AdvancedIndustrialWorkbenchBlockEntity bench
				&& bench.canOpen(player)) {
			OptionalInt containerId = serverPlayer.openMenu(bench);
			serverPlayer.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
			if (containerId.isPresent() && bench.isGridEmpty()) {
				bench.lastRecipe()
						.flatMap(recipe -> displayOf(serverLevel, recipe))
						.ifPresent(display -> serverPlayer.connection.send(
								new ClientboundPlaceGhostRecipePacket(containerId.getAsInt(), display)));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof AdvancedIndustrialWorkbenchBlockEntity bench ? bench : null;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos,
			net.minecraft.core.Direction direction) {
		return level.getBlockEntity(pos) instanceof AdvancedIndustrialWorkbenchBlockEntity bench
				? bench.comparatorSignal() : 0;
	}

	/** Hover tooltip of this block's item (ADR-040): two short plain lines, no tier - the bench takes no energy. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.advanced_industrial_workbench",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.note("tooltip.alaindustrial.advanced_industrial_workbench.pipes",
								MachineTooltipSpec.Tone.GRAY)),
				List.of());
	}

	/** The recipe-book display of {@code recipe}; empty for a recipe that is gone or has no display. */
	private static Optional<RecipeDisplay> displayOf(ServerLevel level, ResourceKey<Recipe<?>> recipe) {
		AtomicReference<RecipeDisplay> first = new AtomicReference<>();
		level.getServer().getRecipeManager().listDisplaysForRecipe(recipe, entry -> {
			if (first.get() == null) {
				first.set(entry.display());
			}
		});
		return Optional.ofNullable(first.get());
	}
}

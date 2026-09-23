package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.IndustrialWorkbenchBlockEntity;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Industrial Workbench (MOD-062 job site, MOD-656 crafting bench): a crafting table that remembers.
 *
 * <p>Right-click opens vanilla's 3x3 crafting grid ({@link IndustrialWorkbenchMenu}). When the bench has a
 * recipe on record, the grid opens with that recipe's layout already drawn in as ghost items — vanilla's
 * own recipe-book placeholder, sent with vanilla's own packet. The ghost is a hint and nothing more: it
 * holds no items, is ignored by the recipe matcher, and vanilla clears it the moment the player clicks
 * a grid slot.
 *
 * <p>The villager side is untouched: the profession's point of interest is every state of this block,
 * and the block still has exactly one state.
 */
public class IndustrialWorkbenchBlock extends Block implements EntityBlock {

	private static final Component CONTAINER_TITLE = Component.translatable("container.crafting");

	public IndustrialWorkbenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IndustrialWorkbenchBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			OptionalInt containerId = serverPlayer.openMenu(state.getMenuProvider(level, pos));
			serverPlayer.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
			if (containerId.isPresent() && level.getBlockEntity(pos) instanceof IndustrialWorkbenchBlockEntity bench) {
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
		return new SimpleMenuProvider((containerId, inventory, player) ->
				new IndustrialWorkbenchMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)),
				CONTAINER_TITLE);
	}

	/**
	 * The recipe-book display of {@code recipe}, the same object vanilla hands the ghost packet when a
	 * player clicks a recipe they cannot afford. Empty for a recipe that is gone (a datapack removed it)
	 * and for special recipes that have no display (dyeing armour, cloning maps).
	 */
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

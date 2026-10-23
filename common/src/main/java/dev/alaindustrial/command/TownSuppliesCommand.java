package dev.alaindustrial.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.TownSupplies;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /ala town supplies} (0.1.219): fills diamond chests next to the player with everything the town they stand
 * in — or the nearest one — still needs to be built out, plus a margin, plus what a town needs to work from nothing
 * ({@link TownSupplies}). A test command of the dev tree ({@link AlaCommandCommon}), op-only, so a tester stops
 * gathering blocks by hand.
 *
 * <p>The chests stand on free floor two to {@link #MAX_RING} blocks from the player at the height of their feet,
 * nearest first; with too little room nothing is placed and the player is told.
 */
public final class TownSuppliesCommand {

	/** How far from the player's feet a chest may stand, in blocks (Chebyshev). */
	static final int MAX_RING = 4;
	/** How far from a core a player outside every territory may stand and still get its supplies. */
	static final int NEAR_TOWN = 128;

	private static final String KEY = "command.alaindustrial.town.supplies.";

	private TownSuppliesCommand() {
	}

	/** The {@code supplies} branch of {@code /ala town}; public so a gametest can run it on a dispatcher of its own. */
	public static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("supplies")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> run(ctx.getSource()));
	}

	private static int run(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		BlockPos feet = player.blockPosition();
		Optional<Settlement> inside = SettlementRegistry.get(level.getServer()).territoryAt(level.dimension(), feet);
		Optional<Settlement> town = inside.isPresent() ? inside : nearestTown(level, feet, NEAR_TOWN);
		if (town.isEmpty()) {
			source.sendFailure(Component.translatable(KEY + "no_town", NEAR_TOWN));
			return 0;
		}
		List<ItemStack> stacks = TownSupplies.stacks(level, town.get());
		int size = DiamondChestBlockEntity.CONTAINER_SIZE;
		int chests = (stacks.size() + size - 1) / size;
		List<BlockPos> spots = chestSpots(level, feet, chests);
		if (spots.size() < chests) {
			source.sendFailure(Component.translatable(KEY + "no_room", chests));
			return 0;
		}
		for (int c = 0; c < chests; c++) {
			BlockPos at = spots.get(c);
			Direction facing = facingPlayer(at, feet);
			level.setBlock(at, ModContent.DIAMOND_CHEST.get().defaultBlockState()
					.setValue(HorizontalMachineBlock.FACING, facing), Block.UPDATE_ALL);
			if (!(level.getBlockEntity(at) instanceof Container chest)) {
				continue;
			}
			for (int slot = 0; slot < size && c * size + slot < stacks.size(); slot++) {
				chest.setItem(slot, stacks.get(c * size + slot));
			}
			chest.setChanged();
		}
		Set<Item> kinds = new HashSet<>();
		stacks.forEach(stack -> kinds.add(stack.getItem()));
		int id = town.get().id();
		if (inside.isEmpty()) {
			source.sendSuccess(() -> Component.translatable(KEY + "nearest", id).withStyle(ChatFormatting.GRAY),
					false);
		}
		int stackCount = stacks.size();
		int kindCount = kinds.size();
		source.sendSuccess(() -> Component.translatable(KEY + "done", id, chests, stackCount, kindCount)
				.withStyle(ChatFormatting.AQUA), true);
		for (BlockPos at : spots.subList(0, chests)) {
			source.sendSuccess(() -> Component.translatable(KEY + "chest", at.getX(), at.getY(), at.getZ())
					.withStyle(ChatFormatting.GRAY), false);
		}
		return chests;
	}

	/** The town whose core lies nearest {@code pos} in its dimension, within {@code range} blocks. */
	static Optional<Settlement> nearestTown(ServerLevel level, BlockPos pos, int range) {
		return SettlementRegistry.get(level.getServer()).all().stream()
				.filter(town -> town.dimension().equals(level.dimension()))
				.filter(town -> town.corePos().distSqr(pos) <= (double) range * range)
				.min(Comparator.comparingDouble(town -> town.corePos().distSqr(pos)));
	}

	/**
	 * Up to {@code needed} free cells for a chest around {@code feet}, ring by ring from two blocks out, nearest first
	 * in a ring: a cell at the height of the feet that is free and dry, with room above to open the lid and a solid
	 * floor under it.
	 */
	static List<BlockPos> chestSpots(ServerLevel level, BlockPos feet, int needed) {
		List<BlockPos> out = new ArrayList<>();
		for (int ring = 2; ring <= MAX_RING && out.size() < needed; ring++) {
			List<BlockPos> cells = new ArrayList<>();
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
						cells.add(feet.offset(dx, 0, dz));
					}
				}
			}
			cells.sort(Comparator.comparingDouble(cell -> cell.distSqr(feet)));
			for (BlockPos cell : cells) {
				if (out.size() < needed && free(level, cell)) {
					out.add(cell);
				}
			}
		}
		return out;
	}

	private static boolean free(ServerLevel level, BlockPos cell) {
		BlockState here = level.getBlockState(cell);
		BlockState above = level.getBlockState(cell.above());
		BlockPos below = cell.below();
		return here.canBeReplaced() && here.getFluidState().isEmpty() && above.canBeReplaced()
				&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** The side of a chest at {@code at} that looks at the player standing at {@code feet}. */
	private static Direction facingPlayer(BlockPos at, BlockPos feet) {
		int dx = feet.getX() - at.getX();
		int dz = feet.getZ() - at.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}
}

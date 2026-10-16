package dev.alaindustrial.item.tool;

import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the bore modules installed on an electric drill do right now (MOD-482 column, MOD-821 row): which
 * are installed, which are switched on, and where they reach. The drill itself, its crack overlay mixin and
 * the toggle key all read the same answers here.
 */
public final class DrillBore {
	private DrillBore() {
	}

	/** Is the column bore installed on this drill, and switched on? Both must hold for a column. */
	public static boolean isColumnActive(ItemStack stack) {
		return DrillUpgrades.has(stack, DrillUpgrades.COLUMN_BORE) && isColumnEnabled(stack);
	}

	/** Is the row bore (MOD-821) installed on this drill, and switched on? Both must hold. */
	public static boolean isRowActive(ItemStack stack) {
		return DrillUpgrades.has(stack, DrillUpgrades.ROW_BORE) && isRowEnabled(stack);
	}

	/** The extra blocks this drill takes right now, as one mode. */
	public static DrillBoreMode boreMode(ItemStack stack) {
		return DrillBoreMode.of(isColumnActive(stack), isRowActive(stack));
	}

	/**
	 * The column mode's switch. <b>Absent means on</b> (see {@code ModDataComponents.DRILL_COLUMN_ENABLED}):
	 * an upgrade the player paid the Upgrade Table for should work the moment they take the drill out,
	 * and only switching it off writes anything onto the stack.
	 */
	public static boolean isColumnEnabled(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.DRILL_COLUMN_ENABLED.get(), Boolean.TRUE);
	}

	/** Switching back on REMOVES the component rather than writing {@code true}, so a drill that has been
	 * toggled twice is component-identical to one that never was. */
	public static void setColumnEnabled(ItemStack stack, boolean enabled) {
		if (enabled) {
			stack.remove(ModDataComponents.DRILL_COLUMN_ENABLED.get());
		} else {
			stack.set(ModDataComponents.DRILL_COLUMN_ENABLED.get(), Boolean.FALSE);
		}
	}

	/** The row mode's switch, with the same contract as {@link #isColumnEnabled}: absent means on. */
	public static boolean isRowEnabled(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.DRILL_ROW_ENABLED.get(), Boolean.TRUE);
	}

	/** Switching back on removes the component, exactly as {@link #setColumnEnabled} does. */
	public static void setRowEnabled(ItemStack stack, boolean enabled) {
		if (enabled) {
			stack.remove(ModDataComponents.DRILL_ROW_ENABLED.get());
		} else {
			stack.set(ModDataComponents.DRILL_ROW_ENABLED.get(), Boolean.FALSE);
		}
	}

	/**
	 * The positions the active bore modules take besides {@code pos}, in a fixed order: above, below
	 * (column), then right, left of the player's horizontal facing (row). Nothing is filtered by block
	 * state here — whether each one is really taken is decided by the caller, so mining and the crack
	 * overlay share this one list of WHERE and each applies its own WHETHER.
	 */
	public static List<BlockPos> extraPositions(ItemStack stack, BlockPos pos, Player player) {
		List<BlockPos> extras = new ArrayList<>(4);
		if (isColumnActive(stack)) {
			extras.add(pos.above());
			extras.add(pos.below());
		}
		if (isRowActive(stack)) {
			Direction facing = player.getDirection();
			extras.add(pos.relative(facing.getClockWise()));
			extras.add(pos.relative(facing.getCounterClockWise()));
		}
		return extras;
	}

	/** Most extra blocks one drill can take (column 2 + row 2); each owns one mirrored crack id. */
	public static final int MAX_EXTRAS = 4;

	/**
	 * The crack id of the {@code slot}-th extra block of the player {@code playerId}. Negative, so it can never
	 * equal a real entity id (and doubles as a recursion guard for the crack hooks), and unique per slot because
	 * the client keeps ONE crack per breaker id and replaces it when the id names another position.
	 */
	public static int mirrorId(int playerId, int slot) {
		return -(playerId * MAX_EXTRAS + 1 + slot);
	}

	/**
	 * The extra blocks a stroke on {@code aimPos} WILL break — the one plan the server's mining, the server's
	 * crack overlay and the client's prediction (cracks, removal, particles) all share, so that what is drawn is
	 * what happens. Empty unless every condition holds:
	 * <ul>
	 * <li>{@code stack} is the player's main-hand item and the player is not in creative (the server only calls
	 * {@code mineBlock} outside creative, so nothing is bored there);</li>
	 * <li>the aimed block is a pickaxe block the drill bills (non-zero hardness);</li>
	 * <li>the drill affords the aimed block AND every extra that really qualifies:
	 * {@code charge >= aimPrice + n * extraPrice}, n counting only blocks that are inside build height, not air,
	 * pickaxe blocks and breakable. Air or dirt beside the hole therefore costs and counts nothing.</li>
	 * </ul>
	 * Pure of client types: the caller passes the charge and prices of ITS side (the server's real values, or
	 * the client's shown ones).
	 */
	public static List<BlockPos> plannedExtras(ItemStack stack, Level level, BlockPos aimPos, BlockState aimState,
			Player player, long charge, int aimPrice, int extraPrice) {
		if (player.getMainHandItem() != stack || player.preventsBlockDrops()) {
			return List.of();
		}
		if (!aimState.is(BlockTags.MINEABLE_WITH_PICKAXE)
				|| !ElectricMiningRule.billsBlock(false, aimState.getDestroySpeed(level, aimPos))) {
			return List.of();
		}
		List<BlockPos> qualifying = new ArrayList<>(MAX_EXTRAS);
		for (BlockPos extra : extraPositions(stack, aimPos, player)) {
			if (level.isOutsideBuildHeight(extra)) {
				continue;
			}
			BlockState state = level.getBlockState(extra);
			if (!state.isAir() && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
					&& state.getDestroySpeed(level, extra) >= 0.0f) {
				qualifying.add(extra);
			}
		}
		if (qualifying.isEmpty() || charge < aimPrice + (long) qualifying.size() * extraPrice) {
			return List.of();
		}
		return List.copyOf(qualifying);
	}
}

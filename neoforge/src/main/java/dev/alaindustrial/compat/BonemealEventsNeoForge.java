package dev.alaindustrial.compat;

import dev.alaindustrial.item.misc.ReinforcedBonemeal;
import dev.alaindustrial.item.misc.ReinforcedBonemeal.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's bone-meal event for reinforced bone meal (MOD-906), whose signature differs between the
 * Minecraft lines (ADR-036). {@link #install()}, called by the NeoForge entry point, makes it
 * {@code ReinforcedBonemeal.LOADER_GATE}, so the meal asks the same event vanilla bone meal asks and claim and
 * moderation mods can refuse it.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where {@code EventHooks.fireBonemealEvent} takes a
 * {@link BonemealSource}; a click is {@link BonemealSource#INTERACTION}.
 *
 * <p>The verdict follows NeoForge's patched {@code BoneMealItem.applyBonemeal}: a cancelled event stops the meal;
 * if the canceller also marked it successful, it handled the application itself — the click counts as a success,
 * but the stack is that mod's to spend, so the meal is NOT spent here (NeoForge's own bone meal returns
 * {@code isSuccessful()} without a shrink).
 */
public final class BonemealEventsNeoForge {

	private BonemealEventsNeoForge() {
	}

	/** Makes this the reinforced bone meal's loader gate. */
	public static void install() {
		ReinforcedBonemeal.LOADER_GATE = BonemealEventsNeoForge::check;
	}

	public static Verdict check(@Nullable Player player, Level level, BlockPos pos, BlockState state,
			ItemStack stack) {
		BonemealEvent event = EventHooks.fireBonemealEvent(player, level, pos, state, BonemealSource.INTERACTION,
				stack);
		if (!event.isCanceled()) {
			return Verdict.PROCEED;
		}
		return event.isSuccessful() ? Verdict.HANDLED : Verdict.DENIED;
	}
}

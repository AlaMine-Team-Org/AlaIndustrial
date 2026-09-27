package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import dev.alaindustrial.network.PayloadBudget;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click diagnostics for an energy network (MOD-016 / MOD-047): reads the network at the
 * clicked cable, reports its stats in the actionbar, and pushes a {@link NetworkAnalyzerPayload} so
 * the client can highlight it in the world. Read-only — never mutates the network.
 *
 * <p>Two modes (MOD-047), carried on the tool as a {@code network_analyzer_mode} data component:
 * <ul>
 *   <li><b>TRAVERSE</b> (default) — walk through storage sinks (BatteryBox) and highlight every
 *       connected cable segment beyond them, via {@link NetworkTraverser}.</li>
 *   <li><b>STOP_AT_STORAGE</b> — show only the clicked cable's own network (original MOD-016).</li>
 * </ul>
 *
 * <p>What a click does is one table, {@link AnalyzerClick}: a click on a network scans (Shift or not);
 * anywhere else Shift switches the mode and a plain click clears the highlight — in the air too (D9).
 */
public class NetworkAnalyzerItem extends Item {
	/** Server-wide scan rate limit (D4) — one per JVM is enough, it is keyed by player. */
	private static final ScanThrottle THROTTLE = new ScanThrottle();

	public NetworkAnalyzerItem(Properties properties) {
		super(properties);
	}

	/** Right-click in the air (no block target). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		act(AnalyzerClick.decide(false, player.isSecondaryUseActive()), serverLevel, serverPlayer,
				player.getItemInHand(hand));
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		EnergyNetwork net = NetworkManager.networkAt(level, context.getClickedPos());
		AnalyzerClick.Action action = AnalyzerClick.decide(net != null, context.isSecondaryUseActive());
		if (action != AnalyzerClick.Action.SCAN) {
			act(action, level, player, stack);
			return InteractionResult.SUCCESS;
		}
		if (!THROTTLE.tryScan(player.getUUID(), context.getClickedPos().asLong(), level.getGameTime())) {
			// Holding the use key repeats this every four ticks; the picture already shown stands (D4).
			return InteractionResult.SUCCESS;
		}
		AnalyzerMode mode = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		if (mode == null) {
			mode = AnalyzerMode.TRAVERSE; // items spawned without the component default to traverse
		}
		TraversalResult result = NetworkTraverser.traverse(level, net, mode, Config.networkAnalyzerMaxTraversedNetworks);
		// Persist the reading on the tool so its tooltip can replay it after the actionbar fades.
		stack.set(ModDataComponents.NETWORK_SCAN.get(),
				new NetworkScanData(result.cableCount(), result.producerList().size(), result.consumerList().size(),
						result.storageList().size(), result.supply(), result.demand(), result.moved()));
		// Loader-neutral dispatch (MOD-022): NetworkDispatcher replaces the Fabric-direct ServerPlayNetworking.
		NetworkAnalyzerPayload payload = NetworkAnalyzerPayload.of(level.dimension(), result, mode,
				context.getClickedPos(), PayloadBudget.MAX_POSITIONS);
		NetworkDispatcher.get().sendToPlayer(player, payload);
		player.sendOverlayMessage(readout(result, payload.truncated(), Config.networkAnalyzerMaxTraversedNetworks));
		return InteractionResult.SUCCESS;
	}

	/**
	 * The one actionbar line a scan produces (D7). The actionbar holds a single message and the last one
	 * sent wins, so the traverse-limit warning used to wipe out the statistics it was sent after. The
	 * notes now ride on the same line.
	 */
	public static Component readout(TraversalResult result, boolean truncated, int networkLimit) {
		MutableComponent line = Component.translatable("gui.alaindustrial.network_analyzer.stats", result.cableCount(),
				result.producerList().size(), result.consumerList().size(), result.storageList().size(),
				result.supply(), result.demand(), result.moved()).withStyle(ChatFormatting.AQUA);
		if (result.hitLimit()) {
			line.append(Component.literal(" "))
					.append(Component.translatable("gui.alaindustrial.network_analyzer.note.limit", networkLimit)
							.withStyle(ChatFormatting.YELLOW));
		}
		if (truncated) {
			line.append(Component.literal(" "))
					.append(Component.translatable("gui.alaindustrial.network_analyzer.note.truncated",
							PayloadBudget.MAX_POSITIONS).withStyle(ChatFormatting.YELLOW));
		}
		return line;
	}

	/** The non-scan actions of {@link AnalyzerClick}. */
	private static void act(AnalyzerClick.Action action, ServerLevel level, ServerPlayer player, ItemStack stack) {
		switch (action) {
			case SWITCH_MODE -> switchMode(player, stack);
			case CLEAR -> {
				NetworkDispatcher.get().sendToPlayer(player, NetworkAnalyzerPayload.empty(level.dimension()));
				player.sendOverlayMessage(Component.translatable("gui.alaindustrial.network_analyzer.none")
						.withStyle(ChatFormatting.GRAY));
			}
			case SCAN -> {
				// Only reachable through useOn, which handles it itself.
			}
		}
	}

	/** Cycle the mode on the tool and tell the player what it is now. */
	private static void switchMode(ServerPlayer player, ItemStack stack) {
		AnalyzerMode current = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		AnalyzerMode next = (current == null ? AnalyzerMode.TRAVERSE : current).next();
		stack.set(ModDataComponents.NETWORK_ANALYZER_MODE.get(), next);
		String key = next == AnalyzerMode.TRAVERSE
				? "gui.alaindustrial.network_analyzer.mode_switched.traverse"
				: "gui.alaindustrial.network_analyzer.mode_switched.stop_at_storage";
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA));
	}
}

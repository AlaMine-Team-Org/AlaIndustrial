package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * <p>Shift + right-click in the air / off-network cycles the mode; right-clicking a cable always
 * scans in the current mode (Shift held or not). The split is: a click aimed at a cable is a
 * diagnostic action, a click aimed anywhere else is a mode switch.
 */
public class NetworkAnalyzerItem extends Item {
	public NetworkAnalyzerItem(Properties properties) {
		super(properties);
	}

	/** Right-click in the air (no block target): only meaning is to switch the mode (Shift held). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive()) {
			switchMode(serverLevel, serverPlayer, stack);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		// Shift + right-click that is NOT aimed at a cable network = mode switch (no scan).
		if (context.isSecondaryUseActive()) {
			EnergyNetwork aimed = NetworkManager.networkAt(level, context.getClickedPos());
			if (aimed == null) {
				switchMode(level, player, stack);
				return InteractionResult.SUCCESS;
			}
			// Shift + right-click ON a cable: scan in the current mode (decision Q1 of MOD-047).
		}
		EnergyNetwork net = NetworkManager.networkAt(level, context.getClickedPos());
		if (net == null) {
			NetworkDispatcher.get().sendToPlayer(player, NetworkAnalyzerPayload.empty(level.dimension()));
			player.sendOverlayMessage(
					Component.translatable("gui.alaindustrial.network_analyzer.none").withStyle(ChatFormatting.GRAY));
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
		NetworkDispatcher.get().sendToPlayer(player,
				new NetworkAnalyzerPayload(level.dimension(), result.cableList(), result.producerList(),
						result.consumerList(), result.storageList(), mode, result.supply(), result.demand(),
						result.moved()));
		player.sendOverlayMessage(Component
				.translatable("gui.alaindustrial.network_analyzer.stats", result.cableCount(), result.producerList().size(),
						result.consumerList().size(), result.storageList().size(), result.supply(), result.demand(),
						result.moved())
				.withStyle(ChatFormatting.AQUA));
		if (result.hitLimit()) {
			player.sendOverlayMessage(Component
					.translatable("gui.alaindustrial.network_analyzer.traverse_limit", Config.networkAnalyzerMaxTraversedNetworks)
					.withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}

	/** Cycle the mode on the tool and tell the player what it is now. */
	private static void switchMode(ServerLevel level, ServerPlayer player, ItemStack stack) {
		AnalyzerMode current = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		AnalyzerMode next = (current == null ? AnalyzerMode.TRAVERSE : current).next();
		stack.set(ModDataComponents.NETWORK_ANALYZER_MODE.get(), next);
		String key = next == AnalyzerMode.TRAVERSE
				? "gui.alaindustrial.network_analyzer.mode_switched.traverse"
				: "gui.alaindustrial.network_analyzer.mode_switched.stop_at_storage";
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA));
	}
}

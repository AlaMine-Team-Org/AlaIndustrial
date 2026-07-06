package dev.alaindustrial.item;

import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Right-click diagnostics for an energy network (MOD-016): reads the {@link EnergyNetwork} at the
 * clicked cable, reports its stats in the actionbar, and pushes a {@link NetworkAnalyzerPayload} so
 * the client can highlight the network in the world. Read-only — never mutates the network.
 */
public class NetworkAnalyzerItem extends Item {
	public NetworkAnalyzerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		EnergyNetwork net = NetworkManager.networkAt(level, context.getClickedPos());
		if (net == null) {
			ServerPlayNetworking.send(player, NetworkAnalyzerPayload.empty(level.dimension()));
			player.sendOverlayMessage(
					Component.translatable("gui.alaindustrial.network_analyzer.none").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		long supply = net.producerSupplyEstimate();
		long demand = net.consumerDemandEstimate();
		long moved = net.lastTickMoved();
		var producers = net.producerPositions();
		var consumers = net.consumerPositions();
		// Persist the reading on the tool so its tooltip can replay it after the actionbar fades (MOD-016).
		context.getItemInHand().set(ModDataComponents.NETWORK_SCAN,
				new NetworkScanData(net.size(), producers.size(), consumers.size(), supply, demand, moved));
		ServerPlayNetworking.send(player, new NetworkAnalyzerPayload(level.dimension(), List.copyOf(net.cables()),
				producers, consumers, supply, demand, moved));
		player.sendOverlayMessage(Component
				.translatable("gui.alaindustrial.network_analyzer.stats", net.size(), producers.size(),
						consumers.size(), supply, demand, moved)
				.withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}
}

package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import dev.alaindustrial.BuildInfo;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.NetworkManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * {@code /ala} build-visibility command. Permission level 0 (available to everyone) so any player can
 * verify which build of Ala Industrial the server is running.
 *
 * <ul>
 *   <li>{@code /ala version} — one line: version, git hash, build time.</li>
 *   <li>{@code /ala status} — the version line plus registered block/item/recipe counts in the
 *       {@code alaindustrial} namespace, the energy-network model, and key {@link Config} values.</li>
 *   <li>{@code /ala net} — live energy-network telemetry per dimension (network/awake/cable counts
 *       and EU throughput), backed by {@link NetworkManager#stats}.</li>
 * </ul>
 */
public final class AlaCommand {
	private AlaCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("ala")
						.requires(Commands.hasPermission(Commands.LEVEL_ALL))
						.then(Commands.literal("version")
								.executes(ctx -> {
									ctx.getSource().sendSuccess(() -> Component.literal(versionLine()), false);
									return Command.SINGLE_SUCCESS;
								}))
						.then(Commands.literal("status")
								.executes(ctx -> {
									ctx.getSource().sendSuccess(() -> Component.literal(versionLine()), false);
									ctx.getSource().sendSuccess(() -> Component.literal(statusBody(ctx.getSource().getServer())), false);
									return Command.SINGLE_SUCCESS;
								}))
						.then(Commands.literal("net")
								.executes(ctx -> {
									ctx.getSource().sendSuccess(() -> Component.literal(netBody(ctx.getSource().getServer())), false);
									return Command.SINGLE_SUCCESS;
								}))));
	}

	private static String versionLine() {
		return "Ala Industrial " + BuildInfo.version() + " · build " + BuildInfo.git() + " · " + BuildInfo.built();
	}

	private static String statusBody(net.minecraft.server.MinecraftServer server) {
		int blocks = countNamespace(BuiltInRegistries.BLOCK.keySet());
		int items = countNamespace(BuiltInRegistries.ITEM.keySet());
		int recipes = 0;
		if (server != null) {
			for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
				if (Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())) {
					recipes++;
				}
			}
		}
		return "Registry: blocks=" + blocks + " items=" + items + " recipes=" + recipes
				+ " | Energy network: cached union-find graph (cable transport = throughput limit, no loss)"
				+ " | Config: machineEuPerTick=" + Config.machineEuPerTick
				+ " networksPerTick=" + Config.networksPerTick;
	}

	/** Render live energy-network telemetry: one line per dimension that has networks, plus a total. */
	private static String netBody(MinecraftServer server) {
		if (server == null) {
			return "Energy networks: server unavailable";
		}
		StringBuilder sb = new StringBuilder("Energy networks (cached union-find graph):");
		int totalNets = 0;
		int totalCables = 0;
		long totalLast = 0;
		long totalAll = 0;
		boolean any = false;
		for (ServerLevel level : server.getAllLevels()) {
			NetworkManager.Stats s = NetworkManager.stats(level);
			totalNets += s.networks();
			totalCables += s.cables();
			totalLast += s.euMovedLastTick();
			totalAll += s.euMovedTotal();
			if (s.networks() == 0) {
				continue; // skip idle dimensions to keep the readout short
			}
			any = true;
			sb.append("\n  ").append(level.dimension().identifier())
					.append(": nets=").append(s.networks())
					.append(" (awake ").append(s.awake()).append(" / asleep ").append(s.asleep()).append(')')
					.append(" cables=").append(s.cables())
					.append(" | ticked=").append(s.tickedLastTick())
					.append(" EU/t=").append(s.euMovedLastTick())
					.append(" total=").append(s.euMovedTotal());
		}
		if (!any) {
			sb.append("\n  (no energy networks loaded)");
		}
		sb.append("\nTotals: nets=").append(totalNets)
				.append(" cables=").append(totalCables)
				.append(" EU last tick=").append(totalLast)
				.append(" EU total=").append(totalAll);
		return sb.toString();
	}

	private static int countNamespace(Iterable<Identifier> keys) {
		int n = 0;
		for (Identifier id : keys) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				n++;
			}
		}
		return n;
	}
}

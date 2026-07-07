package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.alaindustrial.BuildInfo;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Loader-neutral {@code /ala} build-visibility command (MOD-022). The command tree + rendering use only
 * vanilla brigadier + neutral {@link BuildInfo}/{@link Config}/{@link NetworkManager}, so both loaders
 * register the same tree onto their own {@link CommandDispatcher}: Fabric via {@code CommandRegistrationCallback}
 * ({@code AlaCommand}), NeoForge via {@code RegisterCommandsEvent}. Permission level 0 (everyone).
 *
 * <ul>
 *   <li>{@code /ala version} — one line: version, git hash, build time.</li>
 *   <li>{@code /ala status} — the version line plus registered block/item/recipe counts in the
 *       {@code alaindustrial} namespace, the energy-network model, and key {@link Config} values.</li>
 *   <li>{@code /ala net} — live energy-network telemetry per dimension, backed by {@link NetworkManager#stats}.</li>
 * </ul>
 */
public final class AlaCommandCommon {
	private AlaCommandCommon() {
	}

	/** Register the {@code /ala} tree onto the given dispatcher. Called from each loader's command hook. */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ala")
				.requires(Commands.hasPermission(Commands.LEVEL_ALL))
				.then(Commands.literal("version")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(AlaCommandCommon::versionLine, false);
							return Command.SINGLE_SUCCESS;
						}))
				.then(Commands.literal("status")
						.executes(ctx -> {
							MinecraftServer server = ctx.getSource().getServer();
							ctx.getSource().sendSuccess(AlaCommandCommon::versionLine, false);
							ctx.getSource().sendSuccess(() -> statusBody(server), false);
							return Command.SINGLE_SUCCESS;
						}))
				.then(Commands.literal("net")
						.executes(ctx -> {
							MinecraftServer server = ctx.getSource().getServer();
							ctx.getSource().sendSuccess(() -> netBody(server), false);
							return Command.SINGLE_SUCCESS;
						})));
	}

	private static Component versionLine() {
		return Component.translatable("command.alaindustrial.version",
				BuildInfo.version(), BuildInfo.git(), BuildInfo.built());
	}

	private static Component statusBody(MinecraftServer server) {
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
		MutableComponent msg = Component.translatable("command.alaindustrial.status.registry", blocks, items, recipes);
		msg.append(" | ").append(Component.translatable("command.alaindustrial.status.network"));
		msg.append(" | ").append(Component.translatable("command.alaindustrial.status.config",
				Config.machineEuPerTick, Config.networksPerTick));
		return msg;
	}

	/** Render live energy-network telemetry: one line per dimension that has networks, plus a total. */
	private static Component netBody(MinecraftServer server) {
		if (server == null) {
			return Component.translatable("command.alaindustrial.net.unavailable");
		}
		MutableComponent msg = Component.translatable("command.alaindustrial.net.header");
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
			msg.append("\n").append(Component.translatable("command.alaindustrial.net.dimension",
					level.dimension().identifier().toString(),
					s.networks(), s.awake(), s.asleep(), s.cables(),
					s.tickedLastTick(), s.euMovedLastTick(), s.euMovedTotal()));
		}
		if (!any) {
			msg.append("\n").append(Component.translatable("command.alaindustrial.net.empty"));
		}
		msg.append("\n").append(Component.translatable("command.alaindustrial.net.totals",
				totalNets, totalCables, totalLast, totalAll));
		return msg;
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

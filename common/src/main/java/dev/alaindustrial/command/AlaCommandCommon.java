package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.alaindustrial.BuildInfo;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.NetworkManager;
import net.minecraft.ChatFormatting;
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

	// --- /ala palette (ChatFormatting enum — the only styling mechanism used in this project) ---
	/** Section headers + mod name; AQUA is the project's "network/diagnostics" accent (MachineTooltips). */
	private static final ChatFormatting HEADER = ChatFormatting.AQUA;
	/** Field labels; matches the readable default of MachineTooltips/NetworkAnalyzerItem. */
	private static final ChatFormatting LABEL = ChatFormatting.GRAY;
	/** Indentation + separators (":"); matches the secondary "Hold SHIFT" hint tone. */
	private static final ChatFormatting SEP = ChatFormatting.DARK_GRAY;
	/** Numeric/string values. */
	private static final ChatFormatting VALUE = ChatFormatting.WHITE;
	/** Loss — the one value worth flagging (resistive cable loss confuses players). */
	private static final ChatFormatting WARN = ChatFormatting.YELLOW;

	private static Component versionLine() {
		MutableComponent msg = Component.translatable("command.alaindustrial.name").withStyle(HEADER, ChatFormatting.BOLD);
		msg.append(" ").append(Component.literal(BuildInfo.version()).withStyle(VALUE));
		msg.append("\n").append(Component.translatable("command.alaindustrial.status.build",
				BuildInfo.git(), BuildInfo.built()).withStyle(LABEL));
		return msg;
	}

	private static Component statusBody(MinecraftServer server) {
		int blocks = countNamespace(BuiltInRegistries.BLOCK.keySet());
		int items = countNamespace(BuiltInRegistries.ITEM.keySet());
		int recipes = countRecipes(server);

		MutableComponent msg = Component.empty();
		// --- Registry ---
		msg.append(sectionHeader("command.alaindustrial.status.section.registry"));
		msg.append("\n").append(Component.literal("  ").withStyle(SEP))
				.append(label("command.alaindustrial.status.label.blocks"))
				.append(colonSep()).append(val(blocks))
				.append(spacedSep())
				.append(label("command.alaindustrial.status.label.items"))
				.append(colonSep()).append(val(items))
				.append(spacedSep())
				.append(label("command.alaindustrial.status.label.recipes"))
				.append(colonSep()).append(val(recipes));
		// --- Energy Network ---
		msg.append("\n\n").append(sectionHeader("command.alaindustrial.status.section.network"));
		msg.append("\n").append(field("command.alaindustrial.status.label.model",
				Component.translatable("command.alaindustrial.status.value.model").withStyle(VALUE)));
		msg.append("\n").append(field("command.alaindustrial.status.label.transport",
				Component.translatable("command.alaindustrial.status.value.transport").withStyle(VALUE)));
		msg.append("\n").append(field("command.alaindustrial.status.label.loss",
				Component.translatable("command.alaindustrial.status.value.loss").withStyle(WARN)));
		// --- Configuration ---
		msg.append("\n\n").append(sectionHeader("command.alaindustrial.status.section.config"));
		msg.append("\n").append(field(Component.literal("machineEuPerTick").withStyle(LABEL), val(Config.machineEuPerTick)));
		msg.append("\n").append(field(Component.literal("networksPerTick").withStyle(LABEL), val(Config.networksPerTick)));
		return msg;
	}

	/** AQUA+BOLD section header. */
	private static Component sectionHeader(String key) {
		return Component.translatable(key).withStyle(HEADER, ChatFormatting.BOLD);
	}

	/** GRAY translatable field label. */
	private static Component label(String key) {
		return Component.translatable(key).withStyle(LABEL);
	}

	/** WHITE literal value. */
	private static MutableComponent val(Object v) {
		return Component.literal(String.valueOf(v)).withStyle(VALUE);
	}

	/** "  label: value" line (translatable label). */
	private static Component field(String labelKey, Component value) {
		return field(Component.translatable(labelKey).withStyle(LABEL), value);
	}

	/** "  label: value" line (literal label, used for raw Config field names). */
	private static Component field(Component label, Component value) {
		MutableComponent line = Component.literal("  ").withStyle(SEP);
		line.append(label).append(colonSep()).append(value);
		return line;
	}

	/** ":" separator in SEP colour. */
	private static Component colonSep() {
		return Component.literal(": ").withStyle(SEP);
	}

	/** "  " spacer between inline label groups (e.g. "Blocks: 28  Items: 52"). */
	private static Component spacedSep() {
		return Component.literal("  ").withStyle(SEP);
	}

	private static int countRecipes(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		int recipes = 0;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())) {
				recipes++;
			}
		}
		return recipes;
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

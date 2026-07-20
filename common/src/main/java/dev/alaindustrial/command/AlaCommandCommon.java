package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.BuildInfo;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.command.demo.DemoStand;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.stats.LevelMath;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>{@code /ala config reload} — op-only (level 2); re-reads {@code config/alaindustrial.json} into
 *       {@link Config} so a server operator applies balance edits without a restart (MOD-100).</li>
 * </ul>
 */
public final class AlaCommandCommon {
	private AlaCommandCommon() {
	}

	/**
	 * Register the {@code /ala} tree onto the given dispatcher. Called from each loader's command hook.
	 *
	 * @param devEnvironment true when the mod runs from a development environment (Fabric
	 *                       {@code isDevelopmentEnvironment()} / NeoForge {@code !FMLEnvironment.isProduction()}).
	 *                       Gates the hidden {@code /ala demo} subtree (MOD-058): in production the branch is
	 *                       not registered at all — players cannot see, tab-complete or invoke it. For manual
	 *                       QA on a real launcher it can be force-enabled with the JVM flag
	 *                       {@code -Dalaindustrial.demo=true}.
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean devEnvironment) {
		boolean demoEnabled = devEnvironment || Boolean.getBoolean("alaindustrial.demo");
		LiteralArgumentBuilder<CommandSourceStack> tree = Commands.literal("ala")
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
						}))
				// MOD-067: recover a lost Guide Book. Does not touch the auto-give ledger — a player
				// can always re-fetch the tutorial. Uses the vanilla give-feedback lang key.
				.then(Commands.literal("guide")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							net.minecraft.world.item.ItemStack book =
									new net.minecraft.world.item.ItemStack(dev.alaindustrial.registry.ModContent.GUIDE_BOOK.get());
							Component name = book.getDisplayName();
							if (!player.addItem(book)) {
								player.drop(book, false);
							}
							ctx.getSource().sendSuccess(() -> Component.translatable(
									"commands.give.success.single", 1, name, player.getDisplayName()), false);
							return Command.SINGLE_SUCCESS;
						}));
		tree.then(configTree());
		tree.then(profileTree());
		if (demoEnabled) {
			tree.then(demoTree());
		}
		dispatcher.register(tree);
	}

	/**
	 * The {@code /ala config reload} subtree (MOD-100). Op-only (level 2) — it re-reads the server balance
	 * file. Feedback is a plain English literal by the same op/diagnostic convention as {@link #demoTree()},
	 * keeping the 18 player-facing locale files free of admin-only keys. Reload runs synchronously on the
	 * server thread (where {@link Config}'s static fields are also read each tick), so there is no data race.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> configTree() {
		return Commands.literal("config")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(ctx -> {
					Config.LoadResult result = Config.reload();
					switch (result) {
						case LOADED -> ctx.getSource().sendSuccess(() -> Component.literal(
								"Reloaded config/alaindustrial.json.").withStyle(HEADER), true);
						case DEFAULTS_WRITTEN -> ctx.getSource().sendSuccess(() -> Component.literal(
								"config/alaindustrial.json was missing — wrote defaults.").withStyle(HEADER), true);
						case ERROR -> {
							ctx.getSource().sendFailure(Component.literal(
									"Failed to read config/alaindustrial.json — check the server log; live balance unchanged."));
							return 0;
						}
					}
					return Command.SINGLE_SUCCESS;
				}));
	}

	/**
	 * The {@code /ala profile} subtree (MOD-133): inspect and force a player's mod-XP for QA — the only
	 * way to reach high ranks without dozens of hours of real play. Op-only (level 2); plain English
	 * literals by the same op/diagnostic convention as {@link #configTree()}/{@link #demoTree()}, so no
	 * locale keys are spent on an admin tool.
	 *
	 * <p>{@code set <xp>} solves for the {@code euUsefulConsumedTotal} that lands total XP exactly on
	 * the target (XP is derived from two career totals, so the existing generator term is backed out —
	 * see {@link LevelMath#consumedForTargetXp}) and overwrites {@code highestLevelReached} so the tool
	 * can move a profile <em>down</em> as well as up. Career production is never destroyed, so a target
	 * below what production alone already grants is refused with the reachable floor rather than
	 * silently missed.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> profileTree() {
		return Commands.literal("profile")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("show").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ctx.getSource().sendSuccess(() -> profileBody(player), false);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("set")
						.then(Commands.argument("xp", IntegerArgumentType.integer(0)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							long targetXp = IntegerArgumentType.getInteger(ctx, "xp");
							PlayerModStats before = PlayerStatsStore.get(player);
							long consumed = LevelMath.consumedForTargetXp(targetXp, before.euProducedTotal(),
									Config.euPerXp, Config.euPerXpGenerated);
							if (consumed < 0) {
								// Unreachable: career production alone already grants more than the target, and
								// career EU is the ground truth — we do not destroy it to satisfy a QA number.
								long floor = before.euProducedTotal() / Math.max(1, Config.euPerXpGenerated);
								ctx.getSource().sendFailure(Component.literal(
										"Cannot set XP to " + targetXp + ": career generator output alone already grants "
												+ floor + " XP. Lowest reachable value is " + floor + "."));
								return 0;
							}
							// A QA command must be able to move DOWN too, so highestLevelReached is overwritten
							// rather than max()'d — the no-demotion rule protects players, not the op tool.
							int level = LevelMath.levelForXp(targetXp, Config.xpLevelOneCost, Config.levelXpMultiplier);
							PlayerStatsStore.set(player, new PlayerModStats(
									before.euProducedTotal(), consumed, level,
									before.producedByGenerator(), before.activeTicks()));
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Set mod XP to " + targetXp + " (level " + level + ").").withStyle(HEADER), false);
							return Command.SINGLE_SUCCESS;
						})));
	}

	/** One-shot readout of a player's mod-XP stats (op/QA output — plain English, no locale keys). */
	private static Component profileBody(ServerPlayer player) {
		PlayerModStats stats = PlayerStatsStore.get(player);
		long xp = stats.xp(Config.euPerXp, Config.euPerXpGenerated);
		int level = Math.max(stats.highestLevelReached(),
				LevelMath.levelForXp(xp, Config.xpLevelOneCost, Config.levelXpMultiplier));
		String rank = LevelMath.rankKey(level) + " " + LevelMath.roman(LevelMath.subLevel(level));
		MutableComponent msg = Component.literal("Ala profile — " + player.getScoreboardName()).withStyle(HEADER, ChatFormatting.BOLD);
		msg.append("\n").append(field(Component.literal("Rank").withStyle(LABEL),
				Component.literal(rank + " (level " + level + ")").withStyle(VALUE)));
		msg.append("\n").append(field(Component.literal("XP").withStyle(LABEL), val(xp)));
		msg.append("\n").append(field(Component.literal("EU produced").withStyle(LABEL), val(stats.euProducedTotal())));
		msg.append("\n").append(field(Component.literal("EU useful-consumed").withStyle(LABEL), val(stats.euUsefulConsumedTotal())));
		msg.append("\n").append(field(Component.literal("Active minutes").withStyle(LABEL), val(stats.activeTicks() / 1200)));
		msg.append("\n").append(field(Component.literal("Generator types").withStyle(LABEL), val(stats.producedByGenerator().size())));
		return msg;
	}

	/**
	 * The {@code /ala demo} subtree (MOD-058): build/clear the generated showcase stand and jump
	 * between its fixed camera points. Op-only (level 2) — it edits the world. Feedback is plain
	 * English literals by convention for op/diagnostic output (see the MOD-058 task log), keeping
	 * the 18 player-facing locale files free of admin-only keys.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> demoTree() {
		return Commands.literal("demo")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("build").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					BlockPos origin = DemoStand.findOrigin(level);
					DemoStand.buildAll(level, origin);
					run(ctx.getSource(), "time set noon");
					run(ctx.getSource(), "weather clear");
					// Freeze the time of day via the API, not a dispatched command string: 26.2
					// renamed the rule doDaylightCycle -> advance_time (GameRules.ADVANCE_TIME;
					// the old camelCase id survives only in the world datafixer), so the literal
					// "gamerule doDaylightCycle false" fails to parse.
					level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME,
							false, ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal(
							"Demo stand built at " + origin.toShortString()
									+ ". Zones: /ala demo tp <" + tpNames() + ">").withStyle(HEADER),
							true);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("clear").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					DemoStand.clear(level, DemoStand.findOrigin(level));
					ctx.getSource().sendSuccess(() -> Component.literal("Demo stand cleared.").withStyle(HEADER), true);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("tp")
						.then(Commands.argument("zone", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (DemoStand.TpPoint p : DemoStand.TP_POINTS) {
										builder.suggest(p.name());
									}
									return builder.buildFuture();
								})
								.executes(AlaCommandCommon::demoTp)));
	}

	/** Teleport the calling player to the named stand camera point (and set the matching time of day). */
	private static int demoTp(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws CommandSyntaxException {
		String zone = StringArgumentType.getString(ctx, "zone");
		DemoStand.TpPoint point = DemoStand.TP_POINTS.stream()
				.filter(p -> p.name().equals(zone)).findFirst().orElse(null);
		if (point == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown zone '" + zone + "'. Zones: " + tpNames()));
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos origin = DemoStand.findOrigin(level);
		run(ctx.getSource(), point.night() ? "time set midnight" : "time set noon");
		// tp via the vanilla command so the camera angle is applied atomically with the position.
		run(ctx.getSource(), String.format(Locale.ROOT, "tp %s %.1f %.1f %.1f %.1f %.1f",
				player.getScoreboardName(),
				origin.getX() + point.dx(), origin.getY() + point.dy(), origin.getZ() + point.dz(),
				point.yaw(), point.pitch()));
		ctx.getSource().sendSuccess(() -> Component.literal("Demo camera: " + zone).withStyle(LABEL), false);
		return Command.SINGLE_SUCCESS;
	}

	/** Dispatch a vanilla command as the demo caller (already gated to op level 2 by the subtree). */
	private static void run(CommandSourceStack source, String command) {
		source.getServer().getCommands().performPrefixedCommand(source, command);
	}

	private static String tpNames() {
		return String.join("|", DemoStand.TP_POINTS.stream().map(DemoStand.TpPoint::name).toList());
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

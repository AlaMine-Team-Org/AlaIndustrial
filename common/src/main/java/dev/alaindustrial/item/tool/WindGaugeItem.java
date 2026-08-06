package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.WindFlow;
import dev.alaindustrial.core.environment.WindProfile;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Wind Gauge (MOD-347) — a hand instrument that measures the wind where the player is standing, so a
 * tower site can be chosen <em>before</em> a mill is built. Shift + right-click prints one actionbar
 * line — height, wind speed, weather, sky — and chimes at a pitch that tracks the reading.
 *
 * <p><b>It measures, it does not advise.</b> No "climb higher", no "optimal here", no "N blocks to
 * the jet", and no EU/t. The player climbs, watches the number move, hears the note rise, and draws
 * their own conclusion. That restraint is the feature — see the task log.
 *
 * <p><b>Why km/h and not EU/t.</b> A mill's output is an integer of at most eight steps, so an EU/t
 * readout would repeat itself for dozens of blocks and say nothing about where inside the band you
 * are. The gauge reports the underlying wind at one-decimal resolution, which changes on every single
 * block; the arithmetic lives in {@link WindFlow} on top of {@link WindProfile} — the same curve, fed
 * the same {@code Config} constants, that the mills convert into EU.
 *
 * <p><b>Sky and weather match the mill, not the solar panel.</b> Wind mills need
 * {@link SolarSky.Access#CLEAR} (foliage kills them outright, unlike a panel, which only dims) and
 * read the global {@code isRaining}/{@code isThundering} flags rather than biome precipitation. The
 * gauge does both the same way, or its readings would drift from the generator it exists to predict.
 *
 * <p>Outside the Overworld there is no wind at all ({@code openSky} requires it), so the gauge says
 * so in words instead of printing a height-derived number it would have to invent.
 */
public class WindGaugeItem extends Item {
	public WindGaugeItem(Properties properties) {
		super(properties);
	}

	/** Shift + right-click in the air: measure. A plain right-click is not ours — pass it on. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		measure(level, player);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Shift + right-click aimed at a block. Vanilla already routes a sneaking use with a non-empty
	 * hand straight here, skipping {@code BlockState.useItemOn}, so no bypass of our own is needed.
	 * The reading is still taken at the player's own position, not the clicked one: "the wind here"
	 * has to mean one thing whether or not something happened to be under the crosshair.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !context.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		measure(context.getLevel(), player);
		return InteractionResult.SUCCESS;
	}

	/** Server-side reading, delivered to the actionbar (mirrors {@link NetworkAnalyzerItem}). */
	private static void measure(Level level, Player player) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return; // the client half of the interaction has nothing to measure
		}
		BlockPos pos = serverPlayer.blockPosition();
		if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
			serverPlayer.sendOverlayMessage(Component.translatable("gui.alaindustrial.wind_gauge.no_wind")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean openSky = SolarSky.classify(serverLevel, pos) == SolarSky.Access.CLEAR;
		boolean raining = serverLevel.isRaining();
		boolean thundering = serverLevel.isThundering();
		int ridge = WindProfile.ridgeY(serverLevel.getSeaLevel(), Config.windMillMaxBaseEuPerTick,
				WindProfile.DEFAULT_BLOCKS_PER_BASE, Config.windCloudY);
		int tenths = WindFlow.readingTenths(pos.getY(), serverLevel.getSeaLevel(), openSky, raining, thundering,
				ridge, Config.windCloudY, Config.windDeadY, Config.windRidgeFactor, Config.windTraceFactor,
				Config.windGaugePeakKmh, Config.windMillRainFactor, Config.windMillThunderFactor);
		serverPlayer.sendOverlayMessage(Component.translatable("gui.alaindustrial.wind_gauge.reading",
				pos.getY(), WindFlow.format(tenths),
				Component.translatable(weatherKey(raining, thundering)),
				Component.translatable(openSky
						? "gui.alaindustrial.wind_gauge.sky.open"
						: "gui.alaindustrial.wind_gauge.sky.closed"))
				.withStyle(openSky ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		playScanChime(serverLevel, pos, tenths);
	}

	/**
	 * The scan chime. Pitch rises with the reading, so the instrument answers before the player has
	 * read the line: climbing a tower and hearing the note go up is the same information as watching
	 * the number go up, and it survives the actionbar fading. A dead spot (no sky, thin air above the
	 * clouds) gets the bottom note, which is audibly a "nothing here".
	 *
	 * <p>Vanilla {@code AMETHYST_BLOCK_CHIME} rather than a shipped .ogg: this is a UI blip, not a
	 * machine loop, and an original sound asset would have to be authored, licensed and localised in
	 * sounds.json for a single click.
	 *
	 * <p><b>Played through the level with a {@code null} source, not through the player.</b>
	 * {@code Player#playSound} passes the player itself as {@code Level#playSound}'s <em>excluded</em>
	 * entity, so on the server — which is the only side this method runs on — the one person who
	 * pressed the button is precisely the one who would not hear it. Verified against the 26.2
	 * bytecode, not assumed.
	 */
	private static void playScanChime(ServerLevel level, BlockPos pos, int tenths) {
		float full = Math.max(1.0f, Config.windGaugePeakKmh * 10.0f);
		float span = Math.min(1.0f, tenths / full);
		// 0.6 .. 1.6 — a full octave and a bit, the range Minecraft pitches read cleanly over.
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.6F + span);
	}

	private static String weatherKey(boolean raining, boolean thundering) {
		if (thundering) {
			return "gui.alaindustrial.wind_gauge.weather.thunder";
		}
		return raining ? "gui.alaindustrial.wind_gauge.weather.rain" : "gui.alaindustrial.wind_gauge.weather.clear";
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("tooltip.alaindustrial.wind_gauge.usage")
				.withStyle(ChatFormatting.GRAY));
	}
}

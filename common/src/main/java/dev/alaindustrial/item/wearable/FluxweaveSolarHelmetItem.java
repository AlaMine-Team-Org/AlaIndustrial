package dev.alaindustrial.item.wearable;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarWearOutput;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.WornSourceDistributor;
import dev.alaindustrial.skill.SkillEnergy;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;

/**
 * Solar Fluxweave Helmet (MOD-664): the Fluxweave helmet with a solar panel on the crown. Worn under a
 * clear daytime sky it yields a few EU a second and hands them to the wearer's gear.
 *
 * <p>Still a {@link FluxweaveArmorItem}, so it is the same helmet for everything the set cares about —
 * the 4/4 regeneration bonus, the upkeep, the worn look — and nothing about the other three pieces
 * changes. What this class adds is one step, once a second, <b>before</b> the base class's own tick:
 * the base returns early on an empty helmet, and a panel that only runs on a charged helmet could never
 * start itself.
 *
 * <p><b>Where the EU goes, in order:</b> the helmet itself (so its bonuses and upkeep do not flicker),
 * then the other worn armour, then the rest of the carried gear. Whatever does not fit is lost — the
 * panel is not a battery — and nothing is written to a stack that takes nothing.
 */
public class FluxweaveSolarHelmetItem extends FluxweaveArmorItem {

	public FluxweaveSolarHelmetItem(Properties properties) {
		super(equipmentProperties(properties, ArmorType.HELMET), ArmorType.HELMET);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot == EquipmentSlot.HEAD && entity instanceof Player player && level.getGameTime() % 20 == 0) {
			solarStep(stack, player);
		}
		super.inventoryTick(stack, level, entity, slot);
	}

	/**
	 * One second of generation for a worn helmet; returns the EU produced (what was offered, not what
	 * fitted). Public and separate from the game-time gate for the same reason
	 * {@link FluxweaveArmorItem#secondStep} is: a gametest cannot advance the clock inside one tick.
	 */
	public static long solarStep(ItemStack helmet, Player wearer) {
		long generated = SolarWearOutput.euPerSecond(ToolConfig.fluxweaveSolarEuPerSecond,
				isDaylight(wearer.level()), skyOf(wearer), weatherOf(wearer),
				wearer.isEyeInFluid(FluidTags.WATER),
				GeneratorConfig.solarSnowFactor, GeneratorConfig.solarTransparentFactor);
		if (generated <= 0) {
			return 0L;
		}
		// 1) The helmet itself, within its own input rate (a per-tick figure, paid here for 20 ticks).
		long toHelmet = Math.min(generated, Math.min(ItemEnergy.room(helmet),
				SkillEnergy.inputRate(ItemEnergy.inputRate(helmet), wearer) * 20L));
		if (toHelmet > 0) {
			ItemEnergy.add(helmet, toHelmet);
		}
		// 2) The other worn armour, 3) everything else carried, the start of that list rotating by the second.
		long left = generated - toHelmet;
		if (left > 0) {
			WornSourceDistributor.distributeArmorFirst(wearer, left, 20, EquipmentSlot.HEAD,
					(int) (wearer.level().getGameTime() / 20L));
		}
		return generated;
	}

	private static boolean isDaylight(Level level) {
		return level.dimension().equals(Level.OVERWORLD) && level.isBrightOutside();
	}

	private static SolarWearOutput.Sky skyOf(Player wearer) {
		return switch (SolarSky.classify(wearer.level(), eyePos(wearer))) {
			case CLEAR -> SolarWearOutput.Sky.CLEAR;
			case PARTIAL -> SolarWearOutput.Sky.PARTIAL;
			case BLOCKED -> SolarWearOutput.Sky.BLOCKED;
		};
	}

	private static SolarWearOutput.Precipitation weatherOf(Player wearer) {
		return switch (SolarSky.classifyWeather(wearer.level(), eyePos(wearer))) {
			case NONE -> SolarWearOutput.Precipitation.NONE;
			case RAIN -> SolarWearOutput.Precipitation.RAIN;
			case SNOW -> SolarWearOutput.Precipitation.SNOW;
		};
	}

	private static BlockPos eyePos(Player wearer) {
		return BlockPos.containing(wearer.getEyePosition());
	}

	// MOD-498 — same reason as FluxweaveArmorItem#appendHoverText: the only per-item tooltip hook.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, adder, flag);
		adder.accept(Component.translatable("tooltip.alaindustrial.fluxweave_solar_helmet.output",
				ServerBalance.fluxweaveSolarEuPerSecond()).withStyle(ChatFormatting.YELLOW));
		adder.accept(Component.translatable("tooltip.alaindustrial.fluxweave_solar_helmet.limits")
				.withStyle(ChatFormatting.GRAY));
	}
}

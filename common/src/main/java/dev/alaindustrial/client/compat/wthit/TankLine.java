package dev.alaindustrial.client.compat.wthit;

import dev.alaindustrial.client.screen.FluidGauge;
import dev.alaindustrial.compat.wthit.TankBarRatio;
import dev.alaindustrial.compat.wthit.TankData;
import dev.alaindustrial.item.fluid.FluidDisplayNames;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.component.PairComponent;
import mcp.mobius.waila.api.component.SpriteBarComponent;
import mcp.mobius.waila.api.component.WrappedComponent;
import mcp.mobius.waila.api.data.FluidData;
import mcp.mobius.waila.api.util.WNumbers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Client half of the WTHIT tank line (MOD-938): draws the line WTHIT's own fluid provider would — same
 * tag, fluid name, textured bar, {@code stored/capacity} in the unit chosen in WTHIT — from
 * {@link TankData}, with the bar fill taken through {@link TankBarRatio} so a nearly empty tank cannot
 * crash the game.
 *
 * <p>It draws nothing when WTHIT's fluid info is off for this block: then no {@link FluidData} arrived
 * (see {@link TankData}).
 */
final class TankLine implements IBlockComponentProvider {
	/** Right after WTHIT's fluid line (550), so ours takes its place among the other lines. */
	static final int PRIORITY = 551;

	@Override
	public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
		if (accessor.getData().get(FluidData.TYPE) == null) {
			return;
		}
		CompoundTag tank = accessor.getData().raw().getCompoundOrEmpty(TankData.KEY);
		Identifier id = Identifier.tryParse(tank.getStringOr("fluid", ""));
		Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
		long amount = tank.getLongOr("amount", 0);
		long capacity = tank.getLongOr("capacity", 0);
		if (fluid == Fluids.EMPTY || amount <= 0) {
			return;
		}
		FluidData.Unit unit = config.getEnum(FluidData.CONFIG_DISPLAY_UNIT);
		String text = format(amount, unit) + "/" + format(capacity, unit) + " " + unit.symbol;
		tooltip.setLine(FluidData.ID.withSuffix("." + id.toLanguageKey()), new PairComponent(
				new WrappedComponent(FluidDisplayNames.of(fluid)),
				new SpriteBarComponent(TankBarRatio.of(amount, capacity), FluidGauge.sprite(fluid), 16, 16,
						FluidGauge.tint(fluid), Component.literal(text))));
	}

	private static String format(long millibuckets, FluidData.Unit unit) {
		return WNumbers.suffix((long) FluidData.Unit.convert(FluidData.Unit.MILLIBUCKETS, unit, millibuckets));
	}
}

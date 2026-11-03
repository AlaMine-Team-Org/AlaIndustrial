package dev.alaindustrial.block;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Reinforced glass trapdoor (MOD-864): a steel frame around a see-through pane. Opens by hand and by
 * redstone like the tempered iron trapdoor; breaks and steps like glass, clicks like the iron trapdoor
 * (owner decision 2026-11-02).
 *
 * <p>The sound lives only here: the {@link TrapDoorBlock} constructor overwrites the properties' sound type
 * with the block set type's. Not registered with vanilla's {@code BlockSetType} map, for the reason given on
 * {@link TemperedIronTrapdoorBlock}.
 */
public class ReinforcedGlassTrapdoorBlock extends TrapDoorBlock {

	/** Opens by hand, not by a wind charge; glass to break and walk on, iron trapdoor clicks to open. */
	public static final BlockSetType REINFORCED_GLASS = new BlockSetType("alaindustrial:reinforced_glass", true,
			false, false, BlockSetType.PressurePlateSensitivity.MOBS, SoundType.GLASS, SoundEvents.IRON_DOOR_CLOSE,
			SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
			SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

	public ReinforcedGlassTrapdoorBlock(Properties properties) {
		super(REINFORCED_GLASS, properties);
	}
}

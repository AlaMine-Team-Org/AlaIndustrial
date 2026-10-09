package dev.alaindustrial.block;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Tempered iron trapdoor (MOD-795): opens by hand like a copper trapdoor and by redstone like any
 * trapdoor, with the iron trapdoor's sounds (owner decision 2026-10-09).
 *
 * <p>The block set type is not registered with vanilla's {@code BlockSetType} map: that map serves block
 * codecs, which 26.3 blocks no longer carry.
 */
public class TemperedIronTrapdoorBlock extends TrapDoorBlock {

	/** Opens by hand, not by a wind charge; iron sounds throughout. */
	public static final BlockSetType TEMPERED_IRON = new BlockSetType("alaindustrial:tempered_iron", true, false,
			false, BlockSetType.PressurePlateSensitivity.MOBS, SoundType.METAL, SoundEvents.IRON_DOOR_CLOSE,
			SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
			SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

	public TemperedIronTrapdoorBlock(Properties properties) {
		super(TEMPERED_IRON, properties);
	}
}

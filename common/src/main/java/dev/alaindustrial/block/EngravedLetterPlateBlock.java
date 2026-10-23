package dev.alaindustrial.block;

import dev.alaindustrial.core.guide.ArchiveRecord;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The letter plate of a town house's number plaque (owner's round 4 of 0.1.219, R4-12): any letter of the record
 * alphabet ({@link ArchiveRecord#ALPHABET}), whole while the house has a resident, broken once it stands empty. The
 * town's drones engrave it; a player mines it as any plate, and its loot keeps the letter and the break.
 */
public class EngravedLetterPlateBlock extends EngravedPlateBlock {

	/** The letter, as its index in {@link ArchiveRecord#ALPHABET}. */
	public static final IntegerProperty LETTER = IntegerProperty.create("letter", 0,
			ArchiveRecord.ALPHABET.length() - 1);
	/** Whether the plate is broken: the letter of a former resident. */
	public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

	public EngravedLetterPlateBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LETTER, 0).setValue(BROKEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LETTER, BROKEN);
	}

	/** This plate showing {@code letter} of the record alphabet, whole or {@code broken}, facing {@code facing}. */
	public BlockState plate(char letter, boolean broken, Direction facing) {
		int index = Math.max(0, ArchiveRecord.ALPHABET.indexOf(letter));
		return defaultBlockState().setValue(LETTER, index).setValue(BROKEN, broken).setValue(FACING, facing);
	}
}

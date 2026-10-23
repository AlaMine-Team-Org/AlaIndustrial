package dev.alaindustrial.settlement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The colours of a town's chat lines, in one place (owner's round 4 of 0.1.219, R4-7): a line of plain white text is
 * read past, so each part of it says what it is by its colour.
 *
 * <ul>
 *   <li>the town's heading — «Settlement No. 1 (core at …):» — in the chat's own white, its coordinates grey;</li>
 *   <li>a building's, a stage's or a plan's name yellow;</li>
 *   <li>what is done green, what went wrong red, plain news white;</li>
 *   <li>what the player is to do next gold.</li>
 * </ul>
 *
 * <p>The colours ride on the parts as {@link Component} styles — every part is an argument or a sibling of its own —
 * never as section signs inside a translation, so no locale can lose or break them.
 */
public final class TownChatStyle {

	/** The town's heading and its number. */
	public static final ChatFormatting HEADING = ChatFormatting.WHITE;
	/** Coordinates: the quietest part of a line. */
	public static final ChatFormatting COORDINATES = ChatFormatting.GRAY;
	/** A building's, a stage's or a plan's name. */
	public static final ChatFormatting NAME = ChatFormatting.YELLOW;
	/** A number inside a sentence: «stage 2 of 5». */
	public static final ChatFormatting NUMBER = ChatFormatting.WHITE;
	/** Something done: a stage, a restoration, a clearing. */
	public static final ChatFormatting DONE = ChatFormatting.GREEN;
	/** Something gone wrong: a building damaged. */
	public static final ChatFormatting TROUBLE = ChatFormatting.RED;
	/** News that is neither: a house laid. */
	public static final ChatFormatting NEWS = ChatFormatting.WHITE;
	/** What the player is to do next. */
	public static final ChatFormatting NEXT_STEP = ChatFormatting.GOLD;

	private TownChatStyle() {
	}

	/** {@code name} as a name of the line. */
	public static MutableComponent name(Component name) {
		return name.copy().withStyle(NAME);
	}

	/** {@code number} as a number of the line. */
	public static MutableComponent number(Object number) {
		return Component.literal(String.valueOf(number)).withStyle(NUMBER);
	}

	/** {@code pos} as «x, y, z» in the coordinates' grey. */
	public static MutableComponent coordinates(BlockPos pos) {
		return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(COORDINATES);
	}
}

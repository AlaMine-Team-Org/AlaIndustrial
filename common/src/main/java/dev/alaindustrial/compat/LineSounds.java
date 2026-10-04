package dev.alaindustrial.compat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Vanilla sound events whose constant changed type between the Minecraft lines (MOD-703, ADR-036). Every
 * line has a twin of this class with the same signatures; only the bodies differ. Callers get a plain
 * {@link SoundEvent} and use the {@code playSound} overload that takes one, which both lines have.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where these {@code SoundEvents} constants became
 * {@code Holder.Reference<SoundEvent>}.
 */
public final class LineSounds {

	private LineSounds() {
	}

	/** The sound a hoe makes tilling soil. */
	public static SoundEvent hoeTill() {
		return SoundEvents.HOE_TILL.value();
	}
}

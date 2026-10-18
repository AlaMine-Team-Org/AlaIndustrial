package dev.alaindustrial.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to the merge count of an {@link ExperienceOrb} (MOD-822).
 *
 * <p>Vanilla merges orbs of the same value: {@code tryMergeToExisting} (when orbs are awarded) and
 * {@code scanForMerges} (orbs lying together) raise {@code count} on the orb that stays instead of adding a
 * second entity, so the points an orb carries are {@code getValue() * count}. The field is private and has no
 * getter on either line (26.3, 26.2 — checked with javap); a player picking the orb up takes one value per
 * count. The setter lets a gametest build a merged orb without waiting on the random merge.
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
	@Accessor("count")
	int alaindustrial$getCount();

	@Accessor("count")
	void alaindustrial$setCount(int count);
}

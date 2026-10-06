package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStamina;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Who runs in a mob wheel (MOD-763) and how tired it is — the drive's occupant record, kept apart from the
 * drive's tick so its save format lives in one place. Plain state: {@link MobWheelBlockEntity} decides every
 * transition, this class only holds and persists it.
 *
 * <p>Stamina belongs to the mob ({@link #staminaOwner}): let the same pig out and shut it in again and it is
 * just as tired; a different mob starts fresh (D3).
 */
final class MobWheelOccupant {
	@Nullable
	EntityReference<Mob> ref;
	/** Species of the occupant, kept so a mob in an unloaded chunk still rests at its own rate. */
	@Nullable
	MobWheelProfile species;
	/** Entity type id of the occupant ({@code minecraft:zombie_villager} runs as a zombie): what the screen names. */
	@Nullable
	String runnerType;
	/** Whose stamina {@link #stamina} is: survives a release, so the same mob comes back as tired as it left. */
	@Nullable
	UUID staminaOwner;
	@Nullable
	MobWheelStamina stamina;
	int runTick;
	int missingTicks;
	boolean lost;
	/** The wheel made this piglin immune to zombification (D8) and must take that back on release. */
	boolean grantedImmunity;

	/** Begin holding {@code mob} of {@code profile}; a different mob than last time starts with full stamina. */
	void shutIn(Mob mob, MobWheelProfile profile, String typeId) {
		ref = EntityReference.of(mob);
		species = profile;
		runnerType = typeId;
		if (stamina == null || !mob.getUUID().equals(staminaOwner)) {
			stamina = MobWheelStamina.full(profile.staminaTicks());
			staminaOwner = mob.getUUID();
			runTick = 0;
		}
		lost = false;
		missingTicks = 0;
		grantedImmunity = false;
	}

	/** Stop holding anyone; stamina and its owner stay for a possible return. */
	void clear() {
		ref = null;
		lost = false;
		missingTicks = 0;
		grantedImmunity = false;
	}

	void save(ValueOutput output) {
		EntityReference.store(ref, output, "Occupant");
		if (species != null) {
			output.putString("Species", species.name());
		}
		if (runnerType != null) {
			output.putString("RunnerType", runnerType);
		}
		if (staminaOwner != null) {
			output.store("StaminaOwner", UUIDUtil.CODEC, staminaOwner);
		}
		if (stamina != null) {
			output.putInt("StaminaMax", stamina.max());
			output.putInt("StaminaLeft", stamina.remaining());
			output.putBoolean("Exhausted", stamina.exhausted());
			output.putLong("RestProgress", stamina.restAccumulator());
			output.putBoolean("SelfRestored", stamina.selfRestored());
		}
		output.putInt("RunTick", runTick);
		output.putBoolean("Lost", lost);
		output.putBoolean("GrantedImmunity", grantedImmunity);
	}

	void load(ValueInput input) {
		ref = EntityReference.read(input, "Occupant");
		species = MobWheelProfile.byName(input.getStringOr("Species", ""));
		// Absent stays absent: a fallback here would write a RunnerType the first save never had.
		String storedType = input.getStringOr("RunnerType", "");
		runnerType = storedType.isEmpty() ? null : storedType;
		staminaOwner = input.read("StaminaOwner", UUIDUtil.CODEC).orElse(null);
		int max = input.getIntOr("StaminaMax", 0);
		stamina = max > 0 ? MobWheelStamina.restore(max, input.getIntOr("StaminaLeft", max),
				input.getBooleanOr("Exhausted", false), input.getLongOr("RestProgress", 0L),
				input.getBooleanOr("SelfRestored", false)) : null;
		runTick = input.getIntOr("RunTick", 0);
		lost = input.getBooleanOr("Lost", false);
		grantedImmunity = input.getBooleanOr("GrantedImmunity", false);
		missingTicks = 0;
	}
}

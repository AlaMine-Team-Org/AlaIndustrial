package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alaindustrial.client.sound.SoundAbsorberZones;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The sound absorber's reach into every block and ambient sound, the game's and other mods' (MOD-804): the
 * channel volume the engine computes is scaled by the zone the sound stands in
 * ({@link SoundAbsorberZones#factorFor}). Subtitles are untouched — the engine notifies its listeners before
 * it computes this volume.
 *
 * <p>Two points, because 26.3 computes the volume in two places (checked with {@code javap -c}):
 * {@code play(SoundInstance)} calls {@code calculateVolume(float, SoundSource)} directly for the sound's first
 * frame — the only frame a one-shot sound (a piston, a furnace crackle) ever has — and the ticking path and
 * a category volume change call {@code calculateVolume(SoundInstance)}. Scaling one without the other would
 * leave one-shots loud, or let a looping sound jump back to full volume on its first tick.
 *
 * <p>In the optional config ({@code alaindustrial.compat-optional.mixins.json}, {@code defaultRequire 0}): if
 * another mod rewrites the same method and an injection finds no target, the feature switches off and the
 * game runs on. The absorber never returns 0 (its strongest tier keeps 15 %), so a muffled sound is never
 * refused by the engine's "volume was zero" check.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineAbsorberMixin {

	@ModifyExpressionValue(method = "play",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;"
					+ "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
	private float alaindustrial$muffleOnStart(float volume, @Local(argsOnly = true) SoundInstance sound) {
		return volume * SoundAbsorberZones.factorFor(sound);
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
			at = @At("RETURN"))
	private float alaindustrial$muffleWhilePlaying(float volume, SoundInstance sound) {
		return volume * SoundAbsorberZones.factorFor(sound);
	}
}

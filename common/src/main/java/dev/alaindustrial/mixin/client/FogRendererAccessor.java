package dev.alaindustrial.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches {@code FogRenderer.FOG_ENVIRONMENTS} so the oil fog can be inserted into it (MOD-248).
 *
 * <p>The field is a private static {@code ArrayList} built with {@code Lists.newArrayList(...)} and
 * never reassigned, so adding to it is enough — see {@code OilFogEnvironment} for why there is no
 * data-driven alternative and why the insertion position matters.
 */
@Mixin(FogRenderer.class)
public interface FogRendererAccessor {

	@Accessor("FOG_ENVIRONMENTS")
	static List<FogEnvironment> alaindustrial$fogEnvironments() {
		throw new AssertionError("mixin accessor not applied");
	}
}

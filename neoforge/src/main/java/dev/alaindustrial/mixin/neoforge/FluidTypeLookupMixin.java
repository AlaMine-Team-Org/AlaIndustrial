package dev.alaindustrial.mixin.neoforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.registry.neoforge.NeoForgeFluidTypes;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the mod's fluids their NeoForge {@link FluidType} without a subclass per fluid (MOD-708).
 *
 * <p>NeoForge's patched {@code Fluid.getFluidType()} caches its answer in a field and, while the field is
 * empty, asks {@code CommonHooks.getVanillaFluidType(this)}, which throws for a fluid it does not know. That
 * one call is wrapped: a fluid the mod registered gets the type {@code NeoForgeFluidTypes} recorded for it, any
 * other fluid goes on to the original call — NeoForge's own answer, or the next mod's wrapper. The wrap runs
 * only until the field is filled, so the lookup is paid once per fluid. Verified by javap against neoforge
 * 26.3.0.7-beta and 26.2.0.67 ({@code Fluid.getFluidType}, {@code CommonHooks.getVanillaFluidType(Fluid)}).
 *
 * <p>A MixinExtras {@code @WrapOperation}, not a {@code @Redirect}: two mods redirecting the same call cannot
 * both apply, while wrappers chain. MixinExtras ships with NeoForge itself (jar-in-jar
 * {@code mixinextras-neoforge}, also on the compile classpath). {@code require = 1} keeps the failure loud:
 * a target that disappears stops the game from loading instead of leaving the mod's fluids typeless.
 */
@Mixin(Fluid.class)
public abstract class FluidTypeLookupMixin {

	@WrapOperation(method = "getFluidType", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/common/CommonHooks;getVanillaFluidType"
					+ "(Lnet/minecraft/world/level/material/Fluid;)Lnet/neoforged/neoforge/fluids/FluidType;"))
	private FluidType alaindustrial$modFluidType(Fluid fluid, Operation<FluidType> original) {
		FluidType own = NeoForgeFluidTypes.typeOf(fluid);
		return own != null ? own : original.call(fluid);
	}
}

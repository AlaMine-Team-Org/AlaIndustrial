package dev.alaindustrial.arch.fixture.clientstandin;

/**
 * Plays a client-only type for {@code ArchitectureRulesNegativeControl} (MOD-703). The real ones
 * ({@code net.minecraft.client..}, {@code com.mojang.blaze3d..}) are not on {@code :common}'s
 * Minecraft-free test classpath, so a fixture cannot reference them; what the control proves is the HOST
 * list of {@code clientTypesStayInsideClientPackages}, and for that any target package will do.
 */
public final class ClientTypeStandIn {

	public void draw() {
	}
}

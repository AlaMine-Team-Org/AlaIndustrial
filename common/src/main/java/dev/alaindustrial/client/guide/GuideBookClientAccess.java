package dev.alaindustrial.client.guide;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening the Guide Book screen, called from
 * {@link dev.alaindustrial.item.misc.GuideBookItem#use} inside a {@code level.isClientSide()} guard.
 * A tiny indirection so the item class holds no direct {@code Minecraft}/{@code Screen} reference at
 * its top level; the classes here are only loaded on the logical client, when {@code use} actually
 * reaches this call.
 *
 * <p>Lives under {@code client.guide}, not next to the item (MOD-435): the ArchUnit rule
 * {@code clientTypesStayInsideClientPackages} forbids {@code net.minecraft.client} references outside
 * {@code dev.alaindustrial.client..}, so the one class that legitimately holds one sits where the
 * rule expects it.
 */
public final class GuideBookClientAccess {
	private GuideBookClientAccess() {
	}

	public static void open() {
		Minecraft.getInstance().setScreenAndShow(new GuideBookScreen());
	}
}

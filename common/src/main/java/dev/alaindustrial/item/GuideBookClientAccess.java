package dev.alaindustrial.item;

import dev.alaindustrial.client.guide.GuideBookScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening the Guide Book screen, called from {@link GuideBookItem#use}
 * inside a {@code level.isClientSide()} guard. A tiny indirection so the item class holds no direct
 * {@code Minecraft}/{@code Screen} reference at its top level; the classes here are only loaded on the
 * logical client, when {@code use} actually reaches this call.
 */
public final class GuideBookClientAccess {
	private GuideBookClientAccess() {
	}

	public static void open() {
		Minecraft.getInstance().setScreenAndShow(new GuideBookScreen());
	}
}

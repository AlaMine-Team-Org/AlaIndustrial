package dev.alaindustrial.item;

/**
 * Whether a modifier key is held while a tooltip is drawn (MOD-108), so common items can offer the
 * usual "hold Shift for details" split without importing a client class.
 *
 * <p>{@code Screen.hasShiftDown()} lives in the client jar, but {@link net.minecraft.world.item.Item}
 * subclasses are loaded on a dedicated server too. Same shape as {@link dev.alaindustrial.sound.MachineHum}:
 * the client entrypoint installs the hook, and on a dedicated server it simply stays unset — tooltips are
 * never drawn there, so the answer does not matter.
 */
public final class TooltipKeys {

	/** Installed by each loader's client entrypoint. */
	public interface ClientHook {
		boolean shiftDown();
	}

	public static volatile ClientHook CLIENT;

	/** True only when a client installed the hook and Shift is actually held. */
	public static boolean shiftDown() {
		ClientHook hook = CLIENT;
		return hook != null && hook.shiftDown();
	}

	private TooltipKeys() {}
}

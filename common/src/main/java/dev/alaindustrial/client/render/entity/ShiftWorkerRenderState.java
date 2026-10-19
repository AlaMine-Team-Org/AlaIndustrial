package dev.alaindustrial.client.render.entity;

import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** What one frame of a shift worker needs (MOD-832): a humanoid's state plus the number and the look. */
public class ShiftWorkerRenderState extends HumanoidRenderState {
	/** The number on the back, {@code <letter>-<NNN>}; empty — none yet (nothing is drawn). */
	public String number = "";
	/** The skin, as a resource id ({@code <ns>:textures/...png}). */
	public Identifier texture = ShiftWorkerRenderer.FALLBACK_CLASSIC;
	public boolean slim;
	public ShiftWorkerLook.Helmet helmet = ShiftWorkerLook.Helmet.OPEN;
	public ShiftWorkerLook.Hair hair = ShiftWorkerLook.Hair.NONE;
}

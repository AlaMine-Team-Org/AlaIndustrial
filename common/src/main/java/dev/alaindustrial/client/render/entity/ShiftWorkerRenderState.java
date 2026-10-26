package dev.alaindustrial.client.render.entity;

import dev.alaindustrial.settlement.shift.Profession;
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
	/** Its goals are off — the town's core is dark (R10): no breath, no look, no wave. */
	public boolean hibernating;
	/** 0 — it watches no one, 1 — it watches a player; eased on the client (MOD-882). */
	public float watching;
	/** Ticks since its greeting began, or -1 — none (MOD-882). */
	public float greetTicks = -1.0F;
	/** Its trade, {@link Profession#NONE} — none: the patch on the shoulder (MOD-888, R151). */
	public Profession profession = Profession.NONE;
}

package dev.alaindustrial.client.render.entity;

/**
 * How a shift worker moves on top of a humanoid's animation (MOD-882): the owner's picks from the clips of
 * {@code tools/model_sources/shift_worker/} — the walk of variant B «heavy suit», the stand of variant A «alive» and
 * the greeting of variant C. No Minecraft type: the model reads these numbers and adds them to its parts, so the
 * formulas are tested on their own ({@code ShiftWorkerMotionTest}).
 *
 * <p>All angles are radians and all shifts pixels, in the model's signs: {@code +x} tilts the head down, an arm
 * back and the body forward; {@code +y} turns the head to its right; {@code +z} of a hanging right arm takes it
 * outward. The upper half — head, body, both arms — is lifted as one ({@link #lift}), because in a humanoid's model
 * they are siblings, not children of the body.
 *
 * <p><b>Walk (B).</b> A slower cadence than vanilla's for the same distance, a shorter swing, the weight rolling from
 * foot to foot, the upper half sinking on each foot's contact and the balloon on the back bouncing a beat later.
 * Everything but the swing is scaled by how fast the worker walks, so a worker that stops eases into its stand.
 *
 * <p><b>Stand (A).</b> Breathing — the upper half rises and falls, the arms open a little with it — the balloon
 * swaying after the breath, and a slow look from side to side while the worker watches no one.
 *
 * <p><b>Greeting (C).</b> The right arm goes up and out and waves, the head nods. While the arm is up it leans out
 * at least {@link #GREET_MIN_OUTWARD} from the vertical: the helmet is half a pixel wider than the head and the arm's
 * inner edge starts right at the head's edge, so a vertical arm would go into the helmet. The arm is raised by its
 * {@code xRot} before its {@code zRot} turns it (the part's rotation order is Z·Y·X), and for a raised right arm
 * «outward» is the negative {@code zRot}.
 */
public final class ShiftWorkerMotion {

	private static final float PI = (float) Math.PI;
	private static final float TAU = 2.0F * PI;

	/** Vanilla's walk phase per unit of walk position (HumanoidModel.setupAnim). */
	static final float VANILLA_RATE = 0.6662F;
	/** The heavy suit's steps against vanilla's over the same distance. */
	static final float CADENCE = 0.8F;
	/** Swing of the legs and the arms per unit of walk speed (vanilla: 1.4 and 1.0). */
	static final float LEG_SWING = 1.1F;
	static final float ARM_SWING = 0.64F;
	/** Walk speed at which the walk's extras are at full strength. */
	static final float FULL_WALK_SPEED = 1.0F / 3.0F;
	/** How far the upper half sinks on a foot's contact, in pixels. */
	static final float STEP_SINK = 1.1F;
	/** The balloon's bounce a beat after the contact. */
	static final float BALLOON_BOUNCE = rad(10.0F);
	static final float BALLOON_LAG = 0.72F;

	/** One breath, in ticks. */
	static final float BREATH_TICKS = 40.0F;
	/** One look from side to side, in ticks, and how far it turns the head. */
	static final float LOOK_TICKS = 80.0F;
	static final float LOOK_YAW = rad(10.0F);

	/** The greeting, in ticks. */
	public static final float GREET_TICKS = 48.0F;
	/** One wave of the hand, in ticks. */
	static final float WAVE_TICKS = 10.0F;
	/** The least the raised arm leans out from the vertical. */
	public static final float GREET_MIN_OUTWARD = rad(18.0F);

	// The result of the last compute(): deltas to add to the parts, the greeting's targets and its weight.
	public float headX;
	public float headY;
	public float headZ;
	public float bodyY;
	public float bodyZ;
	public float rightArmX;
	public float rightArmZ;
	public float leftArmX;
	public float leftArmZ;
	public float rightLegX;
	public float leftLegX;
	/** Pixels the upper half moves down (negative — up). */
	public float lift;
	/** The balloon's tilt about its top: {@code +x} swings its bottom away from the back. */
	public float packX;
	public float packZ;
	/** 0 — no greeting, 1 — the right arm is wholly the greeting's ({@link #greetArmX}, {@link #greetArmZ}). */
	public float greet;
	public float greetArmX;
	public float greetArmZ;

	/**
	 * Works out one frame.
	 *
	 * @param walkPos   the walk animation's position (distance walked)
	 * @param walkSpeed the walk animation's speed
	 * @param age       ticks lived, with the partial tick
	 * @param greetAge  ticks since the greeting began, or a negative number — none
	 * @param watching  0 — the worker watches no one, 1 — it watches a player; in between while it turns to one
	 */
	public void compute(float walkPos, float walkSpeed, float age, float greetAge, float watching) {
		float s = Math.max(0.0F, walkSpeed);
		float walk = clamp(s / FULL_WALK_SPEED);
		float stand = 1.0F - walk;

		// Walk: the heavy suit's swing replaces vanilla's.
		float vanilla = walkPos * VANILLA_RATE;
		float p = vanilla * CADENCE;
		rightArmX = cos(p + PI) * ARM_SWING * s - cos(vanilla + PI) * s;
		leftArmX = cos(p) * ARM_SWING * s - cos(vanilla) * s;
		rightLegX = cos(p) * LEG_SWING * s - cos(vanilla) * 1.4F * s;
		leftLegX = cos(p + PI) * LEG_SWING * s - cos(vanilla + PI) * 1.4F * s;
		float contact = 1.0F - Math.abs(sin(p));
		bodyY = cos(p) * 0.05F * walk;
		bodyZ = cos(p) * 0.07F * walk;
		headX = rad(4.0F) * walk;
		headY = 0.0F;
		headZ = -cos(p) * 0.05F * walk;
		rightArmZ = rad(8.0F) * walk;
		leftArmZ = -rad(8.0F) * walk;
		lift = STEP_SINK * contact * walk;
		packX = BALLOON_BOUNCE * (1.0F - Math.abs(sin(p - BALLOON_LAG))) * walk;
		packZ = 0.0F;

		// Stand: breathing, the balloon after the breath, a look about while watching no one.
		float breath = sin(age * TAU / BREATH_TICKS);
		lift -= 0.35F * breath * stand;
		headX -= rad(2.0F) * breath * stand;
		rightArmX -= rad(2.0F) * breath * stand;
		leftArmX += rad(2.0F) * breath * stand;
		rightArmZ += rad(3.0F + 1.5F * breath) * stand;
		leftArmZ -= rad(3.0F + 1.5F * breath) * stand;
		packX += rad(2.5F) * sin(age * TAU / BREATH_TICKS - 0.9F) * stand;
		float look = sin(age * TAU / LOOK_TICKS);
		headY += LOOK_YAW * look * look * look * stand * (1.0F - clamp(watching));

		// Greeting.
		greet = 0.0F;
		if (greetAge >= 0.0F && greetAge < GREET_TICKS) {
			float u = greetAge / GREET_TICKS;
			greet = clamp(Math.min(u / 0.15F, (1.0F - u) / 0.2F));
			float wave = sin(greetAge * TAU / WAVE_TICKS);
			greetArmX = rad(-150.0F);
			greetArmZ = -(GREET_MIN_OUTWARD + rad(12.0F) + rad(12.0F) * wave);
			float nod = sin(PI * clamp((greetAge - 0.55F * GREET_TICKS) / (0.18F * GREET_TICKS)));
			headX += rad(16.0F) * nod;
			headZ -= rad(4.0F) * greet;
			packZ += rad(2.0F) * sin((greetAge - 3.0F) * TAU / WAVE_TICKS) * greet;
		}
	}

	static float rad(float degrees) {
		return degrees * PI / 180.0F;
	}

	private static float sin(float a) {
		return (float) Math.sin(a);
	}

	private static float cos(float a) {
		return (float) Math.cos(a);
	}

	private static float clamp(float v) {
		return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
	}
}

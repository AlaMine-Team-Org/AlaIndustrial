package dev.alaindustrial.settlement.shift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * One fragment of a shift worker's biography — an entry of the datapack registry
 * {@code alaindustrial:shift_worker_biography} (MOD-854, epic decisions R26a and R72). A biography is four lines, one
 * fragment per {@link Slot}, chosen from the world seed and the worker's number ({@link ShiftWorkerBiography}). The
 * mod's fragments are data files of the same format a datapack would add:
 * {@code data/<ns>/alaindustrial/shift_worker_biography/<slot>/<name>.json}.
 *
 * <p><b>The text is not in the file.</b> {@link #key} names a translation; every fragment has two forms in every lang
 * file of the mod — {@code <key>.male} and {@code <key>.female} — because past-tense archive lines take the worker's
 * sex in many languages (Russian "prinyat" / "prinyata", "taken in"). A language with no such forms writes the same
 * text twice. There is no neutral single-key variant on purpose: one rule for every fragment keeps the lang files
 * checkable.
 *
 * <p><b>Substitutions.</b> {@link #substitutions} lists, in the order of the text's {@code %1$s}, {@code %2$s}, … what
 * the town supplies: a real house plaque of this town, the lab's number, or one digit of it ({@link Substitution}).
 * A fragment that needs a fact its town does not know is never chosen for that town.
 *
 * @param slot          which line of the biography the fragment fills
 * @param key           the translation key of the text, without the {@code .male} / {@code .female} suffix
 * @param tags          what the fragment says about the worker ("desert", "underground", …)
 * @param forbid        tags no other fragment of the same biography may carry
 * @param substitutions the town facts the text takes as arguments, in argument order
 * @param clue          the riddle question this fragment is evidence for (counted from stage 8; data only now)
 * @param effect        the profession bonus of an occupation (stage 5a; data only now, allowed on {@code occupation})
 * @param weight        how often the fragment is chosen against the others of its slot; 0 — never
 */
public record ShiftWorkerBiographyFragment(Slot slot, String key, List<String> tags, List<String> forbid,
		List<Substitution> substitutions, Optional<Identifier> clue, Optional<Identifier> effect, int weight) {

	/** The four lines of a biography, in the order they are read. */
	public enum Slot implements StringRepresentable {
		/** Where the worker was taken in from. */
		ORIGIN("origin"),
		/** What the worker did before. */
		OCCUPATION("occupation"),
		/** A habit the archive noted. */
		HABIT("habit"),
		/** A scrap of memory. */
		MEMORY("memory");

		public static final Codec<Slot> CODEC = StringRepresentable.fromEnum(Slot::values);
		private final String name;

		Slot(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** A town fact a fragment's text takes as an argument. */
	public enum Substitution implements StringRepresentable {
		/** The three digits of one house plaque of the worker's town. */
		PLAQUE("plaque"),
		/** The three digits of the lab under the town hall. */
		LAB("lab"),
		/** The first digit of the lab's number. */
		LAB_DIGIT_1("lab_digit_1"),
		/** The second digit of the lab's number. */
		LAB_DIGIT_2("lab_digit_2"),
		/** The third digit of the lab's number. */
		LAB_DIGIT_3("lab_digit_3");

		public static final Codec<Substitution> CODEC = StringRepresentable.fromEnum(Substitution::values);
		private final String name;

		Substitution(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	private static final Codec<ShiftWorkerBiographyFragment> RAW_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Slot.CODEC.fieldOf("slot").forGetter(ShiftWorkerBiographyFragment::slot),
					Codec.STRING.fieldOf("key").forGetter(ShiftWorkerBiographyFragment::key),
					Codec.STRING.listOf().optionalFieldOf("tags", List.of())
							.forGetter(ShiftWorkerBiographyFragment::tags),
					Codec.STRING.listOf().optionalFieldOf("forbid", List.of())
							.forGetter(ShiftWorkerBiographyFragment::forbid),
					Substitution.CODEC.listOf().optionalFieldOf("substitutions", List.of())
							.forGetter(ShiftWorkerBiographyFragment::substitutions),
					Identifier.CODEC.optionalFieldOf("clue").forGetter(ShiftWorkerBiographyFragment::clue),
					Identifier.CODEC.optionalFieldOf("effect").forGetter(ShiftWorkerBiographyFragment::effect),
					Codec.INT.optionalFieldOf("weight", 1).forGetter(ShiftWorkerBiographyFragment::weight)
			).apply(instance, ShiftWorkerBiographyFragment::new));

	/**
	 * The whole entry, as a datapack writes it. Unknown fields are skipped, so a newer pack still reads; an
	 * {@code effect} on any slot but {@code occupation} is refused, since only an occupation has a profession to add
	 * to.
	 */
	public static final Codec<ShiftWorkerBiographyFragment> CODEC = RAW_CODEC.validate(fragment ->
			fragment.effect().isPresent() && fragment.slot() != Slot.OCCUPATION
					? DataResult.error(() -> "biography fragment '" + fragment.key()
							+ "': an effect is allowed only on an occupation")
					: DataResult.success(fragment));

	/** The translation key of the text in the worker's form: {@code <key>.female} for a slim look, else {@code .male}.
	 */
	public String textKey(boolean slim) {
		return key + (slim ? ".female" : ".male");
	}
}

package dev.alaindustrial.settlement.shift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

/**
 * One look of a shift worker — an entry of the datapack registry {@code alaindustrial:shift_worker_look} (MOD-832,
 * ADR-050 point 6). The mod's twenty-four looks (two helmets × the eight faces of MOD-828 and the four of MOD-851) are
 * data files of the same format a datapack would add: {@code data/<ns>/alaindustrial/shift_worker_look/<look>.json}.
 *
 * <p><b>What the client gets.</b> The registry is synced; {@link #NETWORK_CODEC} carries only what drawing needs —
 * the texture, the body, the helmet and the hair. The conditions and the weight are the server's business.
 *
 * <p><b>The texture</b> is a 64 × 64 skin in a player's layout for the head, body, arms and legs, with the helmet
 * on the hat layer; the jacket, sleeve and trouser layers are not drawn, and their area holds the hair pieces of
 * {@link Hair} (MOD-832 research.md, "Skin layout") and, on a slim skin, the chest piece at (42, 32) (MOD-851). The id
 * is written without {@code textures/} and {@code .png}, as {@code alaindustrial:entity/shift_worker/window_f1}.
 *
 * @param texture    the skin, as a texture id without the {@code textures/} prefix and the {@code .png} suffix
 * @param model      {@code classic} (wide arms) or {@code slim} (thin arms and a chest piece on the suit, MOD-851) —
 *                   the only thing the worker's sex changes (R3g)
 * @param helmet     {@code open} or {@code window}; the window's glass is a translucent layer of the model
 * @param hair       the hair that comes out from under an open helmet; a window helmet keeps all hair inside
 * @param conditions where the look may be given; none — anywhere
 * @param weight     how often the look is chosen against the others; 0 — never
 */
public record ShiftWorkerLook(Identifier texture, BodyModel model, Helmet helmet, Hair hair,
		Optional<Conditions> conditions, int weight) {

	/** The two bodies of a player model. */
	public enum BodyModel implements StringRepresentable {
		CLASSIC("classic"),
		SLIM("slim");

		public static final Codec<BodyModel> CODEC = StringRepresentable.fromEnum(BodyModel::values);
		private final String name;

		BodyModel(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** The two helmets of variant E (MOD-828). */
	public enum Helmet implements StringRepresentable {
		/** The «open helmet»: a copper visor and frame, the face and the lower hair open. */
		OPEN("open"),
		/** The «window»: a closed helmet with a big glass window in front. */
		WINDOW("window");

		public static final Codec<Helmet> CODEC = StringRepresentable.fromEnum(Helmet::values);
		private final String name;

		Helmet(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Hair that comes out from under the open helmet as model pieces (MOD-828: never over the number plate). */
	public enum Hair implements StringRepresentable {
		NONE("none"),
		/** A ponytail down the back, ending above the number plate. */
		TAIL("tail"),
		/** A braid down the back, ending above the number plate. */
		BRAID("braid"),
		/** A bob around the sides and the back of the head, under the helmet's edge. */
		BOB("bob"),
		/** Curls around the sides and the back of the head, under the helmet's edge. */
		CURLS("curls");

		public static final Codec<Hair> CODEC = StringRepresentable.fromEnum(Hair::values);
		private final String name;

		Hair(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/**
	 * Where a look may be given.
	 *
	 * @param biomes      the biome the worker is issued in must be in this tag
	 * @param professions the worker's profession must be one of these (from stage 5a; ignored before it)
	 */
	public record Conditions(Optional<TagKey<Biome>> biomes, List<Identifier> professions) {

		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TagKey.hashedCodec(Registries.BIOME).optionalFieldOf("biomes").forGetter(Conditions::biomes),
				Identifier.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(Conditions::professions)
		).apply(instance, Conditions::new));
	}

	/** The whole entry, as a datapack writes it. Unknown fields are skipped, so a newer pack still reads. */
	public static final Codec<ShiftWorkerLook> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("texture").forGetter(ShiftWorkerLook::texture),
			BodyModel.CODEC.fieldOf("model").forGetter(ShiftWorkerLook::model),
			Helmet.CODEC.fieldOf("helmet").forGetter(ShiftWorkerLook::helmet),
			Hair.CODEC.optionalFieldOf("hair", Hair.NONE).forGetter(ShiftWorkerLook::hair),
			Conditions.CODEC.optionalFieldOf("conditions").forGetter(ShiftWorkerLook::conditions),
			Codec.INT.optionalFieldOf("weight", 1).forGetter(ShiftWorkerLook::weight)
	).apply(instance, ShiftWorkerLook::new));

	/** What the client gets: only what drawing needs (ADR-050 point 3). */
	public static final Codec<ShiftWorkerLook> NETWORK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("texture").forGetter(ShiftWorkerLook::texture),
			BodyModel.CODEC.fieldOf("model").forGetter(ShiftWorkerLook::model),
			Helmet.CODEC.fieldOf("helmet").forGetter(ShiftWorkerLook::helmet),
			Hair.CODEC.optionalFieldOf("hair", Hair.NONE).forGetter(ShiftWorkerLook::hair)
	).apply(instance, (texture, model, helmet, hair) ->
			new ShiftWorkerLook(texture, model, helmet, hair, Optional.empty(), 1)));

	/** The texture as a resource id: {@code <ns>:textures/<path>.png}. */
	public Identifier textureLocation() {
		return texture.withPath(path -> "textures/" + path + ".png");
	}

	/** Whether this look has the slim body — a woman's look (R3g: the sex changes only the look). */
	public boolean slim() {
		return model == BodyModel.SLIM;
	}
}

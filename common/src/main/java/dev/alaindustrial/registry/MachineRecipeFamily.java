package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

/**
 * What the three machine recipe families have in common (MOD-708): an id, a default energy cost, the
 * block that works the family, its draw per tick, the codec factories of its recipe class, and the type
 * and serializer each loader binds. {@link ModRecipes.Kind} (positional item input),
 * {@link ModRecipes.FluidKind} (a fluid input) and {@link ModRecipes.AlloyKind} (an unordered three-slot
 * input) differ only in the input type and the recipe class, which are this class's type parameters.
 *
 * @param <I> the recipe input the machine matches against
 * @param <R> the recipe class of the family
 * @param <F> the concrete family class, handed to the codec factories
 */
public abstract sealed class MachineRecipeFamily<I extends RecipeInput, R extends Recipe<I>,
		F extends MachineRecipeFamily<I, R, F>> implements RecipeFamily<R>
		permits ModRecipes.Kind, ModRecipes.FluidKind, ModRecipes.AlloyKind {
	private final String id;
	private final int defaultEnergy;
	/** Read lazily: the {@link ModContent} slot is rebound by whichever loader registered it. */
	private final Supplier<Block> station;
	/**
	 * Read lazily: the balance is reloadable, so a captured int would go stale. Every family reads it
	 * through {@code ServerBalance} (MOD-743) — the rate is shown in the recipe viewers, never used by a
	 * machine.
	 */
	private final IntSupplier euPerTick;
	private final Function<F, MapCodec<R>> mapCodecFactory;
	private final Function<F, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory;
	private Supplier<RecipeType<R>> type = unbound("type");
	private Supplier<RecipeSerializer<R>> serializer = unbound("serializer");

	MachineRecipeFamily(String id, int defaultEnergy, Supplier<Block> station, IntSupplier euPerTick,
			Function<F, MapCodec<R>> mapCodecFactory,
			Function<F, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodecFactory) {
		this.id = id;
		this.defaultEnergy = defaultEnergy;
		this.station = station;
		this.euPerTick = euPerTick;
		this.mapCodecFactory = mapCodecFactory;
		this.streamCodecFactory = streamCodecFactory;
	}

	private <T> Supplier<T> unbound(String what) {
		return () -> {
			throw new IllegalStateException("ModRecipes." + getClass().getSimpleName() + " " + what
					+ " read before its loader bound it");
		};
	}

	/** This family as its concrete class, for the codec factories. */
	@SuppressWarnings("unchecked")
	private F self() {
		return (F) this;
	}

	@Override
	public String id() {
		return id;
	}

	public int defaultEnergy() {
		return defaultEnergy;
	}

	/**
	 * The block that works this family — what a recipe viewer shows as the category icon and
	 * registers as the crafting station (MOD-558).
	 *
	 * <p><b>Why it lives on the family.</b> The pair "family → machine" used to be written out once
	 * per viewer: a {@code MACHINES} table in the REI plugin and one in each of the two JEI
	 * plugins. Three hand-kept lists of the same fact, none of which the compiler could compare —
	 * and the JEI ones ran from a static initialiser, so a family missing from one of them took
	 * the whole viewer down rather than one category (MOD-146: the fermenter shipped a release
	 * cycle with no Ala Industrial recipe cards at all on NeoForge). Declared here, next to the
	 * family itself, the viewers replay the family lists and there is no second list to forget.
	 * A new family cannot be added without naming its station: the constructor demands one.
	 */
	public Supplier<Block> station() {
		return station;
	}

	/**
	 * What the machine working this family draws per tick, as the SERVER sets it. Almost every machine
	 * shares one rate.
	 *
	 * <p>For the recipe viewers and tooltips only (MOD-743): the machines tick on their own tariff
	 * ({@code baseEuPerTick}) and never call this. The declarations in {@link ModRecipes} therefore read
	 * the rate through {@code ServerBalance} — the server's snapshot on a client, the local {@code Config}
	 * everywhere else — and {@code ArchitectureRules.recipeDataReadsNoBalanceKnob} keeps a {@code Config}
	 * read out of them: on a dedicated server that is the player's own file. One viewer does call it on the
	 * server: REI's server-side filler times the distillation column's card ({@code FluidOutputDisplay})
	 * there and sends the ticks over, which is the server's own rate, but fixed until {@code /reload}.
	 */
	public int euPerTick() {
		return Math.max(1, euPerTick.getAsInt());
	}

	/**
	 * Base processing time of a recipe of this family costing {@code energy} EU — the number the
	 * recipe viewers print. It has to divide by <em>this family's</em> rate: the incubator draws
	 * four times what the other machines do, and the shared rate made its 15 seconds read as 60.
	 * The global speed multiplier is a runtime balance knob and deliberately not applied — the
	 * viewer shows the recipe's intrinsic time. Viewer-only, like {@link #euPerTick()}: the rate is the
	 * server's (MOD-743), read on the client — or, for REI's distillation-column card, on the server.
	 */
	public int ticksFor(int energy) {
		return Math.max(1, energy / euPerTick());
	}

	public RecipeType<R> type() {
		return type.get();
	}

	@Override
	public RecipeSerializer<R> serializer() {
		return serializer.get();
	}

	@Override
	public Optional<RecipeType<R>> createType() {
		return Optional.of(RecipeFamily.namedType(id));
	}

	@Override
	public RecipeSerializer<R> createSerializer() {
		return new RecipeSerializer<>(mapCodecFactory.apply(self()), streamCodecFactory.apply(self()));
	}

	/** Bind this family's type + serializer suppliers. Called once per loader during its registration. */
	@Override
	public void bind(Supplier<RecipeType<R>> typeSupplier, Supplier<RecipeSerializer<R>> serializerSupplier) {
		this.type = typeSupplier;
		this.serializer = serializerSupplier;
	}

	/** A per-machine cached lookup (mirrors vanilla {@code AbstractFurnaceBlockEntity.quickCheck}). */
	public RecipeManager.CachedCheck<I, R> newCheck() {
		return RecipeManager.createCheck(type.get());
	}
}

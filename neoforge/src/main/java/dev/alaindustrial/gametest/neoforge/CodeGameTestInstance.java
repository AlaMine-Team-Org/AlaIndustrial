package dev.alaindustrial.gametest.neoforge;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A {@link GameTestInstance} that runs an in-memory {@code Consumer<GameTestHelper>} directly (MOD-022,
 * NeoForge world gametest lane).
 *
 * <p><b>Why not {@code FunctionGameTestInstance}.</b> The vanilla {@code FunctionGameTestInstance}
 * (verified against {@code net.minecraft.gametest.framework.FunctionGameTestInstance}, 26.2.0.8-beta
 * sources) does NOT hold the test body — it holds a {@code ResourceKey<Consumer<GameTestHelper>>} into
 * {@code Registries.TEST_FUNCTION} and re-resolves the body from the registry at run time. That registry
 * is a {@code BuiltInRegistries} simple registry
 * ({@code BuiltInRegistries.TEST_FUNCTION = registerSimple(..., BuiltinTestFunctions::bootstrap)}),
 * populated at class-init via {@code TestFunctionLoader.runLoaders} and frozen before any mod code runs.
 * NeoForge's {@code RegisterGameTestsEvent} only exposes the {@code TEST_ENVIRONMENT} and
 * {@code TEST_INSTANCE} registries (verified: {@code RegisterGameTestsEvent} fields + the
 * {@code RegistryDataLoader} call site) — there is no hook to add a {@code TEST_FUNCTION} entry. So the
 * function-key route is a dead end for a mod; a mod must register a {@code GameTestInstance} whose
 * {@code run(GameTestHelper)} owns the body.
 *
 * <p><b>Why a custom subclass is safe at run time.</b> {@code GameTestServer.evaluateTestsToRun} reads
 * live {@code Holder.Reference<GameTestInstance>} objects straight out of {@code Registries.TEST_INSTANCE}
 * and {@code GameTestInfo} calls {@code this.test.value().run(new GameTestHelper(this))} on the in-memory
 * instance (both verified in the 26.2.0.8-beta sources). No codec round-trip is applied to a registered
 * instance, so {@link #codec()} is never invoked on this run path — it exists only to satisfy the abstract
 * contract and would only matter if the instance were serialized to a datapack, which the gametest server
 * never does for programmatically registered tests.
 */
public final class CodeGameTestInstance extends GameTestInstance {

	private final Consumer<GameTestHelper> body;

	public CodeGameTestInstance(Consumer<GameTestHelper> body,
			TestData<Holder<TestEnvironmentDefinition<?>>> info) {
		super(info);
		this.body = body;
	}

	@Override
	public void run(GameTestHelper helper) {
		this.body.accept(helper);
	}

	/**
	 * Self-codec, registered under {@code alaindustrial:code} in {@code TEST_INSTANCE_TYPE} (see
	 * {@code NeoForgeGameTests#INSTANCE_TYPES}). The {@code gameTestServer} runs the in-memory instances
	 * directly and never touches this — but a real client DOES encode the whole {@code TEST_INSTANCE}
	 * registry during the known-packs handshake ({@code ServerboundSelectKnownPacks}), and the earlier
	 * approach (borrowing {@code FunctionGameTestInstance.CODEC}) threw {@code ClassCastException} there and
	 * broke world load. Encoding only the {@link net.minecraft.gametest.framework.TestData} is enough for the
	 * registry sync; decode rebuilds an instance with a no-op body (it is never run — only the in-memory
	 * body registered via {@code RegisterGameTestsEvent} ever executes).
	 */
	public static final MapCodec<CodeGameTestInstance> CODEC =
			TestData.CODEC.xmap(data -> new CodeGameTestInstance(helper -> { }, data), CodeGameTestInstance::info);

	@Override
	public MapCodec<? extends GameTestInstance> codec() {
		return CODEC;
	}

	@Override
	protected MutableComponent typeDescription() {
		return Component.literal("alaindustrial:code");
	}
}

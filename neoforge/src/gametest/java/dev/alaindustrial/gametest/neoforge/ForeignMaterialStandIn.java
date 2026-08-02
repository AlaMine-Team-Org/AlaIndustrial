package dev.alaindustrial.gametest.neoforge;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The NeoForge twin of {@code dev.alaindustrial.gametest.ForeignMaterialMod} (MOD-290): the same two
 * item ids under the same foreign namespace, so the shared scenario bodies look them up by id and run
 * unchanged on both loaders.
 *
 * <p>Unlike Fabric, NeoForge runs the gametests inside the main mod rather than as a separate one, so
 * "foreign" here means the namespace and nothing else — which is exactly the part that matters: the
 * recipes under test reach these items through {@code #c:ingots/tin} and {@code #c:ores/tin} (added by
 * this source set's own datapack under {@code neoforge/src/gametest/resources}), never by id.
 *
 * <p>Dev-only by construction — the {@code gametest} source set is part of the mod in dev runs and
 * absent from the shipped jar (MOD-242) — plus the same {@link FMLEnvironment#isProduction()} belt the
 * energy stand-in wears.
 *
 * <p>{@link DeferredRegister} rather than an eager {@code Registry.register}: NeoForge freezes the
 * registries before mod init, so eager registration there throws {@code already frozen}. The register
 * is created with the foreign namespace, which is all it takes to own ids in it.
 */
public final class ForeignMaterialStandIn {
	private ForeignMaterialStandIn() {
	}

	/** Namespace of the imaginary third-party mod. Anything but {@code alaindustrial}. */
	public static final String NAMESPACE = "foreignmod";

	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

	static {
		ITEMS.registerItem("tin_ingot", Item::new);
		ITEMS.registerItem("tin_ore", Item::new);
	}

	/** Wire the register onto the mod bus — dev/gametest environments only. */
	public static void register(IEventBus modBus) {
		if (FMLEnvironment.isProduction()) {
			return;
		}
		ITEMS.register(modBus);
	}
}

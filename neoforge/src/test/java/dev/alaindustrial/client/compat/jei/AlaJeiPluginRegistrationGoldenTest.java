package dev.alaindustrial.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ItemLike;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Characterization of what the JEI plugin registers (MOD-716, batches 11–12): the plugin's own
 * {@code registerCategories}, {@code registerRecipeCatalysts} and {@code registerGuiHandlers} run against
 * recording stand-ins of JEI's registration interfaces, and the record is held against the committed
 * {@code jei-plugin-registration.golden.txt}.
 *
 * <p><b>What is pinned, and in which order.</b>
 * <ul>
 *   <li>Categories in REGISTRATION order — that order is the order of JEI's recipe tabs, which the owner
 *       decided (2026-10-04) each viewer keeps as shipped. Each line carries the uid, the title (its
 *       translation keys), the size, and the icon item the category asked the GUI helper for.</li>
 *   <li>Crafting stations grouped by recipe type, in registration order WITHIN a type. JEI files them
 *       per type, so the order across types is not observable; the order of the stations of one type is
 *       (it is the order of the catalyst column).</li>
 *   <li>Click areas grouped by screen class, in registration order within a screen: JEI keys them by the
 *       screen class (an {@code IGuiContainerHandler} per call), so only the order on one screen matters.</li>
 *   <li>Container and ghost-ingredient handlers by screen class.</li>
 * </ul>
 *
 * <p><b>Not pinned here:</b> {@code registerRecipes}. It reads the client's synced recipes through
 * {@code Minecraft.getInstance()}, which is null on this lane. Its observable output — the
 * {@code Registered AlaIndustrial JEI recipes: …} line — is held by the {@code jei-smoke} lane of the build
 * server ({@code docs/tools/testing/jei_smoke_check.py}).
 *
 * <p>JEI's API is on this lane's runtime classpath (the full JEI jar is a {@code runtimeOnly} of the
 * NeoForge module) but not on its compile classpath, so the registration interfaces are reached by name
 * and implemented with {@link Proxy}; nothing here compiles against JEI.
 *
 * <p><b>Updated only by an explicit command (ADR-032):</b>
 * <pre>
 * GOLDEN=&lt;repo&gt;/neoforge/src/test/resources/dev/alaindustrial/client/compat/jei
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.jeiPluginGolden.writeTo=$GOLDEN"
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.client.compat.jei.AlaJeiPluginRegistrationGoldenTest
 * </pre>
 * The run writes the file and then fails on purpose.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class AlaJeiPluginRegistrationGoldenTest {

	static final String WRITE_TO_PROPERTY = "alaindustrial.jeiPluginGolden.writeTo";

	static final String GOLDEN_FILE = "jei-plugin-registration.golden.txt";

	private static final String RESOURCE = "/dev/alaindustrial/client/compat/jei/" + GOLDEN_FILE;

	private static final String PLUGIN = "dev.alaindustrial.client.compat.jei.AlaJeiPlugin";

	@Test
	void everyRegistrationMatchesTheGoldenFile(MinecraftServer server) throws Exception {
		List<String> actual = capture();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path target = Path.of(writeTo).resolve(GOLDEN_FILE);
			Files.createDirectories(target.getParent());
			Files.writeString(target, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
			fail("JEI plugin golden rewritten at " + target + " (" + actual.size() + " lines) - review the diff,"
					+ " then run again without -D" + WRITE_TO_PROPERTY);
		}
		assertEquals(readGolden(), actual, "MOD-716: the JEI plugin registers a different tab order, title, icon,"
				+ " station or click area; a deliberate change updates the golden file with the command in this"
				+ " class's javadoc (ADR-032)");
	}

	/** Runs the three registration methods and renders what they registered, one line per fact. */
	static List<String> capture() throws Exception {
		Object plugin = Class.forName(PLUGIN).getConstructor().newInstance();
		Recording rec = new Recording();
		invoke(plugin, "registerCategories", "mezz.jei.api.registration.IRecipeCategoryRegistration", rec);
		invoke(plugin, "registerRecipeCatalysts", "mezz.jei.api.registration.IRecipeCatalystRegistration", rec);
		invoke(plugin, "registerGuiHandlers", "mezz.jei.api.registration.IGuiHandlerRegistration", rec);

		List<String> out = new ArrayList<>();
		out.add("# categories, in registration (= tab) order");
		out.addAll(rec.categories);
		out.add("# crafting stations by recipe type");
		rec.stations.forEach((type, items) -> out.add("station " + type + " " + String.join(" ", items)));
		out.add("# click areas by screen");
		rec.clickAreas.forEach((screen, areas) -> areas.forEach(area -> out.add("click " + screen + " " + area)));
		out.add("# handlers by screen");
		out.addAll(rec.handlers);
		if (!rec.unexpected.isEmpty()) {
			out.add("# unexpected calls");
			out.addAll(rec.unexpected);
		}
		return out;
	}

	private static void invoke(Object plugin, String method, String registrationInterface, Recording rec)
			throws Exception {
		Class<?> type = Class.forName(registrationInterface);
		plugin.getClass().getMethod(method, type).invoke(plugin, rec.stub(type));
	}

	/** Records every registration call; any other interface method answers with a do-nothing stand-in. */
	private static final class Recording implements InvocationHandler {
		final List<String> categories = new ArrayList<>();
		final Map<String, List<String>> stations = new TreeMap<>();
		final Map<String, List<String>> clickAreas = new TreeMap<>();
		final List<String> handlers = new ArrayList<>();
		final List<String> unexpected = new ArrayList<>();
		/** The icon the GUI helper was last asked for — a category asks for it in its constructor. */
		private String lastIcon = "-";

		Object stub(Class<?> type) {
			return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, this);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch (method.getName()) {
				case "toString":
					return "stub";
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "addRecipeCategories":
					for (Object category : (Object[]) args[0]) {
						categories.add(describeCategory(category));
					}
					return null;
				case "createDrawableItemLike":
					lastIcon = itemId((ItemLike) args[0]);
					return blank(method.getReturnType());
				case "addCraftingStation":
					if (args.length == 2 && args[1] instanceof ItemLike[] items) {
						List<String> list = stations.computeIfAbsent(uid(args[0]), k -> new ArrayList<>());
						for (ItemLike item : items) {
							list.add(itemId(item));
						}
						return null;
					}
					break;
				case "addRecipeClickArea":
					clickAreas.computeIfAbsent(((Class<?>) args[0]).getSimpleName(), k -> new ArrayList<>())
							.add(args[1] + " " + args[2] + " " + args[3] + " " + args[4]
									+ " -> " + uids((Object[]) args[5]));
					return null;
				case "addGuiContainerHandler":
				case "addGhostIngredientHandler":
					handlers.add(method.getName() + " " + ((Class<?>) args[0]).getSimpleName() + " "
							+ handlerName(args[1]));
					return null;
				default:
					break;
			}
			Class<?> ret = method.getReturnType();
			if (ret.isInterface()) {
				return stub(ret);
			}
			if (ret == void.class) {
				unexpected.add(method.getDeclaringClass().getSimpleName() + "." + method.getName());
				return null;
			}
			return blank(ret);
		}

		private Object blank(Class<?> ret) {
			if (ret == boolean.class) {
				return false;
			}
			if (ret == int.class) {
				return 0;
			}
			return ret.isInterface() ? stub(ret) : null;
		}

		private String describeCategory(Object category) throws Exception {
			Class<?> api = Class.forName("mezz.jei.api.recipe.category.IRecipeCategory");
			Object type = api.getMethod("getRecipeType").invoke(category);
			Component title = (Component) api.getMethod("getTitle").invoke(category);
			int width = (int) api.getMethod("getWidth").invoke(category);
			int height = (int) api.getMethod("getHeight").invoke(category);
			String line = "category " + uid(type) + " " + width + "x" + height + " icon=" + lastIcon
					+ " title=" + text(title);
			lastIcon = "-";
			return line;
		}
	}

	private static String uid(Object recipeType) throws Exception {
		return Class.forName("mezz.jei.api.recipe.types.IRecipeType").getMethod("getUid").invoke(recipeType).toString();
	}

	private static String uids(Object[] recipeTypes) throws Exception {
		List<String> out = new ArrayList<>();
		for (Object type : recipeTypes) {
			out.add(uid(type));
		}
		return String.join(",", out);
	}

	private static String itemId(ItemLike item) {
		return BuiltInRegistries.ITEM.getKey(item.asItem()).toString();
	}

	/** A named handler by its class; an anonymous one by the interface it implements. */
	private static String handlerName(Object handler) {
		Class<?> type = handler.getClass();
		return type.isAnonymousClass() ? "anonymous " + type.getInterfaces()[0].getSimpleName() : type.getSimpleName();
	}

	/** A title as its translation keys and literals, siblings in order — the language plays no part. */
	private static String text(Component component) {
		StringBuilder out = new StringBuilder();
		if (component.getContents() instanceof TranslatableContents translatable) {
			out.append('{').append(translatable.getKey()).append('}');
		} else {
			out.append(component.getContents());
		}
		for (Component sibling : component.getSiblings()) {
			out.append(" + ").append(text(sibling));
		}
		return out.toString();
	}

	private static List<String> readGolden() throws IOException {
		try (InputStream in = AlaJeiPluginRegistrationGoldenTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(in, RESOURCE + " is missing - capture it with the command in this class's javadoc");
			List<String> lines = new ArrayList<>(List.of(
					new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)));
			if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			return lines;
		}
	}
}

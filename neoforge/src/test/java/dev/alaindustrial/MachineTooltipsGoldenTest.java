package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.tool.NetworkScanData;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModDataComponents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Characterization of every hover tooltip {@link MachineTooltips} writes (MOD-716, batch 0).
 *
 * <p><b>What is pinned.</b> For every item of this mod, in a handful of stack states (plain; for a powered
 * item also one EU, full, a full stack of two and full with its modes switched; a scanned analyzer; a dyed
 * cable) and in each of the four modes [SHIFT] up/down x {@code showEuNumbers} on/off, the exact sequence of
 * lines: translation key, arguments (nested components rendered the same way), colour and appended
 * siblings. Two vanilla probes cover the item-independent prologue (a plain vanilla item, a graded ingot).
 * The tooltip refactoring of MOD-716 (batches 9 and 10: the owner-declared {@code MachineTooltipSpec}, one
 * powered-tool tooltip) must leave this file byte-identical.
 *
 * <p>Numbers are the compiled defaults read through {@link ServerBalance} with no server snapshot received,
 * so the golden file describes a fresh install of this line (ADR-029: each line captures its own).
 *
 * <p><b>Updated only by an explicit command (ADR-032)</b>, never by the build, a hook or {@code regen.py}:
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.tooltipGolden.writeTo=&lt;repo&gt;/neoforge/src/test/resources/dev/alaindustrial"
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.MachineTooltipsGoldenTest
 * </pre>
 * The run writes the file and then fails on purpose, so it can never pass for a check. Review the diff as a
 * change of what players read.
 *
 * <p>Runs on the NeoForge L1.5 lane because the stacks need the live item and data-component registries;
 * {@link MachineTooltips} is loader-neutral {@code common} code, so the same bytes are what Fabric shows.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class MachineTooltipsGoldenTest {

	/** System property naming the DIRECTORY the explicit update command writes the golden file into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.tooltipGolden.writeTo";

	static final String GOLDEN_FILE = "machine-tooltips.golden.txt";

	private static final String RESOURCE = "/dev/alaindustrial/" + GOLDEN_FILE;

	/** The four modes, in the order every stack state lists them: shift x EU numbers. */
	private static final boolean[][] MODES = {{false, true}, {true, true}, {false, false}, {true, false}};

	@Test
	void everyTooltipMatchesTheGoldenFile(MinecraftServer server) throws IOException {
		List<String> actual = capture(server);
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path target = Path.of(writeTo).resolve(GOLDEN_FILE);
			Files.createDirectories(target.getParent());
			Files.writeString(target, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
			fail("tooltip golden rewritten at " + target + " (" + actual.size() + " lines) - review the diff,"
					+ " then run again without -D" + WRITE_TO_PROPERTY);
		}
		List<String> expected = readGolden();
		int first = firstDifference(expected, actual);
		if (first >= 0) {
			fail("MOD-716: tooltip differs from " + GOLDEN_FILE + " at line " + (first + 1) + ":\n  expected: "
					+ at(expected, first) + "\n  actual:   " + at(actual, first) + "\n  under: "
					+ header(actual, first) + "\nA deliberate tooltip change updates the golden file with the"
					+ " command in this class's javadoc (ADR-032).");
		}
		assertEquals(expected.size(), actual.size(), "line count");
	}

	/** Floor: the capture reaches the code it pins (machines, a powered tool, the analyzer). */
	@Test
	void captureCoversMachinesToolsAndTheAnalyzer(MinecraftServer server) {
		String all = String.join("\n", capture(server));
		assertTrue(all.contains("tooltip.alaindustrial.energy_per_op"), "no machine detail line captured");
		assertTrue(all.contains("tooltip.alaindustrial.electric_drill.charge"), "no powered-tool charge line captured");
		assertTrue(all.contains("tooltip.alaindustrial.electric_drill_diamond_tip.silk_on"), "no silk mode captured");
		assertTrue(all.contains("tooltip.alaindustrial.network_analyzer.flow"), "no analyzer scan captured");
		assertTrue(all.contains("tooltip.alaindustrial.battery.stack_total"), "no battery stack line captured");
	}

	/** The whole tooltip table, one line per tooltip line under a header per stack state and mode. */
	static List<String> capture(MinecraftServer server) {
		boolean previousNumbers = AlaClientConfig.showEuNumbers;
		boolean previousDetailed = AlaClientConfig.alwaysDetailedTooltips;
		ServerBalance.reset();
		AlaClientConfig.alwaysDetailedTooltips = false;
		try {
			List<String> out = new ArrayList<>();
			for (Map.Entry<String, ItemStack> state : states(server).entrySet()) {
				List<String> body = new ArrayList<>();
				for (boolean[] mode : MODES) {
					AlaClientConfig.showEuNumbers = mode[1];
					List<Component> lines = new ArrayList<>();
					MachineTooltips.append(state.getValue().copy(), lines, mode[0]);
					body.add("-- " + (mode[0] ? "shift" : "plain") + (mode[1] ? " eu" : " no-eu"));
					for (Component line : lines) {
						body.add(render(line));
					}
				}
				if (body.size() == MODES.length) {
					out.add("== " + state.getKey() + " : none");
				} else {
					out.add("== " + state.getKey());
					out.addAll(body);
				}
			}
			return out;
		} finally {
			AlaClientConfig.showEuNumbers = previousNumbers;
			AlaClientConfig.alwaysDetailedTooltips = previousDetailed;
		}
	}

	/** Every stack state, keyed "item-id state", in item-id order. */
	private static Map<String, ItemStack> states(MinecraftServer server) {
		Holder<Enchantment> silkTouch = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SILK_TOUCH);
		Map<String, ItemStack> states = new LinkedHashMap<>();
		TreeSet<String> ids = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				ids.add(id.toString());
			}
		}
		assertTrue(ids.size() > 100, "the item registry holds only " + ids.size() + " items of this mod");
		for (String id : ids) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
			assertNotNull(item, id);
			states.put(id + " plain", new ItemStack(item));
			if (item instanceof PoweredItem) {
				states.put(id + " eu=1", charged(item, s -> 1L, 1));
				states.put(id + " full", charged(item, ItemEnergy::capacity, 1));
				if (item.getDefaultMaxStackSize() > 1) {
					states.put(id + " full x2", charged(item, ItemEnergy::capacity, 2));
				}
				ItemStack modes = charged(item, ItemEnergy::capacity, 1);
				if (switchModes(modes, silkTouch)) {
					states.put(id + " full modes", modes);
				}
			}
			if (item instanceof NetworkAnalyzerItem) {
				ItemStack scanned = new ItemStack(item);
				scanned.set(ModDataComponents.NETWORK_ANALYZER_MODE.get(), AnalyzerMode.STOP_AT_STORAGE);
				scanned.set(ModDataComponents.NETWORK_SCAN.get(), new NetworkScanData(12, 2, 3, 1, 40L, 32L, 30L));
				states.put(id + " scanned", scanned);
			}
			if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof CableBlock) {
				ItemStack dyed = new ItemStack(item);
				dyed.set(ModDataComponents.CABLE_COLOR.get(), DyeColor.RED);
				states.put(id + " dyed", dyed);
			}
		}
		states.put("minecraft:dirt plain", new ItemStack(Items.DIRT));
		ItemStack graded = new ItemStack(Items.IRON_INGOT);
		MutationGrades.set(graded, MutationGrade.RARE);
		states.put("minecraft:iron_ingot graded", graded);
		return states;
	}

	private static ItemStack charged(Item item, Function<ItemStack, Long> charge, int count) {
		ItemStack stack = new ItemStack(item);
		stack.setCount(count);
		ItemEnergy.set(stack, charge.apply(stack));
		return stack;
	}

	/**
	 * Switches every mode a powered tool's tooltip reports away from its default: Silk Touch on the three
	 * diamond-tipped tools, the column bore installed and on for a drill, the saber's blade off. Returns
	 * whether the item has any such mode.
	 */
	private static boolean switchModes(ItemStack stack, Holder<Enchantment> silkTouch) {
		Item item = stack.getItem();
		boolean any = false;
		if (item instanceof ElectricDrillDiamondTipItem || item instanceof ElectricChainsawDiamondTipItem
				|| item instanceof ElectricShovelDiamondTipItem) {
			stack.enchant(silkTouch, 1);
			any = true;
		}
		if (item instanceof ElectricDrillItem) {
			DrillUpgrades.install(stack, DrillUpgrades.COLUMN_BORE);
			ElectricDrillItem.setColumnEnabled(stack, true);
			any = true;
		}
		if (item instanceof ElectricSaberItem) {
			ElectricSaberItem.setEnabled(stack, false);
			any = true;
		}
		return any;
	}

	/** One component as text: key or literal, arguments, colour, then its siblings. */
	static String render(Component component) {
		StringBuilder out = new StringBuilder();
		ComponentContents contents = component.getContents();
		if (contents instanceof TranslatableContents translatable) {
			out.append(translatable.getKey());
			Object[] args = translatable.getArgs();
			if (args.length > 0) {
				out.append('(');
				for (int i = 0; i < args.length; i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(args[i] instanceof Component nested
							? "<" + render(nested) + ">" : String.valueOf(args[i]));
				}
				out.append(')');
			}
		} else if (contents instanceof PlainTextContents plain) {
			out.append('"').append(plain.text()).append('"');
		} else {
			out.append(contents.getClass().getSimpleName()).append(':').append(component.getString());
		}
		TextColor color = component.getStyle().getColor();
		if (color != null) {
			out.append(" {").append(color.serialize()).append('}');
		}
		for (Component sibling : component.getSiblings()) {
			out.append(" + ").append(render(sibling));
		}
		return out.toString();
	}

	private static List<String> readGolden() throws IOException {
		try (InputStream in = MachineTooltipsGoldenTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(in, RESOURCE + " is missing - capture it with the command in this class's javadoc");
			List<String> lines = new ArrayList<>(List.of(
					new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)));
			if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			return lines;
		}
	}

	static int firstDifference(List<String> expected, List<String> actual) {
		int shared = Math.min(expected.size(), actual.size());
		for (int i = 0; i < shared; i++) {
			if (!expected.get(i).equals(actual.get(i))) {
				return i;
			}
		}
		return expected.size() == actual.size() ? -1 : shared;
	}

	private static String at(List<String> lines, int index) {
		return index < lines.size() ? lines.get(index) : "<end of file>";
	}

	/** The nearest "== state" header at or above {@code index}, so a failure names the item. */
	private static String header(List<String> lines, int index) {
		for (int i = Math.min(index, lines.size() - 1); i >= 0; i--) {
			if (lines.get(i).startsWith("== ")) {
				return lines.get(i);
			}
		}
		return "<none>";
	}
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral gametest bodies for the low-charge warning (MOD-652): the half of the feature the L1 suites cannot
 * reach — that the debit in {@code ItemEnergy.spend} really puts a beep and an action-bar line on the wire of the
 * one player whose item it was, and on nobody else's. The decision rules are {@code LowChargeRuleTest}'s and the
 * per-player clocks {@code LowChargeLedgerTest}'s.
 *
 * <p>The packets are read off the mock player's in-memory channel. The mock has a live
 * {@code EmbeddedChannel} connection ({@link AlaGameTestHelper#survivalPlayer}) with no packet codec in its
 * pipeline, so what {@code Connection.send} writes stays an object in the outbound queue. Reaching the channel
 * takes two reflective reads of fields vanilla keeps private; the names are the unobfuscated ones the dev runtime
 * uses on both loaders.
 */
public final class LowChargeWarningScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(LowChargeWarningScenarios::fun01CrossingWarnsOnlyTheOwner,
						"low_charge_crossing_warns_only_the_owner").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::fun02SecondSpendInTheSameSecondIsSilent,
						"low_charge_repeat_is_rate_limited").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::fun03JetpackRunningOutHasItsOwnSignal,
						"low_charge_jetpack_depletion_signal").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::fun04ToolsWarnToo, "low_charge_tools_warn").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::fun05ToolSaysDepletedWhenItCannotPayAgain,
						"low_charge_tool_depleted_once").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::neg01AboveTheThresholdIsSilent,
						"low_charge_above_threshold_silent").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::neg02CreativeGetsNothing, "low_charge_creative_silent")
						.ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::neg03SwitchedOffGetsNothing,
						"low_charge_switched_off_silent").ticks(20, 40),
				RosterEntry.of(LowChargeWarningScenarios::neg04MobsGetNothing, "low_charge_mobs_silent")
						.ticks(20, 40));

		private Roster() {}
	}

	private LowChargeWarningScenarios() {}

	private static final String LOW_KEY = "message.alaindustrial.low_charge";
	private static final String DEPLETED_KEY = "message.alaindustrial.charge_depleted";

	private static ItemStack stackOf(net.minecraft.world.item.Item item, long eu) {
		ItemStack stack = new ItemStack(item);
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack jetpack(long eu) {
		return stackOf(ModContent.JETPACK.get(), eu);
	}

	/** The mock's in-memory channel: listener.connection -> Connection.channel (both private upstream). */
	private static EmbeddedChannel channelOf(ServerPlayer player) throws ReflectiveOperationException {
		Field listenerConnection = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
		listenerConnection.setAccessible(true);
		Connection connection = (Connection) listenerConnection.get(player.connection);
		Field channel = Connection.class.getDeclaredField("channel");
		channel.setAccessible(true);
		return (EmbeddedChannel) channel.get(connection);
	}

	/** Everything written to the player since the last call. */
	private static List<Object> drain(GameTestHelper helper, ServerPlayer player) {
		try {
			EmbeddedChannel channel = channelOf(player);
			List<Object> sent = new ArrayList<>(channel.outboundMessages());
			channel.outboundMessages().clear();
			return sent;
		} catch (ReflectiveOperationException e) {
			helper.fail("cannot reach the mock player's channel: " + e);
			return List.of();
		}
	}

	private static List<ClientboundSoundPacket> sounds(List<Object> sent) {
		return sent.stream().filter(ClientboundSoundPacket.class::isInstance).map(ClientboundSoundPacket.class::cast)
				.toList();
	}

	private static List<ClientboundSystemChatPacket> overlays(List<Object> sent) {
		return sent.stream().filter(ClientboundSystemChatPacket.class::isInstance)
				.map(ClientboundSystemChatPacket.class::cast).filter(ClientboundSystemChatPacket::overlay).toList();
	}

	private static TranslatableContents translatable(GameTestHelper helper, Component line) {
		if (line.getContents() instanceof TranslatableContents contents) {
			return contents;
		}
		helper.fail("the action-bar line is not translatable: " + line);
		throw new IllegalStateException();
	}

	/** Fails unless exactly one beep of {@code sound} and one overlay line with {@code key} were sent. */
	private static TranslatableContents expectWarning(GameTestHelper helper, List<Object> sent,
			net.minecraft.sounds.SoundEvent sound, String key) {
		List<ClientboundSoundPacket> heard = sounds(sent);
		List<ClientboundSystemChatPacket> lines = overlays(sent);
		if (heard.size() != 1 || heard.get(0).getSound().value() != sound) {
			helper.fail("expected exactly one " + sound.location() + " packet, got " + heard.size() + ": " + sent);
		}
		if (lines.size() != 1) {
			helper.fail("expected exactly one action-bar line, got " + lines.size() + ": " + sent);
		}
		TranslatableContents contents = translatable(helper, lines.get(0).content());
		if (!key.equals(contents.getKey())) {
			helper.fail("the line must be " + key + ", got " + contents.getKey());
		}
		return contents;
	}

	private static void expectSilence(GameTestHelper helper, List<Object> sent, String what) {
		if (!sounds(sent).isEmpty() || !overlays(sent).isEmpty()) {
			helper.fail(what + " must stay silent, but sent " + sent);
		}
	}

	/**
	 * FUN01: the debit that takes a jetpack from 15% to 9.67% sends the owner one beep and one line reading the
	 * item and the percent, and sends the other player on the server nothing.
	 */
	public static void fun01CrossingWarnsOnlyTheOwner(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ServerPlayer bystander = survivalPlayer(helper);
		ItemStack jetpack = jetpack(ToolConfig.jetpackBuffer * 15L / 100);
		drain(helper, owner);
		drain(helper, bystander);

		ItemEnergy.spend(jetpack, ToolConfig.jetpackBuffer * 5L / 100 + 100, owner);

		TranslatableContents line = expectWarning(helper, drain(helper, owner), ModSounds.LOW_CHARGE_BEEP.get(),
				LOW_KEY);
		if (line.getArgs().length != 2 || !"9%".equals(line.getArgs()[1])) {
			helper.fail("the second argument must be the finished percent text \"9%\", got "
					+ java.util.Arrays.toString(line.getArgs()));
		}
		expectSilence(helper, drain(helper, bystander), "the player who does not own the jetpack");
		helper.succeed();
	}

	/** FUN02: a second debit in the same tick, still below the threshold, does not beep again. */
	public static void fun02SecondSpendInTheSameSecondIsSilent(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ItemStack jetpack = jetpack(ToolConfig.jetpackBuffer * 15L / 100);
		ItemEnergy.spend(jetpack, ToolConfig.jetpackBuffer * 5L / 100 + 100, owner);
		drain(helper, owner);

		ItemEnergy.spend(jetpack, ToolConfig.jetpackEuPerTick, owner);

		expectSilence(helper, drain(helper, owner), "a second debit inside the one-second repeat period");
		helper.succeed();
	}

	/** FUN03: the debit that empties the jetpack sends the depletion beep and line, not the low-charge ones. */
	public static void fun03JetpackRunningOutHasItsOwnSignal(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ItemStack jetpack = jetpack(30);
		drain(helper, owner);

		ItemEnergy.spend(jetpack, ToolConfig.jetpackEuPerTick, owner);

		if (ItemEnergy.get(jetpack) != 0) {
			helper.fail("the debit must empty the jetpack");
		}
		expectWarning(helper, drain(helper, owner), ModSounds.CHARGE_DEPLETED.get(), DEPLETED_KEY);
		helper.succeed();
	}

	/** FUN04: the saber warns on its crossing too — the TOOL profile, not only the jetpack's. */
	public static void fun04ToolsWarnToo(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ItemStack saber = stackOf(ModContent.ELECTRIC_SABER.get(), 1_050);
		drain(helper, owner);

		ItemEnergy.spend(saber, ToolConfig.electricSaberEuPerHit, owner);

		expectWarning(helper, drain(helper, owner), ModSounds.LOW_CHARGE_BEEP.get(), LOW_KEY);
		helper.succeed();
	}

	/**
	 * FUN05 (the owner's report): a drill never reaches zero — it stops at a remainder smaller than a block. With
	 * a little over two blocks' worth, the first block leaves it still able to pay (no "depleted"), the second
	 * leaves less than a block and sounds the depletion beep and line exactly once.
	 */
	public static void fun05ToolSaysDepletedWhenItCannotPayAgain(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		net.minecraft.world.item.Item item = ModContent.ELECTRIC_DRILL.get();
		long price = ((dev.alaindustrial.item.energy.PoweredItem) item).actionCost();
		ItemStack drill = stackOf(item, 2 * price + 10);
		drain(helper, owner);

		ItemEnergy.spend(drill, price, owner);
		for (ClientboundSoundPacket packet : sounds(drain(helper, owner))) {
			if (packet.getSound().value() == ModSounds.CHARGE_DEPLETED.get()) {
				helper.fail("the drill can still pay for one more block: it must not say depleted yet");
			}
		}

		ItemEnergy.spend(drill, price, owner);

		if (ItemEnergy.get(drill) != 10) {
			helper.fail("the premise failed: expected 10 EU left, got " + ItemEnergy.get(drill));
		}
		expectWarning(helper, drain(helper, owner), ModSounds.CHARGE_DEPLETED.get(), DEPLETED_KEY);
		helper.succeed();
	}

	/** NEG01: a debit that stays above the threshold sends nothing. */
	public static void neg01AboveTheThresholdIsSilent(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ItemStack jetpack = jetpack(ToolConfig.jetpackBuffer);
		drain(helper, owner);

		ItemEnergy.spend(jetpack, ToolConfig.jetpackEuPerTick, owner);

		expectSilence(helper, drain(helper, owner), "a full jetpack");
		helper.succeed();
	}

	/** NEG02: a creative player is not debited, so nothing crosses and nothing is sent. */
	public static void neg02CreativeGetsNothing(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.mockPlayerInLevel(helper);
		ItemStack jetpack = jetpack(1_000);
		drain(helper, owner);

		ItemEnergy.spend(jetpack, ToolConfig.jetpackEuPerTick, owner);

		if (ItemEnergy.get(jetpack) != 1_000) {
			helper.fail("the premise failed: the creative mock was debited");
		}
		expectSilence(helper, drain(helper, owner), "a creative player");
		helper.succeed();
	}

	/** NEG03: with the switch off, the crossing is silent. */
	public static void neg03SwitchedOffGetsNothing(GameTestHelper helper) {
		ServerPlayer owner = survivalPlayer(helper);
		ItemStack jetpack = jetpack(ToolConfig.jetpackBuffer * 15L / 100);
		drain(helper, owner);

		try (ConfigOverrides o = ConfigOverrides.sync().set("lowChargeWarnEnabled", false)) {
			ItemEnergy.spend(jetpack, ToolConfig.jetpackBuffer * 5L / 100 + 100, owner);
		}

		expectSilence(helper, drain(helper, owner), "a player with the warning switched off");
		helper.succeed();
	}

	/** NEG04: a skeleton firing a charged bow is not a player: no packet, no exception, the shot is still paid. */
	public static void neg04MobsGetNothing(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new net.minecraft.core.BlockPos(1, 2, 1));
		ItemStack bow = stackOf(ModContent.ELECTRIC_BOW.get(), 1_050);

		ItemEnergy.spend(bow, 150, skeleton);

		if (ItemEnergy.get(bow) != 900) {
			helper.fail("the mob's shot must still be paid, got " + ItemEnergy.get(bow));
		}
		helper.succeed();
	}
}

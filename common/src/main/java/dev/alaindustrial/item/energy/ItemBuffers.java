package dev.alaindustrial.item.energy;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.ToolConfig;

/**
 * The EU buffers of every powered item, read from one source (MOD-790): one component per buffer knob, named
 * exactly like the {@link ToolConfig} field. A powered item picks its own buffer out of it
 * ({@link PoweredItem#energyCapacity(net.minecraft.world.item.ItemStack, ItemBuffers)}), so which knob belongs to
 * which item is written once, and the two sources feed the same picking: {@link #live()} — the knobs this side
 * plays by, for charging, spending and the clamp — and {@link #shown()} — the server's numbers, for what a client
 * draws.
 *
 * <p>A record and not an interface with two implementations on purpose: the client-reach rule
 * ({@code ClientBalanceReachRules}) follows a call on an interface into every implementation, so a "source"
 * interface whose live implementation reads {@code ToolConfig} would make every client read reach the knobs. A
 * record has nothing to dispatch to.
 *
 * <p>Built anew per question and never cached: {@code /ala config reload} and the gametests' {@code ConfigOverrides}
 * write the knobs in place, and a cached copy would miss them.
 */
public record ItemBuffers(int lvPouchBuffer, int batteryBuffer, int energyCrystalBuffer, int lapotronCrystalBuffer,
		int resonantCrystalBuffer, int energyPackBuffer, int electricDrillBuffer, int electricDrillNetheriteTipBuffer,
		int electricChainsawBuffer, int electricShovelBuffer, int electricHoeBuffer, int electricSaberBuffer,
		int electricBowBuffer, int magnetBuffer, int magnetAdvancedBuffer, int jetpackBuffer, int fluxweaveBuffer) {

	/** The buffers this side plays by — {@link ToolConfig}, read now. Server logic only. */
	public static ItemBuffers live() {
		return new ItemBuffers(ToolConfig.lvPouchBuffer, ToolConfig.batteryBuffer, ToolConfig.energyCrystalBuffer,
				ToolConfig.lapotronCrystalBuffer, ToolConfig.resonantCrystalBuffer, ToolConfig.energyPackBuffer,
				ToolConfig.electricDrillBuffer, ToolConfig.electricDrillNetheriteTipBuffer,
				ToolConfig.electricChainsawBuffer, ToolConfig.electricShovelBuffer, ToolConfig.electricHoeBuffer,
				ToolConfig.electricSaberBuffer, ToolConfig.electricBowBuffer, ToolConfig.magnetBuffer,
				ToolConfig.magnetAdvancedBuffer, ToolConfig.jetpackBuffer, ToolConfig.fluxweaveBuffer);
	}

	/**
	 * The buffers a client draws against — the server's, through {@link ServerBalance}, or this side's own until a
	 * server has sent them. Display only: the clamp and every transfer go through {@link #live()}.
	 */
	public static ItemBuffers shown() {
		return new ItemBuffers(ServerBalance.lvPouchBuffer(), ServerBalance.batteryBuffer(),
				ServerBalance.energyCrystalBuffer(), ServerBalance.lapotronCrystalBuffer(),
				ServerBalance.resonantCrystalBuffer(), ServerBalance.energyPackBuffer(),
				ServerBalance.electricDrillBuffer(), ServerBalance.electricDrillNetheriteTipBuffer(),
				ServerBalance.electricChainsawBuffer(), ServerBalance.electricShovelBuffer(),
				ServerBalance.electricHoeBuffer(), ServerBalance.electricSaberBuffer(),
				ServerBalance.electricBowBuffer(), ServerBalance.magnetBuffer(), ServerBalance.magnetAdvancedBuffer(),
				ServerBalance.jetpackBuffer(), ServerBalance.fluxweaveBuffer());
	}
}

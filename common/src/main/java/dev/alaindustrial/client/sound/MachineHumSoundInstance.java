package dev.alaindustrial.client.sound;

import dev.alaindustrial.block.LitMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Continuous looping ambient hum for a working machine, positioned at a block. Unlike a periodic
 * server-side {@code playSound}, this loops on the client so the sound engine attenuates it smoothly
 * as the listener moves — approaching a running machine ramps up from silence instead of snapping on
 * with a delay. The instance self-terminates when the block stops working ({@code lit=false}), is
 * removed/replaced, or the listener leaves range; the manager then re-creates it on demand.
 *
 * <p>Client-only class (references {@code net.minecraft.client.*}). It is only ever loaded on the
 * physical client — the block's {@code humMachineTicker} dispatches through {@code MachineHum.CLIENT},
 * which is installed exclusively from each loader's client entrypoint — so it never loads on a server.
 */
public final class MachineHumSoundInstance extends AbstractTickableSoundInstance {

	/** Beyond this the loop ends (hysteresis vs. the manager's smaller start radius). */
	private static final double STOP_DISTANCE_SQR = 32.0 * 32.0;

	private final BlockPos pos;
	private final Block block;

	public MachineHumSoundInstance(SoundEvent event, BlockPos pos, Block block, float volume) {
		super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.pos = pos;
		this.block = block;
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
		this.volume = volume;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			stop();
			return;
		}
		BlockState state = mc.level.getBlockState(pos);
		boolean working = state.is(block)
				&& state.hasProperty(LitMachineBlock.LIT)
				&& state.getValue(LitMachineBlock.LIT);
		if (!working || mc.player.distanceToSqr(x, y, z) > STOP_DISTANCE_SQR) {
			stop();
		}
	}
}

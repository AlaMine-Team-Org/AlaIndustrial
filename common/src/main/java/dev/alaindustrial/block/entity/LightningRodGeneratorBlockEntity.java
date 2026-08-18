package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.LightningRodGeneratorBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.LightningRodOutput;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.menu.LightningRodGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * LV lightning rod generator (spec: alaindustrial:lightning_rod_generator) — the mod's first
 * <b>event-driven</b> generator. Instead of a rate per tick it catches a whole lightning strike at
 * once, banks it in the installed conductor tip's capacitor, and bleeds that reserve into the
 * network at a flat rate until it is empty.
 *
 * <p><b>The rod summons its own strikes.</b> Every tick that it is raining over open sky the rod
 * rolls a one-in-N chance ({@link Config#lightningRodThunderStrikeChanceDivisor} in thunder,
 * {@link Config#lightningRodRainStrikeChanceDivisor} in plain rain) and, on a hit, spawns a
 * <b>visual-only</b> {@link LightningBolt} at itself for the player's benefit before crediting the
 * energy in code. Three reasons this is not "intercept vanilla lightning":
 * <ul>
 *   <li><b>Rain.</b> Vanilla only ever spawns lightning during a thunderstorm; a rod that earned
 *       nothing in ordinary rain was the one behaviour this feature was asked for.</li>
 *   <li><b>Loader parity.</b> Vanilla's strike hook is {@code LightningBolt#powerLightningRod},
 *       which hard-checks {@code instanceof LightningRodBlock} and calls a method that exists on no
 *       supertype; and vanilla's <em>attraction</em> is keyed on {@code PoiTypes.LIGHTNING_ROD},
 *       whose blockstate map a mod can join on NeoForge ({@code ExtendPoiTypesEvent}) but
 *       <b>not</b> on Fabric. Either route means loader-specific plumbing in a repo whose whole
 *       architecture is one shared manifest replayed by both loaders.</li>
 *   <li><b>Testability.</b> A summoned strike is a pure function of config + an RNG roll, so a
 *       gametest drives it deterministically instead of waiting on chunk-random vanilla weather
 *       (~1 strike per 17 s <em>somewhere</em> in the loaded area, and never at all in a desert or
 *       a snowy biome).</li>
 * </ul>
 *
 * <p><b>Visual-only is a safety property, not a shortcut.</b> {@code LightningBolt} reads
 * {@code visualOnly} in exactly two places — {@code spawnFire()} and the entity-damage branch — so
 * the bolt still flashes, still thunders and still fires {@code GameEvent.LIGHTNING_STRIKE}, but
 * sets nothing on fire, hurts nobody and creates no charged creeper. A wooden base under a rod is
 * safe by construction rather than by a config the player has to find.
 *
 * <p><b>Overload never destroys anything.</b> The mod removed overvoltage deliberately (see
 * {@code EnergyTier}: "there is no overvoltage penalty"), so a strike arriving at a full capacitor
 * does not burn the block or a cable. It is simply <b>lost</b>, and the tip takes
 * {@link Config#lightningRodOverloadWearFactor}× wear for having stood there and taken it. Losing
 * ~20 000 EU of free energy is the punishment; the base staying intact is the point.
 *
 * <p><b>Why a capacitor at all.</b> {@code AbstractGeneratorBlockEntity#onServerTick} credits at
 * most the buffer's free room each tick and drops the rest (use-it-or-lose-it, no queue), so a
 * 20 000 EU burst poured straight in would lose ~80 % of itself against a 4000 EU buffer. Banking
 * it in the tip and returning a flat rate from {@link #produce} instead keeps the burst at the
 * <em>input</em> and lets the ordinary generator machinery — the global EU multiplier, the career
 * statistics, cable losses, the tier ceiling — work unchanged. Same shape as the geothermal
 * generator's {@code lavaTicks} reserve.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 capacitor charge (permille 0..1000), 3 denominator
 * (constant 1000), 4 mode, 5 effective rate (EU/t after {@link Config#globalEuRateMultiplier}).
 * Permille rather than raw EU because a {@code DataSlot} transmits 16 bits and a capacitor holds up
 * to 32 000 — the same overflow the solar panel and wind mill work around for their evolve counter.
 */
public class LightningRodGeneratorBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/** The one game slot: the conductor tip. */
	public static final int TIP_SLOT = 0;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes from this (MOD-439). */
	public static final int SLOT_COUNT = 1;

	/** Six-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so the menu's client stub sizes from here. */
	public static final int DATA_COUNT = 6;
	/** Channel carrying the mode code below. */
	public static final int MODE_CHANNEL = 4;
	/** Channel carrying the effective (post-multiplier) EU/t the GUI prints. */
	public static final int RATE_CHANNEL = 5;

	/** No conductor tip installed — the rod still attracts nothing and banks nothing. */
	public static final int MODE_NO_TIP = 0;
	/** Dry or clear weather: nothing to catch. */
	public static final int MODE_IDLE = 1;
	/** It is raining, but this rod cannot be struck here (roofed, or a biome that has no rain). */
	public static final int MODE_SHELTERED = 2;
	/** Storm overhead, open sky, capacitor has room: armed and waiting for a strike. */
	public static final int MODE_ARMED = 3;
	/** The capacitor holds charge and is bleeding it into the network. */
	public static final int MODE_DISCHARGING = 4;
	/** A strike was wasted on a full capacitor within the last {@link #OVERLOAD_DISPLAY_TICKS}. */
	public static final int MODE_OVERLOAD = 5;

	/** How long the GUI keeps showing {@link #MODE_OVERLOAD} after a wasted strike (4 s). */
	private static final int OVERLOAD_DISPLAY_TICKS = 80;

	/** EU banked in the installed tip's capacitor. Persisted; the tip item itself carries only wear. */
	private int capacitorEu;
	/** Countdown for the overload readout. Persisted so a relog mid-penalty is not silently cleared. */
	private int overloadTicks;

	/** Effective EU/t published on {@link #RATE_CHANNEL}. Transient — recomputed every tick. */
	private int effectiveRate;
	/** Cached "a strike could land here" answer, refreshed every {@link Config#lightningRodSampleTicks}. */
	private boolean struckable;
	private int sampleCountdown;

	public LightningRodGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.LIGHTNING_ROD_GENERATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.lightningRodBuffer, EnergyTier.LV.maxVoltage());
		this.maxProgress = 1000;
	}

	/**
	 * The grade of the installed conductor tip, or {@code null} when the slot is empty. Unlike the
	 * mills there is no T1 fallback: an empty slot is a real, visible state here (the rod keeps
	 * catching strikes and wasting every one of them), not a degraded reading to paper over.
	 */
	private ComponentTier installedTip() {
		return tierOf(items.get(TIP_SLOT), null);
	}

	/** Capacitor size of the installed tip, 0 with no tip. */
	public int capacitorCapacity() {
		ComponentTier tip = installedTip();
		return tip == null ? 0 : LightningRodOutput.capacityFor(Config.lightningRodBaseCapacitorEu,
				tip.outputMultiplier(), Config.lightningRodMaxCapacitorEu);
	}

	/** EU currently banked in the capacitor. */
	public int capacitorCharge() {
		return capacitorEu;
	}

	/**
	 * Top face is where the lightning lands and must not emit EU; the other five sides are standard
	 * generator outputs — the same five-side contract the solar panels carry (they are the other
	 * rotation-symmetric generators in the mod, with no {@code FACING} to hang the default on).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (overloadTicks > 0) {
			overloadTicks--;
		}
		sampleConditions(level, pos);

		ComponentTier tip = installedTip();
		// Roll for a strike before bleeding, so a strike landing this tick is already spendable.
		if (level instanceof ServerLevel serverLevel && struckable) {
			int divisor = LightningRodOutput.strikeDivisor(level.isThundering(), level.isRaining(),
					Config.lightningRodThunderStrikeChanceDivisor,
					Config.lightningRodRainStrikeChanceDivisor);
			if (divisor > 0 && serverLevel.getRandom().nextInt(divisor) == 0) {
				strike(serverLevel, pos, tip);
				tip = installedTip(); // the strike may have worn the tip out entirely
			}
		}

		updateTipped(level, tip != null);
		clampCapacitorToTip();
		int bleed = bleed(tip);
		this.progress = publishCharge(tip);
		this.maxProgress = 1000;
		return bleed;
	}

	/**
	 * Drain one tick's worth of the capacitor. Gated on the buffer having room for the same reason
	 * the geothermal generator gates its lava: the reserve is already banked, so pausing wastes
	 * nothing, whereas draining into a full buffer would silently destroy EU the player earned.
	 */
	private int bleed(ComponentTier tip) {
		if (tip == null || capacitorEu <= 0) {
			return 0;
		}
		long room = energy.getCapacity() - energy.getAmount();
		if (room <= 0) {
			return 0;
		}
		int rate = LightningRodOutput.bleedFor(Config.lightningRodBaseBleedEuPerTick,
				tip.outputMultiplier(), Config.lightningRodMaxBleedEuPerTick);
		int drained = (int) Math.min(Math.min(capacitorEu, rate), room);
		if (drained > 0) {
			capacitorEu -= drained;
			setChanged();
		}
		return drained;
	}

	/**
	 * Refresh {@link #struckable} on a cadence. The cheap {@code isRaining()} flag short-circuits
	 * first so a clear-weather world never pays for the heightmap and biome lookups at all.
	 *
	 * <p>The condition is deliberately vanilla's own: {@code isRainingAt(pos.above())} folds together
	 * open sky, "nothing above this column", the dimension check and the biome's precipitation — a
	 * desert or a snowy peak gets no strikes, exactly as vanilla lightning does not fall there.
	 * Sampling {@code pos.above()} rather than {@code pos} is required, not cosmetic: the rod itself
	 * is a motion-blocking block, so the heightmap test would reject its own position.
	 */
	private void sampleConditions(Level level, BlockPos pos) {
		if (--sampleCountdown > 0) {
			return;
		}
		sampleCountdown = Math.max(1, Config.lightningRodSampleTicks);
		struckable = level.isRaining() && level.isRainingAt(pos.above());
	}

	/**
	 * One strike lands. Spawns the visual bolt for feedback, then banks what fits and charges wear
	 * for what the tip actually did — normal wear on the EU banked, punitive wear when the strike
	 * was wasted. With no tip at all there is nothing to wear and nothing to bank: the rod takes the
	 * hit, the player sees the sparks, and the energy is gone.
	 */
	private void strike(ServerLevel level, BlockPos pos, ComponentTier tip) {
		spawnVisualBolt(level, pos);
		int strikeEu = Math.max(0, Config.lightningRodStrikeEu);
		int banked = 0;
		if (tip != null) {
			banked = LightningRodOutput.accepted(strikeEu, capacitorCapacity() - capacitorEu);
			if (banked > 0) {
				capacitorEu += banked;
				wearComponent(level, pos, TIP_SLOT, banked, 1.0f, tip.euPerDamage());
			} else {
				wearComponent(level, pos, TIP_SLOT, strikeEu,
						Math.max(1.0f, Config.lightningRodOverloadWearFactor), tip.euPerDamage());
			}
		}
		if (banked < strikeEu) {
			overload(level, pos);
		}
		// The wear above may have just destroyed the tip; the charge cannot outlive its capacitor.
		clampCapacitorToTip();
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * Keep the stored charge inside what the CURRENTLY installed tip can hold.
	 *
	 * <p>The capacitor is not a property of the block, it is the tip — so every way the tip can leave
	 * has to take its contents with it: worn out under a strike (found by
	 * {@code LightningRodScenarios#strikeOnFullCapacitorIsWastedAndWearsTheTip}, which caught a rod
	 * holding 20 000 EU in a capacitor of capacity 0), pulled out by hand, or swapped down to a
	 * smaller grade. Without this the charge is stranded — {@link #bleed} refuses to drain with no tip
	 * installed, so it would sit there for ever, and the GUI gauge would read past full.
	 */
	private void clampCapacitorToTip() {
		int capacity = capacitorCapacity();
		if (capacitorEu > capacity) {
			capacitorEu = capacity;
			setChanged();
		}
	}

	/**
	 * Deliver one strike now, bypassing the weather roll. <b>Public for the shared GameTest body</b>,
	 * the same reason {@code CableBlock#tryShockPlayer} is: normal play enters through the per-tick
	 * roll in {@link #produce}, which no test can drive deterministically, while every consequence
	 * worth asserting — what is banked, what is wasted, what the tip pays in wear — lives past that
	 * roll. Testing through the RNG instead would either flake or need a seeded world.
	 */
	public void strikeNow(ServerLevel level) {
		strike(level, worldPosition, installedTip());
	}

	/**
	 * A cosmetic bolt so the strike is something the player sees and hears rather than a number that
	 * changed. {@code setVisualOnly(true)} is what makes it safe — verified against the 26.2 source:
	 * {@code visualOnly} gates {@code spawnFire()} and the entity-damage sweep and nothing else.
	 */
	private static void spawnVisualBolt(ServerLevel level, BlockPos pos) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(Vec3.atBottomCenterOf(pos.above()));
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	/** The wasted-strike signal: a sharp crack and sparks off the head, plus the GUI readout. */
	private void overload(ServerLevel level, BlockPos pos) {
		overloadTicks = OVERLOAD_DISPLAY_TICKS;
		level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.7f, 1.4f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
				18, 0.3, 0.3, 0.3, 0.15);
	}

	/**
	 * Show or hide the mast, mirroring {@code EnergyBlockEntity#updateLit}: only write the blockstate
	 * when the value actually changes, so inserting a tip is one block update rather than one per tick.
	 * A model change has to be a blockstate — the alternative, a block-entity renderer reading the slot,
	 * would put the mast outside the baked model and outside every L3 screenshot that checks it.
	 */
	private void updateTipped(Level level, boolean tipped) {
		if (level.isClientSide()) {
			return;
		}
		BlockState current = getBlockState();
		if (current.hasProperty(LightningRodGeneratorBlock.TIPPED)
				&& current.getValue(LightningRodGeneratorBlock.TIPPED) != tipped) {
			level.setBlock(worldPosition, current.setValue(LightningRodGeneratorBlock.TIPPED, tipped),
					Block.UPDATE_CLIENTS);
		}
	}

	/** Capacitor charge as permille of the installed tip's capacity — see the class doc on DataSlot width. */
	private int publishCharge(ComponentTier tip) {
		if (tip == null || capacitorEu <= 0) {
			return 0;
		}
		int capacity = capacitorCapacity();
		if (capacity <= 0) {
			return 0;
		}
		// max(1, ...) so any non-zero charge still paints a pixel, as the solar/wind evolve bars do.
		return Math.max(1, (int) Math.min((long) capacitorEu * 1000 / capacity, 1000));
	}

	/**
	 * Mode shown as a word in the GUI. Ordered by what the player most needs to know: a missing tip
	 * beats everything (nothing works), a recent overload beats the steady states (it explains where
	 * the energy went), and discharging beats armed (it is the state that is actually paying out).
	 */
	private int mode() {
		if (installedTip() == null) {
			return MODE_NO_TIP;
		}
		if (overloadTicks > 0) {
			return MODE_OVERLOAD;
		}
		if (capacitorEu > 0) {
			return MODE_DISCHARGING;
		}
		// getLevel() is null between construction and world attach; the readout is polled from the
		// server menu, so that window is never observed — but a null here would be a crash, not a
		// wrong word, and this method exists to make the block explain itself.
		Level level = getLevel();
		if (level == null || !level.isRaining()) {
			return MODE_IDLE;
		}
		return struckable ? MODE_ARMED : MODE_SHELTERED;
	}

	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.effectiveRate = effectiveEuPerTick;
	}

	private final ContainerData rodData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 3 -> 1000;
				case MODE_CHANNEL -> mode();
				case RATE_CHANNEL -> effectiveRate;
				default -> LightningRodGeneratorBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 3 && index != MODE_CHANNEL && index != RATE_CHANNEL) {
				LightningRodGeneratorBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return rodData;
	}

	/**
	 * Only a conductor tip, filtered by tag so every grade fits. No empty-slot guard: a tip stacks to
	 * 1, and guarding would block the one-click swap of a worn tip for a fresh one (the same reason
	 * the wind mill's rotor slot has none).
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == TIP_SLOT && stack.is(ModTags.Items.CONDUCTOR_TIPS);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.lightning_rod_generator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new LightningRodGeneratorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("CapacitorEu", capacitorEu);
		output.putInt("OverloadTicks", overloadTicks);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Clamped on load: a world saved before an operator lowered the capacitor caps would otherwise
		// keep a charge its tip can no longer hold, and the GUI bar would read over 100 %.
		capacitorEu = Math.max(0, input.getIntOr("CapacitorEu", 0));
		overloadTicks = Math.max(0, input.getIntOr("OverloadTicks", 0));
	}
}

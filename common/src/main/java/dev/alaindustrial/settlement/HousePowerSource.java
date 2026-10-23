package dev.alaindustrial.settlement;

/**
 * Where a shift worker's house keeps its power (MOD-876; epic MOD-824, decisions R7, R90): the house inlet, the wall
 * socket a cable enters a town building through. What draws from it is the house's charging beds, where the workers
 * sleep and charge (MOD-862); a bed has no buffer of its own and asks its building's inlet.
 *
 * <p>Minecraft-free on purpose, so a bed's charging rule can be checked on the L1 lane against a plain stub.
 */
public interface HousePowerSource {

	/** EU the house holds right now. */
	long storedEnergy();

	/**
	 * Takes up to {@code amount} EU out of the house's buffer.
	 *
	 * @param amount what the caller wants; zero or less takes nothing
	 * @return what was actually taken, never more than {@link #storedEnergy()} held
	 */
	long drawEnergy(long amount);
}

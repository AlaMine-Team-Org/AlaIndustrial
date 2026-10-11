package dev.alaindustrial.command.demo.energy;

import java.util.List;
import java.util.function.Consumer;

/**
 * One bay of the energy stand (MOD-799): a small self-contained network in its own rectangle of the arena, the
 * two lines its sign shows, how it is built and the rules {@code /ala demo energy check} holds it to.
 *
 * @param id     short id shown on the sign and in chat ({@code "E01"})
 * @param title  what the bay shows, in a few English words (two sign lines of about 15 characters)
 * @param x0     the bay's west edge in arena coordinates
 * @param z0     the bay's north edge in arena coordinates
 * @param width  extent along X; the bay writes only inside {@code [x0, x0 + width)}
 * @param depth  extent along Z, sign row included
 * @param build  writes the bay through a {@link BayWriter} whose local origin is the bay's corner
 * @param expect the rules of the bay
 */
record Bay(String id, String title, int x0, int z0, int width, int depth, Consumer<BayWriter> build,
		List<Expectation> expect) {
}

package dev.alaindustrial.gametest;

/**
 * Fabric wrappers of the scenarios that exist on ONE Minecraft line only (MOD-705) — bodies in
 * {@link LineOnlyScenarios}, NeoForge registrations in {@code LineOnlyRegistrations}. The class name is
 * the same on both lines, so its line in {@code fabric.mod.json} is too; the content differs per line.
 * On 26.2 there is no line-only scenario, so this twin has no {@code @GameTest} method.
 */
public class LineOnlyGameTest {
}

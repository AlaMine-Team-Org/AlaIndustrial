package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Golden master of a world characterization (MOD-715, ADR-032), written only by the
 * explicit command below; never by {@code regen.py}, a hook or a merge driver. A
 * behaviour-preserving change leaves every line as it is.
 *
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:worldgen_feature_snapshot_matches
 *   -Dalaindustrial.worldgenFeatureSnapshot.writeTo=&lt;this file&gt;"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing. Captured on one lane, it must pass on both.
 */
final class WorldgenFeatureSnapshot {

	private WorldgenFeatureSnapshot() {
	}

	static final List<String> LINES = List.of(
			"oil_lake_small placed=true box=-3,-2,-3..3,2,3 layout=f7289899ef3b5372",
			"oil_lake_small alaindustrial:oil[0] 62",
			"oil_lake_small cave_air 107",
			"oil_lake_medium placed=true box=-4,-2,-5..5,2,5 layout=1fe05e1215dc2c69",
			"oil_lake_medium alaindustrial:oil[0] 123",
			"oil_lake_medium cave_air 187",
			"oil_lake_large placed=true box=-10,-4,-10..10,4,9 layout=11e16fd4ad844b5",
			"oil_lake_large alaindustrial:oil[0] 1009",
			"oil_lake_large cave_air 1223",
			"oil_geyser placed=true box=-9,-54,-9..9,0,9 layout=816e13a67f0fd3f5",
			"oil_geyser alaindustrial:oil[0] 3107",
			"abandoned_lab placed=true box=-3,-43,-5..11,1,5 layout=c45570bf3aad5b02",
			"abandoned_lab air 545",
			"abandoned_lab alaindustrial:broken_engraved_plate_b[south] 1",
			"abandoned_lab alaindustrial:engraved_plate_6[south] 1",
			"abandoned_lab alaindustrial:engraved_plate_8[south] 1",
			"abandoned_lab alaindustrial:engraved_plate_9[south] 1",
			"abandoned_lab andesite 10",
			"abandoned_lab barrel[up,false] 1",
			"abandoned_lab cobblestone 19",
			"abandoned_lab cobweb 5",
			"abandoned_lab cracked_stone_bricks 122",
			"abandoned_lab crafting_table 1",
			"abandoned_lab dirt 9",
			"abandoned_lab gravel 17",
			"abandoned_lab iron_chain[y,false] 2",
			"abandoned_lab ladder[east,false] 39",
			"abandoned_lab lectern[north,false,false] 1",
			"abandoned_lab lectern[south,false,false] 1",
			"abandoned_lab moss_carpet 5",
			"abandoned_lab mossy_stone_bricks 19",
			"abandoned_lab oak_leaves[7,true,false] 20",
			"abandoned_lab oak_log[y] 2",
			"abandoned_lab oxidized_copper_trapdoor[east,top,false,false,false] 1",
			"abandoned_lab polished_andesite 99",
			"abandoned_lab polished_deepslate 8",
			"abandoned_lab red_bed[south,false,foot] 1",
			"abandoned_lab red_bed[south,false,head] 1",
			"abandoned_lab soul_lantern[false,false] 1",
			"abandoned_lab stone 49",
			"abandoned_lab stone_bricks 284",
			"abandoned_lab tnt[false] 23",
			"abandoned_lab trapped_chest[north,single,false] 1",
			"abandoned_lab tuff 9");
}

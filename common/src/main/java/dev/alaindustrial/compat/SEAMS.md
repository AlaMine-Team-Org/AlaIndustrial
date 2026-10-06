# Version facades of Ala Industrial

This is the register of the version facades (ADR-036): every class whose name and signatures are the same on
the 26.3 line (`main`) and the 26.2 line (`mc/26.2`) while its body is that line's own. Code outside these
packages calls the facade instead of the Minecraft API it wraps, so the caller is the same source on both lines
and a port is a plain `git cherry-pick -x`; the adaptation, if any, lands in the facade body of the target line.

The roots: `dev.alaindustrial.compat` (server-safe) and `dev.alaindustrial.compat.client` (client types in the
signature) in `common/src/main`; `dev.alaindustrial.gametest.compat` in `common/src/gametest` (on NeoForge the
gametest source set is its own JPMS module and cannot share a package with the main one); `dev.alaindustrial.compat`
in `fabric/src/gametest` for the L3 client lane. Subpackages that integrate other mods (`compat.rei`,
`compat.modmenu`, `client.compat.jei`) are not facades. Whatever this register says, the javadoc of each twin is
the authority on what its line does and why (javap evidence included).

## How it is held

- Bytecode, `VersionedApiRules.versionedApiIsCalledOnlyThroughItsFacade` (MOD-703): outside `compat` and
  `compat.client`, no class of `common/src/main` or `common/src/gametest` calls `PoseStack.rotate`/`rotateDegrees`,
  `PoseStack.mulPose(Quaternionfc)`, the bone-meal trio (`isValidBonemealTarget`, `isBonemealSuccess`,
  `performBonemeal`), `drop(ItemStack, …)` on a player or `Inventory.placeItemBackInInventory`;
  `ArchitectureRulesNegativeControl` proves on fixtures that the rule can fail and on the production classes that
  each of the three facades calls one of the listed members on the line it runs on.
- Text, `docs/tools/arch/arch_check.py`: `no-mouse-button-literals-outside-facades`,
  `content-manifest-has-no-version-tokens` (MOD-719), `right-click-tools-go-through-seam` (MOD-704),
  `chunk-waits-and-glfw-stay-in-facades` (MOD-703: `.waitForChunksRender(` and GLFW only inside the facade roots
  — `L3Chunks`, the 26.2 `Keyboard` — across `src/main` and `src/gametest` of the three subprojects).
- Byte equality of everything else: `docs/tools/lines/line_seam_check.py` (MOD-719) — outside the facade roots the Java
  sources of the two lines are byte-equal, and today's remainder is the ratchet
  `docs/standards/line_seam_baseline.json`.

## `dev.alaindustrial.compat` (common/src/main)

| Facade | Signature | 26.3 body | 26.2 body | Task |
|---|---|---|---|---|
| `LineBlockProps` | `Properties popsOnPush(Properties)`, `Properties pinnedAgainstPistons(Properties)`, `Properties washedAwayByFluids(Properties)` | `PushReaction.POPPED`, `IMMOVEABLE`; washing is the `#minecraft:washed_away_by_fluids` tag, the properties stay as they are | `PushReaction.DESTROY`, `BLOCK`; washing is `forceSolidOff()` | MOD-703 |
| `ServerDrops` | `void drop(Player, ItemStack)`, `void placeBackInInventory(Player, ItemStack)` | `drop(stack, false, Prediction.SERVER_ONLY)`, `placeItemBackInInventory(stack, Prediction.SERVER_ONLY)` | `drop(stack, false)`, `placeItemBackInInventory(stack)` | MOD-703 |
| `Invulnerability` | `int graceTicks(Entity)`, `void setGraceTicks(Entity, int)`, `void setPermanent(Entity, boolean)` | `get/setInvulnerableTime`, `setPermanentlyInvulnerable` | the public field `invulnerableTime`, `setInvulnerable` | MOD-703 |
| `Bonemeal` | `boolean isValidTarget(BonemealableBlock, LevelReader, BlockPos, BlockState)`, `boolean isSuccess(BonemealableBlock, Level, RandomSource, BlockPos, BlockState)`, `void perform(BonemealableBlock, ServerLevel, RandomSource, BlockPos, BlockState)` | the trio with `BonemealSource.INTERACTION` | the trio without a source | MOD-703 |
| `LineSounds` | `SoundEvent hoeTill()` | `SoundEvents.HOE_TILL.value()` (a `Holder.Reference`) | `SoundEvents.HOE_TILL` (a `SoundEvent`) | MOD-703 |
| `FurnaceFuel` | `boolean isFuel(Level, ItemStack)`, `boolean isFuelOrUnknown(@Nullable Level, ItemStack)`, `<T extends BlockEntity & Container> int burnDuration(ServerLevel, T, ItemStack)` | the `minecraft:cooking_fuel` component, its `ResolvableInt` resolved through a loot context | `level.fuelValues().isFuel/burnDuration` | MOD-703 |
| `RightClickTransform` | enum `HOE`, `SHOVEL`; `Item.Properties declare(Item.Properties)`, `InteractionResult apply(UseOnContext)`, `static boolean wouldTill(UseOnContext)` | the `minecraft:block_transformer` component | delegation to `Items.DIAMOND_HOE`/`DIAMOND_SHOVEL.useOn`, reading `HoeItem.TILLABLES` | MOD-704 |
| `CriterionPlayer` | `MapCodec<…> FIELD` (the optional `"player"` field of a criterion instance) | `LootItemCondition.CODEC.optionalFieldOf("player")`, type `Optional<Holder<LootItemCondition>>` | `EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")`, type `Optional<ContextAwarePredicate>` | MOD-704 |

The field type of `CriterionPlayer.FIELD` is the line's: `SimpleCriterionTrigger.SimpleInstance#player()` fixes
it, so the `player` component of the three triggers' record headers stays a one-line difference.

## `dev.alaindustrial.compat.client` (common/src/main)

| Facade | Signature | 26.3 body | 26.2 body | Task |
|---|---|---|---|---|
| `Poses` | `void rotate(PoseStack, Quaternionfc)` | `poseStack.rotate(q)` | `poseStack.mulPose(q)` | MOD-703 |
| `ModelSubmit` | `<S> void withCrumbling(SubmitNodeCollector, Model<S>, S, PoseStack, int, int, int, SpriteId, SpriteGetter, int, @Nullable CrumblingOverlay)` | `submitModel` without the overlay, then `order(1).submitCrumblingOverlay(…)` | `submitModel(…, outlineColor, crumbling)` | MOD-703 |
| `Keyboard` | `InputConstants.Type keyMappingType()`, `boolean isDown(int)` | `Type.KEYBOARD` (SDL3 scancodes); `InputConstants.isKeyDown(key)` | `Type.KEYSYM` (GLFW keys); `isKeyDown(window, key)` behind a window check | MOD-703 |
| `Links` | `void confirmAndOpen(Minecraft, Screen, String)` | `ConfirmLinkScreen(…, URI, …)` and `Blaze3D.openUri(URI)` | `ConfirmLinkScreen(…, String, …)` and `Util.getPlatform().openUri(String)` | MOD-703 |
| `TranslucentTypes` | `RenderType blockSheetNoDepthWrite()` | a copy of `RenderPipelines.ITEM_TRANSLUCENT` (`com.mojang.renderpearl.api.pipeline`) with `DepthStencilState(depthTest, false)`; the setup of vanilla's `itemTranslucent` on the block atlas with `setOitPipelines(RenderPipelines.OIT_ITEM)` | the same copy from `com.mojang.blaze3d.pipeline`; no OIT, the setup sends it to `OutputTarget.ITEM_ENTITY_TARGET` as vanilla's `itemTranslucent` does | MOD-777 |

`TranslucentTypes` reaches the package-private `RenderType.create` through the invoker
`mixin.client.RenderTypeInvoker`, whose target signature is the same on both lines, so the invoker itself is
line-neutral.

## `dev.alaindustrial.gametest.compat` (common/src/gametest)

| Facade | Signature | 26.3 body | 26.2 body | Task |
|---|---|---|---|---|
| `GameTestData` | `TestData<Holder<TestEnvironmentDefinition<?>>> of(Holder<…>, Identifier, int, boolean, Rotation, boolean, int)` | the 12-argument `TestData` (with a dimension) | the 11-argument one | MOD-717 |
| `FeaturePlacement` | `boolean place(ServerLevel, Identifier, RandomSource, BlockPos)` | a configured `Feature` from `Registries.FEATURE` | a `ConfiguredFeature` from `Registries.CONFIGURED_FEATURE` | MOD-704 |
| `SiteBiome` | `void fill(ServerLevel, ChunkAccess, Holder<Biome>)` | `fillBiomesFromNoise(resolver)` | `fillBiomesFromNoise(resolver, Climate.Sampler)` | MOD-704 |
| `LineBlockFacts` | `String pushIntent(BlockState)`, `boolean washedAwayByLine(Block)` | the 26.3 push reactions; membership of `#minecraft:washed_away_by_fluids` | the 26.2 push reactions; the `forceSolidOff` flag of the block's properties | MOD-703 |
| `UseResults` | `boolean isSwingSuccess(InteractionResult)`, `boolean isNoSwingConsume(InteractionResult)` | a `Success` with swing source `PREDICTED` / `NONE` | swing source `CLIENT` / `NONE` | MOD-703 |
| `LineShovelFacts` | `boolean dousesLitCampfire(Direction, boolean)` | `!sneaking`: the campfire douses by the `#minecraft:douses_campfires` tag (NeoForge: `SHOVEL_DOUSE`, by the same tag) from any face, and a sneaking click skips the block | `face != DOWN`: `ShovelItem.useOn` answers `PASS` for `DOWN` first and ignores sneaking | MOD-744 |

## `dev.alaindustrial.compat` (fabric/src/gametest, the L3 lane)

| Facade | Signature | 26.3 body | 26.2 body | Task |
|---|---|---|---|---|
| `L3Chunks` | `void waitRender(TestSingleplayerContext)` | `getConnection().waitForChunksRender()` | `getClientLevel().waitForChunksRender()` | MOD-703 |

## What is not a facade

- An override whose signature is the line's (the bone-meal trio in `TrellisBlock`/`KokSagyzBlock`,
  `KokSagyzRootBlock.playerDestroy`), a `codec()` of a block with a vanilla parent other than `BaseEntityBlock`,
  `LegacyBlockStates`, the line's mixins, the worldgen `Feature` adapters (the algorithm is in the line-neutral
  `*Placer`) — documented seams in `docs/BRANCHES.md` and `docs/standards/line_seam_baseline.json`.
- `LineOnlyScenarios`: scenarios of one line, the same class name with a different content.

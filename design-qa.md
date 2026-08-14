# Premium TV design QA

## Source and implementation captures

- Visual target: `C:\Users\heel\.codex\generated_images\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\exec-f8264aaf-5c6b-4d56-bf44-665c84b18e71.png`
- Full home, 1920×1080, English, 100% font: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\home-1080-final.png`
- Focused local trailer preview, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\preview-final-2.png`
- Expanded rail with stopped preview, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\rail-final.png`
- Detail, German, 100% font: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\detail-final.png`
- Detail, German, 200% font, all four actions: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\detail-font2-prominent.png`
- Responsive home, 1280×720: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\home-720.png`
- Home Sections with restored hero, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\home-sections-hero-fixed-1080.png`
- D-pad next-row focus from Discover to Discover Movies, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\home-sections-dpad-next-row-1080.png`
- Home Sections with restored hero and strict recent row, 1280×720: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\home-sections-hero-fixed-720.png`
- Direction-3 hero carousel, focused, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\hero-carousel-1080.png`
- Direction-3 hero carousel, first media row focused, 1920×1080: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\hero-carousel-dpad-row-1080.png`
- Direction-3 hero carousel, focused, 1280×720: `C:\Users\heel\.codex\visualizations\2026\08\13\019ffc02-7b0b-7881-8c1d-f0c6fb41a271\tv-premium-qa\hero-carousel-720.png`

The live Jellyfin state used for the final home capture had no item eligible for the fixed 30-day window. The full hero was therefore compared in the preceding 1920×1080 pass (`home-1080-v2.png`); the final geometry change was then verified independently by the layout test and the final row, rail, preview, and detail captures above.

The Home Sections regression pass installed commit `4de8235` over the existing authenticated TV app. The hero remained LazyColumn slot zero with server sections enabled. Live D-pad traversal moved from the second My Media card to the first Continue Watching card, and from `Reacher` in Discover directly to `Toy Story 5`, the first Discover Movies card. The same build passed at 1920×1080 and 1280×720.

## Direction-3 hero carousel QA

The 30-day content is now presented only in the 360 dp Home hero. The separate recent row and its heading are absent, so the first configured or regular media row begins directly below the hero. The rendered first-card position is 452 dp in the isolated layout test.

The exact debug TV APK was installed on the authenticated TV AVD and checked at 1920×1080 and 1280×720. Both action buttons remained visible at 720p. Live remote traversal passed in both resolutions: Hero → Play → Details → first card of the first media row, then Up → Play → Hero. Back from the Hero opened the rail and Right restored the Hero focus. No fatal app exception was recorded after the restored 1080p launch.

The Home instrumentation class passed 12/12 tests, including cyclic multi-item Left/Right selection, stable Hero focus, active-item Play/Details callbacks, rail restoration, preview cancellation, offscreen row focus, no duplicate recent presentation, rendered geometry, and full-interval auto-cycle restart. The complete TV instrumentation suite passed 19/19 tests.

## Comparison history

1. The first source/implementation comparison showed an oversized compact rail, an overly tall hero, and a portrait focus morph that consumed too much of the row.
2. The rail was reduced to the source proportion, screen insets were tightened, and the expanded safe area was kept exactly equal to the 226 dp open rail.
3. The hero was reduced from 360 dp to 260 dp, with a smaller logo, tighter copy, and more compact hero actions so the first media row enters the initial viewport.
4. Portrait cards now morph from 140 dp to a 300 dp 16:9 preview; landscape cards use a restrained 250/266 dp focus transition. The final preview capture matches the target's emphasis without losing neighboring context.
5. The detail compact action width was increased after the 200% German pass exposed truncated labels. The final capture shows Play, Favorite, Watched, and Trailer completely.
6. The Home header was restored to a 360 dp immersive carousel. It is now the single presentation of 30-day content, keeps its outer focus target stable while slides animate for 240 ms, and routes explicit D-pad movement between the Hero, its actions, and media rows.

## Surface review

- Source fidelity: full-bleed artwork, left-to-right gradients, lavender focus treatment, compact icon rail, mixed poster/preview rhythm, and restrained dark chrome match the selected direction.
- Layout and spacing: row density, rail proportions, open-rail safe area, 720p fallback, and first-row visibility are verified. No focused card or action is clipped.
- Typography: hierarchy remains clear at 100%; German detail actions and labels remain present and legible at 200% font scale.
- Colors and surfaces: the existing Jellystack purple/lavender theme is retained with subtle borders, shadow, brightness, and scale instead of heavy outer frames.
- Interaction states: home-first focus, exact focus restoration, portrait morph, local preview after 3 seconds, immediate artwork restoration, open rail, and primary/secondary detail focus states were exercised on the emulator.
- Home regression states: hero-first rendering with Ready/Loading/Unavailable server sections, deterministic first-card Up/Down routing, offscreen row focus, boundary retention, and preview cancellation were covered by 11 passing TV instrumentation tests and live remote traversal.
- Hero carousel states: empty, single, multiple, cyclic wrap, stable-ID refresh, fallback counter, long-press suppression, focus pause, rail pause, Armed/Playing preview pause, and full configured interval restart are covered by unit and TV instrumentation tests.

passed

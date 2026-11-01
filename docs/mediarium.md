# Mediarium request provider

Jellystack can connect directly to Mediarium's native API for movie and TV discovery,
search, details, adding/requesting titles and approval history. Jellyfin remains the
library and playback server. No Seerr, proxy, extra container, or Mediarium changes
are needed for this provider. Existing Seerr connections keep their provider and
credentials; switching the request provider does not remove either server.

## Configuration

1. In Mediarium, configure movie/TV folders, metadata (TMDB), indexers and download
   settings. Configure its Jellyfin integration and Jellyfin's library scan as usual.
2. Create an API key for the account that should make requests under **Settings >
   Account & Access > Security > API keys**. Use a key that permits writes when adding
   titles. Accounts without permission to add directly submit requests for approval.
3. On phone/tablet, open **Settings > Connections**, connect Mediarium, and enter its
   base URL and API key. The request connection form also offers Seerr/Mediarium
   selection. The HTTP confirmation still applies to unencrypted addresses.
4. On TV, open the request connection dialog in Settings, select **Mediarium**, and
   enter its URL and key. Each household profile binds its own request connection;
   a new household member must supply their own key. Jellyfin credentials are never
   used to sign in to Mediarium.
5. Use the existing Discover, Search and details screens. TV history entries without
   metadata identity show the title and approval status and cannot open a detail page.

Use the base address, for example `https://media.example.com/mediarium`, **without**
`/api`, `/radarr` or `/sonarr`. A Mediarium `URL_BASE` sub-path is preserved. Use the
final server address: native API requests do not follow redirects. Keys are saved
through `ServerCredentialVault` in platform-protected storage, not in the server
metadata table. Editing a Mediarium connection asks for the key again.

## Native API contract

Verified against [Mediarium 3.9.2, commit 3615787f](https://github.com/rdborg/Mediarium/tree/3615787f64c3aebfd4b347fbf3b30ca27e09fd44).
This is a source contract reference, not a claim of live compatibility testing or a
minimum-version guarantee. See also [Mediarium API reference](https://mediarium.app/docs/api/).

| Operation | Native route | Source handler |
| --- | --- | --- |
| Authenticate key, permissions | `GET /api/auth/me` | `handlers_auth.go`, `handleMe` |
| Movie discovery | `GET /api/discover/trending`, `GET /api/discover/popular` | `handlers_library.go` |
| TV discovery | `GET /api/discover/tv/trending`, `GET /api/discover/tv/popular` | `handlers_series.go` |
| Title search | `GET /api/discover/search?q=...` | `sources.go`, `handleTitleSearch` |
| Movie details | `GET /api/tmdb/movies/{tmdbId}` | `handlers_movie_detail.go` |
| TV details | `GET /api/tmdb/tv/{tmdbId}` | `handlers_movie_detail.go` |
| Add/request movie | `POST /api/movies` | `handlers_library.go`, `handleAddMovie` |
| Add/request TV | `POST /api/series` | `handlers_series.go`, `handleAddSeries` |
| Approval history | `GET /api/requests` | `requests.go`, `handleListRequests` |

All source handler paths above are under `internal/api/`; registration and access
rules are in `internal/api/server.go`. Requests authenticate with `X-API-Key` and
never send Seerr cookies, impersonation headers or Jellyfin credentials.

Movie adds send `tmdbId` and `searchNow: true`; show adds additionally send
`monitor: "all"`. Quality, folders, sources, quotas, approvals and download selection
stay under Mediarium's control. A `201` response means an immediate provider-library
add; a `202` response contains a pending approval request. Library IDs, approval IDs
and TMDB IDs are distinct. POSTs are not automatically retried.

## Request side effects

Jellystack treats the Request action as an acquisition request, rather than a
metadata-only library bookmark. It therefore sends `searchNow: true` for movies
and TV. In the verified native handlers, a direct add starts a background search
which can select a release and enqueue downloads immediately. A successful add
is not evidence that a download completed or that Jellyfin can play it.

TV requests send `monitor: "all"` because this integration offers whole-series
requests only. This uses Mediarium's native all-episode monitoring mode (the
native add default); it does not mean a request for only the currently displayed
season. Episodes eligible for acquisition, including existing missing episodes,
can be searched by Mediarium. Future episode acquisition remains governed by its
monitoring and automation. Specials follow Mediarium's native episode policy.
`future`, `none`, and per-season selection are not offered by this implementation.

Accounts requiring approval take Mediarium's native pending-request path instead
of the direct-add path. Jellystack does not approve requests or start a second
search itself. Mediarium owns approval and the subsequent acquisition workflow.
Only the explicit Request action submits the add: opening a card, searching for a
title, or connecting a server never posts a movie or series.

These fixed options keep the initial client small, but are intentional product
choices rather than API requirements. Users wanting to add without searching or
change the episode-monitoring scope should do so in Mediarium. Quality profile,
folder, source selection and upgrade policy are omitted from the payload, leaving
Mediarium's native defaults in control. There is no separate downloader service
or polling worker in Jellystack, and add POSTs are never automatically retried.

The reference handlers are `handleAddMovie` in `handlers_library.go` and
`handleAddSeries` in `handlers_series.go` at the source commit above. Contract
tests assert the exact submitted options. Live acquisition behavior still needs
the checks in [Mediarium validation](mediarium-validation.md).

## Architecture and limits

- `RequestProvider` is a shared-core boundary. `JellyseerrRepository` remains the
  compatibility facade for existing coordinators and screens; it routes to
  `SeerrRequestProvider` or `MediariumProvider`. Legacy domain names are intentionally
  retained to avoid a large UI rename. Transport DTOs stay in shared-network.
- Existing installations default to Seerr. Provider selection is persisted separately
  from each service's active server ID. TV profile bindings retain their existing
  `seerrConnectionId` field name but accept either request-provider type. No SQL
  schema migration or change to existing Seerr credentials is required.
- Trending/popular and native title search are finite, non-paginated arrays. A later
  search page returns no extra entries. Upcoming rails and optional ratings/similar/
  recommendation enrichment are not implemented for Mediarium.
- Approval-history filters support All, Pending and Approved. Acquisition-state
  filters cannot be inferred from the native history response and yield no matches.
- Approval history omits TMDB IDs and the submitted payload. Jellystack does not
  infer identity from titles, turn a library ID into a request ID, or create fake
  request IDs for direct adds. The submission response can identify its own pending
  request. During that server session, refreshes can safely reuse its exact request
  ID/type association to update status. The association is not guessed or persisted
  across app restarts.
- Native details expose Mediarium's acquisition status. A title added in Mediarium
  is shown as processing in the shared request state to prevent duplicate adds.
  Even `downloaded`/`availableAt` does not establish playable Jellyfin availability.
  Playback continues to resolve actual Jellyfin items independently.
- Individual-season requests, 4K-copy requests, Seerr service/language profiles,
  request cancellation, approval/decline, retry and media deletion are not exposed
  for Mediarium. Manage those in Mediarium. Unsupported request options are also
  rejected at the provider boundary before HTTP submission.
- Per-media account permissions control request buttons. A read-only key can still
  receive `403` on submission; the native server is the final authority. `409` is
  treated as a duplicate. Authentication and HTTP errors are not decoded as success,
  and coroutine cancellation is propagated.

## Validation

Added MockEngine tests cover native paths, URL_BASE, authentication headers, query
encoding, serialized add options, `201`/`202`, unsuccessful responses and cancellation.
Shared-core tests cover provider routing, finite lists, unsupported options, history
identity, permissions, secret persistence, selection persistence and Jellyfin
availability separation. TV row tests cover status-only history; phone form tests
cover the Mediarium API-key requirement. Existing Seerr tests are retained.

Run the repository's documented checks from its root:

```bash
./gradlew spotlessCheck detekt
./gradlew :shared-network:jvmTest :shared-core:testDebugUnitTest
./gradlew :design:testDebugUnitTest :design-tv:testDebugUnitTest
./gradlew :app-android:assembleDebug :app-tv:assembleDebug
```

Also validate screenshot baselines and D-pad connection/focus behavior on a TV
emulator. A physical TV-device run with a real remote is required separately before
marking the feature ready. Use the [validation checklist](mediarium-validation.md). Live Mediarium/Jellyfin integration and device testing require a
configured test installation; MockEngine tests do not replace those checks. See the
PR for the checks actually executed and their outcomes.

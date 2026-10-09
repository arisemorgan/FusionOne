# FusionOne

Android app (Kotlin, Jetpack Compose, MVVM, Hilt, Room, Retrofit). Three modules, all
backed by **live, free data sources — no mock data**.

## ⚠ Version note (downgraded on request)

This build is pinned to **AGP 8.5.0 / Gradle 8.7 / Kotlin 2.0.0 / compileSdk & targetSdk 34**
instead of the newer stack from the first version, to match an older Android Studio
install. Two concrete effects of that:

- `compileSdk`/`targetSdk` are **34, not 36** — AGP 8.5.0 doesn't recognize SDK 36.
  Once you update Android Studio, bump both back up in `app/build.gradle.kts` and this
  file's `agp`/`kotlin` versions in `gradle/libs.versions.toml` accordingly.
- `gradle/wrapper/gradle-wrapper.properties` points at Gradle **8.7** (AGP 8.5.0's
  minimum). If Android Studio's own bundled Gradle version differs, let it offer to sync
  to the project's wrapper version rather than overriding it.

## Modules

1. **URL Security Scanner** — Google Safe Browsing v4, URLhaus (abuse.ch), a raw-socket
   WHOIS client, a live TLS certificate inspector, manual redirect-chain walking, and
   Levenshtein-distance typosquat detection, combined into a 0–100 score and a
   SAFE/WARNING/DANGER verdict. Supports Android's share-sheet.
2. **Photo Analysis** — real EXIF GPS extraction with on-device reverse geocoding, plus
   byte-level payload-indicator scanning: data appended after the true JPEG/PNG end
   marker, Shannon entropy of that trailing data, and signature scanning for embedded
   executables/scripts.
3. **Football** — top 5 European leagues (Premier League, La Liga, Bundesliga, Serie A,
   Ligue 1) via football-data.org's free tier: live matches, upcoming fixtures,
   standings, and head-to-head history per fixture.

## Before you build

Two free API keys:

| Key | Where to get it | Free tier |
|---|---|---|
| `SAFE_BROWSING_API_KEY` | https://console.cloud.google.com/apis/library/safebrowsing.googleapis.com | 10,000 requests/day |
| `FOOTBALL_DATA_API_KEY` | https://www.football-data.org/client/register | 10 req/min, top-5 leagues included |

URLhaus needs no key.

Copy `local.properties.example` to `local.properties` and fill in your keys (and your
Android SDK path), or pass them as Gradle properties:

```
./gradlew assembleDebug -PSAFE_BROWSING_API_KEY=xxx -PFOOTBALL_DATA_API_KEY=yyy
```

## Building

1. Open the project root in Android Studio.
2. Let Gradle sync — first point that needs network access, to download Gradle 8.7 and
   resolve dependencies.
3. Run on a device/emulator API 26+.

## What's genuinely NOT included (and why)

- **Social media video downloader** — dropped per request; a real one for Instagram/
  TikTok/Facebook/Twitter would mean reverse-engineering private endpoints against those
  platforms' Terms of Service regardless of library license.
- **VirusTotal / PhishTank / OTX / Firebase** — left out to keep every included source
  either fully free with no key (URLhaus) or free-tier-with-signup with clear limits.
- **Whole-file DB encryption (SQLCipher)** — its community license is more restrictive for
  commercial distribution than Apache/MIT. Instead, the sensitive field (scanned URL
  text) is encrypted at rest with AES-256-GCM via a hardware-backed Android Keystore key
  (`core/util/CryptoManager.kt`).

## Module map

```
app/src/main/java/com/fusionone/app/
├── core/
│   ├── database/     Room entities + DAOs
│   ├── network/       Retrofit interfaces + DTOs for all 3 external APIs
│   └── util/          WhoisClient, SslCertChecker, RedirectChainResolver,
│                        TyposquatDetector, ExifLocationExtractor, PayloadDetector,
│                        CryptoManager
├── di/                 Hilt modules
├── feature/
│   ├── urlscanner/     Repository + ViewModel + Compose screen
│   ├── photoanalysis/  ViewModel + Compose screen
│   └── football/       Repository + ViewModel + Compose screen
├── home/               Home, History, Settings screens
├── navigation/         NavGraph + bottom navigation
└── ui/theme/           Material 3 theme — black / metallic gold / teal
```

See `PRIVACY_POLICY_TEMPLATE.md` for the privacy policy draft.

## UI/UX redesign + bug fixes (this pass)

**Real bugs fixed:**
- **Photo Analysis false positives** — the payload signature scanner was checking the
  *entire compressed image byte stream* for short signatures like `MZ`. Compressed JPEG
  data is high-entropy, so 2-byte sequences like that appear by pure chance constantly —
  this is why ordinary screenshots were being flagged. Fixed to only scan the genuinely
  *appended* data after the real image end marker, which is the only place a real payload
  can live.
- **Football HTTP 403** — this specific code means your `FOOTBALL_DATA_API_KEY` is
  missing or invalid; the error message now says that directly instead of showing a bare
  status code (see `core/util/NetworkErrorMapper.kt`).
- **No back button** — Scanner, Live, and Protect are now bottom-nav tabs (not pushed
  screens), and every screen that IS pushed (History) has an explicit back arrow in its
  top app bar.

**Redesign to spec:**
- Colors now match the brand spec exactly: Rich Black `#0A0A0A` background, Teal
  `#00F5C8` as the primary CTA color, Gold `#FFD700` for alerts/score/premium, Dark Gray
  `#1A1A1A` cards, off-white/gray text hierarchy, red-orange `#FF5757` for danger. Light
  mode uses the darker teal/gold variants from the spec for contrast. Material You
  dynamic color is now off by default so the brand palette always shows.
- Dashboard redesigned to match the reference: header row, Link/Image Scanner card with
  teal "Scan Now" CTA, Live Football Matches card, a real (not fake) circular Security
  Score gauge computed from your actual scan history average, and a Recent Scans list.
- Football redesigned Sofascore-style: search bar, Matches/Leagues tabs, Live filter chip,
  day navigator, matches grouped by competition with crests, and a genuine local
  kickoff-reminder toggle (WorkManager + Android notification, no backend).

**On live video streaming — still not included, and here's exactly why:** pulling an
arbitrary match's live video feed legally requires broadcast rights licensing per
league/region — there's no free API that provides this. The Dashboard's live-match "play"
button and the Football screen open match details/stats (real data), not a video player.
If you want to explore this further, dedicated football-streaming APIs (e.g. IPTV
aggregators) exist but almost all require paid/regional licensing — worth knowing before
committing to that as a feature.

## API-Football integration (stats, lineups, player ratings, transfer history)

Added as a **second, separate** football data source alongside football-data.org:

| Key | Where to get it | Free tier |
|---|---|---|
| `API_FOOTBALL_KEY` | https://dashboard.api-football.com/register | **100 requests/day TOTAL** |

**Why two football APIs instead of one:** football-data.org stays the fast-refreshing
source for fixtures, live scores, and standings (its free tier allows far more calls).
API-Football is used *only* for what football-data.org doesn't provide — match
statistics, lineups, per-player ratings, and transfer history — because its free tier's
100-requests/day cap makes it unsuitable as a primary/frequent-polling data source.

**How the 100/day budget is protected:**
- Stats/Lineups/Players/Transfers are only fetched when you actually open that tab in a
  match's detail sheet — never prefetched or polled.
- Every response is cached for 12 hours (`ApiFootballRepository`'s `cachedOrNull`), so
  re-opening the same match's stats later the same day costs zero additional requests.
- The bridge between the two providers (football-data.org match/team IDs and
  API-Football's own fixture/team IDs — two unrelated ID systems) is resolved once by
  matching team names + date, then cached **permanently** in Room
  (`api_football_fixture_mapping` / `api_football_team_mapping` tables) — that resolution
  never has to happen twice for the same match or team.

**Known limitation — team name matching:** the two providers don't always spell club
names identically ("Manchester United FC" vs "Manchester United"). The resolver
normalizes and does a loose contains-match, which works for the large majority of
top-5-league clubs, but an unusual name mismatch could occasionally fail to resolve —
in that case the UI shows "stats/lineups aren't available" rather than guessing wrong.

**Market values (Transfermarkt-style "€45M" valuations) are still not included** — there
is no legitimate free or paid API that provides this; what's included is transfer
*history* (who moved, from/to which club, and when), which API-Football's free tier does
provide.

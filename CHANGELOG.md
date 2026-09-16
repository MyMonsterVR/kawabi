# Changelog

User-facing notes on what changed, most recent first. Not every commit gets an
entry -- only things worth telling the reader about. Shown in-app under
Settings -> About -> Changelog.

The heading directly below MUST be written exactly `## Unreleased` (no date, no
extra text) -- .github/workflows/build.yml's release step matches that exact
string to rename it to the shipped version and open a fresh blank one above it.
A differently-worded heading silently breaks that automation (it just skips the
stamp, it won't fail the build).

## Unreleased

## 0.1.0-62 - 2026-09-16

## 0.1.0-60 - 2026-09-13

- Library refresh is now near-instant -- the backend keeps chapter/episode
  lists warm on its own every 30 minutes, so pulling to refresh no longer
  waits on a live scrape of every manga/anime in your library.

## 0.1.0-58 - 2026-09-12

- Anime: switching an anime to another source now sticks -- the episode list,
  playback and the back button follow the source you picked, and any leftover
  copy of the same show from another source is folded into your entry (keeping
  its watch progress) instead of quietly taking over again.
- Anime detail: episodes are always playable; the same anime from different
  sources is one library entry with a source switcher.
- Anime tab redesigned: Home (continue watching, new episodes for you, new
  releases), Watching list, and Library with status filters.
- Anime covers and last-watched dates now come through from imports; tracker
  links sync across devices.
- Tracker connections are verified on launch; a tracker whose login expired
  shows 'Needs reconnect' instead of 'Connected'.
- Anime tab (preview): browse, search, library and episode lists from anime
  sources.
- Episodes now play in-app: full-screen landscape video player with server and
  quality switching, subtitle track picker, playback speed, skip
  intro/ending, resume where you left off, auto-mark watched, and auto-advance
  to the next episode. New Settings -> Playback group for the mark-watched
  point, preferred quality and auto-skip.
- Import your MAL/Kitsu/AniList anime list into the Anime library (Settings ->
  Tracking).
- Anime library auto-imports your "Watching" list from connected trackers
  (every 12h and right after connecting).
- AniList is now supported as a tracker alongside MyAnimeList and Kitsu, for
  both manga and anime.

## 0.1.0-36 - 2026-08-24

## 0.1.0-34 - 2026-08-24

## 0.1.0-32 - 2026-08-20

- Chapter list now shows how long ago each chapter was released (e.g. "3d
  ago") when the source provides that info.

## 0.1.0-30 - 2026-08-09

- Manga detail page now updates automatically (chapter checkmarks, resume
  point) when returning from the reader -- previously needed a trip back to
  Library and in again to see it.

## 0.1.0-28 - 2026-08-08

## 0.1.0-26 - 2026-08-08

- Reading progress now syncs across devices right when you finish a chapter,
  not just on app startup -- fixes progress getting stuck behind on a device
  that stays open a long time (e.g. a tablet).
- Fixed blurry pages on some MangaFire chapters (mainly unofficial/scanlation
  releases) that come as one big stitched image -- these now render at full
  sharpness instead of being downscaled.
- Library refresh is dramatically faster, especially for larger libraries --
  no longer waits on the old one-request-per-manga limit, so it no longer
  slows to a crawl past ~30 manga.
- Added an in-app changelog (Settings -> About -> Changelog) -- you're
  reading it.
- Settings screen redesigned with grouped cards instead of a flat list.
- New reader settings: page fit mode (fit width / fit height / original
  size), an adjustable "mark read at N% scrolled" threshold, and a per-manga
  reading-direction override (accessible from the manga's own page).
- New theming options (Settings -> Appearance): a Catppuccin Mocha palette
  alongside the original look, an AMOLED true-black toggle, and Material You
  dynamic color on Android 12+.
- Fixed missing cover art on roughly half your library -- covers in WebP
  format (common on AsuraScans and some other sources) were failing to load
  entirely. If you still see a missing cover, opening that manga once and
  going back should refresh it.

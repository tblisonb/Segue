# Segue

A music player for Android that plays your own music files, and TIDAL, in one queue, and
scrobbles to Last.fm.

- **Your library:** browse by artist, album, song, folder and playlist, and search. Segue reads
  the `Music` folder directly and keeps an index of it, so a rescan reads only what changed.
- **Playback:**
  - gapless (with FFmpeg's decoders, which Android's own MP3 decoder isn't);
  - volume levelling from ReplayGain tags, by track or by album;
  - the media notification, lock screen and Bluetooth controls.
- **The queue:** tapping a track plays its album, playlist, folder or search results from that
  track. **Play next** and **Add to queue** add to the queue without replacing it. Shuffle and
  repeat. Segue picks up where you left off, including from a headset's play button after the app
  was closed.
- **Playlists:** `.m3u8` files in `Music/Playlists`, readable by desktop players such as
  [fooyin](https://github.com/fooyin/fooyin). TIDAL tracks are written as `tidal://track/<id>`
  lines, the same as in the fooyin TIDAL plugin.
- **TIDAL:**
  - search, your favorites and your playlists (mixes included), sortable by recently added;
  - playback up to 24-bit FLAC, in the same queue as your files;
  - adding to and removing from your playlists and favorites.

  Needs a TIDAL subscription.
- **Last.fm:** scrobbling, with "now playing" shown on your profile; scrobbles made offline are
  sent later. Your stats: recent scrobbles, and top artists, albums and tracks for a chosen period.
  Now Playing shows how many times you've played the track.
- **Appearance:** Follow system, Light, Dark or Black (for AMOLED screens), with your
  wallpaper's colours or one of nine accent colours.
- **[MediaSync](https://github.com/tblisonb/MediaSync), if you use it:**
  - Segue rescans right after a sync;
  - it uses the loudness values the server measures;
  - it streams, over your tailnet, the server's tracks the phone doesn't keep.

  Optional: Segue works with any `Music` folder.

## Installing

Segue needs Android 13 or later.

1. Either:
   - **With F-Droid**, which then keeps Segue up to date: add the repo at
     [tblisonb.github.io/fdroid](https://tblisonb.github.io/fdroid/) (open that page on the phone
     and tap its link), then install Segue from it.
   - **By hand:** from the [Releases](https://github.com/tblisonb/Segue/releases) page, download
     `segue-<version>.apk` on the phone and open it. The first time, Android asks you to allow
     installing apps from your browser or file manager.

   Both are the same APK, so either updates a Segue installed the other way.
2. Open Segue and allow **All files access**. It reads the `Music` folder and writes playlists in
   it, which Android's narrower media permission doesn't allow.

   The first scan of a big library takes a couple of minutes (about 2 for 9,000 tracks). Later
   scans read only what changed.

Each release installs over the one before. Everything else is in Segue's **Settings**:

### TIDAL

TIDAL needs two logins, the same ones the fooyin TIDAL plugin uses. TIDAL only gives
third-party developer apps 30-second previews, so streaming needs a client of one of TIDAL's own
apps.

- **Your account** is for search, favorites and playlists. It uses your own developer app from
  [developer.tidal.com](https://developer.tidal.com). Add `http://localhost:47130/callback` as a
  redirect address, and give it the `user.read`, `collection.read`, `collection.write`,
  `playlists.read`, `playlists.write`, `search.read` and `playback` scopes.
- **Playback** uses a client ID (and secret, if it has one) of TIDAL's Android app, such as the
  one python-tidal uses. TIDAL could stop such a client working at any time.

Enter the client IDs and tap **Log in (both)**. TIDAL's login page opens inside Segue for your
account, then again for playback. The second time it usually goes straight through without your
password or an emailed code. If the page won't work inside Segue, log in with the browser and paste
back the address the browser ends on (an error page is fine).

The IDs and tokens stay on the phone, in Segue's own storage.

### Last.fm

Last.fm requires each app to have its own API account:

1. Create one at [last.fm/api/account/create](https://www.last.fm/api/account/create). Any name
   works, and you can leave the callback URL empty.
2. Enter its **API key** and **shared secret** in Segue's Settings and tap **Log in**.
3. Last.fm's approval page opens. Tap "Yes, allow access", and Segue notices by itself.

Your stats are under the menu (⋮) > **Last.fm**.

A track scrobbles once it has played (seeking doesn't count) for half its length or four minutes,
whichever comes first, and only if it's at least 30 seconds long. Segue sends only a track's first
artist, so featured artists don't turn into separate "A, B" artists on Last.fm.

### MediaSync

Install the MediaSync app (1.3.1 or later) and pair it as usual. Segue and MediaSync then share
these, with nothing to set up:

- the sync's "these files changed", so Segue rescans right away;
- the phone's pairing, for **Settings > Music from the server**. That lists the server's tracks the
  phone doesn't hold, marked with a cloud, and streams them over Tailscale.

Streaming needs MediaSync server 1.5.0 or later. The two apps trust each other through a
signature permission, so this works with the released APKs of both, which are signed with the same
key. A Segue you build yourself works fine, but without the MediaSync link.

## Using it

- Tap a track to play its list from that track.
- Swipe the mini player up for **Now Playing**, and swipe down anywhere on Now Playing to close it.
  Its queue button shows what plays next: a track there can be moved up to play next or
  removed, and the rest cleared.
- Every track's menu has Play next, Add to queue, Add to playlist and Go to album or artist. When
  you're logged in to TIDAL, it also has Add to TIDAL playlist and Add to or Remove from TIDAL
  favorites. Your own files are matched to TIDAL tracks by artist, title and length.
- TIDAL playlists and your favorite tracks can be sorted with the button next to Play and
  Shuffle. One sort applies to all of them.

## Building

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

You need JDK 17 or later and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in
`local.properties`).

Release builds are signed with the key that `~/.android/mediasync-release.properties` describes
(or the file named by `-Psegue.signing=<file>`): `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`. Without it they're unsigned.

`scripts/release.sh` tags a version, publishes its APK on GitHub and adds it to the F-Droid repo
(see `--help`).
[DESIGN.md](DESIGN.md) has the design decisions, what was measured on a phone, and how the TIDAL,
Last.fm and MediaSync parts work.

## License

GPL-3.0-or-later (see [COPYING](COPYING)). Segue bundles Jellyfin's GPL-3.0 build of
Media3's FFmpeg decoder, and uses [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger)
(LGPL) and [MaterialKolor's material-color-utilities](https://github.com/jordond/materialkolor)
(MIT).

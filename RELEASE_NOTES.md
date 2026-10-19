# Apple Music TV — v1.5

- The app now quits when you leave it: no cached process, no idle decoders, no background audio — sized for the 1.5–2 GB boxes these TVs actually ship with.

*🧹 Memory & lifecycle*
Leaving the app — Home, recents, switching apps, or the TV going to standby — saves the session, stops playback and ends the process, so nothing stays resident holding the Widevine CDM, the decoders and the artwork caches.
Being *covered* still only pauses: the TV's ambient screensaver or a system overlay won't kill playback, so the :8080 phone page keeps working while the TV idles.
Every launch is now a cold start; Now Playing still restores the last track, paused, at the position you left it.

*🗑️ Removed*
Background playback and its toggle (Dev menu and the phone web page) — it contradicted quitting on exit.
System Picture-in-Picture: the manifest flag, the PiP params/remote action and the minimal PiP view. Music videos still play audio-only when you navigate away from Now Playing.

*🔧 Under the hood*
The session snapshot on exit is written synchronously and in order, before the player is stopped — so the restored position is the one you actually left, not 0.

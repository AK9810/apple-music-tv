# Shortcut verification

Every signed artifact in this directory ships with a real-device record. A clean
`sign.sh` run is not verification — it only proves the output is an AEA1
container, not that a phone accepts it or that a TV keeps the token.

**Status: verified (2026-11-14).** The final build was imported and run on an
iPhone 16 Pro: the happy path and the no-token branch both behaved as specified.
The iPad and the Android companion were verified on the previous build, which
differs only in the no-token branch and a comment string; the companion path is
independent of the shortcut file.

The **one-tap import link** was tested separately on 2026-11-14: on an iPhone it
reported *Import Failed*, while **Download the Shortcut from this TV** imported
and ran with the same bytes. GitHub's release download answers `302`, and the
import handler needs a direct file. Rather than maintain an https distribution
channel for a file the TV already serves, the one-tap path was **removed** — see
[ADR-0004](../docs/adr/0004-ios-downloads-the-shortcut-from-the-tv.md); the TV
download is the only install path.

## Artifact

| Field | Value |
| --- | --- |
| Signed on | 2026-11-14 |
| macOS / Shortcuts | 26.6 (25G72) / 7.0 |
| Shortcut name | Send Token to TV (from the file name on import) |
| Source `sha256` | `79da7a7c08d60c202a71170bf77ca70625871b05b5605ce645437f6e38c0cf12` |
| Signed `sha256` | `e8dfba9ddf2b5e8dcc5df1ec82ed74500bb00808783dfa0161288c8763814fd8` |
| First 4 bytes | `41454131` ("AEA1") |

## Build provenance

The Mac's Shortcuts app has no *Run JavaScript on Web Page* action (only *Run
JavaScript for Mac Automation* and *Run JavaScript on the Active Safari Tab*), so
the workflow could not be authored through the Mac editor alone. It was generated
as a plist with the iOS action identifier
(`is.workflow.actions.runjavascriptonwebpage`), signed, and imported on macOS to
prove it parses. The maintainer chose to ship that scaffold rather than re-export
from the iPad, so the file in `source/` is the plist that was signed — it is not
an export from the Shortcuts app.

On the devices, the TV address was edited locally to `http://192.168.1.4:8080`.
The shipped default is the placeholder `http://TV-IP:8080`, which every user edits
after import. The verified workflow and the shipped workflow differ only in that
one string.

The status check reads the `/status` response as text and looks for
`"hasMUT":true` rather than parsing a dictionary — see
[the recipe](README.md#why-the-status-check-is-a-text-match) for the two failed
alternatives observed while building this.

## Device

| Field | Value |
| --- | --- |
| Phone | iPhone 16 Pro, iOS 27.2 |
| Tablet | iPad Air 7 (2025), iPadOS 26.6.1 |
| TV | app at `192.168.1.4:8080`; exact model not recorded |
| Android companion | same LAN; native `GET /set-token` hand-off |
| TV address used during verification | `http://192.168.1.4:8080` |

## Checks

| # | Check | Expected | Result |
| --- | --- | --- | --- |
| 1 | Signed file imports from Files or AirDrop | Review screen → Add Shortcut; no "untrusted" refusal | **Pass** — iPhone 16 Pro on the final build; iPad Air 7 on the previous build (same AEA1/signing path) |
| 2 | Shortcut details: Show in Share Sheet, input Safari web pages | Appears in Safari's share sheet as "Send Token to TV" | **Pass** |
| 3 | Run from Safari's share sheet on a signed-in `music.apple.com` page | "Token received — the TV can now play your library." | **Pass** — iPhone 16 Pro on the final build; iPad Air 7 on the previous build |
| 4 | `GET /status` right after the run | `"hasMUT":true`, and a fresh save in the log | **Pass** — `[14:43:49.475][OK] Music-User-Token saved (246 chars)`; the final build's run saved at ~15:20:00 (its proxy sync timed out at 15:20:10) |
| 5 | Library on the TV | Songs, albums, playlists populated | **Pass** (maintainer) |
| 6 | Play a library song | Full song, not a 30-second preview | **Pass** — logs show `isFullStream=true` and full durations |
| 7 | Run on a page with no token (e.g. any non-Apple page in Safari) | "No token on this page."; no `set-token` in the log | **Pass on the final build** — run from `example.com`; the notification appeared and no save followed |
| 8 | Run with the TV off / wrong address | iOS "Could not connect to the server." | **Observed** — "无法连接服务器" with the TV app closed |
| 9 | Android companion sign-in, unchanged | Token lands; TV reloads its control page | **Pass** — `[15:02:46.013][OK] Music-User-Token saved (246 chars)`; the companion's native GET hand-off still writes the token |
| 10 | One-tap import link | — | **Removed (2026-11-14)** — the release URL failed with *Import Failed*; the path was dropped in favour of the TV download ([ADR-0004](../docs/adr/0004-ios-downloads-the-shortcut-from-the-tv.md)) |

## Notes

- Replacement is proven by the fresh `saved` line, not by the notification: a
  pre-existing token also makes `hasMUT` true, so the notification alone cannot
  distinguish "kept the new token" from "still had the old one".
- `[WARN] Server MUT sync failed: failed to connect to /10.0.2.2 (port 3000)` is
  expected without a PC proxy; the on-device token is already saved.
- The Mac and the TV could not reach each other (wired/wireless isolation), so
  the TV-side evidence came from the TV's own log page, not from the Mac.
- Quote each failure word for word; the copy in [`README.md`](README.md) and the
  setup docs must match what the phone actually says.
- If check 8 ever gains a custom notification, that means Shortcuts shipped error
  handling; update the recipe instead of keeping this note.
- Token lifetime is roughly six months. A library that suddenly empties months
  later is an expired token, not a broken Shortcut.

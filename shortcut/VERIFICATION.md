# Shortcut verification

Every signed artifact in this directory ships with a real-device record. A clean
`sign.sh` run is not verification — it only proves the output is an AEA1
container, not that a phone accepts it or that a TV keeps the token.

**Status: verified on iPhone, iPad and the Android companion (2026-11-14).** One
re-check is pending: the final build changes the empty-token branch, so check 7
must be run once more on a signed-out page.

The device evidence below was collected on the build with source
`57097e61…` / signed `aeb13ef0…`. The current build changes only the no-token
branch (`completion()` instead of `""`) and the comment text; the happy path is
unchanged.

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
| 1 | Signed file imports from Files or AirDrop | Review screen → Add Shortcut; no "untrusted" refusal | **Pass** on both iPhone and iPad |
| 2 | Shortcut details: Show in Share Sheet, input Safari web pages | Appears in Safari's share sheet as "Send Token to TV" | **Pass** |
| 3 | Run from Safari's share sheet on a signed-in `music.apple.com` page | "Token received — the TV can now play your library." | **Pass** on both devices |
| 4 | `GET /status` right after the run | `"hasMUT":true`, and a fresh save in the log | **Pass** — `[14:43:49.475][OK] Music-User-Token saved (246 chars)` |
| 5 | Library on the TV | Songs, albums, playlists populated | **Pass** (maintainer) |
| 6 | Play a library song | Full song, not a 30-second preview | **Pass** — logs show `isFullStream=true` and full durations |
| 7 | Run on a page with no token (e.g. any non-Apple page in Safari) | "No token on this page."; no `set-token` in the log | **Pending on the final build** — the earlier observation was a run with no Safari page. The final build returns no output instead of `""` here, so this branch must be re-run |
| 8 | Run with the TV off / wrong address | iOS "Could not connect to the server." | **Observed** — "无法连接服务器" with the TV app closed |
| 9 | Android companion sign-in, unchanged | Token lands; TV reloads its control page | **Pass** — `[15:02:46.013][OK] Music-User-Token saved (246 chars)`; the companion's native GET hand-off still writes the token |

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

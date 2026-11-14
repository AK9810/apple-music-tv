# Shortcut verification

Every signed artifact in this directory ships with a real-device record. A clean
`sign.sh` run is not verification — it only proves the output is an AEA1
container, not that a phone accepts it or that a TV keeps the token.

**Status: provisional.** The current artifact imports and runs on macOS, and its
form encoding and status branch were exercised there. It has **not** been
imported or run on an iPhone or iPad yet; the checks below (1–8) are still open.

## Artifact

| Field | Value |
| --- | --- |
| Signed on | 2026-11-14 |
| macOS / Shortcuts | 26.6 (25G72) / 7.0 |
| Shortcut name | Send Token to TV |
| Source `sha256` | `0b877709b1fbc04676a451926ff2bbbd52e7b8d05d385b9d727e2d07161279ac` |
| Signed `sha256` | `4969636e78efdb53400d14a88b2fb274bfeae83054ea70c5d2df93b94b508109` |
| First 4 bytes | `41454131` ("AEA1") |

## Build provenance

The Mac's Shortcuts app has no *Run JavaScript on Web Page* action (only *Run
JavaScript for Mac Automation* and *Run JavaScript on the Active Safari Tab*), so
the workflow file could not be produced through the Mac editor alone. It was
generated as a plist scaffold with the iOS action identifier
(`is.workflow.actions.runjavascriptonwebpage`), then signed and imported on macOS
to prove it parses. The iPad authoring path was blocked, so this scaffold is what
the maintainer will open, repair if needed, and re-export.

The unsigned plist is stored in `source/` and is exactly what was signed; it is
not yet an export from the Shortcuts app. After the iPad edits, replace it with
the app's export, re-sign, and update the hashes above.

The status check reads the `/status` response as text and looks for
`"hasMUT":true` rather than parsing a dictionary — see
[the recipe](README.md#why-the-status-check-is-a-text-match) for the two failed
alternatives observed while building this.

## Device

| Field | Value |
| --- | --- |
| Phone / iPad | — |
| iOS / iPadOS | — |
| TV model and app build | — |
| TV address used in action 2 | — |

## Checks

| # | Check | Expected | Result |
| --- | --- | --- | --- |
| 1 | Signed file imports from Files or AirDrop, after deleting the unsigned copy | Review screen → Add Shortcut; no "untrusted" refusal | — |
| 2 | Shortcut details: Show in Share Sheet, input Safari web pages | Visible in Safari's share sheet as "Send Token to TV" | — |
| 3 | Run from Safari's share sheet on a signed-in `music.apple.com` page | Notification "Token received — the TV can now play your library." | — |
| 4 | `GET http://<TV>:8080/status` right after the run | `"hasMUT":true` | — |
| 5 | Library on the TV | Songs, albums, playlists populated | — |
| 6 | Play a library song | The song plays in full (not a 30-second preview) | — |
| 7 | Run on a page with no token (signed-out page, or any non-Apple page in Safari) | "No token on this page." — and no `set-token` line in the TV's `/logs` | — |
| 8 | Run with the TV off, or with a wrong address in action 2 | iOS stops with its own "Could not connect to the server." alert | — |
| 9 | Android companion sign-in, unchanged | Token lands; TV reloads its control page | — |

## What was verified on macOS

| Check | Result |
| --- | --- |
| Signed artifact imports (same AEA1 file) | Pass — preview shows the name and "Show in Share Sheet" |
| All control-flow blocks and variables render | Pass — no unresolved parameters |
| Form POST encoding | `token` + `+` + `%` → `mut=…%2B…%25…`, `Content-Type: application/x-www-form-urlencoded` |
| Status branch | `/status` text containing `"hasMUT":true` takes the success branch |
| JavaScript action input | Not runnable on macOS (scripts disabled); must be confirmed on iOS that it receives the shared webpage |

## Notes

- Quote each failure word for word; the copy in [`README.md`](README.md) and the
  setup docs must match what the phone actually says.
- If check 8 ever gains a custom notification, that means Shortcuts shipped error
  handling; update the recipe instead of keeping this note.
- Token lifetime is roughly six months. A library that suddenly empties months
  later is an expired token, not a broken Shortcut.

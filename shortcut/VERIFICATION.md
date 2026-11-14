# Shortcut verification

Every signed artifact in this directory ships with a real-device record. A clean
`sign.sh` run is not verification — it only proves the output is an AEA1
container, not that a phone accepts it or that a TV keeps the token.

**Status: pending — awaiting the first signed build ([#13]).**

## Artifact

| Field | Value |
| --- | --- |
| Signed on | — |
| macOS / `shortcuts` | — |
| Shortcut name | Send Token to TV |
| Source `sha256` | — |
| Signed `sha256` | — |
| First 4 bytes | — (must be `41454131`, "AEA1") |

## Device

| Field | Value |
| --- | --- |
| Phone / iPad | — |
| iOS / iPadOS | — |
| TV model and app build | — |
| TV address used in action 1 | — |

## Checks

| # | Check | Expected | Result |
| --- | --- | --- | --- |
| 1 | Signed file imports from Files or AirDrop, after deleting the unsigned copy | Review screen → Add Shortcut; no "untrusted" refusal | — |
| 2 | Run from Safari's share sheet on a signed-in `music.apple.com` page | Notification "Token received — the TV can now play your library." | — |
| 3 | `GET http://<TV>:8080/status` right after the run | `"hasMUT":true` | — |
| 4 | Library on the TV | Songs, albums, playlists populated | — |
| 5 | Play a library song | The song plays in full (not a 30-second preview) | — |
| 6 | Run on a page with no token (signed-out page, or any non-Apple page in Safari) | "No token on this page." — and no `set-token` line in the TV's `/logs` | — |
| 7 | Run with the TV off, or with a wrong address in action 1 | iOS stops with its own "Could not connect to the server." alert | — |
| 8 | Android companion sign-in, unchanged | Token lands; TV reloads its control page | — |

## Notes

- Quote each failure word for word; the copy in [`README.md`](README.md) and the
  setup docs must match what the phone actually says.
- If check 7 ever gains a custom notification, that means Shortcuts shipped error
  handling; update the recipe instead of keeping this note.
- Token lifetime is roughly six months. A library that suddenly empties months
  later is an expired token, not a broken Shortcut.

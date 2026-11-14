# The iOS Shortcut

**English** | [简体中文](README.zh-CN.md)

The iPhone and iPad path to a Music-User-Token (MUT): a signed Shortcut reads
`document.cookie` on a signed-in `music.apple.com` page in Safari and POSTs the
token to the TV's port-8080 server. No iOS app, no Xcode, no developer account —
the reasoning is in [ADR-0002](../docs/adr/0002-ios-shortcut-instead-of-an-app.md).

The TV side already exists and is unchanged: `POST /set-token` with a form field
`mut`, then `GET /status` for `hasMUT`. This directory is only about the phone.

## What is here

| Path | What it is |
| --- | --- |
| `source/Send Token to TV.shortcut` | The unsigned source, exactly what gets signed. Normally an export from the Shortcuts app; the current file is the generated scaffold recorded in [`VERIFICATION.md`](VERIFICATION.md). |
| `Send Token to TV.shortcut` | The signed artifact (AEA1 container). What a phone imports; the TV will serve it once [#14](https://github.com/AK9810/apple-music-tv/issues/14) lands. |
| `sign.sh` | Signs the source and checks the AEA1 header. |
| `VERIFICATION.md` | The on-device run that justifies shipping the artifact. |

The signed file differs from the source only by the AEA1 wrapper and signature.
Neither contains a secret: the only per-user value is the TV address, and that is
an editable action.

## Who does what

- **Recipe and signing** — this document and `sign.sh`; repeatable.
- **Build** — the maintainer, once, in the Shortcuts app. The `shortcuts`
  command-line tool can run, list, view and sign, but not create. The current
  artifact predates that step: it was generated as a plist scaffold because the
  Mac has no *Run JavaScript on Web Page* action, then approved as the shipped
  source. [`VERIFICATION.md`](VERIFICATION.md) records the provenance.
- **Verify** — a real iPhone (not a simulator) and a real TV: import, run from
  Safari, and a library song playing. The simulator cannot prove any of it.

## The three phone prerequisites

Call these out before the user starts. The first one fails without a clear
message, so it is easy to misread as "the token is missing".

1. **Allow Running Scripts.** Settings → Shortcuts → Advanced → Allow Running
   Scripts = on. This is a Shortcuts setting, not a Safari one. With it off the
   JavaScript action fails.
2. **Share-sheet availability.** In the Shortcut's details, Show in Share Sheet =
   on, accepting Safari web pages. The Shortcut is run from Safari's share sheet;
   run from the Shortcuts app it has no web page to read. Only Safari works —
   Chrome and Firefox do not hand the page to the action.
3. **Local Network permission.** The first run asks *"Shortcuts" would like to
   find and connect to devices on your local network* — Allow. If it was denied:
   Settings → Privacy & Security → Local Network → Shortcuts = on. Phone and TV
   must be on the same network.

On the first run Safari also asks whether the Shortcut may interact with the page
(it can read cookies). Allow once per page per Shortcut. That is a prompt, not a
prerequisite.

## Build recipe

The file name **is** the shortcut's name on import: keep `Send Token to TV.shortcut`
or the share-sheet label changes. (The workflow plist carries no name of its own.)

Build it on the iPhone that will run it. The actions are portable, and the Mac's
Shortcuts app can author the same file — it is what produced the first draft —
but the Mac has no *Run JavaScript on Web Page* action, so the JavaScript step
must exist in the plist (identifier `is.workflow.actions.runjavascriptonwebpage`)
or be added on iOS. The current artifact was generated that way; the steps below
describe the same workflow for any rebuild.

Actions, in order:

1. **Run JavaScript on Web Page** — rename its output `Token`; input Shortcut
   Input (the Safari web page); script below. Put it first: the first action's
   input is the shared webpage by default.
2. **Comment** — the prompt above the address: replace `TV-IP` with the address
   shown on the TV's setup page, for example `http://192.168.1.4:8080`, with no
   trailing slash.
3. **Text** — rename the action `TV address`; value `http://TV-IP:8080`. Every
   user edits this after import: the signature gates import, not later edits. The
   placeholder is deliberately not a real address, so an unedited run fails
   visibly instead of probing some other device.
4. **If** `Token` **has any value**
   1. **Get Contents of URL** — URL: `TV address` + `/set-token`; Method `POST`;
      Request Body `Form`; one field `mut` = `Token`.
   2. **Get Contents of URL** — URL: `TV address` + `/status`; Method `GET`.
   3. **Get Text from Input** — input `Contents of URL`; output `Status text`.
   4. **If** `Status text` **contains** `"hasMUT":true`
      - **Show Notification** — title `Apple Music TV`; body `Token received —
        the TV can now play your library.`
      - **Otherwise** → **Show Notification** — title `Apple Music TV`; body
        `The TV answered but did not keep the token. Run it again, or paste the
        token on the TV's setup page.`
5. **Otherwise** → **Show Notification** — title `Apple Music TV`; body `No token
   on this page. Sign in at music.apple.com in Safari, then run this Shortcut
   from that page's share sheet.`

The empty token branches *before* the POST, so an empty value is never written.
`Otherwise` is inserted as its own action (search for it while inside the `If`
block), not toggled.

To point an action at a variable, tap the parameter field and pick the action
from *Select Variable* — renaming the JavaScript action and the Text action is
what makes the variable names unambiguous.

### Why the status check is a text match

`Get Dictionary Value` would be the obvious way to read `hasMUT`, but its output
is a generic value, and Shortcuts only offers the *has any value* conditions for
it — a crafted `is true` condition imports unresolved ("choose a value for each
parameter"). Coercing the boolean to text does not help either: the coercion is
localized, so it is `true` in English but `是` in Chinese, and a literal `true`
comparison silently takes the failure branch. Both were observed while building
this. `GET /status` returns `{"hasMUT":true,…}` with no spaces (`InAppWebServer.status()`),
so testing the response text for `"hasMUT":true` is deterministic and
locale-independent today. It is a formatting dependency: if that JSON ever gains
a space, this condition must change with it.

### The page script

```js
const match = document.cookie.match(/(?:^|;\s*)media-user-token=([^;]*)/);
if (!match || !match[1]) {
  completion();            // no value, not an empty string
} else {
  let token = match[1];
  try { token = decodeURIComponent(token); } catch (e) { /* keep the raw value */ }
  completion(token);
}
```

- `completion(...)` is the action's callback; its value becomes `Token`.
- No cookie → `completion()` with no argument, so the action produces **no
  output** and the `If` is false. Do not return `""` instead: Shortcuts can treat
  empty text as a value, which would POST `mut=`; the TV ignores it, but if a
  token was already set the status still reads true and the Shortcut reports
  success. (Observed while building this.)
- `decodeURIComponent` is guarded, so a stray `%` keeps the raw value instead of
  aborting the Shortcut.
- A literal `+` is safe end to end. The TV's form parser preserves a literal `+`
  rather than decoding it to a space ([FormBody.kt, #11](../android/app/src/main/java/com/applemusicktv/util/FormBody.kt)):
  a silently altered token looks like "signed in but the library is empty".
- The page must be `music.apple.com`. Apple's own web player reads the same
  cookie, which is why no Apple API or developer token is involved.

### When the TV is unreachable

Shortcuts has no try/catch, and `Get Contents of URL` stops the Shortcut when the
connection fails. So "TV unreachable" is iOS's own **Could not connect to the
server** alert, not a notification from us — still distinct from both
notifications above, and it arrives before `/status` is read. The user checks
Wi-Fi, that the TV app is running, and the address in action 3. Do not "fix" this
by posting the token in a URL: not putting the token in a URL is one of the
design's requirements ([#10](https://github.com/AK9810/apple-music-tv/issues/10)).

## Export, sign, verify

1. In the Shortcut editor, tap the Shortcut's name (the down-arrow beside it) →
   **Export File** → save to Files or AirDrop it over. Do not use *iCloud Link*:
   that publishes a copy instead of giving us the file.
2. Put the export at `shortcut/source/Send Token to TV.shortcut`.
3. `./shortcut/sign.sh` — runs `shortcuts sign -m anyone`, refuses anything that
   is not an AEA1 container, and prints both SHA-256s.
4. On a real iPhone: remove the local unsigned copy, import the signed artifact
   from Files or AirDrop, run it from Safari's share sheet on a signed-in
   `music.apple.com` page, then confirm on the TV: token present, library
   populated, a library song plays.
5. Record the run in [`VERIFICATION.md`](VERIFICATION.md): device, OS versions,
   hashes, and what each failure looked like.

The signature is what lets a phone import a file without the old "untrusted
shortcut" toggle. The Mac must be signed in to iCloud for the signing step; if it
reports an authentication error, check System Settings → Apple Account (and
iCloud Drive) before retrying.

## Rebuilding after an edit

Edit in the Shortcuts app → export → replace `source/…` → `./sign.sh` → replace
the artifact → repeat the device checks in `VERIFICATION.md` and update its
date, hashes and results. Never sign a file that is not the one in `source/`:
that is the drift this directory exists to prevent.

# iOS Shortcut setup

**English** | [简体中文](ios-shortcut.zh-CN.md)

On an iPhone or iPad there is no Chrome DevTools, so the desktop token-copy steps
do not work. Instead, a signed **Shortcut** captures the Media-User-Token (MUT)
from your own signed-in Safari session on `music.apple.com` and hands it to the TV
over the local network. There is no iOS app, no Xcode and no developer account —
why this route was chosen is in
[ADR-0002](adr/0002-ios-shortcut-instead-of-an-app.md). This guide takes a fresh
iPhone or iPad to a playing library song.

## Before you start: the three prerequisites

Set these once, before the first run. The names match the TV's setup page exactly.

1. **Allow Running Scripts** — Settings → Shortcuts → Advanced → Allow Running
   Scripts = on. This is a Shortcuts setting, not a Safari one. If it is off, the
   Shortcut stops at its JavaScript step with an error; nothing reaches the TV.
2. **Share-sheet availability** — open the Shortcut's details and set Show in
   Share Sheet = on, accepting Safari web pages. If it is off, "Send Token to TV"
   is missing from Safari's share sheet; running the Shortcut from the Shortcuts
   app instead gives it no web page to read, and the run ends with the no-token
   notification.
3. **Local Network permission** — the first run asks whether "Shortcuts" may find
   and connect to devices on your local network; tap **Allow**. If it was denied,
   the run cannot reach the TV and iOS shows its connection alert. Re-enable it in
   Settings → Privacy & Security → Local Network → Shortcuts = on. Phone and TV
   must be on the same network.

On the first run Safari also asks whether the Shortcut may interact with the page
— it must, in order to read the cookie. Allow once per page per Shortcut. That is
a normal prompt, not a prerequisite.

## Install the Shortcut

### One tap from the TV page

1. On the phone, open `http://<TV_IP>:8080` — the address is shown on the TV's
   sign-in screen.
2. In the **Sign in with the Shortcut (iOS)** card, tap **Install the Shortcut**.
   The link opens the Shortcuts import preview directly.
3. Tap **Add** to finish. iOS always asks for this confirmation and the page
   cannot skip it: if you see the preview, the install has not failed, it is just
   waiting for that tap.

iOS refuses `http://` targets for import links, which is why the one-tap link
points at the https release asset. It is the same signed file the TV serves.

### Offline: download from the TV

If the phone cannot reach the internet during setup, the TV can still deliver the
file:

1. On the same setup page, tap **Download the Shortcut from this TV**.
2. Open the downloaded `Send Token to TV.shortcut` in **Files**. (The link's path
   is hyphenated, `/send-token-to-tv.shortcut`; the saved file keeps the spaces in
   its name, which is what names the Shortcut on import.)
3. Tap **Add**.

This is the identical signed file as the one-tap path — a different URL, not a
second build. On a replacement phone, repeat either install path and set the TV
address again; nothing on the TV changes.

## Set the TV address

After import, open the Shortcut and find the **Text** action named `TV address`.
Replace `http://TV-IP:8080` with the address at the top of the TV's setup page,
for example `http://192.168.1.4:8080` — with no trailing slash.

The address stays editable after import; the signature gates import, not later
edits. If the TV's IP changes, edit this action. There is no need to re-import the
Shortcut.

## Run it

1. Open **Safari** and sign in at `music.apple.com` with your Apple ID as usual
   (password and 2FA).
2. Stay on that page and tap **Share**.
3. Choose **Send Token to TV** in the share sheet.
4. Let it run. It reads `media-user-token` from the page, POSTs the token to
   `http://<TV_IP>:8080/set-token`, then reads `/status` to check that the TV kept
   it.

## What success looks like

- The notification says `Token received — the TV can now play your library.`
- The TV setup page's Status card turns green and reads `Music-User-Token active`.
- The TV's **Library** fills with your songs, albums and playlists.
- A library song plays in full, not a 30-second preview.

Playback is the real acceptance test, not the notification: a pre-existing token
also makes the TV look signed in, so confirm the library and play a song.

## If it does not work

| Symptom | What it means and what to do |
| --- | --- |
| `No token on this page. Sign in at music.apple.com in Safari, then run this Shortcut from that page share sheet.` | Not signed in, or the page carries no `media-user-token` cookie. Sign in at `music.apple.com` **in Safari**, stay on that page, and run the Shortcut from its share sheet. |
| The Shortcut stops at the JavaScript step (`Run JavaScript on Web Page`) with an error; no notification appears. | **Allow Running Scripts** is off. Settings → Shortcuts → Advanced → Allow Running Scripts = on, then run again. |
| **Send Token to TV** is missing from Safari's share sheet, or the run ends with the no-token notification. | **Share-sheet availability** is off, the Shortcut was run from the Shortcuts app, or the page is in Chrome / Firefox. Set Show in Share Sheet = on for Safari web pages, open the page in Safari, and share from there — Chrome and Firefox do not hand the page to the action. |
| Every run fails to reach the TV even though the phone is on Wi-Fi. | **Local Network permission** was denied. Settings → Privacy & Security → Local Network → Shortcuts = on, then run again. |
| iOS shows `Could not connect to the server.` (Chinese device: `无法连接服务器`). | The TV is unreachable. This is iOS's own alert, not a notification from the Shortcut. Check that the phone and TV are on the same Wi-Fi, that the TV app is running, and that `TV address` matches the address at the top of the TV page. |
| `The TV answered but did not keep the token. Run it again, or paste the token on the TV setup page.` | The TV received the hand-off but did not store the token. Run it again; if it repeats, use a fallback below. |
| The library worked for months, then emptied; songs stop playing. | The token lasts roughly six months. Sign in at `music.apple.com` in Safari and run the Shortcut again — no re-import is needed. |

## Token lifetime

The MUT lasts roughly six months. The Shortcut and the TV need no updates for
this; when the token expires, sign in at `music.apple.com` again and re-run the
Shortcut. A library that suddenly empties, or songs that stop playing, is the
signal.

## Fallbacks

### Paste the token on the TV page

Open `http://<TV_IP>:8080`, find the **Set Token** card, paste the token into the
box and tap **Save Token**. The form POSTs to `/set-token` and the TV redirects
back to `/`, where the Status card turns green and reads `Music-User-Token
active`. The value to paste is the `media-user-token` cookie; the desktop steps in
[TECHNICAL.md](TECHNICAL.md#getting-your-music-user-token) or the Mac walkthrough
below show how to copy it.

### Mac + Safari Web Inspector

With a Mac available:

1. Open Safari on the Mac and sign in at `music.apple.com`.
2. Enable the Develop menu: Safari → Settings → Advanced → **Show features for
   web developers**.
3. With the `music.apple.com` tab frontmost, choose Develop → **Show Web
   Inspector**.
4. Open **Storage** → **Cookies** → `music.apple.com`.
5. Select `media-user-token` and copy its value.
6. Paste it into the TV page's **Set Token** card and tap **Save Token**.

The **Set Token** card is on every version of the setup page, including the iOS
one, so manual paste is always available.

## Security

The hand-off is plaintext HTTP on your home LAN and has no pairing step. Anyone
who can reach the TV's port 8080 can read its status and logs and set the token.
Keep the TV on a trusted home network and do not expose port 8080 to the
internet — more in [SECURITY.md](../SECURITY.md).

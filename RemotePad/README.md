# RemotePad — control your Windows PC from your Android phone

Works over WiFi or phone hotspot. No internet needed.
- **PC:** Windows 7 or newer
- **Phone:** Android 10 or newer

Two parts:
1. `PC_Server` — small program that runs on the PC
2. `Android_App` — the phone app (open in Android Studio and build)

---

## PART 1 — PC setup (one time)

1. Install **Python 3.8.10** (the last version that runs on Windows 7).
   On Windows 10/11 any newer Python is fine.
   During install, tick **"Add Python to PATH"**.
2. Copy the `PC_Server` folder to the PC (for example Desktop).
3. Right-click `allow_firewall.bat` → **Run as administrator**. (One time only.)
4. Double-click `run_server.bat`.
5. A black window opens and shows:
   - **PC IP** (example 192.168.43.25)
   - **PIN** (4 digits, new every time you start)

Keep this window open while using the phone.

## AUTO MODE (recommended) — no typing after restart

1. Right-click `PC_Server/install_autostart.bat` → **Run as administrator** (one time).
   The server now starts hidden every time Windows starts, and the PIN is fixed (saved in `pin.txt`).
2. Open the phone app once, type the PIN from `pin.txt`, tap Connect.
3. From then on: restart PC → turn on WiFi/hotspot → open the app. It finds the PC by itself
   (even if the IP changes) and reconnects automatically if the link drops.
   To undo auto-start: run `remove_autostart.bat`.

## PART 2 — Build the phone app

1. Install **Android Studio** (free) on your computer.
2. Open Android Studio → **Open** → pick the `Android_App` folder → **OK**.
3. Wait until the bottom bar finishes **Gradle Sync** (first time takes a few minutes, needs internet).
4. Build the APK: menu **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
5. Click **locate** in the popup. Copy `app-debug.apk` to the phone and install it
   (allow "Install unknown apps" if asked).

(Or connect the phone with USB, turn on **Developer options → USB debugging**, and press the green ▶ **Run** button.)

## PART 3 — Connect

1. Put phone and PC on the **same WiFi**, OR turn on the **phone hotspot** and connect the PC to it.
2. Open **RemotePad** on the phone.
3. Type the **PC IP** and **PIN** from the PC window → tap **Connect**.

## What the buttons do

| Button | Opens |
|---|---|
| Mouse / Touchpad | Drag = move cursor (hover). Tap = left click. 2-finger tap = right click. Top-left ◀ Page / Page ▶ = press the browser Back / Forward arrows (mouse side buttons). Left-side strip = PC volume (slide up/down). Right-side strip = scroll. ◀ / ▶ arrow keys (hold to repeat). ⌨ Keys button (top right), ⚡ Shortcuts button (top right of the tab row) opens the keyboard directly. Left / Double / Middle / Right buttons, Drag mode, Speed slider |
| Arrow Keys | ▲ ◀ ▼ ▶ (hold to repeat), Enter, Esc, Backspace, Tab, Space, Del, Home, End, PgUp, PgDn, Win, F5, Alt+F4 |
| Volume & Media | Volume +/− (hold), Mute, Prev, Play/Pause, Next, Stop. Phone volume buttons also work here |
| Keyboard | Shortcut buttons at the top, then the phone keyboard box (whatever you type appears on the PC live), then Esc, Copy, Paste, F2, F5, then a touchpad. 🖱 Mouse button (top right) goes straight to the Mouse screen |
| Shortcuts | Shut down (Alt+F4, wait 2 sec, Enter), Close app (Alt+F4), Copy history (Win+V), Next tab (Ctrl+Tab), Prev tab (Ctrl+Shift+Tab), New tab (Ctrl+T), Close tab (Ctrl+W), Find (Ctrl+F) |

## Problems?

- **"No reply"** → check IP and PIN match the PC window, same WiFi, and run `allow_firewall.bat` as administrator.
- Use the IP of the PC's **WiFi adapter**. If the window shows several IPs, try each one.
- PIN changes every time you restart the server.
- The server does not control Windows login screen or programs running as Administrator (Windows blocks this). Run `run_server.bat` as administrator if you need that.
- Anyone on your WiFi who knows IP + PIN could control the PC, so only use on networks you trust.

- **Memory keys (Shortcuts screen, bottom row):** M1–M4 buttons. Tap runs the action. Long-press to:
  - choose a preset (mouse clicks, Copy, Paste, Select all, Undo, Save, Esc, Enter, Show desktop);
  - **⏺ Record clicks**: use the Mouse screen, click where you want, then tap ⏹ Stop. The PC cursor position and timing of every click are saved. A tap replays them: the cursor moves to each saved spot and clicks at the same pauses;
  - **✏ Name this button** to give the button your own name. Names and choices are saved on the phone.
- **Touchpad smoothness:** finger movements are batched and sent about 60 times per second, instead of one network packet per touch, to reduce stutter on Wi-Fi.

package com.rocky.remotepad

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val client = UdpClient()
    private val handler = Handler(Looper.getMainLooper())

    private val BG = 0xFF101418.toInt()
    private val BTN = 0xB32A2F38.toInt()
    private val ACCENT = 0xFF3D7BFF.toInt()

    private lateinit var host: FrameLayout
    private lateinit var status: TextView
    private lateinit var connectPanel: View
    private lateinit var homePanel: View
    private lateinit var mousePanel: View
    private lateinit var arrowPanel: View
    private lateinit var volumePanel: View
    private lateinit var keyboardPanel: View
    private lateinit var shortcutsPanel: View
    private lateinit var typeField: EditText
    private var current: View? = null
    private var sens = 2.0f
    private var suppressText = false
    private var connected = false
    private var busy = false
    private lateinit var ipField: EditText
    private lateinit var pinField: EditText

    // Windows virtual-key codes
    private val VK_BACK = 0x08
    private val VK_TAB = 0x09
    private val VK_ENTER = 0x0D
    private val VK_SHIFT = 0x10
    private val VK_CTRL = 0x11
    private val VK_ALT = 0x12
    private val VK_ESC = 0x1B
    private val VK_SPACE = 0x20
    private val VK_PGUP = 0x21
    private val VK_PGDN = 0x22
    private val VK_END = 0x23
    private val VK_HOME = 0x24
    private val VK_LEFT = 0x25
    private val VK_UP = 0x26
    private val VK_RIGHT = 0x27
    private val VK_DOWN = 0x28
    private val VK_DEL = 0x2E
    private val VK_LWIN = 0x5B
    private val VK_F4 = 0x73
    private val VK_F5 = 0x74
    private val VK_V = 0x56
    private val VK_T = 0x54
    private val VK_W = 0x57
    private val VK_F = 0x46
    private val VK_VOL_MUTE = 0xAD
    private val VK_VOL_DOWN = 0xAE
    private val VK_VOL_UP = 0xAF
    private val VK_NEXT = 0xB0
    private val VK_PREV = 0xB1
    private val VK_STOP = 0xB2
    private val VK_PLAY = 0xB3

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val lavaBg: android.graphics.drawable.Drawable by lazy {
        CoverBackground(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.lava_bg))
    }
    private fun screenBg(): android.graphics.drawable.Drawable = lavaBg

    // ---------- small UI helpers ----------
    private fun bg(color: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(c)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(ACCENT))
            addState(intArrayOf(), shape(color))
        }
    }

    private fun btn(label: String, color: Int = BTN, size: Float = 18f, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = size
        b.isAllCaps = false
        b.background = bg(color)
        b.stateListAnimator = null
        b.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
        return b
    }

    /** Button that sends a key once, and keeps repeating while held down. */
    private fun holdKey(label: String, vk: Int, size: Float = 18f): Button {
        val b = btn(label, size = size) {}
        b.setOnClickListener(null)
        val repeater = object : Runnable {
            override fun run() {
                client.send("KEY|$vk|tap")
                handler.postDelayed(this, 70)
            }
        }
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    client.send("KEY|$vk|tap")
                    handler.postDelayed(repeater, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                }
            }
            true
        }
        return b
    }

    private fun keyBtn(label: String, vk: Int, size: Float = 18f) =
        btn(label, size = size) { client.send("KEY|$vk|tap") }

    private fun comboBtn(label: String, vararg vks: Int) =
        btn(label, size = 16f) { client.send("COMBO|" + vks.joinToString("+")) }

    private fun cellLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun row(vararg v: View, h: Float = 1f, fixedDp: Int = 0): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = if (fixedDp > 0)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(fixedDp))
        else
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, h)
        v.forEach { r.addView(it, cellLp()) }
        return r
    }

    private fun column(vararg rows: View): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(8), dp(4), dp(8), dp(8))
        rows.forEach { c.addView(it) }
        return c
    }

    private fun label(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER_VERTICAL
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    private fun spacer() = View(this)

    /** Wraps content with a header (Back button + title). */
    private fun panel(title: String, content: View, topRight: View? = null): View {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = screenBg()
        val back = btn("◀ Back", size = 16f) { show(homePanel) }
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(8), dp(4), dp(8), 0)
        header.addView(back, LinearLayout.LayoutParams(dp(110), dp(46)))
        val t = label(title, 20f, true)
        t.setPadding(dp(16), 0, 0, 0)
        header.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        if (topRight != null) header.addView(topRight, LinearLayout.LayoutParams(dp(110), dp(46)))
        p.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        p.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return p
    }

    private fun show(p: View) {
        for (i in 0 until host.childCount) {
            host.getChildAt(i).visibility = if (host.getChildAt(i) === p) View.VISIBLE else View.GONE
        }
        current = p
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (p === keyboardPanel) {
            typeField.post {
                typeField.requestFocus()
                imm.showSoftInput(typeField, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            imm.hideSoftInputFromWindow(host.windowToken, 0)
        }
    }

    // ---------- screens ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = screenBg()

        status = label("Not connected", 13f)
        status.setPadding(dp(12), dp(8), dp(12), dp(4))
        status.setTextColor(0xFF8A97A8.toInt())
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        connectPanel = buildConnect()
        homePanel = buildHome()
        mousePanel = buildMouse()
        arrowPanel = buildArrows()
        volumePanel = buildVolume()
        keyboardPanel = buildKeyboard()
        shortcutsPanel = buildShortcuts()
        listOf(connectPanel, homePanel, mousePanel, arrowPanel, volumePanel, keyboardPanel, shortcutsPanel).forEach {
            host.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        show(connectPanel)
        handler.postDelayed(watchdog, 500)
    }

    // ---------- automatic connect / reconnect ----------
    private val watchdog = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 4000)
        }
    }

    private fun savedPin() = getSharedPreferences("remotepad", Context.MODE_PRIVATE).getString("pin", "") ?: ""
    private fun savedIp() = getSharedPreferences("remotepad", Context.MODE_PRIVATE).getString("ip", "") ?: ""

    private fun onConnected(ip: String) {
        getSharedPreferences("remotepad", Context.MODE_PRIVATE).edit().putString("ip", ip).apply()
        ipField.setText(ip)
        connected = true
        status.text = "Connected to $ip"
        if (current === connectPanel) show(homePanel)
    }

    /** Runs every few seconds: checks the link, and if lost, finds the PC again by itself. */
    private fun tick() {
        if (busy) return
        val pinCode = savedPin()
        if (pinCode.length != 4) return          // first time: user must type the PIN once
        busy = true
        if (connected) {
            client.ping { ok ->
                if (!ok) {
                    connected = false
                    status.text = "Connection lost - reconnecting..."
                }
                busy = false
            }
            return
        }
        status.text = "Looking for your PC..."
        val ip = savedIp()
        fun viaDiscovery() {
            client.discover { found ->
                if (found == null) {
                    busy = false
                } else {
                    client.connect(found, 5005, pinCode) { ok ->
                        if (ok) onConnected(found)
                        busy = false
                    }
                }
            }
        }
        if (ip.isNotEmpty()) {
            client.connect(ip, 5005, pinCode) { ok ->
                if (ok) { onConnected(ip); busy = false } else viaDiscovery()
            }
        } else {
            viaDiscovery()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)                   // reconnect right away when app comes back
    }

    private fun buildConnect(): View {
        val prefs = getSharedPreferences("remotepad", Context.MODE_PRIVATE)
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = screenBg()
        c.setPadding(dp(20), dp(20), dp(20), dp(20))

        c.addView(label("RemotePad", 28f, true))
        val info = label(
            "1. PC server starts by itself (install_autostart.bat, one time).\n" +
                "2. Put phone and PC on the same WiFi, or connect the PC to this phone's hotspot.\n" +
                "3. First time only: type the PIN from pin.txt. IP is optional - the app finds the PC automatically.",
            14f
        )
        info.setPadding(0, dp(8), 0, dp(16))
        c.addView(info)

        val ip = EditText(this)
        ip.hint = "PC IP  (optional - auto-detected)"
        ip.setHintTextColor(0xFF6B7788.toInt())
        ip.setTextColor(Color.WHITE)
        ip.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ip.setText(prefs.getString("ip", ""))
        ipField = ip
        c.addView(ip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val pin = EditText(this)
        pin.hint = "PIN (4 digits)"
        pin.setHintTextColor(0xFF6B7788.toInt())
        pin.setTextColor(Color.WHITE)
        pin.inputType = InputType.TYPE_CLASS_NUMBER
        pin.filters = arrayOf(InputFilter.LengthFilter(4))
        pin.setText(prefs.getString("pin", ""))
        pinField = pin
        c.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val go = btn("Connect", ACCENT, 20f) {}
        go.setOnClickListener {
            val ipText = ip.text.toString().trim().replace(',', '.')
            val pinText = pin.text.toString().trim()
            if (pinText.length != 4) {
                Toast.makeText(this, "Enter the 4-digit PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            go.isEnabled = false
            go.text = "Connecting..."
            fun finish(ok: Boolean, ipUsed: String) {
                go.isEnabled = true
                go.text = "Connect"
                if (ok) {
                    prefs.edit().putString("pin", pinText).apply()
                    onConnected(ipUsed)
                    show(homePanel)
                } else {
                    Toast.makeText(
                        this,
                        "No reply. Check PIN, same WiFi, PC server running, and Windows Firewall.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            if (ipText.isNotEmpty()) {
                client.connect(ipText, 5005, pinText) { ok -> finish(ok, ipText) }
            } else {
                client.discover { found ->
                    if (found == null) finish(false, "")
                    else client.connect(found, 5005, pinText) { ok -> finish(ok, found) }
                }
            }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
        lp.topMargin = dp(16)
        c.addView(go, lp)
        return c
    }

    private fun buildHome(): View {
        val content = column(
            row(
                btn("🖱\nMouse /\nTouchpad", size = 20f) { show(mousePanel) },
                btn("⬆\nArrow Keys", size = 20f) { show(arrowPanel) }
            ),
            row(
                btn("🔊\nVolume &\nMedia", size = 20f) { show(volumePanel) },
                btn("⌨\nKeyboard", size = 20f) { show(keyboardPanel) }
            ),
            row(btn("⚡\nShortcuts", size = 20f) { show(shortcutsPanel) }),
            row(btn("Change PC / Reconnect", size = 14f) { show(connectPanel) }, h = 0.4f)
        )
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = screenBg()
        p.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return p
    }

    private fun buildMouse(): View {
        val pad = TouchpadView(this, client) { sens }
        val volStrip = VolumeStrip(this, client)
        val scrollStrip = ScrollStrip(this, client)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        top.addView(volStrip, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        top.addView(pad, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        top.addView(scrollStrip, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })

        val arrows = row(
            holdKey("◀", VK_LEFT, 22f),
            holdKey("▶", VK_RIGHT, 22f),
            fixedDp = 56
        )

        var dragging = false
        val dragBtn = btn("Drag: OFF", size = 15f) {}
        dragBtn.setOnClickListener {
            dragging = !dragging
            dragBtn.text = if (dragging) "Drag: ON" else "Drag: OFF"
            client.send(if (dragging) "BTN|L|down" else "BTN|L|up")
        }

        val clicks = row(
            btn("Left", size = 16f) { client.send("BTN|L|click") },
            btn("Double", size = 16f) { client.send("BTN|L|double") },
            btn("Middle", size = 16f) { client.send("BTN|M|click") },
            btn("Right", size = 16f) { client.send("BTN|R|click") },
            fixedDp = 56
        )
        val extra = row(
            dragBtn,
            btn("Hold L-Click Off", size = 13f) {
                dragging = false
                dragBtn.text = "Drag: OFF"
                client.send("BTN|L|up")
            },
            fixedDp = 48
        )

        val speedLabel = label("Speed", 14f)
        val seek = SeekBar(this)
        seek.max = 40
        seek.progress = 15
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                sens = 0.5f + p / 10f
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val speedRow = LinearLayout(this)
        speedRow.orientation = LinearLayout.HORIZONTAL
        speedRow.gravity = Gravity.CENTER_VERTICAL
        speedRow.setPadding(dp(8), 0, dp(8), 0)
        speedRow.addView(speedLabel, LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT))
        speedRow.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        speedRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))

        // Top-left: press the browser's Back / Forward arrows (mouse side buttons X1 / X2)
        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.gravity = Gravity.CENTER_VERTICAL
        tabRow.addView(btn("◀ Page", size = 14f) { client.send("BTN|X1|click") },
            LinearLayout.LayoutParams(dp(90), dp(44)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        tabRow.addView(btn("Page ▶", size = 14f) { client.send("BTN|X2|click") },
            LinearLayout.LayoutParams(dp(90), dp(44)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        tabRow.addView(spacer(), LinearLayout.LayoutParams(0, dp(44), 1f))
        tabRow.addView(recStopButton,
            LinearLayout.LayoutParams(dp(110), dp(44)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        tabRow.addView(btn("⚡ Shortcuts", size = 13f) { show(shortcutsPanel) },
            LinearLayout.LayoutParams(dp(110), dp(44)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        tabRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(4), 0, dp(4), dp(4))
        content.addView(tabRow)
        content.addView(top)
        content.addView(arrows)
        content.addView(speedRow)
        content.addView(extra)
        content.addView(clicks)

        val kbButton = btn("⌨ Keys", size = 15f) { show(keyboardPanel) }
        return panel("Mouse / Touchpad", content, kbButton)
    }

    private fun buildArrows(): View {
        val content = column(
            row(spacer(), holdKey("▲", VK_UP, 30f), spacer(), h = 2f),
            row(holdKey("◀", VK_LEFT, 30f), keyBtn("Enter", VK_ENTER, 20f), holdKey("▶", VK_RIGHT, 30f), h = 2f),
            row(spacer(), holdKey("▼", VK_DOWN, 30f), spacer(), h = 2f),
            row(keyBtn("Esc", VK_ESC, 15f), holdKey("Backspace", VK_BACK, 13f), keyBtn("Tab", VK_TAB, 15f), keyBtn("Space", VK_SPACE, 15f)),
            row(holdKey("Del", VK_DEL, 15f), keyBtn("Home", VK_HOME, 15f), keyBtn("End", VK_END, 15f), keyBtn("Win", VK_LWIN, 15f)),
            row(holdKey("PgUp", VK_PGUP, 15f), holdKey("PgDn", VK_PGDN, 15f), keyBtn("F5", VK_F5, 15f), comboBtn("Alt+F4", VK_ALT, VK_F4))
        )
        return panel("Arrow Keys", content)
    }

    private fun buildVolume(): View {
        val content = column(
            row(holdKey("🔊  Volume +", VK_VOL_UP, 22f), h = 1.5f),
            row(holdKey("🔉  Volume −", VK_VOL_DOWN, 22f), h = 1.5f),
            row(keyBtn("🔇  Mute / Unmute", VK_VOL_MUTE, 20f)),
            row(keyBtn("⏮  Prev", VK_PREV, 16f), keyBtn("⏯  Play/Pause", VK_PLAY, 16f), keyBtn("Next  ⏭", VK_NEXT, 16f)),
            row(keyBtn("⏹  Stop", VK_STOP, 16f), h = 0.7f),
            row(label("Tip: your phone's own volume buttons also change PC volume on this screen.", 12f), h = 0.6f)
        )
        return panel("Volume & Media", content)
    }

    /** Alt+F4, wait 2 seconds, then Enter (confirms the Windows shut-down dialog). */
    private fun shutDownSequence() {
        client.send("COMBO|$VK_ALT+$VK_F4")
        handler.postDelayed({ client.send("KEY|$VK_ENTER|tap") }, 2000)
    }

    private fun shortcutBtn(label: String, onClick: () -> Unit): Button =
        btn(label, size = 15f, onClick = onClick)

    /** Shortcut buttons. rowDp = 0 makes rows share the space (Shortcuts screen); >0 fixes row height. */
    private fun shortcutRows(rowDp: Int): Array<View> = arrayOf(
        row(
            shortcutBtn("Shut down\n(Alt+F4, wait 2s, Enter)") { shutDownSequence() },
            shortcutBtn("Close app") { client.send("COMBO|$VK_ALT+$VK_F4") },
            shortcutBtn("Copy history") { client.send("COMBO|$VK_LWIN+$VK_V") },
            h = 1f, fixedDp = rowDp
        ),
        row(
            shortcutBtn("Next tab") { client.send("COMBO|$VK_CTRL+$VK_TAB") },
            shortcutBtn("Prev tab") { client.send("COMBO|$VK_CTRL+$VK_SHIFT+$VK_TAB") },
            shortcutBtn("New tab") { client.send("COMBO|$VK_CTRL+$VK_T") },
            shortcutBtn("Close tab") { client.send("COMBO|$VK_CTRL+$VK_W") },
            h = 1f, fixedDp = rowDp
        ),
        row(
            shortcutBtn("Find") { client.send("COMBO|$VK_CTRL+$VK_F") },
            h = 1f, fixedDp = rowDp
        )
    )

    // ---------- Memory keys: 4 buttons (M1-M4) you set to a mouse click or a shortcut ----------
    // Short tap = run the action. Long press = choose a different action (saved on the phone).
    private val memActions: List<Pair<String, () -> Unit>> by lazy {
        listOf(
            "Left click" to { client.send("BTN|L|click") },
            "Right click" to { client.send("BTN|R|click") },
            "Middle click" to { client.send("BTN|M|click") },
            "Double click" to { client.send("BTN|L|double") },
            "Copy" to { client.send("COMBO|$VK_CTRL+0x43") },
            "Paste" to { client.send("COMBO|$VK_CTRL+0x56") },
            "Select all" to { client.send("COMBO|$VK_CTRL+0x41") },
            "Undo" to { client.send("COMBO|$VK_CTRL+0x5A") },
            "Save" to { client.send("COMBO|$VK_CTRL+0x53") },
            "Esc" to { client.send("KEY|$VK_ESC|tap") },
            "Enter" to { client.send("KEY|$VK_ENTER|tap") },
            "Show desktop" to { client.send("COMBO|$VK_LWIN+0x44") }
        )
    }
    private val memDefaults = intArrayOf(0, 1, 4, 5)   // M1 Left click, M2 Right click, M3 Copy, M4 Paste
    private val memButtons = arrayOfNulls<Button>(4)
    private val memPrefs by lazy { getSharedPreferences("memory_keys", MODE_PRIVATE) }

    private fun memIndex(slot: Int): Int = memPrefs.getInt("slot$slot", memDefaults[slot])

    private fun refreshMemButtons() {
        for (i in 0 until 4) {
            val idx = memIndex(i)
            val label = if (idx == -1) "Recorded clicks" else memActions[idx].first
            val name = memPrefs.getString("name$i", null)
            val top = if (name.isNullOrBlank()) "M${i + 1}" else name
            memButtons[i]?.text = "$top\n$label"
        }
    }

    private fun pickMemAction(slot: Int) {
        val names = (memActions.map { it.first } + listOf("⏺ Record clicks", "✏ Name this button")).toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("M${slot + 1}")
            .setItems(names) { _, which ->
                when (which) {
                    memActions.size -> startRecording(slot)
                    memActions.size + 1 -> askName(slot)
                    else -> {
                        memPrefs.edit().putInt("slot$slot", which).apply()
                        refreshMemButtons()
                    }
                }
            }
            .show()
    }

    private fun askName(slot: Int) {
        val input = android.widget.EditText(this)
        input.hint = "Button name"
        input.setText(memPrefs.getString("name$slot", "") ?: "")
        android.app.AlertDialog.Builder(this)
            .setTitle("Name for M${slot + 1}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                memPrefs.edit().putString("name$slot", input.text.toString().trim()).apply()
                refreshMemButtons()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runMem(slot: Int) {
        val idx = memIndex(slot)
        if (idx == -1) replay(slot) else memActions[idx].second()
    }

    private var recSlot = -1
    private var recStart = 0L
    private val recSteps = mutableListOf<String>()   // each: "time\tbutton\taction\tx\ty"

    private val recStopButton: Button by lazy {
        btn("⏹ Stop", color = 0xFFB22222.toInt(), size = 14f) { stopRecording() }.apply { visibility = View.GONE }
    }

    /** Records only the clicks: where the cursor was on the PC at each click, and when. */
    private fun startRecording(slot: Int) {
        if (recSlot != -1) return
        recSlot = slot
        recSteps.clear()
        recStart = android.os.SystemClock.uptimeMillis()
        client.recorder = { cmd ->
            val p = cmd.split("|")
            if (p.size == 3 && p[0] == "BTN" && (p[1] == "L" || p[1] == "R" || p[1] == "M") &&
                (p[2] == "click" || p[2] == "double")) {
                val t = android.os.SystemClock.uptimeMillis() - recStart
                client.queryPos { pos ->
                    if (pos != null) recSteps.add("$t\t${p[1]}\t${p[2]}\t${pos.first}\t${pos.second}")
                }
            }
        }
        show(mousePanel)
        recStopButton.visibility = View.VISIBLE
        android.widget.Toast.makeText(this, "Recording M${slot + 1}: click where you want. Tap Stop when done.",
            android.widget.Toast.LENGTH_LONG).show()
    }

    private fun stopRecording() {
        val slot = recSlot
        if (slot == -1) return
        client.recorder = null
        recSlot = -1
        recStopButton.visibility = View.GONE
        // Wait briefly so the last click's position reply is included
        handler.postDelayed({
            if (recSteps.isEmpty()) {
                android.widget.Toast.makeText(this, "No clicks recorded", android.widget.Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            val text = recSteps.joinToString("\n")
            recSteps.clear()
            memPrefs.edit().putString("recpos$slot", text).putInt("slot$slot", -1).apply()
            refreshMemButtons()
            android.widget.Toast.makeText(this, "Saved M${slot + 1}", android.widget.Toast.LENGTH_SHORT).show()
        }, 500)
    }

    /** Replays each recorded click: move the cursor to the saved spot, click, at the same time as recorded. */
    private fun replay(slot: Int) {
        if (recSlot != -1) return
        val data = memPrefs.getString("recpos$slot", "") ?: ""
        for (line in data.lines()) {
            val f = line.split("\t")
            if (f.size < 5) continue
            val t = f[0].toLongOrNull() ?: continue
            val button = f[1]
            val action = f[2]
            val x = f[3]
            val y = f[4]
            handler.postDelayed({
                client.send("ABS|$x|$y")
                client.send("BTN|$button|$action")
            }, t)
        }
    }

    private fun memoryRow(): View {
        val views = (0 until 4).map { i ->
            val b = btn("M${i + 1}", size = 14f) { runMem(i) }
            b.setOnLongClickListener { pickMemAction(i); true }
            memButtons[i] = b
            b as View
        }.toTypedArray()
        refreshMemButtons()
        return row(*views, fixedDp = 64)
    }

    private fun buildShortcuts(): View = panel("Shortcuts", column(*shortcutRows(0), memoryRow()))

    private fun buildKeyboard(): View {
        typeField = EditText(this)
        typeField.hint = "Tap here and type — goes to PC live"
        typeField.setHintTextColor(0xFF6B7788.toInt())
        typeField.setTextColor(Color.WHITE)
        typeField.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        typeField.imeOptions = EditorInfo.IME_ACTION_SEND
        typeField.setOnEditorActionListener { _, _, _ ->
            client.send("KEY|$VK_ENTER|tap")
            suppressText = true
            typeField.setText("")
            suppressText = false
            true
        }
        typeField.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_DEL && ev.action == KeyEvent.ACTION_DOWN &&
                typeField.text.isEmpty()
            ) {
                client.send("KEY|$VK_BACK|tap")
                true
            } else false
        }
        typeField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressText || s == null) return
                repeat(before) { client.send("KEY|$VK_BACK|tap") }
                if (count > 0) client.send("TXT|" + s.subSequence(start, start + count))
            }
        })

        val fieldLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            setMargins(dp(8), dp(4), dp(8), dp(4))
        }

        val keys = row(
            keyBtn("Esc", VK_ESC, 15f),
            comboBtn("Copy", VK_CTRL, 0x43),
            comboBtn("Paste", VK_CTRL, 0x56),
            keyBtn("F2", 0x71, 15f),
            keyBtn("F5", 0x74, 15f),
            fixedDp = 56
        )
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(column(*shortcutRows(52)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(typeField, fieldLp)
        content.addView(keys)
        // Touchpad under the keys, so you can move the cursor while typing
        val pad = TouchpadView(this, client) { sens }
        content.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        // Top-right button: jump straight to the Mouse screen
        val toMouse = btn("🖱 Mouse", size = 15f) { show(mousePanel) }
        return panel("Keyboard", content, toMouse)
    }

    // ---------- phone volume keys control PC volume on the Volume screen ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (current === volumePanel) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                client.send("KEY|$VK_VOL_UP|tap")
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                client.send("KEY|$VK_VOL_DOWN|tap")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current != null && current !== homePanel && current !== connectPanel) {
            show(homePanel)
        } else {
            super.onBackPressed()
        }
    }
}

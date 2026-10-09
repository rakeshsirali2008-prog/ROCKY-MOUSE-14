package com.rocky.remotepad

import android.os.Handler
import android.os.Looper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/** Sends small text commands to the PC server over UDP (fast, no lag). */
class UdpClient {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var addr: InetAddress? = null
    @Volatile private var port = 5005
    @Volatile private var pin = ""

    /** While recording a path, every command sent is also passed here (with the app's own timing). */
    @Volatile var recorder: ((String) -> Unit)? = null

    /** Tests the connection: sends HELLO and waits for "OK". */
    fun connect(ip: String, p: Int, pinCode: String, done: (Boolean) -> Unit) {
        exec.execute {
            var ok = false
            try {
                socket?.close()
                val s = DatagramSocket()
                s.soTimeout = 1500
                addr = InetAddress.getByName(ip)
                port = p
                pin = pinCode
                socket = s
                val out = "$pin|HELLO".toByteArray()
                s.send(DatagramPacket(out, out.size, addr, port))
                val buf = ByteArray(16)
                val reply = DatagramPacket(buf, buf.size)
                s.receive(reply)
                ok = String(reply.data, 0, reply.length) == "OK"
            } catch (e: Exception) {
                ok = false
            }
            main.post { done(ok) }
        }
    }

    fun send(cmd: String) {
        recorder?.invoke(cmd)
        exec.execute {
            try {
                val s = socket ?: return@execute
                val a = addr ?: return@execute
                val bytes = "$pin|$cmd".toByteArray()
                s.send(DatagramPacket(bytes, bytes.size, a, port))
            } catch (e: Exception) {
                // ignore – UDP is fire and forget
            }
        }
    }

    /** Asks the PC where the mouse cursor is. done(Pair(x, y)) on the main thread, or done(null) on failure. */
    fun queryPos(done: (Pair<Int, Int>?) -> Unit) {
        exec.execute {
            var result: Pair<Int, Int>? = null
            try {
                val s = socket
                val a = addr
                if (s != null && a != null) {
                    val out = "$pin|POS".toByteArray()
                    s.soTimeout = 1000
                    s.send(DatagramPacket(out, out.size, a, port))
                    val buf = ByteArray(64)
                    val reply = DatagramPacket(buf, buf.size)
                    s.receive(reply)
                    val parts = String(reply.data, 0, reply.length).split(",")
                    if (parts.size == 2) result = Pair(parts[0].trim().toInt(), parts[1].trim().toInt())
                }
            } catch (e: Exception) {
                result = null
            }
            main.post { done(result) }
        }
    }

    /** Quick check that the PC still answers (used to detect drops / PC restarts). */
    fun ping(done: (Boolean) -> Unit) {
        exec.execute {
            var ok = false
            try {
                val s = socket
                val a = addr
                if (s != null && a != null) {
                    val out = "$pin|HELLO".toByteArray()
                    s.soTimeout = 1200
                    s.send(DatagramPacket(out, out.size, a, port))
                    val buf = ByteArray(16)
                    val reply = DatagramPacket(buf, buf.size)
                    s.receive(reply)
                    ok = String(reply.data, 0, reply.length) == "OK"
                }
            } catch (e: Exception) {
                ok = false
            }
            main.post { done(ok) }
        }
    }

    /** Broadcasts "DISCOVER" on the local network; returns the IP of the first PC that answers (or null). */
    fun discover(done: (String?) -> Unit) {
        exec.execute {
            var found: String? = null
            try {
                val s = DatagramSocket()
                s.broadcast = true
                s.soTimeout = 1200
                val msg = "DISCOVER".toByteArray()
                val targets = HashSet<String>()
                targets.add("255.255.255.255")
                try {
                    for (ni in NetworkInterface.getNetworkInterfaces()) {
                        if (!ni.isUp || ni.isLoopback) continue
                        for (ia in ni.interfaceAddresses) {
                            ia.broadcast?.hostAddress?.let { targets.add(it) }
                        }
                    }
                } catch (e: Exception) {
                }
                for (t in targets) {
                    try {
                        s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(t), 5005))
                    } catch (e: Exception) {
                    }
                }
                val buf = ByteArray(32)
                val reply = DatagramPacket(buf, buf.size)
                s.receive(reply)
                if (String(reply.data, 0, reply.length) == "REMOTEPAD") {
                    found = reply.address.hostAddress
                }
                s.close()
            } catch (e: Exception) {
                found = null
            }
            main.post { done(found) }
        }
    }
}

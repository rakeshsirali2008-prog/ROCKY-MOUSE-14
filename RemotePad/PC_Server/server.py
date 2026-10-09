"""
RemotePad PC Server  (Windows 7 or newer)
-----------------------------------------
Needs only Python 3.8 (last version that runs on Windows 7) or any newer Python.
No pip install needed.  Run:  python server.py
"""
import ctypes
import os
import random
import socket
import sys
import time
from ctypes import wintypes

PORT = 5005
PIN_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pin.txt")


def load_pin():
    """PIN is created once and saved, so it stays the same after every restart.
    To change it: edit pin.txt (4 digits) or delete it for a new random one."""
    try:
        with open(PIN_FILE) as f:
            p = f.read().strip()
            if len(p) == 4 and p.isdigit():
                return p
    except Exception:
        pass
    p = "%04d" % random.randint(0, 9999)
    try:
        with open(PIN_FILE, "w") as f:
            f.write(p)
    except Exception:
        pass
    return p

user32 = ctypes.windll.user32
try:
    user32.SetProcessDPIAware()
except Exception:
    pass

# ---------- Win32 structures ----------
KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
INPUT_KEYBOARD = 1

MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x01000


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.c_size_t)]


class MOUSEINPUT(ctypes.Structure):  # only here so the union has the right size
    _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG),
                ("mouseData", wintypes.DWORD), ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.c_size_t)]


class _U(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _U)]


class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]


EXTENDED = {0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2D, 0x2E, 0x5B, 0x5C}

# ---------- actions ----------
def move_mouse(dx, dy):
    p = POINT()
    user32.GetCursorPos(ctypes.byref(p))
    user32.SetCursorPos(p.x + dx, p.y + dy)


def mouse_button(which, action):
    table = {
        "L": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
        "R": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
        "M": (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
    }
    down, up = table.get(which, table["L"])
    if action in ("down", "click", "double"):
        user32.mouse_event(down, 0, 0, 0, 0)
    if action in ("up", "click", "double"):
        user32.mouse_event(up, 0, 0, 0, 0)
    if action == "double":
        user32.mouse_event(down, 0, 0, 0, 0)
        user32.mouse_event(up, 0, 0, 0, 0)


MOUSEEVENTF_XDOWN = 0x0080
MOUSEEVENTF_XUP = 0x0100
XBUTTON1 = 0x0001  # mouse side button 1 = browser/Explorer BACK arrow
XBUTTON2 = 0x0002  # mouse side button 2 = browser/Explorer FORWARD arrow


def side_button(which):
    data = XBUTTON1 if which == "X1" else XBUTTON2
    user32.mouse_event(MOUSEEVENTF_XDOWN, 0, 0, data, 0)
    user32.mouse_event(MOUSEEVENTF_XUP, 0, 0, data, 0)


def scroll(dy, dx=0):
    if dy:
        user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, ctypes.c_ulong(dy & 0xFFFFFFFF).value, 0)
    if dx:
        user32.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, ctypes.c_ulong(dx & 0xFFFFFFFF).value, 0)


def key(vk, action="tap"):
    flags = KEYEVENTF_EXTENDEDKEY if vk in EXTENDED else 0
    if action in ("down", "tap"):
        user32.keybd_event(vk, 0, flags, 0)
    if action in ("up", "tap"):
        user32.keybd_event(vk, 0, flags | KEYEVENTF_KEYUP, 0)


def type_text(text):
    for ch in text:
        if ch == "\n":
            key(0x0D)
            continue
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            i = INPUT(type=INPUT_KEYBOARD)
            i.ki = KEYBDINPUT(0, ord(ch), flags, 0, 0)
            user32.SendInput(1, ctypes.byref(i), ctypes.sizeof(INPUT))


def combo(vks):
    """Hold all keys down then release in reverse (Ctrl+C etc.)."""
    for v in vks:
        key(v, "down")
    for v in reversed(vks):
        key(v, "up")


# ---------- networking ----------
def local_ips():
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))
        ips.add(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ips.add(info[4][0])
    except Exception:
        pass
    return sorted(i for i in ips if not i.startswith("127."))


def handle(parts, sock, addr):
    cmd = parts[0]
    if cmd == "HELLO":
        sock.sendto(b"OK", addr)
    elif cmd == "POS":
        p = POINT()
        user32.GetCursorPos(ctypes.byref(p))
        sock.sendto(f"{p.x},{p.y}".encode(), addr)
    elif cmd == "ABS":
        user32.SetCursorPos(int(float(parts[1])), int(float(parts[2])))
    elif cmd == "MV":
        move_mouse(int(float(parts[1])), int(float(parts[2])))
    elif cmd == "BTN":
        if parts[1] in ("X1", "X2"):
            side_button(parts[1])
        else:
            mouse_button(parts[1], parts[2])
    elif cmd == "SCR":
        scroll(int(float(parts[1])), int(float(parts[2])) if len(parts) > 2 else 0)
    elif cmd == "KEY":
        key(int(parts[1]), parts[2] if len(parts) > 2 else "tap")
    elif cmd == "COMBO":
        combo([int(x) for x in parts[1].split("+")])
    elif cmd == "TXT":
        type_text("|".join(parts[1:]))


def main():
    pin = load_pin()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.bind(("0.0.0.0", PORT))
    except OSError:
        print("RemotePad server is already running.")
        time.sleep(3)
        return
    print("=" * 50)
    print(" RemotePad PC Server is running")
    print(" Enter this in the phone app:")
    for ip in local_ips():
        print("    PC IP :", ip)
    print("    PIN   :", pin, "(fixed - same after restart)")
    print("    Port  :", PORT)
    print(" (If the phone cannot connect, allow Python in")
    print("  Windows Firewall when asked.)  Ctrl+C to stop.")
    print("=" * 50)
    while True:
        try:
            data, addr = sock.recvfrom(4096)
            msg = data.decode("utf-8", "ignore")
            if msg.strip() == "DISCOVER":
                sock.sendto(b"REMOTEPAD", addr)
                continue
            parts = msg.split("|")
            if parts[0] != pin:
                continue
            handle(parts[1:], sock, addr)
        except KeyboardInterrupt:
            sys.exit(0)
        except Exception as e:
            print("error:", e)


if __name__ == "__main__":
    main()

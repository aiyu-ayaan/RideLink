#!/usr/bin/python3
"""Offline emulation of the frame XOR, no Bluetooth needed.

Plays the phone and the fake display (tools/fake_cluster.py) for every --model and checks:
- the wire bytes match the vectors pinned in ClusterProtocolTest.kt (u716StatusFrameIsXoredWithEb,
  u577PremiumNavFrameIsXoredWithEa, detectIgnoresXor);
- a display with the right key reads every frame the phone sends, both ways;
- a display that expects XOR gets garbage from a plain sender (why U716 / U577 premium ignored RideLink).
Run: /usr/bin/python3 tools/emulate_test.py   (exit code 0 = pass)
"""
import os
import sys
from unittest import mock

# fake_cluster imports dbus and gi at the top; stub them where they are not installed (CI, venvs).
try:
    import dbus.service  # noqa: F401
    from gi.repository import GLib  # noqa: F401
except ImportError:
    for name in ("dbus", "dbus.exceptions", "dbus.service", "dbus.mainloop", "dbus.mainloop.glib", "gi", "gi.repository"):
        sys.modules[name] = mock.MagicMock()
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fake_cluster import XOR_KEYS, decode, xor  # noqa: E402

H = bytes.fromhex
# Plain frames from ClusterProtocolTest.kt.
STATUS_NTORQ = H("5b4a35ff1e00090507000004040b1a000000ffff")
NAV_PREMIUM = H("5a4e012c000a01117010010000000000000000ff")
RIDER = H("5b52" + "417961616e" + "00" * 12 + "ff")
WIFI = H("5a2901" + "00" * 16 + "ff")
TELEMETRY = H("5a10003e07f5050000000969" + "00" * 7 + "ff")
FRAMES = [STATUS_NTORQ, NAV_PREMIUM, RIDER]
# Wire vectors pinned in ClusterProtocolTest.kt.
PINNED = [
    (STATUS_NTORQ, 0xEB, H("5b4ade14f5ebe2eeecebebefefe0f1ebebeb14ff")),
    (NAV_PREMIUM, 0xEA, H("5a4eebc6eae0ebfb9afaebeaeaeaeaeaeaeaeaff")),
    (WIFI, 0xEA, H("5a29eb" + "ea" * 16 + "ff")),
]

failures = []


def check(ok, what):
    print(("PASS " if ok else "FAIL ") + what)
    if not ok:
        failures.append(what)


for plain, key, wire in PINNED:
    tag = plain[:2].hex(" ")
    check(xor(plain, key) == wire, f"pinned vector {tag} ^ {key:02x}")
    check(xor(wire, key) == plain, f"pinned vector {tag} decodes back")
    check(wire[:2] == plain[:2] and wire[-1] == 0xFF, f"start, DID and FF stay plain ({tag})")

short = H("5a2901")
check(xor(short, 0xEA) == short, "frames under 4 bytes pass through")

for model, (tx, rx) in sorted(XOR_KEYS.items()):
    for f in FRAMES:
        kind = decode(f).split()[0]
        # Phone -> display: the app XORs with tx, the display undoes it with the same key.
        got = xor(xor(f, tx), tx)
        check(got == f and decode(got) == decode(f), f"{model}: display reads {kind} frame")
        if tx is not None:
            check(xor(f, tx) != f, f"{model}: a plain sender would garble {kind} on this display")
    # Display -> phone: the display XORs with rx, the app decodes with the same key before parsing.
    check(xor(xor(TELEMETRY, rx), rx) == TELEMETRY, f"{model}: app reads display frames")

print(f"\n{len(failures)} failed" if failures else "\nall passed")
sys.exit(1 if failures else 0)

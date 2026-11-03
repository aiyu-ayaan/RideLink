#!/usr/bin/python3
"""End-to-end check of tools/fake_cluster.py from a second local Bluetooth adapter.

Plays the phone: discovers the fake display by service UUID, connects, subscribes to
notifications and writes the same frames the Kotlin unit tests pin. Run the fake display
on one adapter and this on the other:
    /usr/bin/python3 tools/fake_cluster.py --adapter hci0
    /usr/bin/python3 tools/central_test.py --adapter hci1
"""
import argparse
import sys

import dbus
import dbus.mainloop.glib
from gi.repository import GLib

SERVICE = "5456534d-5647-5341-5342-454e544f5251"
WRITE = "00005352-0000-1000-8000-00805f9b34fb"
NOTIFY = "00005354-0000-1000-8000-00805f9b34fb"

# Byte-exact frames from core/src/test/.../ClusterProtocolTest.kt.
FRAMES = [
    "5b 52 41 79 61 61 6e 00 00 00 00 00 00 00 00 00 00 00 00 ff",  # rider name "Ayaan"
    "5b 4a 35 78 46 00 09 05 07 01 00 04 04 0b 1a 00 00 00 00 ff",  # status, 2025+ family
    "5b 4f 54 75 72 6e 20 72 69 67 68 74 00 00 00 00 00 00 00 ff",  # nav text "Turn right"
    "5a 4e 01 2c 00 0a 01 11 70 0a 01 00 00 00 00 00 00 00 00 ff",  # nav control 300 m
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", default="hci1")
    ap.add_argument("--timeout", type=int, default=30)
    args = ap.parse_args()

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    om = dbus.Interface(bus.get_object("org.bluez", "/"), "org.freedesktop.DBus.ObjectManager")
    adapter_path = f"/org/bluez/{args.adapter}"
    adapter = dbus.Interface(bus.get_object("org.bluez", adapter_path), "org.bluez.Adapter1")
    loop = GLib.MainLoop()
    result = {"ok": False, "notified": False}

    def fail(msg):
        print("FAIL:", msg)
        loop.quit()

    def find(uuid, parent):
        for path, ifaces in om.GetManagedObjects().items():
            c = ifaces.get("org.bluez.GattCharacteristic1")
            if c and str(c["UUID"]).lower() == uuid and path.startswith(parent):
                return path
        return None

    def on_resolved(dev_path):
        write_path = find(WRITE, dev_path)
        notify_path = find(NOTIFY, dev_path)
        if not write_path or not notify_path:
            return fail("characteristics not found")
        notify = bus.get_object("org.bluez", notify_path)

        def on_change(iface, changed, _inv):
            if "Value" in changed:
                print("notified:", bytes(changed["Value"]).hex(" "))
                result["notified"] = True

        bus.add_signal_receiver(on_change, "PropertiesChanged", "org.freedesktop.DBus.Properties", path=notify_path)
        dbus.Interface(notify, "org.bluez.GattCharacteristic1").StartNotify()
        chr_ = dbus.Interface(bus.get_object("org.bluez", write_path), "org.bluez.GattCharacteristic1")
        for f in FRAMES:
            chr_.WriteValue(dbus.Array(bytes.fromhex(f.replace(" ", "")), signature="y"), {"type": "request"})
            print("wrote:", f)
        result["ok"] = True
        GLib.timeout_add(1500, loop.quit)

    def try_connect(dev_path):
        dev = bus.get_object("org.bluez", dev_path)
        props = dbus.Interface(dev, "org.freedesktop.DBus.Properties")
        adapter.StopDiscovery()
        print("connecting", props.Get("org.bluez.Device1", "Address"))
        dbus.Interface(dev, "org.bluez.Device1").Connect(
            reply_handler=lambda: None, error_handler=lambda e: fail(f"connect: {e}"), timeout=20)

        def poll():
            if props.Get("org.bluez.Device1", "ServicesResolved"):
                on_resolved(dev_path)
                return False
            return True
        GLib.timeout_add(300, poll)

    def match(path, ifaces):
        d = ifaces.get("org.bluez.Device1")
        if d and path.startswith(adapter_path) and SERVICE in [str(u).lower() for u in d.get("UUIDs", [])]:
            print("found", d.get("Name"), d.get("Address"))
            return True
        return False

    def on_added(path, ifaces):
        if match(path, ifaces):
            try_connect(path)

    bus.add_signal_receiver(on_added, "InterfacesAdded", "org.freedesktop.DBus.ObjectManager")
    # Drop stale cache so we see a fresh advertisement.
    for path, ifaces in om.GetManagedObjects().items():
        if path.startswith(adapter_path + "/dev_") and match(path, ifaces):
            adapter.RemoveDevice(path)
    adapter.SetDiscoveryFilter({"Transport": "le", "UUIDs": dbus.Array([SERVICE], signature="s")})
    adapter.StartDiscovery()
    GLib.timeout_add_seconds(args.timeout, lambda: fail("timeout") or False)
    loop.run()
    print("RESULT", "PASS" if result["ok"] else "FAIL")
    sys.exit(0 if result["ok"] else 1)


if __name__ == "__main__":
    main()

#!/usr/bin/python3
"""Fake TVS Ntorq cluster: a BLE peripheral that looks like the scooter display.

It advertises the TVS SmartXonnect service, accepts every write from the phone app,
decodes the known frame types (see PROTOCOL.md) and logs them. It sends nothing
back unless you type a hex frame on stdin, which is then pushed as a notification.

Run with the system Python (needs python3-dbus and python3-gi):
    /usr/bin/python3 tools/fake_cluster.py [--adapter hci0] [--name TVSBT01]
"""
import argparse
import datetime
import os
import sys
import threading

import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

BLUEZ = "org.bluez"
DBUS_OM = "org.freedesktop.DBus.ObjectManager"
DBUS_PROP = "org.freedesktop.DBus.Properties"
GATT_MANAGER = "org.bluez.GattManager1"
ADV_MANAGER = "org.bluez.LEAdvertisingManager1"
GATT_SERVICE = "org.bluez.GattService1"
GATT_CHRC = "org.bluez.GattCharacteristic1"
ADV_IFACE = "org.bluez.LEAdvertisement1"

SERVICE_UUID = "5456534d-5647-5341-5342-454e544f5251"
WRITE_UUID = "00005352-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID = "00005354-0000-1000-8000-00805f9b34fb"

LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "captures")

SCREENS = {0x00: "default", 0x78: "weather", 0x79: "cricket", 0x80: "football"}

TEXT_DIDS = {
    0x52: "rider-name",
    0x4C: "text-line-1/nav-text",
    0x63: "text-line-2/artist-old",
    0x61: "album",
    0x62: "song",
    0x65: "artist",
    0x53: "sms-sender",
    0x50: "pictogram-text",
    0x4F: "nav-instruction",
}


def ascii_payload(b):
    return bytes(x for x in b if x not in (0x00, 0xFF)).decode("utf-8", "replace")


def decode(frame):
    """Best-effort decode based on the statically recovered protocol."""
    if len(frame) < 2:
        return "short frame"
    start, did = frame[0], frame[1]
    body = frame[2:-1] if frame[-1] == 0xFF else frame[2:]
    if start == 0x5B and did == 0x4A and len(body) >= 16:
        b = body
        nibbles = f"{b[0]:02x}"
        return (
            f"STATUS net/batt={nibbles} overspeed={b[1]} temp={b[2]} "
            f"time={b[4]:02d}:{b[5]:02d}:{b[6]:02d} {'AM' if b[7] == 1 else 'PM'} "
            f"missed={b[8]} netType={b[9]} date={b[10]:02d}/{b[11]:02d}/{b[12]:02d} "
            f"rest={bytes(b[13:]).hex(' ')}"
        )
    if start == 0x5A and did == 0x4E and len(body) >= 10:
        b = body
        dist = int.from_bytes(b[0:2], "big")
        eta_min = int.from_bytes(b[2:4], "big")
        left = int.from_bytes(b[4:7], "big")
        state = {0: "running", 0xFF: "stopped"}.get(b[9], hex(b[9]))
        return (
            f"NAV next={dist}m eta={eta_min}min left={left}m picto={b[7]} "
            f"lines={b[8]} nav={state} rest={bytes(b[10:]).hex(' ')}"
        )
    if start == 0x5A and did == 0x49:
        return f"NAV road-name '{ascii_payload(body[:-4])}' flags={bytes(body[-4:]).hex(' ')}"
    if start == 0x5B and did == 0x4C and body and body[0] == 0x00 and len(body) > 1 and body[1] in SCREENS:
        return f"SCREEN {SCREENS[body[1]]}"
    if start == 0x5B and did == 0x43 and body:
        cat = {1: "number", 2: "name", 3: "country"}.get(body[0], hex(body[0]))
        return f"CALLER {cat} '{ascii_payload(body[1:])}'"
    if start == 0x5B and did in TEXT_DIDS:
        return f"TEXT {TEXT_DIDS[did]} '{ascii_payload(body)}'"
    if start == 0x5B and did == 0x9C and len(body) >= 3:
        return f"SOCIAL whatsapp={body[0] >> 3}msgs/{body[0] & 7}chats facebook={body[1] >> 4}/{body[1] & 0xF} instagram={body[2]}"
    if start == 0x5B and did == 0x5D and len(body) >= 4:
        desc = bytes(body[4:]).decode("utf-8", "replace").rstrip()
        return f"WEATHER icon={body[0]} aqi={int.from_bytes(body[1:3], 'big')} rain={body[3]}% '{desc}'"
    if start == 0x5B and did == 0x5C:
        place = bytes(body).rstrip(b"\x00").decode("utf-8", "replace")
        return f"LOCATION '{place}'"
    if start == 0x5B and did == 0x22:
        return f"USER-ID {bytes(body).hex(' ')}"
    return f"UNKNOWN start={start:#04x} did={did:#04x} ({chr(did) if 32 <= did < 127 else '?'})"


class Logger:
    def __init__(self):
        os.makedirs(LOG_PATH, exist_ok=True)
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        self.path = os.path.abspath(os.path.join(LOG_PATH, f"cluster-{stamp}.log"))
        self.fh = open(self.path, "a", buffering=1)

    def log(self, msg):
        line = f"{datetime.datetime.now().strftime('%H:%M:%S.%f')[:-3]} {msg}"
        print(line, flush=True)
        self.fh.write(line + "\n")


class Application(dbus.service.Object):
    def __init__(self, bus):
        self.path = "/com/ntorqsim"
        self.services = []
        dbus.service.Object.__init__(self, bus, self.path)

    def add_service(self, service):
        self.services.append(service)

    @dbus.service.method(DBUS_OM, out_signature="a{oa{sa{sv}}}")
    def GetManagedObjects(self):
        resp = {}
        for s in self.services:
            resp[s.get_path()] = s.get_properties()
            for c in s.characteristics:
                resp[c.get_path()] = c.get_properties()
        return resp


class Service(dbus.service.Object):
    def __init__(self, bus, index, uuid):
        self.path = f"/com/ntorqsim/service{index}"
        self.uuid = uuid
        self.characteristics = []
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {
            GATT_SERVICE: {
                "UUID": self.uuid,
                "Primary": True,
                "Characteristics": dbus.Array([c.get_path() for c in self.characteristics], signature="o"),
            }
        }


class Characteristic(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = f"{service.path}/char{index}"
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = [dbus.Byte(0)] * 20
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {
            GATT_CHRC: {
                "Service": self.service.get_path(),
                "UUID": self.uuid,
                "Flags": self.flags,
            }
        }

    @dbus.service.method(DBUS_PROP, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        return self.get_properties()[GATT_CHRC]

    @dbus.service.method(GATT_CHRC, in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return self.value

    @dbus.service.signal(DBUS_PROP, signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class WriteChar(Characteristic):
    def __init__(self, bus, index, service, logger):
        super().__init__(bus, index, WRITE_UUID, ["write", "write-without-response"], service)
        self.logger = logger

    @dbus.service.method(GATT_CHRC, in_signature="aya{sv}")
    def WriteValue(self, value, options):
        frame = bytes(value)
        self.logger.log(f"RX {frame.hex(' ')}  | {decode(frame)}")


class NotifyChar(Characteristic):
    def __init__(self, bus, index, service, logger):
        super().__init__(bus, index, NOTIFY_UUID, ["read", "notify"], service)
        self.logger = logger
        self.notifying = False

    @dbus.service.method(GATT_CHRC)
    def StartNotify(self):
        self.notifying = True
        self.logger.log("phone subscribed to notifications (5354)")

    @dbus.service.method(GATT_CHRC)
    def StopNotify(self):
        self.notifying = False
        self.logger.log("phone unsubscribed from notifications")

    def push(self, frame):
        self.value = [dbus.Byte(b) for b in frame]
        if not self.notifying:
            self.logger.log("cannot send: phone not subscribed")
            return
        self.PropertiesChanged(GATT_CHRC, {"Value": dbus.Array(self.value, signature="y")}, [])
        self.logger.log(f"TX {frame.hex(' ')}")


class Advertisement(dbus.service.Object):
    def __init__(self, bus, name):
        self.path = "/com/ntorqsim/adv0"
        self.name = name
        dbus.service.Object.__init__(self, bus, self.path)

    @dbus.service.method(DBUS_PROP, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        return {
            "Type": "peripheral",
            "ServiceUUIDs": dbus.Array([SERVICE_UUID], signature="s"),
            "LocalName": dbus.String(self.name),
        }

    @dbus.service.method(ADV_IFACE)
    def Release(self):
        pass


def stdin_loop(notify_char, logger):
    logger.log("type a hex frame (e.g. 5a 54 ...) and Enter to notify the phone; Ctrl+C to quit")
    for line in sys.stdin:
        hexstr = line.strip().replace(" ", "")
        if not hexstr:
            continue
        try:
            frame = bytes.fromhex(hexstr)
        except ValueError:
            logger.log(f"bad hex: {line.strip()}")
            continue
        GLib.idle_add(notify_char.push, frame)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", default="hci0")
    ap.add_argument("--name", default="TVSBT01", help="must contain 'TVS' and not 'HUD'; keep short")
    args = ap.parse_args()

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    adapter_path = f"/org/bluez/{args.adapter}"
    adapter = bus.get_object(BLUEZ, adapter_path)
    dbus.Interface(adapter, DBUS_PROP).Set("org.bluez.Adapter1", "Powered", dbus.Boolean(True))

    logger = Logger()
    app = Application(bus)
    svc = Service(bus, 0, SERVICE_UUID)
    svc.characteristics.append(WriteChar(bus, 0, svc, logger))
    notify = NotifyChar(bus, 1, svc, logger)
    svc.characteristics.append(notify)
    app.add_service(svc)
    adv = Advertisement(bus, args.name)

    loop = GLib.MainLoop()

    def fail(what):
        def cb(err):
            logger.log(f"{what} failed: {err}")
            loop.quit()
        return cb

    dbus.Interface(adapter, GATT_MANAGER).RegisterApplication(
        app.path, {}, reply_handler=lambda: logger.log("GATT service registered"), error_handler=fail("GATT register"))
    dbus.Interface(adapter, ADV_MANAGER).RegisterAdvertisement(
        adv.path, {}, reply_handler=lambda: logger.log(f"advertising as '{args.name}' on {args.adapter}"),
        error_handler=fail("advertising"))

    logger.log(f"logging to {logger.path}")
    threading.Thread(target=stdin_loop, args=(notify, logger), daemon=True).start()
    try:
        loop.run()
    except KeyboardInterrupt:
        pass
    finally:
        try:
            dbus.Interface(adapter, ADV_MANAGER).UnregisterAdvertisement(adv.path)
            dbus.Interface(adapter, GATT_MANAGER).UnregisterApplication(app.path)
        except dbus.exceptions.DBusException:
            pass
        logger.log("stopped")


if __name__ == "__main__":
    main()

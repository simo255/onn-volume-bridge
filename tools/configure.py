#!/usr/bin/env python3
"""Optional desktop setup. Device addresses and secrets are never printed."""
import argparse
import hashlib
import ipaddress
import os
import shlex
import socket
import ssl
import subprocess

PACKAGE = "io.github.simo255.onnvolumebridge"


def local_ipv4(value):
    address = ipaddress.IPv4Address(value)
    allowed = any(address in ipaddress.IPv4Network(net) for net in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))
    if not allowed:
        raise argparse.ArgumentTypeError("Use the TV's private local IPv4 address")
    return str(address)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Trusted Android platform-tools adb executable")
    parser.add_argument("--device", required=True, help="Authorized onn ADB endpoint")
    parser.add_argument("--provider", choices=("samsung", "roku", "sony", "upnp"), required=True)
    parser.add_argument("--host", required=True, type=local_ipv4)
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--certificate-pin", default="", help="Samsung leaf certificate SHA-256")
    parser.add_argument("--trust-tv-certificate", action="store_true", help="Explicit first-use trust for this local Samsung certificate")
    parser.add_argument("--sony-psk-env", default="ONN_BRIDGE_SONY_PSK", help="Read Sony PSK from this environment variable; app UI is preferred")
    parser.add_argument("--control-path", default="/upnp/control/RenderingControl1")
    parser.add_argument("--input-name", default="", help="Optional exact remote interface name")
    parser.add_argument("--enable", action="store_true", help="Opt in to relaying after configuration")
    args = parser.parse_args()
    if args.port < 0 or args.port > 65535:
        parser.error("Invalid port")

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.device, *command], capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError("ADB operation failed. Check box connection and authorization.")
        return result.stdout

    def shell(*command):
        return adb("shell", shlex.join(command))

    if adb("get-state").strip() != b"device":
        raise RuntimeError("Box unavailable")
    if not shell("pm", "path", PACKAGE).strip().startswith(b"package:"):
        raise RuntimeError("Build and install onn Volume Bridge first")
    pin = args.certificate_pin.lower().replace(":", "")
    if args.provider == "samsung" and not pin:
        if not args.trust_tv_certificate:
            parser.error("Approve the certificate in the app, provide --certificate-pin, or explicitly use --trust-tv-certificate")
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        tls.check_hostname = False
        tls.verify_mode = ssl.CERT_NONE
        with socket.create_connection((args.host, 8002), timeout=5) as raw:
            with tls.wrap_socket(raw, server_hostname=args.host) as connection:
                pin = hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest()
    if args.provider == "samsung" and (len(pin) != 64 or any(c not in "0123456789abcdef" for c in pin)):
        parser.error("Invalid certificate pin")
    command = ["am", "broadcast", "-n", PACKAGE + "/.SetupReceiver", "-a", PACKAGE + ".CONFIGURE",
               "--es", "provider", args.provider.upper(), "--es", "host", args.host,
               "--ei", "port", str(args.port), "--es", "control_path", args.control_path,
               "--es", "input_name", args.input_name, "--ez", "enable", str(args.enable).lower()]
    if pin:
        command += ["--es", "cert_pin", pin]
    if args.provider == "sony" and os.environ.get(args.sony_psk_env):
        command += ["--es", "psk", os.environ[args.sony_psk_env]]
    reply = shell(*command)
    if b"Broadcast completed: result=0" not in reply:
        raise RuntimeError("Configuration rejected. Check provider fields and TV settings.")
    shell("am", "start", "-n", PACKAGE + "/.MainActivity")
    print("Configuration saved. Follow the setup steps in the app and approve local pairing on both devices.")
    print("Other accessibility services and the launcher were not changed.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.SubprocessError):
        raise SystemExit("Setup failed. Check the local TV endpoint, installed app and ADB authorization.")

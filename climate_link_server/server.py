"""ClimateLink prototype receiver. Python 3.11+, standard library only."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
from functools import partial
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
from pathlib import Path
import re
import secrets
import sqlite3
from urllib.parse import parse_qs, urlsplit

MAX_BODY = 16384
HERE = Path(__file__).resolve().parent


def number(value, name, low, high, *, integer=False):
    if (isinstance(value, bool) or not isinstance(value, (int, float))
            or not low <= value <= high or not math.isfinite(value)
            or (integer and int(value) != value)):
        raise ValueError(f"Invalid {name}")
    return int(value) if integer else float(value)


def validate_batch(data):
    if not isinstance(data, dict) or type(data.get("schema_version")) is not int or data["schema_version"] != 1:
        raise ValueError("schema_version must be 1")
    for key, pattern in (("device_id", r"CL-[0-9A-F]{12}"), ("session_id", r"[0-9a-f]{8}")):
        if not isinstance(data.get(key), str) or not re.fullmatch(pattern, data[key]):
            raise ValueError(f"Invalid {key}")
    if type(data.get("simulated")) is not bool:
        raise ValueError("simulated must be a boolean")
    firmware = data.get("firmware")
    if not isinstance(firmware, str) or not re.fullmatch(r"[A-Za-z0-9.+_-]{1,32}", firmware):
        raise ValueError("Invalid firmware")
    battery = data.get("battery_mv")
    if battery is not None:
        number(battery, "battery_mv", 1500, 6000, integer=True)
    dropped = number(data.get("dropped_samples", 0), "dropped_samples", 0, 2**32-1, integer=True)
    samples = data.get("samples")
    if not isinstance(samples, list) or not 1 <= len(samples) <= 32:
        raise ValueError("samples must contain 1 to 32 items")
    normalized, seen = [], set()
    for sample in samples:
        if not isinstance(sample, dict):
            raise ValueError("Invalid sample")
        seq = number(sample.get("sequence"), "sequence", 1, 2**32-1, integer=True)
        if seq in seen:
            raise ValueError("Duplicate sequence in batch")
        seen.add(seq)
        stamp = sample.get("timestamp")
        if stamp is not None:
            stamp = number(stamp, "timestamp", 1704067200, 4102444800, integer=True)
        normalized.append((seq, stamp,
            number(sample.get("temperature_c"), "temperature_c", -80, 125),
            number(sample.get("humidity_pct"), "humidity_pct", 0, 100)))
    return data["device_id"], data["session_id"], firmware, data["simulated"], battery, dropped, normalized


class Store:
    def __init__(self, path):
        self.path = str(path)
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("""CREATE TABLE IF NOT EXISTS measurements (
                id INTEGER PRIMARY KEY, device_id TEXT NOT NULL, session_id TEXT NOT NULL,
                sequence INTEGER NOT NULL, timestamp INTEGER, temperature_c REAL NOT NULL,
                humidity_pct REAL NOT NULL, simulated INTEGER NOT NULL, battery_mv INTEGER,
                firmware TEXT NOT NULL, dropped_samples INTEGER NOT NULL, received_at TEXT NOT NULL,
                UNIQUE(device_id, session_id, sequence))""")
            db.execute("CREATE INDEX IF NOT EXISTS device_recent ON measurements(device_id,id DESC)")

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def ingest(self, data):
        device, session, firmware, simulated, battery, dropped, samples = validate_batch(data)
        received = datetime.now(timezone.utc).isoformat()
        with self.connect() as db:
            before = db.total_changes
            for seq, stamp, temp, humidity in samples:
                db.execute("""INSERT INTO measurements
                    (device_id,session_id,sequence,timestamp,temperature_c,humidity_pct,
                    simulated,battery_mv,firmware,dropped_samples,received_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(device_id,session_id,sequence) DO NOTHING""",
                    (device,session,seq,stamp,temp,humidity,int(simulated),battery,firmware,dropped,received))
            inserted = db.total_changes - before
        return {"ok": True, "accepted": len(samples), "inserted": inserted, "duplicates": len(samples)-inserted}

    def devices(self):
        with self.connect() as db:
            rows = db.execute("""SELECT m.*, a.sample_count FROM measurements m JOIN
                (SELECT device_id,MAX(id) AS latest,COUNT(*) AS sample_count FROM measurements GROUP BY device_id) a
                ON m.id=a.latest ORDER BY m.id DESC""").fetchall()
        return [dict(r) for r in rows]

    def samples(self, device, limit):
        with self.connect() as db:
            rows = db.execute("SELECT * FROM measurements WHERE device_id=? ORDER BY id DESC LIMIT ?", (device,limit)).fetchall()
        return [dict(r) for r in reversed(rows)]


class Handler(BaseHTTPRequestHandler):
    server_version = "ClimateLink/0.1"

    def __init__(self, *args, store, token, **kwargs):
        self.store, self.token = store, token
        super().__init__(*args, **kwargs)

    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def respond(self, status, data, content_type="application/json; charset=utf-8"):
        if not isinstance(data, bytes):
            data = json.dumps(data, ensure_ascii=False, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type",content_type)
        self.send_header("Content-Length",str(len(data)))
        self.send_header("Cache-Control","no-store")
        self.send_header("X-Content-Type-Options","nosniff")
        self.send_header("Referrer-Policy","no-referrer")
        self.send_header("X-Frame-Options","DENY")
        self.send_header("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        supplied = self.headers.get("Authorization", "").encode("utf-8")
        if not hmac.compare_digest(supplied, ("Bearer " + self.token).encode("utf-8")):
            self.respond(401,{"ok":False,"error":"Unauthorized"})
            return False
        return True

    def do_GET(self):
        url = urlsplit(self.path)
        assets = {"/":("index.html","text/html; charset=utf-8"),
                  "/app.js":("app.js","application/javascript; charset=utf-8"),
                  "/style.css":("style.css","text/css; charset=utf-8")}
        if url.path in assets:
            name, mime = assets[url.path]
            self.respond(200,(HERE/"static"/name).read_bytes(),mime)
        elif url.path == "/health":
            self.respond(200,{"ok":True,"service":"climatelink","schema_version":1})
        elif url.path == "/api/v1/devices":
            if self.authorized():
                self.respond(200,{"devices":self.store.devices()})
        elif url.path == "/api/v1/measurements":
            if not self.authorized(): return
            q = parse_qs(url.query)
            device = q.get("device_id",[""])[0]
            try:
                if not re.fullmatch(r"CL-[0-9A-F]{12}",device): raise ValueError()
                limit = int(q.get("limit",["120"])[0])
                if not 1 <= limit <= 500: raise ValueError()
            except ValueError:
                self.respond(400,{"ok":False,"error":"Invalid device_id or limit"})
                return
            self.respond(200,{"samples":self.store.samples(device,limit)})
        else:
            self.respond(404,{"ok":False,"error":"Not found"})

    def do_POST(self):
        if urlsplit(self.path).path != "/api/v1/measurements":
            self.respond(404,{"ok":False,"error":"Not found"}); return
        if not self.authorized(): return
        if self.headers.get("Transfer-Encoding"):
            self.respond(400,{"ok":False,"error":"Chunked requests are not supported"}); return
        if self.headers.get_content_type() != "application/json":
            self.respond(415,{"ok":False,"error":"Use application/json"}); return
        if "Content-Length" not in self.headers:
            self.respond(411,{"ok":False,"error":"Content-Length required"}); return
        try:
            length = int(self.headers["Content-Length"])
        except ValueError:
            length = -1
        if not 1 <= length <= MAX_BODY:
            self.respond(413,{"ok":False,"error":"Invalid body size"}); return
        try:
            raw = self.rfile.read(length)
            if len(raw) != length: raise ValueError("Incomplete request")
            data = json.loads(raw,parse_constant=lambda s: (_ for _ in ()).throw(ValueError("Non-finite number")))
            result = self.store.ingest(data)
        except (ValueError, UnicodeError, RecursionError) as exc:
            self.respond(400,{"ok":False,"error":str(exc)[:160]}); return
        except sqlite3.Error:
            self.respond(503,{"ok":False,"error":"Database unavailable; retry later"}); return
        self.respond(202,result)

    def log_message(self, fmt, *args):
        # Do not log headers, query strings, tokens or request bodies.
        print(f"{self.address_string()} {self.command} {urlsplit(self.path).path} {args[1] if len(args)>1 else ''}",flush=True)


def create_server(host, port, database, token):
    if not re.fullmatch(r"[A-Za-z0-9._~-]{16,128}", token):
        raise ValueError("CLIMATE_API_TOKEN must contain 16–128 URL-safe ASCII characters")
    store = Store(database)
    return ThreadingHTTPServer((host,port),partial(Handler,store=store,token=token))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host",default="127.0.0.1")
    parser.add_argument("--port",type=int,default=8080)
    parser.add_argument("--db",default=str(HERE/"data"/"climate.sqlite3"))
    args=parser.parse_args()
    token=os.environ.get("CLIMATE_API_TOKEN")
    if not token:
        token=secrets.token_urlsafe(32)
        print(f"Temporary API token (set CLIMATE_API_TOKEN to keep it across restarts): {token}",flush=True)
    server=create_server(args.host,args.port,args.db,token)
    print(f"ClimateLink listening on http://{args.host}:{server.server_port}",flush=True)
    print("For ESP32 use this computer's LAN IPv4, not localhost. Public deployment requires an HTTPS reverse proxy.",flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()


if __name__ == "__main__": main()

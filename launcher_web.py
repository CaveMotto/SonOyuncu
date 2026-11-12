import datetime
import http.server
import json
import queue
import subprocess
import threading
import time
import webbrowser
from pathlib import Path
from urllib.parse import urlparse

BASE = Path(__file__).parent
HTML_FILE = BASE / "launcher.html"

JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe"
CREATE_NO_WINDOW = 0x08000000

_OPENS = [
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.io=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens", "java.base/java.net=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    "--add-opens", "java.base/java.security=ALL-UNNAMED",
]

SERVERS = [
    {"key": "bungeecord", "name": "BungeeCord", "dir": "servers/bungeecord", "jar": "bungeecord.jar", "ram": "512M", "nogui": False, "port": 25565},
    {"key": "cakmalobi",  "name": "CakmaLobi",  "dir": "servers/cakmalobi",  "jar": "cakmalobi.jar",  "ram": "768M", "nogui": True,  "port": 25566},
    {"key": "lobi",       "name": "Lobi",        "dir": "servers/lobi",       "jar": "lobi.jar",       "ram": "768M", "nogui": True,  "port": 25567},
    {"key": "opskyblock", "name": "OpSkyBlock",  "dir": "servers/opskyblock", "jar": "skyblock.jar",   "ram": "768M", "nogui": True,  "port": 25568},
    {"key": "bedwars",    "name": "BedWars",     "dir": "servers/bedwars",    "jar": "bedwars.jar",    "ram": "768M", "nogui": True,  "port": 25569},
    {"key": "skywars",    "name": "SkyWars",     "dir": "servers/skywars",    "jar": "Skywars.jar",    "ram": "768M", "nogui": True,  "port": 25570},
    {"key": "titanyum",   "name": "Titanyum",    "dir": "servers/titanyum",   "jar": "Survival.jar",   "ram": "768M", "nogui": True,  "port": 25571},
    {"key": "faction",    "name": "Faction",     "dir": "servers/faction",    "jar": "faction.jar",    "ram": "768M", "nogui": True,  "port": 25572},
]

SERVER_MAP = {s["key"]: s for s in SERVERS}

procs    = {}
statuses = {}
logs     = {}
subs     = {}
lock     = threading.Lock()

for s in SERVERS:
    k = s["key"]
    procs[k]    = None
    statuses[k] = "stopped"
    logs[k]     = []
    subs[k]     = []


# ── helpers ────────────────────────────────────────────────────────────────

def _push(key, line):
    line = line.rstrip("\r\n")
    if not line:
        return
    with lock:
        logs[key].append(line)
        if len(logs[key]) > 500:
            logs[key] = logs[key][-500:]
        dead = []
        for q in subs[key]:
            try:
                q.put_nowait(line)
            except queue.Full:
                dead.append(q)
        for q in dead:
            subs[key].remove(q)


def _reader(key, proc):
    try:
        for raw in proc.stdout:
            _push(key, raw)
    except Exception:
        pass
    _push(key, "[server stopped]")
    with lock:
        if procs.get(key) is proc:
            procs[key] = None
            statuses[key] = "stopped"


def start_server(key):
    s = SERVER_MAP[key]
    with lock:
        if procs[key] and procs[key].poll() is None:
            return
        statuses[key] = "starting"
    srv_dir = BASE / s["dir"]
    cmd = [JAVA, f"-Xms{s['ram']}", f"-Xmx{s['ram']}"] + _OPENS + ["-Dfile.encoding=UTF-8", "-jar", s["jar"]]
    if s["nogui"]:
        cmd.append("nogui")
    _push(key, f"[starting] {' '.join(cmd)}")
    try:
        proc = subprocess.Popen(
            cmd, cwd=str(srv_dir),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            creationflags=CREATE_NO_WINDOW,
            bufsize=1, universal_newlines=True, encoding="utf-8", errors="replace",
        )
        with lock:
            procs[key] = proc
            statuses[key] = "running"
        threading.Thread(target=_reader, args=(key, proc), daemon=True).start()
    except Exception as e:
        _push(key, f"[ERROR] {e}")
        with lock:
            statuses[key] = "error"


def stop_server(key):
    with lock:
        proc = procs.get(key)
    if proc and proc.poll() is None:
        try:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        except Exception:
            proc.terminate()


def start_all():
    for s in SERVERS:
        if s["key"] != "bungeecord":
            threading.Thread(target=start_server, args=(s["key"],), daemon=True).start()
    def _bungee():
        time.sleep(3)
        start_server("bungeecord")
    threading.Thread(target=_bungee, daemon=True).start()


def stop_all():
    stop_server("bungeecord")
    time.sleep(0.5)
    for s in SERVERS:
        if s["key"] != "bungeecord":
            stop_server(s["key"])


# ── player data ────────────────────────────────────────────────────────────

def get_players(key):
    s = SERVER_MAP[key]
    srv_dir = BASE / s["dir"]

    # usercache.json — name + uuid
    cache_file = srv_dir / "usercache.json"
    cache = {}
    if cache_file.exists():
        try:
            for entry in json.loads(cache_file.read_text("utf-8")):
                uuid = entry.get("uuid", "")
                name = entry.get("name", "")
                if uuid and name:
                    cache[uuid] = name
        except Exception:
            pass

    # ops.json
    ops_file = srv_dir / "ops.json"
    ops_uuids = set()
    if ops_file.exists():
        try:
            for entry in json.loads(ops_file.read_text("utf-8")):
                u = entry.get("uuid", entry.get("UUID", ""))
                if u:
                    ops_uuids.add(u)
        except Exception:
            pass

    # banned-players.json
    ban_file = srv_dir / "banned-players.json"
    banned_uuids = set()
    if ban_file.exists():
        try:
            for entry in json.loads(ban_file.read_text("utf-8")):
                u = entry.get("uuid", entry.get("UUID", ""))
                if u:
                    banned_uuids.add(u)
        except Exception:
            pass

    # whitelist.json
    wl_file = srv_dir / "whitelist.json"
    wl_uuids = set()
    if wl_file.exists():
        try:
            for entry in json.loads(wl_file.read_text("utf-8")):
                u = entry.get("uuid", entry.get("UUID", ""))
                if u:
                    wl_uuids.add(u)
        except Exception:
            pass

    result = []
    for uuid, name in cache.items():
        result.append({
            "name": name,
            "uuid": uuid,
            "op": uuid in ops_uuids,
            "banned": uuid in banned_uuids,
            "whitelisted": uuid in wl_uuids,
        })

    result.sort(key=lambda x: x["name"].lower())
    return result


def player_action(key, name, action):
    """Send a command to the running server for a player action."""
    cmds = {
        "op":        f"op {name}",
        "deop":      f"deop {name}",
        "kick":      f"kick {name}",
        "ban":       f"ban {name}",
        "pardon":    f"pardon {name}",
    }
    cmd = cmds.get(action)
    if not cmd:
        return False, "unknown action"
    with lock:
        proc = procs.get(key)
    if proc and proc.poll() is None:
        try:
            proc.stdin.write(cmd + "\n")
            proc.stdin.flush()
            _push(key, f"> {cmd}")
            return True, "ok"
        except Exception as e:
            return False, str(e)
    # server offline — edit files directly for op/ban/pardon
    s = SERVER_MAP[key]
    srv_dir = BASE / s["dir"]
    if action in ("op", "deop"):
        _edit_json_list(srv_dir / "ops.json", name, action == "op",
                        lambda n: {"uuid": "", "name": n, "level": 4, "bypassesPlayerLimit": False})
        return True, "edited ops.json (server offline)"
    if action in ("ban", "pardon"):
        _edit_json_list(srv_dir / "banned-players.json", name, action == "ban",
                        lambda n: {"uuid": "", "name": n, "created": datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S +0000"),
                                   "source": "Panel", "expires": "forever", "reason": "Banned by operator."})
        return True, "edited banned-players.json (server offline)"
    return False, "server offline"


def _edit_json_list(path, name, add, make_entry):
    try:
        entries = json.loads(path.read_text("utf-8")) if path.exists() else []
        entries = [e for e in entries if e.get("name", "").lower() != name.lower()]
        if add:
            entries.append(make_entry(name))
        path.write_text(json.dumps(entries, indent=2), "utf-8")
    except Exception:
        pass


# ── HTTP handler ───────────────────────────────────────────────────────────

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _json(self, code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        p = urlparse(self.path)
        path = p.path.rstrip("/") or "/"

        if path == "/":
            html = HTML_FILE.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return

        if path == "/logo.png":
            img = (BASE / "logo.png").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(img)))
            self.end_headers()
            self.wfile.write(img)
            return

        if path == "/api/status":
            data = [{"key": s["key"], "name": s["name"], "port": s["port"],
                     "status": statuses[s["key"]]} for s in SERVERS]
            self._json(200, data)
            return

        if path.startswith("/api/logs/"):
            key = path[len("/api/logs/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            with lock:
                self._json(200, list(logs[key]))
            return

        if path.startswith("/api/players/"):
            key = path[len("/api/players/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            self._json(200, get_players(key))
            return

        if path.startswith("/api/stream/"):
            key = path[len("/api/stream/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            q = queue.Queue(maxsize=200)
            with lock:
                subs[key].append(q)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("X-Accel-Buffering", "no")
            self.end_headers()
            try:
                while True:
                    try:
                        line = q.get(timeout=15)
                        self.wfile.write(f"data: {json.dumps(line)}\n\n".encode("utf-8"))
                        self.wfile.flush()
                    except queue.Empty:
                        self.wfile.write(b": ping\n\n")
                        self.wfile.flush()
            except Exception:
                pass
            finally:
                with lock:
                    if q in subs[key]:
                        subs[key].remove(q)
            return

        self._json(404, {"error": "not found"})

    def do_POST(self):
        p = urlparse(self.path)
        path = p.path.rstrip("/")
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length else ""

        if path.startswith("/api/start/"):
            key = path[len("/api/start/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            threading.Thread(target=start_server, args=(key,), daemon=True).start()
            self._json(200, {"ok": True})
            return

        if path.startswith("/api/stop/"):
            key = path[len("/api/stop/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            threading.Thread(target=stop_server, args=(key,), daemon=True).start()
            self._json(200, {"ok": True})
            return

        if path == "/api/start-all":
            threading.Thread(target=start_all, daemon=True).start()
            self._json(200, {"ok": True})
            return

        if path == "/api/stop-all":
            threading.Thread(target=stop_all, daemon=True).start()
            self._json(200, {"ok": True})
            return

        if path.startswith("/api/cmd/"):
            key = path[len("/api/cmd/"):]
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            with lock:
                proc = procs.get(key)
            if proc and proc.poll() is None:
                try:
                    cmd = body.strip()
                    proc.stdin.write(cmd + "\n")
                    proc.stdin.flush()
                    _push(key, f"> {cmd}")
                except Exception as e:
                    _push(key, f"[cmd error] {e}")
            self._json(200, {"ok": True})
            return

        if path.startswith("/api/player-action/"):
            # /api/player-action/<key>/<action>/<name>
            parts = path[len("/api/player-action/"):].split("/", 2)
            if len(parts) != 3:
                self._json(400, {"error": "bad request"}); return
            key, action, name = parts
            if key not in SERVER_MAP:
                self._json(404, {"error": "not found"}); return
            ok, msg = player_action(key, name, action)
            self._json(200, {"ok": ok, "msg": msg})
            return

        self._json(404, {"error": "not found"})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()


PORT = 7979

if __name__ == "__main__":
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"MottoSMP Panel: http://127.0.0.1:{PORT}")
    threading.Timer(0.8, lambda: webbrowser.open(f"http://127.0.0.1:{PORT}")).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down...")
        stop_all()
        time.sleep(2)

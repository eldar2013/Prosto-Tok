"""The badge given with Margy Chat: what MargyT's server does with Margy Chat's answer. Margy Chat itself is a stand-in here."""
import json, pathlib, sqlite3, sys, tempfile, threading, unittest, os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import margychat


class FakeChat(BaseHTTPRequestHandler):
    heard = []

    def log_message(self, *args):
        pass

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        FakeChat.heard.append((self.path, self.headers.get("X-Internal-Key"), body))
        code, out = {"GOODON": (200, {"ok": True, "nick": "vera"}), "CHANNL": (403, {"error": "значок только для личных аккаунтов"})}.get(
            body["code"], (404, {"error": "такого кода нет или он устарел"}))
        data = json.dumps(out).encode()
        self.send_response(code)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class MargyChatBadgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeChat)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.key = tempfile.NamedTemporaryFile("w", delete=False)
        cls.key.write("shared\n")
        cls.key.close()
        margychat.CHAT = "http://127.0.0.1:%d" % cls.server.server_address[1]
        margychat.KEY = cls.key.name

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        os.unlink(cls.key.name)

    def table(self):
        db = sqlite3.connect(":memory:")
        db.executescript("""
            CREATE TABLE badge (id TEXT PRIMARY KEY, colour TEXT, image TEXT, title TEXT, title_ru TEXT,
                title_uk TEXT, text TEXT, text_ru TEXT, text_uk TEXT, button TEXT, claimable INTEGER);
            CREATE TABLE held (uid TEXT, badge TEXT, shown INTEGER DEFAULT 1, place INTEGER DEFAULT 0,
                given_at INTEGER DEFAULT 0, PRIMARY KEY (uid, badge));
        """)
        margychat.prepare(db)
        margychat.prepare(db)
        return db

    def test_the_badge_is_in_the_table_once(self):
        db = self.table()
        self.assertEqual(db.execute("SELECT colour, image, title_ru FROM badge").fetchall(), [("1C6B4F", "margychat.png", "Выжил в Margy Chat")])

    def test_a_good_code_gives_the_badge(self):
        db = self.table()
        code, out = margychat.claim(db, "7000000000000000001", {"code": "goodon"})
        self.assertEqual((code, out), (200, {"chat": "@vera"}))
        self.assertEqual(db.execute("SELECT uid, badge FROM held").fetchall(), [("7000000000000000001", "margychat")])
        self.assertEqual(FakeChat.heard[-1], ("/internal/margyt-claim", "shared", {"code": "GOODON", "uid": "7000000000000000001"}))

    def test_what_margy_chat_refuses_gives_nothing(self):
        db = self.table()
        for sent, words in (("CHANNL", "личных"), ("NOSUCH", "устарел")):
            code, out = margychat.claim(db, "7000000000000000001", {"code": sent})
            self.assertEqual(code, 409)
            self.assertIn(words, out["error"])
        self.assertEqual(db.execute("SELECT COUNT(*) FROM held").fetchone()[0], 0)

    def test_nonsense_never_reaches_margy_chat(self):
        db = self.table()
        before = len(FakeChat.heard)
        for bad in ("", "abc", "../../x", 123, "ABCDEFG"):
            self.assertEqual(margychat.claim(db, "7000000000000000001", {"code": bad})[0], 400)
        self.assertEqual(len(FakeChat.heard), before)


if __name__ == "__main__":
    unittest.main()

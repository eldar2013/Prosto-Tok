"""The badge MargyT gives out with Margy Chat: "Выжил в Margy Chat".

A person asks Margy Chat for a code in its settings and types it into MargyT. Margy Chat, on this
same machine, says whether the code is real and its account a personal one that has earned enough
there with its posts; the badge is given here only when it says yes. Channels and bots never get it:
Margy Chat refuses them. The key the two share is a file only root can read.
"""
import json
import os
import re
import time
import urllib.error
import urllib.request

BADGE = "margychat"
CHAT = os.environ.get("MARGYT_CHAT_URL", "http://127.0.0.1:8093")
KEY = os.environ.get("MARGYT_CHAT_KEY", "/opt/margychat/margyt.key")
CODE = re.compile(r"^[A-Za-z0-9]{6}$")

#: the badge as the table of badges holds it, added the first time the server starts with this
BADGE_ROW = {
    "id": BADGE, "colour": "1C6B4F", "image": "margychat.png",
    "title": "Margy Chat", "title_ru": "Выжил в Margy Chat", "title_uk": "Вижив у Margy Chat",
    "text": "survived Margy Chat: signed up to the worst social network in the world and even wrote something there",
    "text_ru": "выжил в Margy Chat: зарегистрировался в худшей соцсети на свете и даже что-то там написал",
    "text_uk": "вижив у Margy Chat: зареєструвався в найгіршій соцмережі на світі й навіть щось там написав",
    "button": "Got it", "claimable": 0,
}


def prepare(db):
    if not db.execute("SELECT 1 FROM badge WHERE id = ?", (BADGE,)).fetchone():
        keys = sorted(BADGE_ROW)
        db.execute("INSERT INTO badge (%s) VALUES (%s)" % (", ".join(keys), ",".join("?" * len(keys))),
                   [BADGE_ROW[key] for key in keys])


def ask(code, uid):
    """What Margy Chat says about a code: (ok, what to tell the person)."""
    try:
        with open(KEY, encoding="utf-8") as handle:
            key = handle.read().strip()
    except OSError:
        return False, "Margy Chat сейчас не отвечает"
    request = urllib.request.Request(CHAT + "/internal/margyt-claim", method="POST",
                                     data=json.dumps({"code": code.upper(), "uid": uid}).encode(),
                                     headers={"Content-Type": "application/json", "X-Internal-Key": key})
    try:
        with urllib.request.urlopen(request, timeout=10) as answer:
            said = json.loads(answer.read())
            return True, "@" + str(said.get("nick", ""))
    except urllib.error.HTTPError as refused:
        try:
            return False, json.loads(refused.read()).get("error") or "Margy Chat не принял код"
        except ValueError:
            return False, "Margy Chat не принял код"
    except (OSError, ValueError):
        return False, "Margy Chat сейчас не отвечает"


def claim(db, uid, body):
    """The code from Margy Chat, for an account that has proved itself; the badge if Margy Chat says yes."""
    code = body.get("code")
    if not isinstance(code, str) or not CODE.match(code.strip()):
        return 400, {"error": "Код из 6 букв и цифр, его показывает Margy Chat в настройках аккаунта"}
    ok, said = ask(code.strip(), uid)
    if not ok:
        return 409, {"error": said}
    db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at) VALUES (?, ?, ?)", (uid, BADGE, int(time.time())))
    db.commit()
    return 200, {"chat": said}

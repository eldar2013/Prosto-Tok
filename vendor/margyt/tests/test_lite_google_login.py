from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class LiteGoogleLoginTest(unittest.TestCase):
    def test_google_oauth_intent_is_not_rewritten(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/LiteLogin.java").read_text(encoding="utf-8")
        self.assertIn("static void created(Activity screen)", source)
        self.assertIn("does not alter its intent", source)
        self.assertNotIn('putExtra("provider_id"', source)
        self.assertNotIn('"margyt_none"', source)
        self.assertNotIn("Toast.makeText", source)


if __name__ == "__main__":
    unittest.main()

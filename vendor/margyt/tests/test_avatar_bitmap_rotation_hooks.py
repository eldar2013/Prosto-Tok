import tempfile
import unittest
from pathlib import Path

from margyt.dexpatch import rewrite_avatar_shapes


class AvatarBitmapRotationHooksTest(unittest.TestCase):
    def test_bitmap_draw_in_avatar_drawable_is_routed_through_photo_rotation(self):
        source = """.class public final LX/0HEe;
.super Ljava/lang/Object;
.method public final draw(Landroid/graphics/Canvas;)V
    .registers 7
    invoke-virtual {p1, v0, v1, v2, v3}, Landroid/graphics/Canvas;->drawBitmap(Landroid/graphics/Bitmap;Landroid/graphics/Rect;Landroid/graphics/RectF;Landroid/graphics/Paint;)V
    return-void
.end method
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "0HEe.smali")
            path.write_text(source, encoding="utf-8")
            counts = rewrite_avatar_shapes(folder)
            patched = path.read_text(encoding="utf-8")
            self.assertEqual(sum(value for key, value in counts.items() if key.startswith("Canvas->drawBitmap")), 1)
            self.assertIn("AvatarShape;->owner(Ljava/lang/Object;)V", patched)
            self.assertIn("AvatarShape;->drawBitmap(Landroid/graphics/Canvas;Landroid/graphics/Bitmap;Landroid/graphics/Rect;Landroid/graphics/RectF;Landroid/graphics/Paint;)V", patched)

    def test_photo_rotation_uses_persisted_speed_and_leaves_mask_stationary(self):
        source = (Path(__file__).resolve().parents[1] /
                  "inject/java/cat/narezany/margyt/AvatarShape.java").read_text(encoding="utf-8")
        self.assertIn('SPIN_SPEED_KEY = "avatar_spin_speed"', source)
        self.assertIn("putFloat(SPIN_SPEED_KEY, value)", source)
        self.assertIn("canvas.rotate(angle(), cx, cy)", source)
        self.assertIn("canvas.drawBitmap(bitmap, src, dst, paint)", source)
        self.assertIn("static boolean turning()", source)
        self.assertIn("static boolean turning() {\n        return false;", source)
        self.assertIn("// The story ring and mask stay fixed while only the photo turns.", source)
        self.assertIn("private static boolean photoTurning()", source)


if __name__ == "__main__":
    unittest.main()

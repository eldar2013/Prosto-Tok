import tempfile
import unittest
from pathlib import Path

from margyt.dexpatch import rewrite_avatar_shapes


class AvatarOutlineRotationHooksTest(unittest.TestCase):
    def test_avatar_drawable_hooks_mask_path_and_not_bitmap_rotation(self):
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
            self.assertIn("AvatarShape;->drawPath(Landroid/graphics/Canvas;Landroid/graphics/Path;Landroid/graphics/Paint;)V", patched)

    def test_outline_rotates_with_speed_while_photo_stays_still(self):
        source = (Path(__file__).resolve().parents[1] /
                  "inject/java/cat/narezany/margyt/AvatarShape.java").read_text(encoding="utf-8")
        self.assertIn('SPIN_SPEED_KEY = "avatar_spin_speed"', source)
        self.assertIn("putFloat(SPIN_SPEED_KEY, value)", source)
        self.assertIn("private static boolean turning(Object who)", source)
        self.assertIn("return photoTurning(who)", source)
        self.assertIn("drawn = turned(path, who)", source)
        self.assertIn("canvas.drawPath(drawn, paint)", source)
        self.assertNotIn("BitmapShader", source)
        self.assertNotIn("canvas.rotate(angle(), cx, cy)", source)
        self.assertIn("canvas.drawBitmap(bitmap, src, dst, paint)", source)
        self.assertIn("// Rotate the avatar outline/ring while its photo remains still.", source)
        self.assertIn("private static boolean photoTurning(Object who)", source)
        self.assertIn("name.contains(\"avatar\")", source)
        self.assertIn("name.contains(\"profile\")", source)
        self.assertIn("if (!spins())", source)


if __name__ == "__main__":
    unittest.main()

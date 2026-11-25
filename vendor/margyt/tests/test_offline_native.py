import tempfile
import unittest
from pathlib import Path

from margyt.dexpatch import rewrite_anchored, rewrite_offline_choices, rewrite_avatar_shapes


class OfflineNativeTest(unittest.TestCase):
    def test_download_count_is_not_reset_to_sixty(self):
        source = """.class public final LX/0wjq;
.super Ljava/lang/Object;
.method public static LIZ()I
    .registers 4
    invoke-interface {v1, v0}, Ljava/util/List;->contains(Ljava/lang/Object;)Z
    move-result v0
    return v0
.end method
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "0wjq.smali")
            path.write_text(source, encoding="utf-8")
            self.assertEqual(rewrite_offline_choices(folder).get("native offline count accepted"), 1)
            self.assertIn("OfflineChoices;->validCount", path.read_text(encoding="utf-8"))


    def test_safe_build_enables_offline_choices_without_other_offline_hooks(self):
        source = """.class public final LX/0wiL;
.super Ljava/lang/Object;
.method public final LIZLLL()LX/0ldB;
    .registers 11
    return-object v10
.end method
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "0wiL.smali")
            path.write_text(source, encoding="utf-8")
            self.assertEqual(rewrite_offline_choices(folder).get("native offline row"), 1)
            patched = path.read_text(encoding="utf-8")
            self.assertIn("OfflineChoices;->row", patched)
            self.assertNotIn("OfflineComments;", patched)

    def test_safe_avatar_profile_also_runs_offline_count_hooks(self):
        source = """.class public final Lkotlin/jvm/internal/AwS704S0100000_26;
.super Ljava/lang/Object;
.method public static final invoke$3208(Lkotlin/jvm/internal/AwS704S0100000_26;)Ljava/lang/Object;
    .registers 2
    invoke-static {}, LX/0wj1;->LJFF()Ljava/util/List;
    move-result-object v1
    return-object v1
.end method
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "AwS704S0100000_26.smali")
            path.write_text(source, encoding="utf-8")
            counts = rewrite_avatar_shapes(folder)
            self.assertEqual(counts.get("native offline choices"), 1)
            self.assertIn("OfflineChoices;->counts", path.read_text(encoding="utf-8"))

    def test_native_comments_keep_annotations_and_are_idempotent(self):
        owners = [
            ("Lcom/ss/android/ugc/aweme/comment/commentlist/api/CommentApi;", "public static LIZIZ", "LX/0Vgo;", "p0", "nativeTask"),
            ("LX/0oah;", "public static LIZIZ", "LX/19ZK;", "p0", "nativeObservable"),
            ("Lcom/ss/android/ugc/aweme/commentv2/commentlist/CommentApiV2;", "public fetchCommentListV2", "LX/19ZK;", "p1", "nativeObservable"),
            ("Lcom/ss/android/ugc/aweme/commentv2/commentlist/CommentApiV2SplitJson;", "public fetchCommentListV2", "LX/19ZK;", "p1", "nativeObservable"),
        ]
        for owner, method, result, param, target in owners:
            with self.subTest(owner=owner), tempfile.TemporaryDirectory() as folder:
                source = f""".class public final {owner}
.super Ljava/lang/Object;
.method {method}(Ljava/lang/String;){result}
    .registers 4
    .param {param}
        .annotation runtime Lfake/Query;
            value = "aweme_id"
        .end annotation
    .end param
    const/4 v0, 0x0
    return-object v0
.end method
"""
                path=Path(folder,"Api.smali");path.write_text(source,encoding="utf-8")
                self.assertEqual(rewrite_anchored(folder).get("native offline comments response"),1)
                patched=path.read_text(encoding="utf-8")
                self.assertIn(f"{{{param} .. {param}}}",patched)
                self.assertIn("OfflineComments;->"+target,patched)
                self.assertLess(patched.index(".end param"),patched.index("OfflineComments;->"))
                rewrite_anchored(folder)
                self.assertEqual(path.read_text(encoding="utf-8"),patched)

    def test_offline_counts_hook_survives_debug_lines(self):
        source = """.class public final Lkotlin/jvm/internal/AwS704S0100000_26;
.super Ljava/lang/Object;
.method public static final invoke$3208(Lkotlin/jvm/internal/AwS704S0100000_26;)Ljava/lang/Object;
    .registers 2
    invoke-static {}, LX/0wj1;->LJFF()Ljava/util/List;
    .line 16777217
    .line 16777218
    move-result-object v1
    return-object v1
.end method
"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "AwS704S0100000_26.smali")
            path.write_text(source, encoding="utf-8")
            self.assertEqual(rewrite_offline_choices(folder).get("native offline choices"), 1)
            self.assertIn("OfflineChoices;->counts", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

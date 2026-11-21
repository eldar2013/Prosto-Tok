"""Native heart pool reuse must not leave an invisible view or a stale flight."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_features_024_runtime import STUBS
from margyt import dexpatch

ROOT = pathlib.Path(__file__).resolve().parents[1]
HARNESS = r'''package cat.narezany.margyt;
import android.view.View;import android.widget.ImageView;import android.animation.ValueAnimator;
public class HeartReuseCheck{
 static void ck(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  ImageView heart=new ImageView();Heart.setImageDrawable(heart,new android.graphics.drawable.Drawable());
  FeedRail.like=new View();
  for(int i=0;i<40;i++){
   heart.attach();Heart.shown(heart);View.flush();
   ck(ValueAnimator.runs==i+1,"every pooled use starts a flight without assigning another drawable");
   ck(heart.getOverlay().count==0&&heart.visibility==View.INVISIBLE,"finished overlay removed; native heart stays hidden until release");
   heart.detach();ck(heart.visibility==0&&heart.listenerCount()==0&&heart.getTag(0x54544C49)==null,"native pool receives a clean visible view");
  }
  heart.attach();ValueAnimator.automatic=false;
  Heart.shown(heart);Heart.shown(heart);int before=ValueAnimator.runs;View.flush();
  ck(ValueAnimator.runs==before+1&&heart.getOverlay().count==1,"superseded delayed callback cannot start another flight");
  Heart.shown(heart);ck(heart.visibility==0&&heart.getOverlay().count==0&&ValueAnimator.active.isEmpty(),"reuse during flight cancels only the old overlay");
  View.flush();ck(heart.getOverlay().count==1,"reused heart flies again immediately");
  int bounces=FeedRail.like.animations;heart.detach();
  ck(heart.getOverlay().count==0&&ValueAnimator.active.isEmpty()&&heart.visibility==0,"detaching during flight clears the overlay and visibility");
  ck(FeedRail.like.animations==bounces,"cancelled flight does not bounce the button");
  heart.attach();Heart.shown(heart);heart.detach();before=ValueAnimator.runs;View.flush();
  ck(ValueAnimator.runs==before&&heart.visibility==0,"detaching before delay cannot hide a recycled heart later");
  heart.attach();FeedRail.like=null;Heart.shown(heart);View.flush();
  ck(heart.visibility==0&&heart.listenerCount()==0,"missing destination leaves the native animation visible");
  FeedRail.like=new View();Heart.shown(heart);View.flush();heart.visibility=8;heart.detach();
  ck(heart.visibility==8,"native GONE state is preserved");
  ck(heart.getOverlay().count==0&&heart.listenerCount()==0,"all resources released");
 }
}'''


class HeartReuseRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_pool_reuse_cancellation_and_missing_target(self):
        sources = dict(STUBS)
        sources['cat/narezany/margyt/Heart.java'] = (ROOT / 'inject/java/cat/narezany/margyt/Heart.java').read_text()
        sources['cat/narezany/margyt/HeartReuseCheck.java'] = HARNESS
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for name, content in sources.items():
                path = pathlib.Path(folder) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            built = subprocess.run(['javac', '-d', folder, *paths], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(['java', '-cp', folder, 'cat.narezany.margyt.HeartReuseCheck'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)

    def test_native_setup_hook_is_once_per_use_and_idempotent(self):
        body = '''.class public LX/0HCj;
.super Landroid/widget/FrameLayout;
.method public final LIZ(Landroid/widget/ImageView;FF)V
    .registers 8
    const/4 p1, 0x0
    return-void
.end method
.method public final LIZ(Landroid/widget/ImageView;)V
    .registers 2
    return-void
.end method
'''
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / 'HeartHost.smali'
            path.write_text(body)
            self.assertEqual(1, dexpatch.rewrite_anchored(folder)['native heart reuse'])
            patched = path.read_text()
            marker = 'Heart;->shown(Landroid/widget/ImageView;)V'
            self.assertEqual(1, patched.count(marker))
            self.assertLess(patched.index(marker), patched.index('const/4 p1'))
            self.assertEqual({}, dexpatch.rewrite_anchored(folder))
            self.assertEqual(patched, path.read_text())
            self.assertIn('Lcat/narezany/margyt/' + marker, dexpatch.rewrite_targets())

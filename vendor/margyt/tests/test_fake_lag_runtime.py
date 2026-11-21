"""Exercise the prank's timers, dialog choices and late frame-copy cleanup off-device."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    'android/R.java': '''package android;public class R{public static class style{
 public static int Theme_DeviceDefault_Light_Dialog_Alert=1,Theme_DeviceDefault_Dialog_Alert=2;}}''',
    'android/content/Context.java': '''package android.content;public class Context{
 public static int MODE_PRIVATE=0;public SharedPreferences getSharedPreferences(String s,int m){return SharedPreferences.ONE;}}''',
    'android/content/SharedPreferences.java': '''package android.content;public class SharedPreferences{
 public static final SharedPreferences ONE=new SharedPreferences();public boolean on;public int commits;
 public boolean getBoolean(String s,boolean fallback){return on;}public Editor edit(){return new Editor();}
 public class Editor{boolean value;public Editor putBoolean(String s,boolean b){value=b;return this;}
 public void apply(){on=value;}public boolean commit(){commits++;on=value;return true;}}}''',
    'android/content/DialogInterface.java': '''package android.content;public interface DialogInterface{
 interface OnClickListener{void onClick(DialogInterface d,int which);}}''',
    'android/app/Activity.java': '''package android.app;public class Activity extends android.content.Context{
 public boolean finishing,destroyed,focus=true,removed,affinity;public final android.view.Window window=new android.view.Window(this);
 public android.view.Window getWindow(){return window;}public boolean isFinishing(){return finishing;}
 public boolean isDestroyed(){return destroyed;}public boolean hasWindowFocus(){return focus;}
 public void finishAndRemoveTask(){removed=true;}public void finishAffinity(){affinity=true;}}''',
    'android/app/AlertDialog.java': '''package android.app;import android.content.*;public class AlertDialog implements DialogInterface{
 public static AlertDialog last;public static int shows;public boolean showing,cancelable,outside;
 public String title,message;public final java.util.Map<Integer,String> labels=new java.util.HashMap<>();
 public final java.util.Map<Integer,DialogInterface.OnClickListener> actions=new java.util.HashMap<>();
 public void show(){showing=true;last=this;shows++;}public void dismiss(){showing=false;}
 public void setCanceledOnTouchOutside(boolean b){outside=b;}public void click(int which){actions.get(which).onClick(this,which);dismiss();}
 public static class Builder{final AlertDialog d=new AlertDialog();public Builder(Activity a,int theme){}
 public Builder setTitle(String s){d.title=s;return this;}public Builder setMessage(String s){d.message=s;return this;}
 public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){d.labels.put(-2,s);d.actions.put(-2,l);return this;}
 public Builder setNeutralButton(String s,DialogInterface.OnClickListener l){d.labels.put(-3,s);d.actions.put(-3,l);return this;}
 public Builder setPositiveButton(String s,DialogInterface.OnClickListener l){d.labels.put(-1,s);d.actions.put(-1,l);return this;}
 public Builder setCancelable(boolean b){d.cancelable=b;return this;}public AlertDialog create(){return d;}}}''',
    'android/os/Looper.java': 'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
    'android/os/Build.java': 'package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}',
    'android/os/Process.java': 'package android.os;public class Process{public static int killed;public static int myPid(){return 42;}public static void killProcess(int pid){killed=pid;}}',
    'android/os/Handler.java': '''package android.os;public class Handler{
 static class Task{Handler owner;Runnable r;long at;Task(Handler h,Runnable run,long t){owner=h;r=run;at=t;}}
 static long now;static final java.util.List<Task> tasks=new java.util.ArrayList<>();public Handler(Looper l){}
 public boolean postDelayed(Runnable r,long delay){tasks.add(new Task(this,r,now+delay));return true;}
 public void removeCallbacks(Runnable r){tasks.removeIf(t->t.owner==this&&t.r==r);}
 public void removeCallbacksAndMessages(Object o){tasks.removeIf(t->t.owner==this);}
 public static int pending(){return tasks.size();}public static void advance(long delta){long end=now+delta;int count=0;
 while(true){Task first=null;for(Task t:tasks)if(t.at<=end&&(first==null||t.at<first.at))first=t;
 if(first==null)break;if(++count>10000)throw new AssertionError("timer loop");tasks.remove(first);now=first.at;first.r.run();}now=end;}}''',
    'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap{
 public enum Config{ARGB_8888}public static int created,recycled;int w,h;boolean dead;
 public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap();b.w=w;b.h=h;created++;return b;}
 public int getWidth(){return w;}public int getHeight(){return h;}
 public void recycle(){if(dead)throw new AssertionError("double recycle");dead=true;recycled++;}}''',
    'android/graphics/Canvas.java': 'package android.graphics;public class Canvas{public Canvas(Bitmap b){}public void scale(float x,float y){}}',
    'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable;public class Drawable{}',
    'android/view/View.java': '''package android.view;public class View{
 public static int GONE=8,VISIBLE=0;public int visibility;public ViewGroup parent;public boolean attached=true;
 public View(android.content.Context c){}public void setClickable(boolean b){}public void setElevation(float f){}
 public void setVisibility(int v){visibility=v;}public int getWidth(){return 1260;}public int getHeight(){return 2800;}
 public boolean isAttachedToWindow(){return attached;}public ViewGroup getParent(){return parent;}
 public void draw(android.graphics.Canvas c){}}''',
    'android/view/ViewGroup.java': '''package android.view;public class ViewGroup extends View{
 public final java.util.List<View> children=new java.util.ArrayList<>();public ViewGroup(android.content.Context c){super(c);}
 public void addView(View v,Object params){if(v.parent!=null)throw new AssertionError("duplicate child");children.add(v);v.parent=this;}
 public void removeView(View v){children.remove(v);v.parent=null;}}''',
    'android/view/Window.java': '''package android.view;public class Window{
 public final android.widget.FrameLayout decor;public Window(android.content.Context c){decor=new android.widget.FrameLayout(c);}
 public View getDecorView(){return decor;}}''',
    'android/view/PixelCopy.java': '''package android.view;public class PixelCopy{
 public static int SUCCESS=0,result=0;public static boolean auto=true;public static OnPixelCopyFinishedListener pending;
 public interface OnPixelCopyFinishedListener{void onPixelCopyFinished(int result);}
 public static void request(Window w,android.graphics.Bitmap b,OnPixelCopyFinishedListener l,android.os.Handler h){
 if(auto)h.postDelayed(()->l.onPixelCopyFinished(result),2);else pending=l;}
 public static void finish(){OnPixelCopyFinishedListener l=pending;pending=null;l.onPixelCopyFinished(result);}}''',
    'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.ViewGroup{
 public FrameLayout(android.content.Context c){super(c);}public static class LayoutParams{public LayoutParams(int w,int h){}}}''',
    'android/widget/ImageView.java': '''package android.widget;public class ImageView extends android.view.View{
 public enum ScaleType{FIT_XY}public android.graphics.Bitmap bitmap;public ImageView(android.content.Context c){super(c);}
 public void setScaleType(ScaleType s){}public void setImageBitmap(android.graphics.Bitmap b){bitmap=b;}
 public void setImageDrawable(android.graphics.drawable.Drawable d){bitmap=null;}}''',
    'cat/narezany/margyt/Margy.java': 'package cat.narezany.margyt;class Margy{static String PREFS="prefs";}',
    'cat/narezany/margyt/Watch.java': 'package cat.narezany.margyt;class Watch{static android.app.Activity current;static android.app.Activity here(){return current;}}',
    'cat/narezany/margyt/SettingsActivity.java': 'package cat.narezany.margyt;class SettingsActivity extends android.app.Activity{}',
    'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt;class Diary{static void note(String s){throw new AssertionError(s);}}',
    'cat/narezany/margyt/NavBar.java': 'package cat.narezany.margyt;class NavBar{static float bright(int c){return 0;}}',
    'cat/narezany/margyt/Md3.java': 'package cat.narezany.margyt;class Md3{static int behind(android.view.View v){return 0;}}',
    'cat/narezany/margyt/Text.java': '''package cat.narezany.margyt;class Text{static String LAG_TITLE="Not responding",LAG_QUESTION="Close?",
 LAG_CLOSE="Close",LAG_WAIT="Wait",LAG_FIX="Fix";}''',
    'cat/narezany/margyt/Check.java': '''package cat.narezany.margyt;
import android.app.*;import android.os.Handler;import android.graphics.Bitmap;import android.view.*;import android.widget.*;
public class Check{
 static void ck(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static void resume(Activity a){Watch.current=a;FakeLag.resumed(a);}
 static ImageView cover(Activity a){return (ImageView)a.window.decor.children.get(0);}
 public static void main(String[] args){
  Activity a=new Activity();resume(a);ck(!FakeLag.isOn(a)&&a.window.decor.children.isEmpty()&&Handler.pending()==0,"off by default, no idle work");
  SettingsActivity settings=new SettingsActivity();resume(settings);FakeLag.set(settings,true);Handler.advance(20000);
  ck(settings.window.decor.children.isEmpty()&&AlertDialog.shows==0,"settings remain usable while the prank is armed");
  resume(a);FakeLag.resumed(a);ck(a.window.decor.children.size()==1,"one cover per resumed activity");
  Handler.advance(605);ck(cover(a).visibility==View.VISIBLE,"screen freezes with a captured frame");
  ck(cover(a).bitmap.getWidth()<=1280&&cover(a).bitmap.getHeight()<=1280,"bitmap allocation is bounded");
  Handler.advance(7000);AlertDialog d=AlertDialog.last;
  ck(d.showing&&!d.cancelable&&!d.outside&&d.actions.size()==3,"native modal has all three choices");
  int count=AlertDialog.shows;d.click(-3);ck(FakeLag.isOn(a),"wait preserves the switch");
  Handler.advance(10000);ck(AlertDialog.shows==count+1&&AlertDialog.last.showing,"wait schedules another freeze and dialog");
  AlertDialog.last.click(-1);ck(!FakeLag.isOn(a)&&a.window.decor.children.isEmpty(),"fix immediately disables and removes the frozen frame");
  Handler.advance(20000);ck(Handler.pending()==0&&Bitmap.created==Bitmap.recycled,"fix cancels timers and releases its bitmap");
  FakeLag.set(a,true);Handler.advance(605);FakeLag.paused(a);
  ck(a.window.decor.children.isEmpty(),"backgrounding removes the overlay");Handler.advance(20000);
  ck(Handler.pending()==0&&!AlertDialog.last.showing,"nothing runs or opens over other apps");
  resume(a);PixelCopy.auto=false;Handler.advance(600);int recycled=Bitmap.recycled;FakeLag.paused(a);
  ck(Bitmap.recycled==recycled,"in-flight bitmap remains valid for PixelCopy");
  resume(a);PixelCopy.finish();ck(Bitmap.recycled==recycled+1&&cover(a).visibility==View.GONE,"late copy releases old bitmap without touching the new screen");
  PixelCopy.auto=true;FakeLag.set(a,false);ck(a.window.decor.children.isEmpty(),"switch off also clears a pending session");
  PixelCopy.auto=false;FakeLag.set(a,true);Handler.advance(8000);PixelCopy.OnPixelCopyFinishedListener pending=PixelCopy.pending;
  ck(AlertDialog.last.showing,"dialog remains available even during a slow copy");AlertDialog.last.click(-3);Handler.advance(2000);
  ck(PixelCopy.pending==pending,"waiting never starts a concurrent copy into the same bitmap");
  FakeLag.set(a,false);PixelCopy.finish();PixelCopy.auto=true;
  PixelCopy.result=1;FakeLag.set(a,true);Handler.advance(605);
  ck(cover(a).visibility==View.VISIBLE,"view capture fallback works when window copy fails");
  FakeLag.set(a,false);PixelCopy.result=0;FakeLag.set(a,true);a.focus=false;count=AlertDialog.shows;Handler.advance(10000);
  ck(AlertDialog.shows==count&&cover(a).visibility==View.GONE,"loss of window focus delays freezes and dialog");
  a.focus=true;Handler.advance(1000);ck(AlertDialog.last.showing,"dialog resumes after focus returns");
  AlertDialog.last.click(-2);ck(a.removed&&a.affinity&&android.os.Process.killed==42,"close removes the task and really terminates the process");
  ck(!FakeLag.isOn(a)&&android.content.SharedPreferences.ONE.commits==1,"close durably disables the prank before termination");
  Handler.advance(20000);ck(a.window.decor.children.isEmpty()&&Handler.pending()==0&&Bitmap.created==Bitmap.recycled,"close and late callbacks leave no resources");
  resume(a);ck(a.window.decor.children.isEmpty(),"reopening stays usable after close");
 }
}''',
}


class FakeLagRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_choices_lifecycle_and_late_copy(self):
        sources = dict(STUBS)
        sources['cat/narezany/margyt/FakeLag.java'] = (
            ROOT / 'inject/java/cat/narezany/margyt/FakeLag.java').read_text()
        with tempfile.TemporaryDirectory() as directory:
            files = []
            for name, content in sources.items():
                path = pathlib.Path(directory, name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))
            result = subprocess.run(['javac', '-d', directory, *files], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', directory, 'cat.narezany.margyt.Check'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

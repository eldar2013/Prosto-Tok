"""Exercise legacy profile reparenting and restoration with the production Java class."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    "android/util/DisplayMetrics.java": "package android.util; public class DisplayMetrics {public float density=1;}",
    "android/util/TypedValue.java": "package android.util; public class TypedValue {public static final int COMPLEX_UNIT_PX=0,COMPLEX_UNIT_SP=2;}",
    "android/content/res/Resources.java": """package android.content.res;
public class Resources {
 public static java.util.Map<String,Integer> ids=new java.util.HashMap<>();
 public int getIdentifier(String n,String k,String p){return ids.getOrDefault(n,0);}
 public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}
}""",
    "android/content/Context.java": """package android.content; public class Context {
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public String getPackageName(){return "test";}
}""",
    "android/view/Gravity.java": "package android.view; public class Gravity {public static final int START=3,CENTER_VERTICAL=16;}",
    "android/view/View.java": """package android.view;
public class View {
 private final android.content.Context context; public ViewGroup parent;
 private ViewGroup.LayoutParams params=new ViewGroup.LayoutParams(-2,-2);
 private int id=-1; public float alpha=1;
 private final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
 public View(android.content.Context c){context=c;}
 public android.content.Context getContext(){return context;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public void setId(int v){id=v;} public int getId(){return id;}
 public View findViewById(int n){return n==id?this:null;}
 public ViewGroup getParent(){return parent;}
 public Object getTag(int n){return tags.get(n);} public void setTag(int n,Object v){tags.put(n,v);}
 public ViewGroup.LayoutParams getLayoutParams(){return params;}
 public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}
 public void setAlpha(float a){alpha=a;}
}""",
    "android/view/ViewGroup.java": """package android.view;
public class ViewGroup extends View {
 public static class LayoutParams {
  public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;public int width,height;
  public LayoutParams(int w,int h){width=w;height=h;}
  public LayoutParams(LayoutParams p){this(p.width,p.height);}
 }
 private final java.util.List<View> children=new java.util.ArrayList<>();
 public ViewGroup(android.content.Context c){super(c);}
 public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);}
 public int indexOfChild(View v){return children.indexOf(v);}
 public void removeView(View v){if(children.remove(v))v.parent=null;}
 public void addView(View v){addView(v,children.size(),v.getLayoutParams());}
 public void addView(View v,int i){addView(v,i,v.getLayoutParams());}
 public void addView(View v,LayoutParams p){addView(v,children.size(),p);}
 public void addView(View v,int i,LayoutParams p){if(v.parent!=null)throw new IllegalStateException("already attached");children.add(i,v);v.parent=this;v.setLayoutParams(p);}
 public void setClipChildren(boolean b){} public void setClipToPadding(boolean b){}
 @Override public View findViewById(int n){View own=super.findViewById(n);if(own!=null)return own;for(View v:children){View r=v.findViewById(n);if(r!=null)return r;}return null;}
}""",
    "android/widget/LinearLayout.java": """package android.widget;
public class LinearLayout extends android.view.ViewGroup {
 public static final int HORIZONTAL=0,VERTICAL=1;private int gravity;
 public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
  public float weight;public int leftMargin,rightMargin,topMargin,bottomMargin;
  public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float x){super(w,h);weight=x;}
  public LayoutParams(LayoutParams p){super(p);weight=p.weight;leftMargin=p.leftMargin;rightMargin=p.rightMargin;topMargin=p.topMargin;bottomMargin=p.bottomMargin;}
  public void setMargins(int l,int t,int r,int b){leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;}
 }
 public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int n){}
 public void setGravity(int n){gravity=n;}public int getGravity(){return gravity;}
}""",
    "android/widget/TextView.java": """package android.widget;
public class TextView extends android.view.View {
 private int gravity=17,maxLines=4;private float size=22;
 public TextView(android.content.Context c){super(c);}
 public int getGravity(){return gravity;}public void setGravity(int n){gravity=n;}
 public float getTextSize(){return size;}public void setTextSize(int unit,float s){size=s;}
 public int getMaxLines(){return maxLines;}public void setMaxLines(int n){maxLines=n;}
}""",
    "cat/narezany/margyt/Check.java": """package cat.narezany.margyt;
import android.view.*;import android.widget.*;import android.content.Context;
public class Check {
 static Context context=new Context();static int seq=1;
 static int id(String n){return android.content.res.Resources.ids.computeIfAbsent(n,k->seq++);}
 static View view(String n){View v=new View(context);v.setId(id(n));return v;}
 static LinearLayout row(){return new LinearLayout(context);}
 static TextView text(String n){TextView v=new TextView(context);v.setId(id(n));v.setLayoutParams(new LinearLayout.LayoutParams(100,-2));return v;}
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 public static void main(String[] args){
  int modern=id("t58");
  LinearLayout column=row(),avatar=row(),nativeInfo=row(),nameLine=row(),stats=row();
  avatar.addView(view("bms"));avatar.setLayoutParams(new LinearLayout.LayoutParams(128,128));
  View bio=view("bio"),highlights=view("highlights");
  LinearLayout nativeName=row();nativeName.setId(id("t9e"));nativeName.addView(text("t75"));
  View pencil=view("t9f");nameLine.addView(nativeName);nameLine.addView(pencil);
  TextView handle=text("t9_");nativeInfo.addView(nameLine);nativeInfo.addView(handle);nativeInfo.addView(stats);
  TextView[] numbers=new TextView[3],labels=new TextView[3];View[] cells=new View[3];
  for(int i=0;i<3;i++){LinearLayout cell=row();numbers[i]=text("t7t");labels[i]=text("t7s");cell.addView(numbers[i]);cell.addView(labels[i]);stats.addView(cell);cells[i]=cell;}
  column.addView(avatar);column.addView(bio);column.addView(highlights);column.addView(nativeInfo);
  ViewGroup.LayoutParams avatarParams=avatar.getLayoutParams(),infoParams=nativeInfo.getLayoutParams(),textParams=numbers[0].getLayoutParams();
  ProfileHead.prepare(column,modern);
  View header=column.getChildAt(0);check(ProfileHead.isHeader(header),"legacy components share a header");
  check(((ViewGroup)header).getChildAt(0)==avatar,"native avatar is on the left");
  check(ProfileHead.edit(column)==pencil&&ProfileHead.handle(column)==handle,"native controls retained");
  ViewGroup newStats=cells[0].getParent();check(newStats!=stats&&newStats.getChildCount()==3,"all three counters get a new row");
  for(View v:cells){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();check(p.width==0&&p.weight==1,"equal width counters");}
  check(numbers[0].getTextSize()==19,"counter size fits the compact header");
  ProfileHead.prepare(column,modern);check(column.getChildAt(0)==header&&column.getChildCount()==3,"repeat layout adds nothing");
  ProfileHead.restore(column);
  check(column.getChildCount()==4&&column.getChildAt(0)==avatar&&column.getChildAt(1)==bio&&column.getChildAt(2)==highlights&&column.getChildAt(3)==nativeInfo,"original component order restored");
  check(avatar.getLayoutParams()==avatarParams&&nativeInfo.getLayoutParams()==infoParams,"original native layout params restored");
  check(numbers[0].getLayoutParams()==textParams&&numbers[0].getTextSize()==22&&numbers[0].getMaxLines()==4,"text styles restored");
  check(nameLine.getChildAt(0)==nativeName&&nameLine.getChildAt(1)==pencil&&handle.getParent()==nativeInfo,"name, handle and edit parents restored");
  check(stats.getChildCount()==3&&stats.getChildAt(0)==cells[0]&&stats.getChildAt(2)==cells[2],"counter order restored");
  ProfileHead.prepare(column,modern);ProfileHead.restore(column);check(column.getChildCount()==4,"on/off cycle leaves no extra views");
  LinearLayout incomplete=row();column.removeView(avatar);incomplete.addView(avatar);ProfileHead.prepare(incomplete,modern);check(incomplete.getChildAt(0)==avatar,"wait for missing account info");
 }
}""",
}


class ProfileHeadTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_native_components_and_restoration(self):
        sources = dict(STUBS)
        sources["cat/narezany/margyt/ProfileHead.java"] = (ROOT / "inject/java/cat/narezany/margyt/ProfileHead.java").read_text()
        with tempfile.TemporaryDirectory() as folder:
            files = []
            for name, content in sources.items():
                path = pathlib.Path(folder) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))
            result = subprocess.run(["javac", "-d", folder, *files], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run(["java", "-cp", folder, "cat.narezany.margyt.Check"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)

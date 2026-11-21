"""Profile updates retain native row indices, late bio blocks, and native ownership."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_profile_head_runtime import STUBS as HEAD_STUBS

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = dict(HEAD_STUBS)
STUBS.update({
    'android/R.java': 'package android;public class R{public static class id{public static int mask=99;}}',
    'android/os/Build.java': 'package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}',
    'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long uptimeMillis(){return 1000;}}',
    'android/util/Log.java': 'package android.util;public class Log{public static int e(String a,String b,Throwable t){throw new AssertionError(t);}}',
    'android/content/res/ColorStateList.java': 'package android.content.res;public class ColorStateList{int colour;public ColorStateList(int c){colour=c;}public int getDefaultColor(){return colour;}}',
    'android/content/Context.java': '''package android.content;public class Context{
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public String getPackageName(){return "test";}public void startActivity(Intent i){}
}''',
    'android/content/Intent.java': '''package android.content;public class Intent{
 public static String ACTION_SEND="send",EXTRA_TEXT="text";public static int FLAG_ACTIVITY_NEW_TASK=1;
 public Intent(String s){}public Intent setType(String s){return this;}public Intent putExtra(String a,String b){return this;}
 public static Intent createChooser(Intent i,String s){return i;}public Intent addFlags(int n){return this;}
}''',
    'android/graphics/Typeface.java': 'package android.graphics;public class Typeface{public static int NORMAL=0;public static Typeface create(String s,int n){return new Typeface();}}',
    'android/graphics/PorterDuff.java': 'package android.graphics;public class PorterDuff{public enum Mode{SRC_IN}}',
    'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;public class Drawable{
 public static class ConstantState{public Drawable newDrawable(){return new Drawable();}}
 public ConstantState getConstantState(){return new ConstantState();}public Drawable mutate(){return this;}
}''',
    'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable;public class GradientDrawable extends Drawable{
 public int fill;public void setColor(int c){fill=c;}public void setCornerRadius(float r){}public void setCornerRadii(float[] r){}
 public android.content.res.ColorStateList getColor(){return new android.content.res.ColorStateList(fill);}
}''',
    'android/graphics/drawable/LayerDrawable.java': '''package android.graphics.drawable;public class LayerDrawable extends Drawable{
 protected Drawable[] layers;public LayerDrawable(Drawable[] d){layers=d;}public int getNumberOfLayers(){return layers.length;}
 public Drawable getDrawable(int i){return layers[i];}public int getId(int i){return i==1?android.R.id.mask:0;}
}''',
    'android/graphics/drawable/InsetDrawable.java': 'package android.graphics.drawable;public class InsetDrawable extends Drawable{public Drawable getDrawable(){return null;}}',
    'android/graphics/drawable/RippleDrawable.java': 'package android.graphics.drawable;public class RippleDrawable extends LayerDrawable{public RippleDrawable(Drawable d){super(new Drawable[]{d,new GradientDrawable()});}}',
    'android/view/Gravity.java': 'package android.view;public class Gravity{public static int START=3,CENTER=17,CENTER_VERTICAL=16;}',
    'android/view/MotionEvent.java': '''package android.view;public class MotionEvent{public static int ACTION_DOWN=0,ACTION_UP=1;
 public static MotionEvent obtain(long a,long b,int c,float x,float y,int d){return new MotionEvent();}public void recycle(){}
}''',
    'android/view/View.java': '''package android.view;public class View{
 public static final int VISIBLE=0,GONE=8,NO_ID=-1;public int detaches;
 private final android.content.Context context;public ViewGroup parent;private int id=-1,visibility=0;
 private ViewGroup.LayoutParams params=new ViewGroup.LayoutParams(-2,-2);
 private final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
 private android.graphics.drawable.Drawable background;private boolean clip;private int l,t,r,b;public float alpha=1;
 private CharSequence description;private OnClickListener click;
 public interface OnClickListener{void onClick(View v);}public void setOnClickListener(OnClickListener c){click=c;}
 public boolean performClick(){if(click==null)return false;click.onClick(this);return true;}public boolean isClickable(){return click!=null;}
 public boolean hasOnClickListeners(){return click!=null;}
 public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public void setId(int n){id=n;}public int getId(){return id;}public View findViewById(int n){return n==id?this:null;}
 public ViewGroup getParent(){return parent;}public Object getTag(int k){return tags.get(k);}public void setTag(int k,Object v){tags.put(k,v);}
 public ViewGroup.LayoutParams getLayoutParams(){return params;}public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}
 public View getRootView(){return parent==null?this:parent.getRootView();}public boolean isAttachedToWindow(){return true;}
 public boolean isShown(){return visibility==0&&(parent==null||parent.isShown());}public int getVisibility(){return visibility;}public void setVisibility(int n){visibility=n;}
 public void setAlpha(float a){alpha=a;}public void setFocusable(boolean b){}public int getHeight(){return params.height>0?params.height:30;}
 public int getWidth(){return params.width>0?params.width:400;}public void getLocationInWindow(int[] p){}
 public boolean dispatchTouchEvent(MotionEvent e){return true;}
 public android.graphics.drawable.Drawable getBackground(){return background;}public void setBackground(android.graphics.drawable.Drawable b){background=b;}
 public void setClipToOutline(boolean b){clip=b;}public boolean getClipToOutline(){return clip;}
 public void setPadding(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
 public int getPaddingLeft(){return l;}public int getPaddingRight(){return r;}public int getPaddingTop(){return t;}public int getPaddingBottom(){return b;}
 public void setContentDescription(CharSequence s){description=s;}public CharSequence getContentDescription(){return description;}
}''',
    'android/view/ViewGroup.java': '''package android.view;public class ViewGroup extends View{
 public static class LayoutParams{public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;public int width,height;
 public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(LayoutParams p){this(p.width,p.height);}}
 public static class MarginLayoutParams extends LayoutParams{public int leftMargin,topMargin,rightMargin,bottomMargin;
 public MarginLayoutParams(int w,int h){super(w,h);}public MarginLayoutParams(LayoutParams p){super(p);}}
 private final java.util.List<View> children=new java.util.ArrayList<>();public ViewGroup(android.content.Context c){super(c);}
 public int getChildCount(){return children.size();}public View getChildAt(int n){return n>=0&&n<children.size()?children.get(n):null;}
 public int indexOfChild(View v){return children.indexOf(v);}public void addView(View v){addView(v,children.size(),v.getLayoutParams());}
 public void addView(View v,int n){addView(v,n,v.getLayoutParams());}public void addView(View v,LayoutParams p){addView(v,children.size(),p);}
 public void addView(View v,int n,LayoutParams p){if(v.parent!=null)throw new AssertionError("already attached");children.add(n,v);v.parent=this;v.setLayoutParams(p);}
 public void removeView(View v){if(children.remove(v)){v.parent=null;v.detaches++;}}public void removeViewAt(int n){removeView(children.get(n));}
 public void removeAllViews(){while(!children.isEmpty())removeViewAt(children.size()-1);}
 public void bringChildToFront(View v){if(children.remove(v))children.add(v);}
 public void setClipChildren(boolean b){}public void setClipToPadding(boolean b){}
 @Override public View findViewById(int n){View own=super.findViewById(n);if(own!=null)return own;for(View v:children){View found=v.findViewById(n);if(found!=null)return found;}return null;}
}''',
    'android/widget/LinearLayout.java': '''package android.widget;public class LinearLayout extends android.view.ViewGroup{
 public static final int HORIZONTAL=0,VERTICAL=1;private int orientation,gravity;
 public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{public float weight;public int gravity;
 public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float f){super(w,h);weight=f;}
 public LayoutParams(LayoutParams p){super(p);weight=p.weight;gravity=p.gravity;}public void setMargins(int l,int t,int r,int b){leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;}}
 public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int n){orientation=n;}public int getOrientation(){return orientation;}
 public void setGravity(int n){gravity=n;}public int getGravity(){return gravity;}
}''',
    'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.ViewGroup{
 public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{public LayoutParams(int w,int h){super(w,h);}}
 public FrameLayout(android.content.Context c){super(c);}
}''',
    'android/widget/HorizontalScrollView.java': '''package android.widget;public class HorizontalScrollView extends FrameLayout{
 public HorizontalScrollView(android.content.Context c){super(c);}public void setHorizontalScrollBarEnabled(boolean b){}
}''',
    'android/widget/TextView.java': '''package android.widget;public class TextView extends android.view.View{
 private int gravity=17,max=4;private float size=22;private CharSequence text="";public int ink;
 public TextView(android.content.Context c){super(c);}public void setText(CharSequence t){text=t;}public CharSequence getText(){return text;}
 public void setTextColor(int c){ink=c;}public void setTextSize(int u,float s){size=s;}public float getTextSize(){return size;}
 public void setTypeface(android.graphics.Typeface t){}public void setSingleLine(boolean b){}
 public int getGravity(){return gravity;}public void setGravity(int n){gravity=n;}public int getMaxLines(){return max;}public void setMaxLines(int n){max=n;}
}''',
    'android/widget/ImageView.java': '''package android.widget;public class ImageView extends android.view.View{
 public enum ScaleType{FIT_CENTER}private android.graphics.drawable.Drawable image;
 public ImageView(android.content.Context c){super(c);}public void setImageDrawable(android.graphics.drawable.Drawable d){image=d;}
 public android.graphics.drawable.Drawable getDrawable(){return image;}public void setScaleType(ScaleType s){}public void setColorFilter(int c,android.graphics.PorterDuff.Mode m){}
}''',
    'cat/narezany/margyt/Md3.java': '''package cat.narezany.margyt;class Md3{static int page=0xfff2edf7;
 static int behind(android.view.View v){return page;}static int ink(int fill){return NavBar.bright(fill)<.5f?-1:0xff161823;}}
''',
    'cat/narezany/margyt/NavBar.java': '''package cat.narezany.margyt;class NavBar{
 static float bright(int c){return (((c>>16)&255)*299+((c>>8)&255)*587+(c&255)*114)/255000f;}
 static int mix(int a,int b,float f){int c=0xff000000;for(int shift=0;shift<=16;shift+=8)c|=Math.round(((a>>shift)&255)*(1-f)+((b>>shift)&255)*f)<<shift;return c;}}
''',
    'cat/narezany/margyt/Accent.java': 'package cat.narezany.margyt;class Accent{static int colour(){return 0xff978fc3;}}',
    'cat/narezany/margyt/Fonts.java': 'package cat.narezany.margyt;class Fonts{static void apply(android.widget.TextView v){}}',
    'cat/narezany/margyt/Motion.java': 'package cat.narezany.margyt;class Motion{static void press(android.view.View v){v.setBackground(new android.graphics.drawable.RippleDrawable(v.getBackground()));}}',
    'cat/narezany/margyt/Local.java': 'package cat.narezany.margyt;class Local{static String of(String s){return s;}}',
    'cat/narezany/margyt/SettingsGlyph.java': 'package cat.narezany.margyt;class SettingsGlyph{static android.graphics.drawable.Drawable make(String s,int c){return new android.graphics.drawable.Drawable();}}',
    'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt;class Diary{static void note(String s){throw new AssertionError(s);}static void detail(String s){throw new AssertionError(s);}}',
    'cat/narezany/margyt/Popup.java': 'package cat.narezany.margyt;class Popup{static String title;static void show(android.view.View v,String t,String b,String k,android.graphics.drawable.Drawable d){title=t;}}',
    'cat/narezany/margyt/ProfileVerdict.java': 'package cat.narezany.margyt;class ProfileVerdict{static boolean plate(android.view.View v){return v.getTag(42)!=null;}}',
})
for name in ['bio.container.ProfileHeaderBioComponent', 'advancedfeature.assemble.ProfileHeaderAdvancedFeatureComponent']:
    full = 'com.ss.android.ugc.profile.platform.business.header.business.' + name
    package, simple = full.rsplit('.', 1)
    STUBS[full.replace('.', '/') + '.java'] = f'package {package};public class {simple}{{}}'

STUBS['cat/narezany/margyt/Check.java'] = '''package cat.narezany.margyt;
import android.view.*;import android.widget.*;import android.graphics.drawable.*;
public class Check{
 static android.content.Context c=new android.content.Context();
 static void ck(boolean b,String why){if(!b)throw new AssertionError(why);}
 static LinearLayout row(){return new LinearLayout(c);}static TextView text(String s){TextView t=new TextView(c);t.setText(s);return t;}
 static void refresh(LinearLayout column){ProfileMd3.column(column,true,10,14,15,16,17);}
 static Object bioOwner(){return new com.ss.android.ugc.profile.platform.business.header.business.bio.container.ProfileHeaderBioComponent();}
 static Object toolsOwner(){return new com.ss.android.ugc.profile.platform.business.header.business.advancedfeature.assemble.ProfileHeaderAdvancedFeatureComponent();}
 static int fill(View v){return ((GradientDrawable)((LayerDrawable)v.getBackground()).getDrawable(0)).fill;}
 public static void main(String[] args){
  LinearLayout column=row();column.setOrientation(1);LinearLayout head=row();head.setId(10);column.addView(head);
  View edit=new View(c);edit.setId(14);head.addView(edit);TextView handle=text("@account");handle.setId(15);head.addView(handle);
  HorizontalScrollView tools=new HorizontalScrollView(c);tools.setLayoutParams(new LinearLayout.LayoutParams(360,-2));
  LinearLayout nativeRow=row();ViewGroup.LayoutParams nativeParams=new FrameLayout.LayoutParams(-1,-2);tools.addView(nativeRow,nativeParams);column.addView(tools);
  ProfileParts.attaching(toolsOwner(),new View(c),nativeRow);
  TextView recommended=text("Recommended"),frame=text("Frame");recommended.setTag(42,true);frame.setTag(42,true);column.addView(recommended);column.addView(frame);
  refresh(column);LinearLayout mods=ProfileChips.peek(tools);
  ck(mods!=null&&mods.getChildCount()==2,"mod chips exist before native balance arrives");
  ck(nativeRow.getChildCount()==0,"mod chips never enter TikTok's indexed row");
  TextView balance=text("Balance");balance.setOnClickListener(v->v.setTag(43,true));nativeRow.addView(balance);
  refresh(column);ck(nativeRow.getChildCount()==1&&nativeRow.getChildAt(0)==balance,"late balance retains native index zero");
  balance.performClick();ck(Boolean.TRUE.equals(balance.getTag(43)),"native balance handler survives");
  LinearLayout bio=row();bio.setOrientation(1);bio.setLayoutParams(new LinearLayout.LayoutParams(270,-2));bio.addView(text("Description, without a college button"));
  ck(ProfileParts.find(column,ProfileParts.BIO)==null,"bio can be absent during first pass");
  ProfileParts.attaching(bioOwner(),bio.getChildAt(0),bio);column.addView(bio);
  refresh(column);ck(ProfileParts.find(column,ProfileParts.BIO)==bio,"late native bio found without optional college");
  ck(bio.getBackground()!=null&&column.indexOfChild(bio)<column.indexOfChild(tools),"bio styled above the chips");
  ck(bio.getLayoutParams().width==-1&&tools.getLayoutParams().width==-1,"both card halves fill the column");
  ck(bio.detaches==0&&tools.detaches==0,"reordering never detaches native blocks");
  for(int i=0;i<6;i++)refresh(column);
  ck(ProfileChips.peek(tools)==mods&&mods.getChildCount()==2,"repeated layout does not duplicate chips");
  ck(nativeRow.getChildAt(0)==balance&&nativeRow.getChildCount()==1,"repeated layout preserves native children");
  nativeRow.removeAllViews();TextView replacement=text("Balance refreshed");nativeRow.addView(replacement);refresh(column);
  ck(nativeRow.getChildAt(0)==replacement&&nativeRow.getChildCount()==1,"native rebuild cannot delete or shift mod chips");
  ck(ProfileChips.peek(tools)==mods&&mods.getChildCount()==2,"mod row survives native rebuild");
  mods.getChildAt(0).performClick();ck("Recommended".equals(Popup.title),"chip opens its detail window");
  int[] adminOpens={0};frame.setText("Админка");frame.setOnClickListener(v->adminOpens[0]++);Popup.title=null;
  mods.getChildAt(1).performClick();ck(adminOpens[0]==1&&Popup.title==null,"admin chip invokes its original action instead of an information popup");
  recommended.setOnClickListener(v->adminOpens[0]++);
  mods.getChildAt(0).performClick();ck(adminOpens[0]==2&&Popup.title==null,"admin action also survives on a marked profile");
  recommended.setOnClickListener(null);mods.getChildAt(0).performClick();
  ck("Recommended".equals(Popup.title),"ordinary recommendations still open details after action changes");
  View actions=column.getChildAt(1);ck(actions instanceof ViewGroup,"own profile action row exists");
  Md3.page=0xff19191e;ProfileMd3.refresh();int darkFill=fill(((ViewGroup)actions).getChildAt(1));
  Md3.page=0xfff2edf7;ProfileMd3.refresh();int lightFill=fill(((ViewGroup)actions).getChildAt(1));
  ck(NavBar.bright(darkFill)<.5f&&NavBar.bright(lightFill)>.5f,"ripple-wrapped button follows the actual page theme");
  LayerDrawable ripple=(LayerDrawable)((ViewGroup)actions).getChildAt(1).getBackground();ck(((GradientDrawable)ripple.getDrawable(1)).fill==0,"ripple mask is unchanged");
  tools.setVisibility(View.GONE);refresh(column);HorizontalScrollView own=null;
  for(int i=0;i<column.getChildCount();i++)if(ProfileChips.own(column.getChildAt(i)))own=(HorizontalScrollView)column.getChildAt(i);
  ck(own!=null&&ProfileChips.peek(own).getChildCount()==2,"own chips remain when native tools are unavailable");
  ck(tools.getChildAt(0)==nativeRow&&tools.getVisibility()==View.GONE,"native visibility remains under TikTok's control");
  tools.setVisibility(View.VISIBLE);refresh(column);
  for(int i=0;i<column.getChildCount();i++)ck(!ProfileChips.own(column.getChildAt(i)),"late visible native strip replaces fallback once");
  ProfileMd3.column(column,false,10,14,15,16,17);
  ck(tools.getChildAt(0)==nativeRow&&nativeRow.getLayoutParams()==nativeParams,"native hierarchy and params restored");
  ck(nativeRow.getChildAt(0)==replacement&&nativeRow.getChildCount()==1,"restoration preserves latest server children");
  ck(bio.getBackground()==null&&bio.getLayoutParams().width==270&&tools.getLayoutParams().width==360,"original card appearance restored");
  refresh(column);column.removeView(bio);refresh(column);ProfileMd3.column(column,false,10,14,15,16,17);
  ck(bio.getParent()==null,"removed native bio is never resurrected");
  // An extra native scroller is retained even when the main strip is recognized.
  HorizontalScrollView extra=new HorizontalScrollView(c);extra.addView(row());column.addView(extra);refresh(column);
  ck(extra.getParent()==column,"additional native strips are not dropped");
 }
}'''


class ProfileMd3RuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_late_components_native_indices_themes_and_restoration(self):
        sources = dict(STUBS)
        for name in ['ProfileHead', 'ProfileMd3', 'ProfileParts', 'ProfileChips']:
            sources[f'cat/narezany/margyt/{name}.java'] = (
                ROOT / f'inject/java/cat/narezany/margyt/{name}.java').read_text()
        with tempfile.TemporaryDirectory() as directory:
            files = []
            for name, content in sources.items():
                path = pathlib.Path(directory) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))
            result = subprocess.run(['javac', '-d', directory, *files], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', directory, 'cat.narezany.margyt.Check'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

"""Exercise the inbox with native rows arriving, rebinding and leaving the screen."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_profile_md3_runtime import STUBS as PROFILE_STUBS

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = dict(PROFILE_STUBS)
STUBS.pop('cat/narezany/margyt/Check.java')
STUBS['android/util/DisplayMetrics.java'] = 'package android.util;public class DisplayMetrics{public float density=1,scaledDensity=1;}'
STUBS['android/R.java'] = 'package android;public class R{public static class id{public static int mask=99,content=100;}}'
STUBS['android/graphics/Color.java'] = 'package android.graphics;public class Color{public static int WHITE=-1,TRANSPARENT=0;}'
STUBS['android/content/res/ColorStateList.java'] = 'package android.content.res;public class ColorStateList{int colour;public ColorStateList(int c){colour=c;}public int getDefaultColor(){return colour;}public static ColorStateList valueOf(int c){return new ColorStateList(c);}}'
STUBS['android/graphics/Typeface.java'] = 'package android.graphics;public class Typeface{public static int NORMAL=0,BOLD=1;int style;public static Typeface create(String s,int n){Typeface t=new Typeface();t.style=n;return t;}public static Typeface create(Typeface t,int n){return create("",n);}public boolean isBold(){return style==1;}}'
STUBS['android/graphics/drawable/ColorDrawable.java'] = 'package android.graphics.drawable;public class ColorDrawable extends Drawable{public int fill;public ColorDrawable(int c){fill=c;}}'
STUBS['android/graphics/drawable/GradientDrawable.java'] = STUBS['android/graphics/drawable/GradientDrawable.java'].replace('public int fill;', 'public int fill;public void setStroke(int n,int c){}')
STUBS['android/graphics/drawable/LayerDrawable.java'] = STUBS['android/graphics/drawable/LayerDrawable.java'].replace('public int getId', 'public void setDrawable(int i,Drawable d){layers[i]=d;}public int getId')
STUBS['android/graphics/drawable/RippleDrawable.java'] = '''package android.graphics.drawable;public class RippleDrawable extends LayerDrawable{
public RippleDrawable(Drawable d){super(new Drawable[]{d,new GradientDrawable()});}
public RippleDrawable(android.content.res.ColorStateList c,Drawable d,Drawable mask){super(new Drawable[]{d,mask});}}'''
STUBS['android/text/TextUtils.java'] = 'package android.text;public class TextUtils{public enum TruncateAt{END}}'
STUBS['android/view/Gravity.java'] = 'package android.view;public class Gravity{public static int START=3,END=5,CENTER=17,CENTER_VERTICAL=16,CENTER_HORIZONTAL=1,BOTTOM=80,TOP=48;}'
STUBS['android/view/View.java'] = STUBS['android/view/View.java'].replace('public static final int VISIBLE', '''
 public interface OnLayoutChangeListener{void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 private java.util.List<OnLayoutChangeListener> layouts=new java.util.ArrayList<>();private int minimum,minwidth,px,py,mw,mh;private float tx,ty,sx=1,sy=1;
 public int getMinimumWidth(){return minwidth;}public void setMinimumWidth(int n){minwidth=n;}
 public float getScaleX(){return sx;}public float getScaleY(){return sy;}public void setScaleX(float n){sx=n;}public void setScaleY(float n){sy=n;}
 public void addOnLayoutChangeListener(OnLayoutChangeListener l){layouts.add(l);}public void removeOnLayoutChangeListener(OnLayoutChangeListener l){layouts.remove(l);}
 public static class MeasureSpec{public static int EXACTLY=1;public static int makeMeasureSpec(int size,int mode){return size;}}
 public void measure(int w,int h){mw=w;mh=h;}public void layout(int l,int t,int r,int b){px=l;py=t;}
 public int getTop(){return py;}public int getLeft(){return px;}public static int generateViewId(){return 777;}
 public float getTranslationX(){return tx;}public float getTranslationY(){return ty;}public void setTranslationX(float f){tx=f;}public void setTranslationY(float f){ty=f;}
 public int getMinimumHeight(){return minimum;}public void setMinimumHeight(int n){minimum=n;}public boolean isEnabled(){return true;}
 public float getAlpha(){return alpha;}
 public static final int VISIBLE''').replace('public void getLocationInWindow(int[] p){}', 'public void getLocationInWindow(int[] p){if(parent!=null)parent.getLocationInWindow(p);p[0]+=px+tx;p[1]+=py+ty;}')
STUBS['android/view/ViewGroup.java'] = STUBS['android/view/ViewGroup.java'].replace('public int leftMargin,topMargin,rightMargin,bottomMargin;', 'public int leftMargin,topMargin,rightMargin,bottomMargin;public int getMarginStart(){return leftMargin;}public int getMarginEnd(){return rightMargin;}public void setMarginStart(int n){leftMargin=n;}public void setMarginEnd(int n){rightMargin=n;}').replace('public void setClipChildren(boolean b){}public void setClipToPadding(boolean b){}', 'private boolean cc=true,cp=true;public void setClipChildren(boolean b){cc=b;}public void setClipToPadding(boolean b){cp=b;}public boolean getClipChildren(){return cc;}public boolean getClipToPadding(){return cp;}')
STUBS['android/widget/FrameLayout.java'] = STUBS['android/widget/FrameLayout.java'].replace('public LayoutParams(int w,int h){super(w,h);}', 'public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,int g){super(w,h);}')
STUBS['android/widget/RelativeLayout.java'] = '''package android.widget;public class RelativeLayout extends android.view.ViewGroup{
public static int ALIGN_PARENT_START=0,CENTER_IN_PARENT=1,CENTER_HORIZONTAL=2,CENTER_VERTICAL=3,START_OF=4,ALIGN_PARENT_LEFT=5;
public RelativeLayout(android.content.Context c){super(c);}public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{
int[] rules=new int[8];public LayoutParams(int w,int h){super(w,h);}public int[] getRules(){return rules;}public int getRule(int n){return rules[n];}
public void removeRule(int n){rules[n]=0;}public void addRule(int n){rules[n]=-1;}public void addRule(int n,int a){rules[n]=a;}}}'''
STUBS['android/widget/TextView.java'] = STUBS['android/widget/TextView.java'].replace('public int ink;', 'public int ink;private android.graphics.Typeface face=new android.graphics.Typeface();public int getCurrentTextColor(){return ink;}public android.graphics.Typeface getTypeface(){return face;}public void setEllipsize(android.text.TextUtils.TruncateAt t){}').replace('setTypeface(android.graphics.Typeface t){}', 'setTypeface(android.graphics.Typeface t){face=t;}')
STUBS['android/widget/TextView.java'] = STUBS['android/widget/TextView.java'].replace('public int ink;', '''public int ink;private int maxwidth=Integer.MAX_VALUE,maxems=-1;private boolean fontpadding=true;
public void setMaxWidth(int n){maxwidth=n;maxems=-1;}public int getMaxWidth(){return maxwidth;}
public void setMaxEms(int n){maxems=n;maxwidth=-1;}public int getMaxEms(){return maxems;}
public void setIncludeFontPadding(boolean n){fontpadding=n;}public boolean getIncludeFontPadding(){return fontpadding;}
''')
STUBS['cat/narezany/margyt/Md3.java'] = '''package cat.narezany.margyt;class Md3{static final String KEY_INBOX="inbox";static boolean on=true;static int page=0xff141218;
static boolean isOn(String s){return on;}static int behind(android.view.View v){return page;}static int ink(int c){return NavBar.bright(c)<.5f?-1:0xff161823;}}'''
STUBS['cat/narezany/margyt/ProfileLayout.java'] = 'package cat.narezany.margyt;class ProfileLayout{static boolean shown=true;static boolean onScreen(android.view.View v,android.view.View r){return shown&&v.isShown();}}'
STUBS['cat/narezany/margyt/Popup.java'] = 'package cat.narezany.margyt;class Popup{static void show(android.view.View v,String a,String b,String c,android.graphics.drawable.Drawable d){throw new AssertionError("unexpected fallback");}}'
STUBS['cat/narezany/margyt/AppearanceColors.java'] = '''package cat.narezany.margyt;class AppearanceColors{
static int nickname;static int forView(android.view.View view,int fallback){
 Integer id=android.content.res.Resources.ids.get("user_name");
 return nickname!=0&&view!=null&&id!=null&&view.getId()==id?nickname:fallback;
}}'''
STUBS['cat/narezany/margyt/Accent.java'] = '''package cat.narezany.margyt;class Accent{
static int colour(){return 0xff978fc3;}static int swap(int c){return c==0xfff13674?colour():c;}}'''
STUBS['android/view/View.java'] = STUBS['android/view/View.java'].replace(
    'public boolean isAttachedToWindow(){return true;}',
    'public boolean attached=true;public boolean isAttachedToWindow(){return attached;}')

HARNESS = '''package cat.narezany.margyt;
import android.view.*;import android.widget.*;import android.graphics.drawable.*;
public class InboxCheck{
static android.content.Context c=new android.content.Context();static int seq=1000,opens,compose;
static void ck(boolean b,String s){if(!b)throw new AssertionError(s);}static int id(String n){return android.content.res.Resources.ids.computeIfAbsent(n,k->seq++);}
static <T extends View>T named(T v,String n){v.setId(id(n));return v;}static TextView text(String n,String s){TextView t=named(new TextView(c),n);t.setText(s);return t;}
public static class Badge extends View{public int count=500,digits=2;public Badge(){super(c);}public int getCount(){return count;}public int getMaxCount(){return digits;}}
public static class BadgeText{public String LJII;BadgeText(String text){LJII=text;}}
public static class LegacyBadgeText{public int LJII;public String LJFF;LegacyBadgeText(String text){LJFF=text;}}
public static class PaintedBadge extends Badge{public Object LL=new BadgeText("99+");}
public static class Avatar extends FrameLayout{View picture=new View(c);public Avatar(){super(c);addView(picture,new FrameLayout.LayoutParams(64,64));}public View getRealAvatarView(){return picture;}}
static int[] at(View view){int[] p=new int[2];view.getLocationInWindow(p);return p;}
static ViewGroup notice(String title,String preview){LinearLayout row=named(new LinearLayout(c),"uxm");row.setLayoutParams(new LinearLayout.LayoutParams(-1,72));row.addView(text("brn",title));row.addView(text("nhq",preview));row.addView(named(new Badge(),"nhs"));row.setOnClickListener(v->opens++);return row;}
public static class Holder{public View itemView;Holder(View view){itemView=view;}}
public static class Adapter{
 public int getItemCount(){return 1;}public int getItemViewType(int at){return 7;}
 public Holder createViewHolder(ViewGroup list,int type){return new Holder(notice("Activity",""));}
 public void bindViewHolder(Holder holder,int at){}
}
public static class Recycler extends LinearLayout{
 public Adapter adapter=new Adapter();public Recycler(){super(c);}
 public Adapter getAdapter(){return adapter;}public int getChildAdapterPosition(View child){return 0;}
}
public static void main(String[] args){
 FrameLayout root=new FrameLayout(c);root.setId(android.R.id.content);root.setLayoutParams(new ViewGroup.LayoutParams(412,850));
 FrameLayout inboxPage=new FrameLayout(c);inboxPage.setLayoutParams(new FrameLayout.LayoutParams(412,760));root.addView(inboxPage);
 LinearLayout list=named(new LinearLayout(c),"l78");list.setLayoutParams(new FrameLayout.LayoutParams(412,700));inboxPage.addView(list);
 View nativeCompose=named(new View(c),"fha");nativeCompose.setOnClickListener(v->compose++);inboxPage.addView(nativeCompose);
 View nav=named(new View(c),"on6");nav.layout(0,760,412,832);root.addView(nav);
 FrameLayout filters=new FrameLayout(c);filters.setLayoutParams(new LinearLayout.LayoutParams(-1,44));
 HorizontalScrollView scroll=new HorizontalScrollView(c);LinearLayout chips=new LinearLayout(c);scroll.addView(chips);filters.addView(scroll);list.addView(filters);
 LinearLayout main=new LinearLayout(c);TextView mainLabel=text("","Основные");mainLabel.setTextColor(0xff9988ff);main.addView(mainLabel);chips.addView(main);
 TextView count=text("","99+");count.setIncludeFontPadding(true);LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(30,32);countParams.gravity=Gravity.TOP;main.addView(count,countParams);
 ViewGroup followers=notice("Alice","подписались на вас"),activity=notice("Активность",""),system=notice("Уведомления системы",""),money=notice("Монетизация","");
 list.addView(followers);list.addView(activity);list.addView(system);list.addView(money);
 LinearLayout chat=named(new LinearLayout(c),"w0w");chat.setLayoutParams(new LinearLayout.LayoutParams(412,72));
 TextView name=text("user_name","Person"),preview=text("mv1","Real native message");name.layout(94,16,250,36);chat.addView(name);chat.addView(preview);list.addView(chat);
 TextView time=text("mv7","16 мин");time.setLayoutParams(new LinearLayout.LayoutParams(50,20));time.layout(280,39,330,59);chat.addView(time);
 LinearLayout actions=named(new LinearLayout(c),"gdj");actions.layout(352,0,412,72);chat.addView(actions);
 View camera=named(new View(c),"gdi"),unread=named(new View(c),"gdm");actions.addView(camera,new LinearLayout.LayoutParams(24,24));camera.layout(20,24,44,48);actions.addView(unread,new LinearLayout.LayoutParams(20,20));unread.layout(20,24,40,44);
 InboxMd3.list(list,true);
 ck(list.getChildCount()==6&&list.getChildAt(1)==followers&&list.getChildAt(5)==chat,"native RecyclerView order and identities unchanged");
 ck(followers.getLayoutParams().height==72&&activity.getLayoutParams().height==72&&system.getLayoutParams().height==72&&money.getLayoutParams().height==72,"native service rows remain visible alongside shortcuts");
 ck(filters.getChildCount()==1&&filters.getLayoutParams().height==44,"no shortcut tiles: the filter header stays native");
 ck(chat.getLayoutParams().height==76&&name.getText().toString().equals("Person")&&preview.getText().toString().equals("Real native message"),"chat style preserves real content");
 LinearLayout fab=(LinearLayout)inboxPage.getChildAt(inboxPage.getChildCount()-1);fab.performClick();ck(compose==1,"compose uses native picker without sending anything");
 ck(fab.getParent()==inboxPage&&root.getChildCount()==2,"compose is owned by the inbox page rather than the activity content");
 FrameLayout conversationPage=new FrameLayout(c);root.addView(conversationPage);
 ck(root.indexOfChild(inboxPage)<root.indexOfChild(conversationPage)&&fab.getParent()==inboxPage,"a newly opened conversation covers the entire inbox including its compose button");
 inboxPage.setVisibility(View.GONE);InboxMd3.frame();ck(!fab.isShown()&&fab.getVisibility()==View.GONE,"leaving inbox hides compose before the next draw without a delayed scan");
 inboxPage.setVisibility(View.VISIBLE);root.removeView(conversationPage);InboxMd3.frame();ck(fab.isShown(),"returning to inbox restores its own button");
 int views=filters.getChildCount();for(int i=0;i<8;i++)InboxMd3.list(list,true);ck(filters.getChildCount()==views&&list.getChildCount()==6,"repeated layout creates no duplicate headers or rows");
 InboxMd3.folder(main,true);ck(InboxMd3.nativeInk(mainLabel)==0xff9988ff,"chip painting preserves native selection signal");
 ck(count.getLayoutParams().height==18&&((LinearLayout.LayoutParams)count.getLayoutParams()).gravity==Gravity.CENTER_VERTICAL&&!count.getIncludeFontPadding(),"folder counter has a compact centred box");
 int counterInk=count.getCurrentTextColor();count.setTextColor(0xffeeeeee);count.setBackground(new ColorDrawable(0xff444444));
 mainLabel.setTextColor(0xff123456);InboxMd3.folderFrame(list);
 ck(count.getCurrentTextColor()==counterInk&&count.getBackground() instanceof GradientDrawable,"native counter colour/background reset is repaired before drawing");
 ck(InboxMd3.nativeInk(mainLabel)==0xff123456,"repair retains the fresh native selection colour");
 mainLabel.setTextColor(0xff9988ff);InboxMd3.folderFrame(list);
 LinearLayout lateChip=new LinearLayout(c);lateChip.addView(text("","Позже"));chips.addView(lateChip);InboxMd3.folder(lateChip,false);
 TextView lateCount=text("","7");lateChip.addView(lateCount,new LinearLayout.LayoutParams(20,32));InboxMd3.folderFrame(list);
 ck(lateCount.getLayoutParams().height==18&&lateCount.getCurrentTextColor()==counterInk,"counter added after the initial scan is styled before drawing");
 LinearLayout emptyChip=new LinearLayout(c);emptyChip.addView(text("","Непрочитанные"));TextView emptyCount=text("","");emptyChip.addView(emptyCount,new LinearLayout.LayoutParams(20,32));chips.addView(emptyChip);InboxMd3.folder(emptyChip,false);
 emptyCount.setText("99+");InboxMd3.folderFrame(list);
 ck(emptyCount.getLayoutParams().height==18&&emptyCount.getCurrentTextColor()==counterInk&&emptyCount.getBackground() instanceof GradientDrawable,"initially empty counter is painted when its count arrives");
 emptyCount.setText("");InboxMd3.folder(emptyChip,false);emptyCount.setText("8");InboxMd3.folderFrame(list);
 ck(emptyCount.getCurrentTextColor()==counterInk,"counter regains its badge text colour after clearing and repopulating");
 ck(at(camera)[1]>=at(time)[1]+time.getHeight()+4&&at(unread)[1]>=at(time)[1]+time.getHeight()+4,"camera and badge clear the timestamp vertically");
 ck(at(unread)[0]+unread.getWidth()+8<=at(camera)[0],"simultaneous badge and camera do not overlap");
 ck(at(name)[0]+name.getMaxWidth()+8<=at(time)[0],"long names reserve timestamp space");
 // Native row binding and the theme sweep both honour the nickname override.
 // A later MD3 pass must not replace it with the generic white foreground.
 int normalInk=name.getCurrentTextColor();AppearanceColors.nickname=0xff9988ff;
 for(int i=0;i<40;i++){
  name.setTextColor(AppearanceColors.forView(name,normalInk));InboxMd3.list(list,true);
  ck(name.getCurrentTextColor()==AppearanceColors.nickname,"scroll rebinding and MD3 agree on the nickname colour");
  InboxMd3.refresh();ck(name.getCurrentTextColor()==AppearanceColors.nickname,"quiet layout does not reset the nickname to white");
 }
 AppearanceColors.nickname=0;InboxMd3.refresh();ck(name.getCurrentTextColor()==normalInk,"clearing the nickname override restores normal foreground");
 LinearLayout stories=new LinearLayout(c),cell=new LinearLayout(c);cell.setPadding(0,8,0,10);cell.setClipToPadding(true);stories.addView(cell);Avatar avatar=named(new Avatar(),"wq0");avatar.layout(8,33,72,97);cell.addView(avatar,new LinearLayout.LayoutParams(64,64));
 TextView note=text("q4n","Что хорошего?"),caption=text("wq6","Real name");note.setLayoutParams(new LinearLayout.LayoutParams(72,52));note.layout(4,9,76,61);cell.addView(note);caption.setLayoutParams(new LinearLayout.LayoutParams(72,20));caption.layout(4,87,76,107);cell.addView(caption);list.addView(stories);
 cell.setLayoutParams(new LinearLayout.LayoutParams(104,150));avatar.getLayoutParams().width=88;avatar.getLayoutParams().height=88;
 LinearLayout live=new LinearLayout(c);live.setLayoutParams(new LinearLayout.LayoutParams(104,150));View liveAvatar=new View(c);live.addView(liveAvatar,new LinearLayout.LayoutParams(88,88));stories.addView(live);
 InboxMd3.refresh();ck(avatar.picture.getScaleX()==1&&avatar.getTranslationY()==0,"story avatar keeps native scale and position");
 ck(avatar.getLayoutParams().width==liveAvatar.getLayoutParams().width&&avatar.getLayoutParams().height==liveAvatar.getLayoutParams().height,"ordinary and LIVE avatars keep the same native size");
 ck(cell.getLayoutParams().width==104&&cell.getLayoutParams().height==150,"self story cell is not narrowed or shortened");
 ck(note.getScaleY()==1&&note.getTranslationY()==0&&caption.getTranslationY()==0,"native notes and captions keep their layout");
 caption.getLayoutParams().height=26;InboxMd3.refresh();ck(caption.getLayoutParams().height==26,"native larger text measurement is preserved");
 ProfileLayout.shown=false;InboxMd3.refresh();ck(fab.getVisibility()==View.GONE,"offscreen cached inbox cannot overlay the profile or feed");ProfileLayout.shown=true;
 // A recycled notification row must regain its original dimensions and opacity.
 ((TextView)followers.findViewById(id("nhq"))).setText("another native row");((TextView)followers.findViewById(id("brn"))).setText("Unknown type");
 InboxMd3.refresh();ck(followers.getLayoutParams().height==72&&followers.getAlpha()==1,"rebound row is restored instead of left hidden");
 Md3.on=false;InboxMd3.refresh();ck(filters.getChildCount()==1&&filters.getLayoutParams().height==44,"disabling restores filter hierarchy and height");
 ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1&&chat.getLayoutParams().height==72,"native service and conversation sizes restored");
 ck(root.getChildCount()==2&&inboxPage.getChildCount()==2&&mainLabel.getCurrentTextColor()==0xff9988ff,"compose removed from its inbox owner and native selection colours restored");
 ck(count.getLayoutParams().height==32&&count.getIncludeFontPadding()&&((LinearLayout.LayoutParams)count.getLayoutParams()).gravity==Gravity.TOP,"native counter geometry restored");
 ck(camera.getTranslationX()==0&&camera.getTranslationY()==0&&name.getMaxWidth()==Integer.MAX_VALUE,"native chat positions restored");
 ck(avatar.picture.getScaleX()==1&&note.getScaleY()==1&&note.getTranslationY()==0,"native story geometry restored");
 ck(cell.getClipToPadding()&&cell.getPaddingBottom()==10,"native story clipping and padding restored");
 Md3.on=true;InboxMd3.list(list,true);ck(filters.getChildCount()==1,"reenabling adds no tiles");
 // Accounts without the filter header retain their real notification entry.
 list.removeView(filters);InboxMd3.refresh();
 ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1,"removing the header restores Activity");int before=opens;activity.performClick();ck(opens==before+1,"Activity still opens without a header");
 LinearLayout other=new LinearLayout(c);root.addView(other);ViewGroup otherActivity=notice("Activity","");other.addView(otherActivity);InboxMd3.list(other,true);
 ck(otherActivity.getLayoutParams().height==72&&otherActivity.getAlpha()==1,"account starting without filters retains Activity");
 list.addView(filters);InboxMd3.refresh();ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1,"adding a shortcut never hides native Activity");
 filters.setVisibility(View.GONE);InboxMd3.refresh();ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1,"hidden header restores native Activity");
 filters.setVisibility(View.VISIBLE);activity.setOnClickListener(null);InboxMd3.refresh();ck(activity.getLayoutParams().height==72,"nonactionable replacement never hides native entry");
 activity.setOnClickListener(v->opens++);((TextView)activity.findViewById(id("brn"))).setId(id("brj"));((TextView)activity.findViewById(id("nhq"))).setId(id("nhr"));
 InboxMd3.refresh();ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1,"alternate native labels never change service row visibility");
 // A service first encountered below the viewport must be left completely native.
 LinearLayout lateList=new LinearLayout(c);root.addView(lateList);lateList.addView(new View(c));InboxMd3.list(lateList,true);
 ViewGroup lateSystem=notice("System notifications",""),lateActivity=notice("Activity","");lateSystem.setMinimumHeight(44);lateSystem.setAlpha(.85f);
 lateList.addView(lateSystem);lateList.addView(lateActivity);InboxMd3.frame();
 ck(lateSystem.getLayoutParams().height==72&&lateSystem.getMinimumHeight()==44&&lateSystem.getAlpha()==.85f&&lateSystem.getVisibility()==View.VISIBLE,"late system row retains native dimensions, opacity and visibility without any tile");
 FrameLayout lateFilters=new FrameLayout(c);lateFilters.setLayoutParams(new LinearLayout.LayoutParams(-1,44));lateFilters.addView(new HorizontalScrollView(c));lateList.addView(lateFilters);InboxMd3.frame();
 for(int i=0;i<20;i++){
  lateList.removeView(lateSystem);lateSystem.attached=false;InboxMd3.frame();lateSystem.attached=true;lateList.addView(lateSystem);InboxMd3.frame();
  ck(lateSystem.getLayoutParams().height==72&&lateSystem.getAlpha()==.85f&&lateActivity.getLayoutParams().height==72,"scrolling with a header never collapses native service rows");
 }
 before=opens;lateSystem.performClick();lateActivity.performClick();ck(opens==before+2,"both native notification actions remain clickable");
 // The single-story variant has the same narrow viewport as the reported crop.
 LinearLayout singleList=new LinearLayout(c);singleList.setLayoutParams(new FrameLayout.LayoutParams(336,400));root.addView(singleList);
 LinearLayout singleStrip=new LinearLayout(c);singleStrip.setLayoutParams(new LinearLayout.LayoutParams(96,170));singleStrip.setClipChildren(true);singleStrip.setClipToPadding(true);singleStrip.setClipToOutline(true);singleList.addView(singleStrip);
 FrameLayout singleCell=new FrameLayout(c);singleCell.setLayoutParams(new LinearLayout.LayoutParams(96,170));singleCell.setClipChildren(true);singleCell.setClipToPadding(true);singleCell.setClipToOutline(true);singleStrip.addView(singleCell);
 Avatar own=named(new Avatar(),"wq0");own.setLayoutParams(new FrameLayout.LayoutParams(92,92));own.layout(22,55,114,147);singleCell.addView(own);
 TextView bubble=text("q4n","Let's talk");bubble.setLayoutParams(new FrameLayout.LayoutParams(100,40));bubble.layout(28,0,128,40);singleCell.addView(bubble);
 InboxMd3.list(singleList,true);
 ck(singleStrip.getLayoutParams().width==ViewGroup.LayoutParams.MATCH_PARENT&&!singleStrip.getClipChildren()&&!singleStrip.getClipToPadding()&&!singleStrip.getClipToOutline(),"one story uses the full inbox viewport without a clipping outline");
 ck(!singleCell.getClipChildren()&&!singleCell.getClipToPadding()&&!singleCell.getClipToOutline(),"story cell does not clip the avatar, note or frame at 96px");
 ck(own.getLayoutParams().width==92&&own.getLayoutParams().height==92&&own.getLeft()==22&&own.getTranslationX()==0,"fixing the container does not resize or shift the avatar");
 InboxMd3.list(singleList,false);ck(singleStrip.getLayoutParams().width==96&&singleStrip.getClipToOutline()&&singleCell.getClipChildren()&&singleCell.getClipToOutline(),"disabling the design restores native story bounds and clipping");
 }
}'''


class InboxMd3RuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_native_rows_lifecycle_counters_actions_and_themes(self):
        sources = dict(STUBS)
        sources['cat/narezany/margyt/InboxMd3.java'] = (ROOT / 'inject/java/cat/narezany/margyt/InboxMd3.java').read_text()
        sources['cat/narezany/margyt/InboxCheck.java'] = HARNESS
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for name, content in sources.items():
                path = pathlib.Path(directory) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            result = subprocess.run(['javac', '-d', directory, *paths], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', directory, 'cat.narezany.margyt.InboxCheck'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

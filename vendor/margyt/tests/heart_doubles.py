"""Small Android animation surface used by the native heart runtime test."""
STUBS = {
    'android/view/View.java': '''package android.view;public class View{
 public static int INVISIBLE=4;public Object tag;public int visibility,animations;public float sx=1,sy=1;public boolean attached=true;
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 final java.util.List<OnAttachStateChangeListener> attaches=new java.util.ArrayList<>();
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attaches.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attaches.remove(l);}
 public int listenerCount(){return attaches.size();}public void detach(){attached=false;for(OnAttachStateChangeListener l:new java.util.ArrayList<>(attaches))l.onViewDetachedFromWindow(this);}
 public void attach(){attached=true;for(OnAttachStateChangeListener l:new java.util.ArrayList<>(attaches))l.onViewAttachedToWindow(this);}
 final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();final ViewOverlay overlay=new ViewOverlay();
 public static final java.util.List<Runnable> pending=new java.util.ArrayList<>();
 public void setTag(int k,Object v){tag=v;tags.put(k,v);}public Object getTag(int k){return tags.get(k);}
 public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return visibility==0;}
 public View getParent(){return null;}public View getRootView(){return this;}public CharSequence getContentDescription(){return null;}
 public int getWidth(){return 60;}public int getHeight(){return 60;}public Object getResources(){return null;}
 public void getLocationInWindow(int[] p){}public float getScaleX(){return sx;}public float getScaleY(){return sy;}public float getAlpha(){return 1;}
 public int getVisibility(){return visibility;}public void setVisibility(int n){visibility=n;}public void draw(android.graphics.Canvas c){}
 public ViewOverlay getOverlay(){return overlay;}public ViewPropertyAnimator animate(){animations++;return new ViewPropertyAnimator(this);}
 public void post(Runnable r){r.run();}public void postDelayed(Runnable r,long d){pending.add(r);}
 public static void flush(){java.util.List<Runnable> old=new java.util.ArrayList<>(pending);pending.clear();for(Runnable r:old)r.run();}
}''',
    'android/view/ViewGroup.java': 'package android.view;public class ViewGroup extends View{public int getChildCount(){return 0;}public View getChildAt(int n){return null;}}',
    'android/view/ViewOverlay.java': '''package android.view;public class ViewOverlay{public int count;
 public void add(android.graphics.drawable.Drawable d){count++;}public void remove(android.graphics.drawable.Drawable d){count--;}}''',
    'android/view/ViewPropertyAnimator.java': '''package android.view;public class ViewPropertyAnimator{
 final View v;Runnable end;public ViewPropertyAnimator(View view){v=view;}public void cancel(){}
 public ViewPropertyAnimator scaleX(float f){v.sx=f;return this;}public ViewPropertyAnimator scaleY(float f){v.sy=f;return this;}
 public ViewPropertyAnimator setDuration(long t){return this;}public ViewPropertyAnimator setInterpolator(Object o){return this;}
 public ViewPropertyAnimator withEndAction(Runnable r){end=r;return this;}public void start(){if(end!=null)end.run();}}''',
    'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap{public enum Config{ARGB_8888}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap();}}''',
    'android/graphics/Canvas.java': 'package android.graphics;public class Canvas{public Canvas(Bitmap b){}}',
    'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;public class Drawable{public int colour;
 public Drawable mutate(){return this;}public void setColorFilter(int c,android.graphics.PorterDuff.Mode m){colour=c;}
 public void setBounds(int a,int b,int c,int d){}public void setAlpha(int n){}}''',
    'android/graphics/drawable/BitmapDrawable.java': 'package android.graphics.drawable;public class BitmapDrawable extends Drawable{public BitmapDrawable(Object r,android.graphics.Bitmap b){}}',
    'android/animation/Animator.java': 'package android.animation;public class Animator{}',
    'android/animation/AnimatorListenerAdapter.java': 'package android.animation;public class AnimatorListenerAdapter{public void onAnimationEnd(Animator a){}}',
    'android/animation/ValueAnimator.java': '''package android.animation;public class ValueAnimator extends Animator{
 public interface Update{void accept(ValueAnimator a);}public static int runs;public static boolean automatic=true;
 public static java.util.List<ValueAnimator> active=new java.util.ArrayList<>();Update update;AnimatorListenerAdapter listener;
 public static ValueAnimator ofFloat(float... f){return new ValueAnimator();}public void setDuration(long t){}public void setInterpolator(Object o){}
 public void addUpdateListener(Update u){update=u;}public void addListener(AnimatorListenerAdapter l){listener=l;}
 public Object getAnimatedValue(){return Float.valueOf(1);}public void start(){runs++;active.add(this);if(update!=null)update.accept(this);if(automatic)finish();}
 public void finish(){if(active.remove(this)&&listener!=null)listener.onAnimationEnd(this);}public void cancel(){finish();}}''',
    'android/view/animation/AccelerateDecelerateInterpolator.java': 'package android.view.animation;public class AccelerateDecelerateInterpolator{}',
    'android/view/animation/OvershootInterpolator.java': 'package android.view.animation;public class OvershootInterpolator{public OvershootInterpolator(float f){}}',
    'cat/narezany/margyt/FeedRail.java': 'package cat.narezany.margyt;class FeedRail{static android.view.View like;static android.view.View likeOnScreen(){return like;}}',
}

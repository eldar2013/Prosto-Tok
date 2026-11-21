package cat.narezany.margyt;
/** Only the native double-tap heart host is routed here. */
public final class Heart {
    private Heart(){}
    private static final java.util.Map<android.view.View,Integer> views=new java.util.WeakHashMap<android.view.View,Integer>();
    private static void tint(android.view.View view) {
        int colour=LikeColors.colour();
        synchronized(views){Integer known=views.get(view);if(known!=null&&known.intValue()==colour)return;views.put(view,colour);}
        // A view-level filter also covers bitmap frames whose drawable filter is reset by the host.
        if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setColorFilter(colour,android.graphics.PorterDuff.Mode.SRC_IN);
    }
    static void refresh() {
        java.util.List<android.view.View> all;
        synchronized(views){all=new java.util.ArrayList<android.view.View>(views.keySet());}
        for(android.view.View view:all)if(view!=null && view.isAttachedToWindow())view.post(()->{
            if(view instanceof com.bytedance.tux.icon.TuxIconView)((com.bytedance.tux.icon.TuxIconView)view).setTintColor(LikeColors.colour());
            tint(view);
        });
    }
    public static void setIconRes(com.bytedance.tux.icon.TuxIconView view,int resource) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setIconRes(resource);
        view.setTintColor(LikeColors.colour());
        tint(view);
    }

    // ------------------------------------------------------------ into the like button

    private static final int FLOWN=0x54544C49;

    /**
     * A heart from a double tap does not just fade where it was: once TikTok
     * has popped it up, it flies on a curve into the like button of the video
     * on screen, shrinking to the button's size, and the button gives a little
     * bounce as it lands.
     */
    /** The like button of the video the heart is over: the rail's own, or one said to be a like. */
    private static android.view.View likeButton(android.view.View heart){
        android.view.View known=FeedRail.likeOnScreen();
        if(known!=null)return known;
        android.view.View cell=heart;
        while(cell.getParent() instanceof android.view.View){
            cell=(android.view.View)cell.getParent();
            if(cell.getClass().getName().contains("VideoViewCellRootView"))break;
        }
        return saidLike(cell,0);
    }

    private static android.view.View saidLike(android.view.View view,int depth){
        if(view==null||depth>30||!view.isShown())return null;
        CharSequence said=view.getContentDescription();
        if(said!=null){
            String words=said.toString().toLowerCase(java.util.Locale.ROOT);
            if((words.contains("лайк")||words.contains("нрав")||words.contains("like")||words.contains("вподоб"))
                    && !words.contains("комментар")&&!words.contains("comment"))return view;
        }
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View found=saidLike(group.getChildAt(i),depth+1);
                if(found!=null)return found;
            }
        }
        return null;
    }

    /** Called on every native use, including hearts reused without a new drawable. */
    public static void shown(android.widget.ImageView heart){
        if(heart==null)return;
        try{
            Object previous=heart.getTag(FLOWN);
            if(previous instanceof Flight)((Flight)previous).dispose();
            Flight next=new Flight(heart);
            heart.setTag(FLOWN,next);
            heart.addOnAttachStateChangeListener(next);
            heart.postDelayed(()->{
                if(heart.getTag(FLOWN)!=next)return;
                try{next.start();}catch(Throwable error){next.dispose();Diary.note("heart flight: "+error);}
            },260);
        }catch(Throwable error){Diary.note("heart cycle: "+error);}
    }

    private static final class Flight implements android.view.View.OnAttachStateChangeListener {
        final android.view.View heart;
        android.view.View root;
        android.graphics.drawable.Drawable copy;
        android.graphics.Bitmap picture;
        android.animation.ValueAnimator animator;
        boolean disposed,hidden;
        int visibility;
        Flight(android.view.View heart){this.heart=heart;}
        @Override public void onViewAttachedToWindow(android.view.View view){}
        @Override public void onViewDetachedFromWindow(android.view.View view){dispose();}

        void removeCopy(){
            if(root!=null&&copy!=null)root.getOverlay().remove(copy);
            copy=null;picture=null;root=null;
        }
        void dispose(){
            if(disposed)return;
            disposed=true;
            if(animator!=null)animator.cancel();
            removeCopy();
            heart.removeOnAttachStateChangeListener(this);
            if(hidden&&heart.getVisibility()==android.view.View.INVISIBLE)heart.setVisibility(visibility);
            if(heart.getTag(FLOWN)==this)heart.setTag(FLOWN,null);
        }
        void start(){
        if(disposed||!heart.isAttachedToWindow()||!heart.isShown()){dispose();return;}
        final android.view.View like=likeButton(heart);
        if(like==null||!like.isAttachedToWindow()||!like.isShown()){dispose();return;}
        if(heart.getWidth()<=0||heart.getHeight()<=0){dispose();return;}
        final float fromW=heart.getWidth()*heart.getScaleX(),fromH=heart.getHeight()*heart.getScaleY();
        if(fromW<=0||fromH<=0){dispose();return;}
        // the heart as it looks, taken off the screen: its drawable is TikTok's own and cannot be copied
        picture=android.graphics.Bitmap.createBitmap(heart.getWidth(),heart.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        heart.draw(new android.graphics.Canvas(picture));
        root=heart.getRootView();
        int[] at=new int[2],rootAt=new int[2],to=new int[2];
        root.getLocationInWindow(rootAt);
        heart.getLocationInWindow(at);
        like.getLocationInWindow(to);
        // the heart's centre, where its scale left it, and the like button's
        final float x0=at[0]-rootAt[0]+heart.getWidth()*heart.getScaleX()/2f,y0=at[1]-rootAt[1]+heart.getHeight()*heart.getScaleY()/2f;
        final float x1=to[0]-rootAt[0]+like.getWidth()/2f,y1=to[1]-rootAt[1]+like.getHeight()*.38f;
        final float toSize=Math.min(like.getWidth(),like.getHeight())*.62f;
        // the curve bows out to the left and up before it comes down to the button
        final float cx=(x0+x1)/2f-Math.abs(x1-x0)*.25f,cy=Math.min(y0,y1)-root.getHeight()*.12f;
        copy=new android.graphics.drawable.BitmapDrawable(heart.getResources(),picture);
        copy.setAlpha(Math.round(255*Math.max(.3f,heart.getAlpha())));
        visibility=heart.getVisibility();hidden=true;
        heart.setVisibility(android.view.View.INVISIBLE);
        root.getOverlay().add(copy);
        android.animation.ValueAnimator flight=android.animation.ValueAnimator.ofFloat(0f,1f);
        animator=flight;
        flight.setDuration(520);
        flight.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        flight.addUpdateListener(a->{
            if(disposed||copy==null)return;
            float t=(Float)a.getAnimatedValue(),u=1-t;
            float x=u*u*x0+2*u*t*cx+t*t*x1,y=u*u*y0+2*u*t*cy+t*t*y1;
            float w=fromW+(toSize-fromW)*t,h=fromH+(toSize-fromH)*t;
            copy.setBounds(Math.round(x-w/2),Math.round(y-h/2),Math.round(x+w/2),Math.round(y+h/2));
            copy.setAlpha(Math.round(255*(1-.35f*t*t)));
        });
        flight.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){
                removeCopy();animator=null;
                // TikTok removes the original at the end of its own animation.
                // Keep it hidden until then; detaching or reusing restores it.
                if(disposed||!like.isAttachedToWindow()||!like.isShown())return;
                like.animate().cancel();
                like.animate().scaleX(1.22f).scaleY(1.22f).setDuration(110).withEndAction(()->
                        like.animate().scaleX(1f).scaleY(1f).setDuration(240)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start()).start();
            }
        });
        flight.start();
        }
    }
    public static void setTintColor(com.bytedance.tux.icon.TuxIconView view,int ignored) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setTintColor(LikeColors.colour());
        tint(view);
    }
    public static void setImageDrawable(android.widget.ImageView view,android.graphics.drawable.Drawable image) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setImageDrawable(image);
        tint(view);
    }
}

package com.jokysss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.jokysss.swipemenulayout.R;


public class SwipeMenuLayout extends ViewGroup {
    private static final String TAG = "SwipeMenuLayout";

    private int mScaleTouchSlop;//为了处理单击事件的冲突
    private int mMaxVelocity;//计算滑动速度用
    private int mPointerId;//多点触摸只算第一根手指的速度
    private int mHeight;//自己的高度
    //右侧菜单宽度总和(最大滑动距离)
    private int mRightMenuWidths;

    //滑动判定临界值（右侧菜单宽度的40%） 手指抬起时，超过了展开，没超过收起menu
    private int mLimit;

    private View mContentView;//存储contentView(第一个View)

    //上一次的xy
    private PointF mLastP = new PointF();
    //仿QQ，侧滑菜单展开时，点击除侧滑菜单之外的区域，关闭侧滑菜单。
    //增加一个布尔值变量，dispatch函数里，每次down时，为true，move时判断，如果是滑动动作，设为false。
    //在Intercept函数的up时，判断这个变量，如果仍为true 说明是点击事件，则关闭菜单。
    private boolean isUnMoved = true;

    //判断手指起始落点，如果距离属于滑动了，就屏蔽一切点击事件。
    //up-down的坐标，判断是否是滑动，如果是，则屏蔽一切点击事件
    private PointF mFirstP = new PointF();
    private boolean isUserSwiped;

    //存储的是当前正在展开的View
    private static SwipeMenuLayout mViewCache;

    //防止多只手指一起滑我的flag 在每次down里判断， touch事件结束清空
    private static boolean isTouching;

    private VelocityTracker mVelocityTracker;//滑动速度变量
    private int maxSpeed = 2000;
    /**
     * 右滑删除功能的开关,默认开
     */
    private boolean isSwipeEnable;

    /**
     * IOS、QQ式交互，默认开
     */
    private boolean isIos;

    private boolean iosInterceptFlag;//IOS类型下，是否拦截事件的flag

    /**
     * 20160929add 左滑右滑的开关,默认左滑打开菜单
     */
    private boolean isLeftSwipe;
    private boolean hasConsume;
    public SwipeMenuLayout(Context context) {
        this(context, null);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    public boolean isSwipeEnable() {
        return isSwipeEnable;
    }

    /**
     * 设置侧滑功能开关
     *
     * @param swipeEnable
     */
    public void setSwipeEnable(boolean swipeEnable) {
        isSwipeEnable = swipeEnable;
    }


    public boolean isIos() {
        return isIos;
    }

    /**
     * 设置是否开启IOS阻塞式交互
     *
     * @param ios
     */
    public SwipeMenuLayout setIos(boolean ios) {
        isIos = ios;
        return this;
    }

    public boolean isLeftSwipe() {
        return isLeftSwipe;
    }

    /**
     * 设置是否开启左滑出菜单，设置false 为右滑出菜单
     *
     * @param leftSwipe
     * @return
     */
    public SwipeMenuLayout setLeftSwipe(boolean leftSwipe) {
        isLeftSwipe = leftSwipe;
        return this;
    }

    /**
     * 返回ViewCache
     *
     * @return
     */
    public static SwipeMenuLayout getViewCache() {
        return mViewCache;
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        mScaleTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMaxVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        //右滑删除功能的开关,默认开
        isSwipeEnable = true;
        //IOS、QQ式交互，默认开
        isIos = true;
        //左滑右滑的开关,默认左滑打开菜单
        isLeftSwipe = true;
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SwipeMenuLayout, defStyleAttr, 0);
        int count = ta.getIndexCount();
        for (int i = 0; i < count; i++) {
            int attr = ta.getIndex(i);
            if (attr == R.styleable.SwipeMenuLayout_swipeEnable) {
                isSwipeEnable = ta.getBoolean(attr, true);
            } else if (attr == R.styleable.SwipeMenuLayout_ios) {
                isIos = ta.getBoolean(attr, true);
            } else if (attr == R.styleable.SwipeMenuLayout_leftSwipe) {
                isLeftSwipe = ta.getBoolean(attr, true);
            }
        }
        ta.recycle();


    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        setClickable(true);//令自己可点击，从而获取触摸事件

        mRightMenuWidths = 0;//由于ViewHolder的复用机制，每次这里要手动恢复初始值
        mHeight = 0;
        int contentWidth = 0;//适配GridLayoutManager，将以第一个子Item(即ContentItem)的宽度为控件宽度
        int childCount = getChildCount();

        //为了子View的高，可以matchParent(参考的FrameLayout 和LinearLayout的Horizontal)
        final boolean measureMatchParentChildren = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        boolean isNeedMeasureChildHeight = false;

        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            //令每一个子View可点击，从而获取触摸事件
            childView.setClickable(true);
            if (childView.getVisibility() != GONE) {
                measureChild(childView, widthMeasureSpec, heightMeasureSpec);
                final MarginLayoutParams lp = (MarginLayoutParams) childView.getLayoutParams();
                mHeight = Math.max(mHeight, childView.getMeasuredHeight()/* + lp.topMargin + lp.bottomMargin*/);
                if (measureMatchParentChildren && lp.height == LayoutParams.MATCH_PARENT) {
                    isNeedMeasureChildHeight = true;
                }
                if (i > 0) {//第一个布局是Left item，从第二个开始才是RightMenu
                    mRightMenuWidths += childView.getMeasuredWidth();
                } else {
                    mContentView = childView;
                    contentWidth = childView.getMeasuredWidth();
                }
            }
        }
        setMeasuredDimension(getPaddingLeft() + getPaddingRight() + contentWidth,
                mHeight + getPaddingTop() + getPaddingBottom());//宽度取第一个Item(Content)的宽度
        mLimit = mRightMenuWidths * 4 / 10;//滑动判断的临界值
        if (isNeedMeasureChildHeight) {//如果子View的height有MatchParent属性的，设置子View高度
            forceUniformHeight(childCount, widthMeasureSpec);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    /**
     * 给MatchParent的子View设置高度
     *
     * @param count
     * @param widthMeasureSpec
     * @see android.widget.LinearLayout# 同名方法
     */
    private void forceUniformHeight(int count, int widthMeasureSpec) {
        // Pretend that the linear layout has an exact size. This is the measured height of
        // ourselves. The measured height should be the max height of the children, changed
        // to accommodate the heightMeasureSpec from the parent
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(),
                MeasureSpec.EXACTLY);//以父布局高度构建一个Exactly的测量参数
        for (int i = 0; i < count; ++i) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (lp.height == LayoutParams.MATCH_PARENT) {
                    int oldWidth = lp.width;//measureChildWithMargins 这个函数会用到宽，所以要保存一下
                    lp.width = child.getMeasuredWidth();
                    // Remeasure with new dimensions
                    measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0);
                    lp.width = oldWidth;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        int left = 0 + getPaddingLeft();
        int right = 0 + getPaddingLeft();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                if (i == 0) {//第一个子View是内容 宽度设置为全屏
                    childView.layout(left, getPaddingTop(), left + childView.getMeasuredWidth(), getPaddingTop() + childView.getMeasuredHeight());
                    left = left + childView.getMeasuredWidth();
                } else {
                    if (isLeftSwipe) {
                        childView.layout(left, getPaddingTop(), left + childView.getMeasuredWidth(), getPaddingTop() + childView.getMeasuredHeight());
                        left = left + childView.getMeasuredWidth();
                    } else {
                        childView.layout(right - childView.getMeasuredWidth(), getPaddingTop(), right, getPaddingTop() + childView.getMeasuredHeight());
                        right = right - childView.getMeasuredWidth();
                    }

                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if(isSwipeEnable){
            acquireVelocityTracker(ev);
            switch (ev.getAction()){
                case MotionEvent.ACTION_DOWN:
                    isUserSwiped = false;//判断手指起始落点，如果距离属于滑动了，就屏蔽一切点击事件。
                    isUnMoved = true;//仿QQ，侧滑菜单展开时，点击内容区域，关闭侧滑菜单。
                    iosInterceptFlag = false;//每次DOWN时，默认是不拦截的
                    if (isTouching) {//如果有别的指头摸过了，那么就return false。这样后续的move..等事件也不会再来找这个View了。
                        return false;
                    } else {
                        isTouching = true;//第一个摸的指头，赶紧改变标志，宣誓主权。
                    }
                    mLastP.set(ev.getRawX(), ev.getRawY());
                    mFirstP.set(ev.getRawX(), ev.getRawY());//判断手指起始落点，如果距离属于滑动了，就屏蔽一切点击事件。

                    //如果down，view和cacheview不一样，则立马让它还原。且把它置为null
                    if (mViewCache != null) {
                        if (mViewCache != this) {
                            mViewCache.smoothClose(0);
                            iosInterceptFlag = isIos;//IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。
                        }
                        //只要有一个侧滑菜单处于打开状态， 就不给外层布局上下滑动了
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    //求第一个触点的id， 此时可能有多个触点，但至少一个，计算滑动速率用
                    mPointerId = ev.getPointerId(0);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    //判断手指起始落点，如果距离属于滑动了，就屏蔽一切点击事件。
                    if (Math.abs(ev.getRawX() - mFirstP.x) > mScaleTouchSlop) {
                        isUserSwiped = true;
                    }
                    //IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。滑动也不该出现
                    if (!iosInterceptFlag && hasConsume) {//且滑动了 才判断是否要收起、展开menu
//                        Log.e(TAG, "dispatchTouchEvent() " + MotionEvent.actionToString(ev.getAction()) + ",--------");
                        //求伪瞬时速度
                        mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                        float velocityX = mVelocityTracker.getXVelocity(mPointerId);
//                        Log.e(TAG, "dispatchTouchEvent()  velocityX:"+velocityX+" --------");
                        if (Math.abs(velocityX) > 1000) {//滑动速度超过阈值
//                            Log.e(TAG, "dispatchTouchEvent()  Math.abs(velocityX) > 1000 --------");
                            if (velocityX < -1000) {
                                if (isLeftSwipe) {//左滑
                                    //平滑展开Menu
                                    smoothExpand(Math.abs(velocityX));

                                } else {
                                    //平滑关闭Menu
                                    smoothClose(Math.abs(velocityX));
                                }
                            } else {
                                if (isLeftSwipe) {//左滑
                                    // 平滑关闭Menu
                                    smoothClose(Math.abs(velocityX));
                                } else {
                                    //平滑展开Menu
                                    smoothExpand(Math.abs(velocityX));
                                }
                            }
                        } else {
//                            Log.e(TAG, "dispatchTouchEvent()  Math.abs(velocityX) < 1000 --------");
                            if (velocityX < 0) {
                                if (isLeftSwipe) {//左滑
                                    //平滑展开Menu
                                    smoothExpand(Math.abs(velocityX));
                                } else {
                                    //平滑关闭Menu
                                    smoothClose(Math.abs(velocityX));
                                }
                            } else if(velocityX > 0){
                                if (isLeftSwipe) {//左滑
                                    // 平滑关闭Menu
                                    smoothClose(Math.abs(velocityX));
                                } else {
                                    //平滑展开Menu
                                    smoothExpand(Math.abs(velocityX));
                                }
                            }else if(velocityX == 0){
                                if (Math.abs(getScrollX()) > mLimit) {//否则就判断滑动距离
                                    //平滑展开Menu
                                    smoothExpand(0);
                                } else {
                                    // 平滑关闭Menu
                                    smoothClose(0);
                                }
                            }
                        }
                    }else{
//                        Log.e(TAG, "dispatchTouchEvent() " + MotionEvent.actionToString(ev.getAction()) + ",........");
                        if (isLeftSwipe) {
                            if (getScrollX() > mScaleTouchSlop) {
                                //这里判断落点在内容区域屏蔽点击，内容区域外，允许传递事件继续向下的的。。。
                                if (ev.getX() < getWidth() - getScrollX()) {
                                    //仿QQ，侧滑菜单展开时，点击内容区域，关闭侧滑菜单。
                                    if (isUnMoved) {
                                        smoothClose(0);
                                    }
                                }
                            }
                        } else {
                            if (-getScrollX() > mScaleTouchSlop) {
                                if (ev.getX() > -getScrollX()) {//点击范围在菜单外 屏蔽
                                    //仿QQ，侧滑菜单展开时，点击内容区域，关闭侧滑菜单。
                                    if (isUnMoved) {
                                        smoothClose(0);
                                    }
                                }
                            }
                        }
                    }
                    //释放
                    releaseVelocityTracker();
                    isTouching = false;
                    hasConsume = false;
                    break;
            }
        }
        boolean superd = super.dispatchTouchEvent(ev);
//        Log.e(TAG, "dispatchTouchEvent() " + MotionEvent.actionToString(ev.getAction()) + ",super:"+superd+",isTouching:"+isTouching);
        return superd;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        Log.e(TAG, "onInterceptTouchEvent() " + MotionEvent.actionToString(ev.getAction()) + ",isTouching:"+isTouching);
        //禁止侧滑时，点击事件不受干扰。
        if (isSwipeEnable ) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isLeftSwipe) {
                        if (getScrollX() > mScaleTouchSlop) {
                            //这里判断落点在内容区域屏蔽点击，内容区域外，允许传递事件继续向下的的。。。
                            if (ev.getX() < getWidth() - getScrollX()) {
                                return true;//true表示拦截
                            }
                        }
                    } else {
                        if (-getScrollX() > mScaleTouchSlop) {
                            if (ev.getX() > -getScrollX()) {//点击范围在菜单外 屏蔽
                                return true;
                            }
                        }
                    }
                    break;
                // fix 长按事件和侧滑的冲突。
                case MotionEvent.ACTION_MOVE:
                    //屏蔽滑动时的事件
                    if (Math.abs(ev.getRawX() - mFirstP.x) > mScaleTouchSlop) {
                        return true;
                    }
                    break;
            }
            //模仿IOS 点击其他区域关闭：
            if (iosInterceptFlag) {
                //IOS模式开启，且当前有菜单的View，且不是自己的 拦截点击事件给子View
                return true;
            }
        }
        boolean superd = super.onInterceptTouchEvent(ev);
//        Log.e(TAG, "onInterceptTouchEvent() " + MotionEvent.actionToString(ev.getAction()) + ",super:"+superd+",isTouching:"+isTouching);
        return superd;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
//        Log.e(TAG, "onTouchEvent() start " + MotionEvent.actionToString(ev.getAction()) + ",isTouching:"+isTouching);
        if (isSwipeEnable) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    hasConsume = true;
                    //IOS模式开启的话，且当前有侧滑菜单的View，且不是自己的，就该拦截事件咯。滑动也不该出现
                    if (iosInterceptFlag) {
                        break;
                    }
                    float gap = mLastP.x - ev.getRawX();
                    //为了在水平滑动中禁止父类ListView等再竖直滑动
                    if (Math.abs(gap) > 10 || Math.abs(getScrollX()) > 10) {//2016 09 29 修改此处，使屏蔽父布局滑动更加灵敏，
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    //仿QQ，侧滑菜单展开时，点击内容区域，关闭侧滑菜单。begin
                    if (Math.abs(gap) > mScaleTouchSlop) {
                        isUnMoved = false;
                    }
                    scrollBy((int) (gap), 0);//滑动使用scrollBy
                    //越界修正
                    if (isLeftSwipe) {//左滑
                        if (getScrollX() < 0) {
                            scrollTo(0, 0);
                        }
                        if (getScrollX() > mRightMenuWidths) {
                            scrollTo(mRightMenuWidths, 0);
                        }
                    } else {//右滑
                        if (getScrollX() < -mRightMenuWidths) {
                            scrollTo(-mRightMenuWidths, 0);
                        }
                        if (getScrollX() > 0) {
                            scrollTo(0, 0);
                        }
                    }

                    mLastP.set(ev.getRawX(), ev.getRawY());
                    break;
                default:
                    break;
            }
//            Log.e(TAG, "onTouchEvent() end " + MotionEvent.actionToString(ev.getAction()) + ",isTouching:"+isTouching);
            return true;
        }else{
//            Log.e(TAG, "onTouchEvent() super() " );
            return super.onTouchEvent(ev);
        }
    }

    /**
     * 平滑展开
     */
    private ValueAnimator mExpandAnim, mCloseAnim;

    private boolean isExpand;//代表当前是否是展开状态

    public void smoothExpand(float velocityX) {
        //展开就加入ViewCache：
        mViewCache = SwipeMenuLayout.this;

        //侧滑菜单展开，屏蔽content长按
        if (null != mContentView) {
            mContentView.setLongClickable(false);
        }

        cancelAnim();
        int scrollX = getScrollX();
        mExpandAnim = ValueAnimator.ofInt(scrollX, isLeftSwipe ? mRightMenuWidths : -mRightMenuWidths);
        mExpandAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((Integer) animation.getAnimatedValue(), 0);
            }
        });
        mExpandAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isExpand = true;
            }
        });
        int duration = 200;
//        Log.e("smoothExpand","velocityX:"+velocityX);
        if(velocityX != 0){
            if(velocityX > maxSpeed) velocityX = maxSpeed;
            if(velocityX < 300) velocityX = 300;
            duration = (int)(Math.abs(scrollX)*500 / velocityX + 0.5F);
        }
        mExpandAnim.setDuration(duration).start();
    }

    /**
     * 每次执行动画之前都应该先取消之前的动画
     */
    private void cancelAnim() {
        if (mCloseAnim != null && mCloseAnim.isRunning()) {
            mCloseAnim.cancel();
        }
        if (mExpandAnim != null && mExpandAnim.isRunning()) {
            mExpandAnim.cancel();
        }
    }

    /**
     * 平滑关闭
     */
    public void smoothClose(float velocityX) {
        mViewCache = null;

        //侧滑菜单展开，屏蔽content长按
        if (null != mContentView) {
            mContentView.setLongClickable(true);
        }

        cancelAnim();
        int scrollX = getScrollX();
        mCloseAnim = ValueAnimator.ofInt(scrollX, 0);
        mCloseAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((Integer) animation.getAnimatedValue(), 0);
            }
        });
        mCloseAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isExpand = false;

            }
        });
//        Log.e("smoothClose","velocityX:"+velocityX);
        int duration = 200;
        if(velocityX != 0){
            if(velocityX > maxSpeed) velocityX = maxSpeed;
            if(velocityX < 300) velocityX = 300;
            duration = (int)(Math.abs(scrollX)*500 / velocityX + 0.5F);
        }
        mCloseAnim.setDuration(duration).start();
        //Log.d(TAG, "smoothClose() called with:getScrollX() " + getScrollX());
    }


    /**
     * @param event 向VelocityTracker添加MotionEvent
     * @see VelocityTracker#obtain()
     * @see VelocityTracker#addMovement(MotionEvent)
     */
    private void acquireVelocityTracker(final MotionEvent event) {
        if (null == mVelocityTracker) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * * 释放VelocityTracker
     *
     * @see VelocityTracker#clear()
     * @see VelocityTracker#recycle()
     */
    private void releaseVelocityTracker() {
        if (null != mVelocityTracker) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    //每次ViewDetach的时候，判断一下 ViewCache是不是自己，如果是自己，关闭侧滑菜单，且ViewCache设置为null，
    // 理由：1 防止内存泄漏(ViewCache是一个静态变量)
    // 2 侧滑删除后自己后，这个View被Recycler回收，复用，下一个进入屏幕的View的状态应该是普通状态，而不是展开状态。
    @Override
    protected void onDetachedFromWindow() {
        if (this == mViewCache) {
            mViewCache.smoothClose(0);
            mViewCache = null;
        }
        super.onDetachedFromWindow();
    }

    //展开时，禁止长按
    @Override
    public boolean performLongClick() {
        if (Math.abs(getScrollX()) > mScaleTouchSlop) {
            return false;
        }
        return super.performLongClick();
    }

    /**
     * 快速关闭。
     * 用于 点击侧滑菜单上的选项,同时想让它快速关闭(删除 置顶)。
     * 这个方法在ListView里是必须调用的，
     * 在RecyclerView里，视情况而定，如果是mAdapter.notifyItemRemoved(pos)方法不用调用。
     */
    public void quickClose() {
        if (this == mViewCache) {
            //先取消展开动画
            cancelAnim();
            mViewCache.scrollTo(0, 0);//关闭
            mViewCache = null;
        }
    }

}

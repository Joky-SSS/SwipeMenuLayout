package com.jokysss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.jokysss.swipemenulayout.R;

import java.util.HashSet;
import java.util.Set;

public class SwipeMenuLayoutNew extends ViewGroup {
    private static final String TAG = "SwipeMenuLayoutNew";

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
    private static SwipeMenuLayoutNew mViewCache;

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
     * 左滑右滑的开关,默认左滑打开菜单
     */
    private boolean isLeftSwipe;
    private boolean hasConsume;
    private ViewDragHelper mDraggerHelper;
    private SwipeMenuLayoutNew self;
    private LinearLayout mContanier;
    private Set<View> mChildSet = new HashSet<>();

    public SwipeMenuLayoutNew(Context context) {
        this(context, null);
    }

    public SwipeMenuLayoutNew(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuLayoutNew(Context context, AttributeSet attrs, int defStyleAttr) {
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
    public SwipeMenuLayoutNew setIos(boolean ios) {
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
     *
     * @return
     */
    public SwipeMenuLayoutNew setLeftSwipe(boolean leftSwipe) {
        isLeftSwipe = leftSwipe;
        return this;
    }

    /**
     * 返回ViewCache
     *
     * @return
     */
    public static SwipeMenuLayoutNew getViewCache() {
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
        mDraggerHelper = ViewDragHelper.create(this, 1.0f, mDraggerCallback);
        self = this;
        mContanier = new LinearLayout(context);
        mContanier.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mContanier.setClickable(true);
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
                //后续计划加入上滑、下滑，则将不再支持Item的margin
                measureChild(childView, widthMeasureSpec, heightMeasureSpec);
                final MarginLayoutParams lp = (MarginLayoutParams) childView.getLayoutParams();
                mHeight = Math.max(mHeight, childView.getMeasuredHeight()/* + lp.topMargin + lp.bottomMargin*/);
                if (measureMatchParentChildren && lp.height == LayoutParams.MATCH_PARENT) {
                    isNeedMeasureChildHeight = true;
                }
                if (i > 0) {//第一个布局是Left item，从第二个开始才是RightMenu
                    mRightMenuWidths += childView.getMeasuredWidth();
                    mChildSet.add(childView);
                } else {
                    mContentView = childView;
                    contentWidth = childView.getMeasuredWidth();
                }
            }
        }
        setMeasuredDimension(getPaddingLeft() + getPaddingRight() + contentWidth, mHeight + getPaddingTop() + getPaddingBottom());//宽度取第一个Item(Content)的宽度
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
     *
     * @see android.widget.LinearLayout# 同名方法
     */
    private void forceUniformHeight(int count, int widthMeasureSpec) {
        // Pretend that the linear layout has an exact size. This is the measured height of
        // ourselves. The measured height should be the max height of the children, changed
        // to accommodate the heightMeasureSpec from the parent
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);//以父布局高度构建一个Exactly的测量参数
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


    /**
     * 平滑展开
     */
    private ValueAnimator mExpandAnim, mCloseAnim;

    private boolean isExpand;//代表当前是否是展开状态

    public void smoothExpand() {
        //展开就加入ViewCache：
        mViewCache = SwipeMenuLayoutNew.this;

        //2016 11 13 add 侧滑菜单展开，屏蔽content长按
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
        long duration = (long) ((mRightMenuWidths - Math.abs(scrollX)) * 1.0 / mRightMenuWidths * (getChildCount() - 1) * 100);
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
    public void smoothClose() {
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
        long duration = (long) (Math.abs(scrollX) * 1.0 / mRightMenuWidths * (getChildCount() - 1) * 100);
        mCloseAnim.setDuration(duration).start();
    }


    /**
     * @param event 向VelocityTracker添加MotionEvent
     *
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
            mViewCache.smoothClose();
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
        close();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mDraggerHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (mViewCache != null && mViewCache != SwipeMenuLayoutNew.this) {
                    mViewCache.close();
                    requestDisallowIntercept(true);
                    return false;
                }
            }
            break;
            case MotionEvent.ACTION_UP: {
                if (mViewCache == null) {
                    requestDisallowIntercept(false);
                }
            }
            break;
        }
        mDraggerHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        if (mDraggerHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private ViewDragHelper.Callback mDraggerCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            requestDisallowIntercept(true);
            mDraggerHelper.captureChildView(mContentView, pointerId);
            return false;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return mRightMenuWidths;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return 0;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            Log.e("Xup", "child.getLeft:" + child.getLeft() + ",left:" + left + ",dx:" + dx + ",getScrollX():" + self.getScrollX());
            return Math.max(Math.min(0, left), -mRightMenuWidths);
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            for (View view : mChildSet) {
                view.offsetLeftAndRight(dx);
            }
            ViewCompat.postInvalidateOnAnimation(SwipeMenuLayoutNew.this);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            Log.e("Xup", "xvel:" + xvel + ",yvel:" + yvel);
            clampRelease(xvel, releasedChild.getLeft());
            requestDisallowIntercept(mViewCache != null);
        }
    };

    private void clampRelease(float xvel, int finalLeft) {
        //        if (xvel > 1500) {
        //            close();
        //        } else if (xvel < -1500) {
        //            open();
        //        } else
        if (xvel > 0) {
            //            if (mRightMenuWidths + finalLeft > mLimit) {
            close();
            //            } else {
            //                open();
            //            }
        } else if (xvel < 0) {
            //            if (mLimit + finalLeft >= 0) {
            open();
            //            } else {
            //                close();
            //            }
        } else {
            if (mRightMenuWidths + 2 * finalLeft >= 0) {
                close();
            } else {
                open();
            }
        }
        ViewCompat.postInvalidateOnAnimation(SwipeMenuLayoutNew.this);
    }

    public void open() {
        if (mContentView != null) {
            mDraggerHelper.smoothSlideViewTo(mContentView, -mRightMenuWidths, 0);
            ViewCompat.postInvalidateOnAnimation(SwipeMenuLayoutNew.this);
            mViewCache = SwipeMenuLayoutNew.this;
        }
    }

    public void close() {
        if (mContentView != null) {
            mDraggerHelper.smoothSlideViewTo(mContentView, 0, 0);
            ViewCompat.postInvalidateOnAnimation(SwipeMenuLayoutNew.this);
            mViewCache = null;
        }
    }

    private void requestDisallowIntercept(boolean disallow) {
        if (getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    private int clampScrollX(int scrollX) {
        return Math.max(0, Math.min(scrollX, mRightMenuWidths));
    }
}

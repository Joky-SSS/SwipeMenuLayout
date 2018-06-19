package com.jokysss;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

public class TestListView extends ListView {
    private ViewDragHelper mDraggerHelper;
    private int mPosition;

    public TestListView(Context context) {
        this(context, null);
    }

    public TestListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TestListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mDraggerHelper = ViewDragHelper.create(this, 1.0f, mDraggerCallback);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return super.onInterceptTouchEvent(event) || mDraggerHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mPosition = pointToPosition((int) event.getX(), (int) event.getY());
        mDraggerHelper.processTouchEvent(event);
        boolean handle = super.onTouchEvent(event);

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
            View view = getChildAt(mPosition - getFirstVisiblePosition());
            mDraggerHelper.captureChildView(view, pointerId);
            return false;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return 100;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return 0;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return Math.max(Math.min(0, left), -100);
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            ViewCompat.postInvalidateOnAnimation(TestListView.this);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            Log.e("Xup", "xvel:" + xvel + ",yvel:" + yvel);
            clampRelease(xvel, releasedChild.getLeft());
        }
    };

    private void clampRelease(float xvel, int finalLeft) {
        ViewCompat.postInvalidateOnAnimation(this);
    }
}

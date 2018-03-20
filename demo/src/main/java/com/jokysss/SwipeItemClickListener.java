package com.jokysss;

import android.view.View;

public interface SwipeItemClickListener {
    void onItemClick(int position, View itemView);

    void onMenuClick(int position, View menuView);
}
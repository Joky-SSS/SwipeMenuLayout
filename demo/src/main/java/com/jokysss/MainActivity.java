package com.jokysss;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;


import com.jokysss.swipemenulayoutdemo.R;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SwipeItemClickListener{
    ListView listView;
    MyAdapter adapter;
    List<String> dataList = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = findViewById(R.id.listview);
        for (int i = 0; i < 20; i++) {
            dataList.add("item:"+i);
        }
        adapter = new MyAdapter(dataList,this);
        listView.setAdapter(adapter);
    }


    @Override
    public void onItemClick(int position, View itemView) {
        Toast.makeText(this,"点击了:"+position,Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMenuClick(int position, View menuView) {
        Toast.makeText(this,"点击了删除:"+position,Toast.LENGTH_SHORT).show();
    }

}

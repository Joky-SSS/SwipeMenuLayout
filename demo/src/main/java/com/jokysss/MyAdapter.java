package com.jokysss;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.jokysss.swipemenulayoutdemo.R;

import java.util.List;

/**
 * Created by Joky on 2018/3/19 0019.
 */

public class MyAdapter extends BaseAdapter implements View.OnClickListener{
    List<String> dataList;
    SwipeItemClickListener l;
    public MyAdapter(List<String> dataList,SwipeItemClickListener listener){
        this.dataList = dataList;
        l = listener;
    }
    @Override
    public int getCount() {
        return dataList==null?0:dataList.size();
    }

    @Override
    public Object getItem(int position) {
        return dataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null){
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item,parent,false);
        }
        TextView text = convertView.findViewById(R.id.text);
        text.setText(dataList.get(position));
        text.setTag(position);
        text.setOnClickListener(this);
        convertView.findViewById(R.id.delete).setTag(position);
        convertView.findViewById(R.id.delete).setOnClickListener(this);
        ((SwipeMenuLayoutNew) convertView).quickClose();
        TextView num = convertView.findViewById(R.id.num);
        TipsView.create((Activity) parent.getContext()).attach(num);
        return convertView;
    }

    @Override
    public void onClick(View v) {
        int position = (Integer) v.getTag();
        if(l != null){
            if(v.getId() == R.id.text){
                l.onItemClick(position,v);
            }else{
                l.onMenuClick(position,v);
            }
        }
    }
}

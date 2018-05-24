package com.jokysss;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.jokysss.swipemenulayoutdemo.R;

public class Main2Activity extends AppCompatActivity {

    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
    }

    public void onClick(View view) {
        Log.e("Xup", "View " + view.getTag());
        if (toast != null)
            toast.cancel();
        toast = Toast.makeText(this, "View " + view.getTag(), Toast.LENGTH_SHORT);
        toast.show();
    }
}

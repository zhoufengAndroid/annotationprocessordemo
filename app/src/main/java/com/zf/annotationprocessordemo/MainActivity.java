package com.zf.annotationprocessordemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.zf.annotationapi.ZFViewBinder;
import com.zf.annotationprocessor.annotation.BindView;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv)
    TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ZFViewBinder.bind(this);
        if (tv!=null){
            tv.setText("绑定成功");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ZFViewBinder.unBind(this);
    }
}

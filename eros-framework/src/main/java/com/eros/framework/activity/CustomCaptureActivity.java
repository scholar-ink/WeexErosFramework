package com.eros.framework.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.eros.framework.R;
import com.eros.framework.manager.ManagerFactory;
import com.eros.framework.manager.impl.dispatcher.DispatchEventManager;
import com.eros.framework.model.CameraResultBean;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

public class CustomCaptureActivity extends AppCompatActivity {


    /**
     * 条形码扫描管理器
     */
    private CaptureManager mCaptureManager;

    /**
     * 条形码扫描视图
     */
    private DecoratedBarcodeView mBarcodeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_zxing_layout);


        mBarcodeView = (DecoratedBarcodeView) findViewById(R.id.zxing_barcode_scanner);

        Button button = (Button) findViewById(R.id.button_click);
        TextView textHand = (TextView) findViewById(R.id.text_hand);
        Button buttonBack = (Button) findViewById(R.id.button_back);

        Intent intent = getIntent();

        String createStatus = intent.getStringExtra("createStatus");
        if(TextUtils.equals("1",createStatus)){
            button.setVisibility(View.INVISIBLE);
            textHand.setVisibility(View.INVISIBLE);
        }else {
            button.setVisibility(View.VISIBLE);
            textHand.setVisibility(View.VISIBLE);
        }

        //在活动中可以通过findViewById来获取布局文件中定义的元素
        button.setOnClickListener(new View.OnClickListener() {
            //为button注册一个监听器
            public void onClick(View v) {

                CameraResultBean bean = new CameraResultBean();

                    bean.text = "1";


                ManagerFactory.getManagerService(DispatchEventManager.class).getBus
                        ().post(bean);
                //销毁这个页面，
                finish();
            }
        });
        buttonBack.setOnClickListener(new View.OnClickListener() {
            //为button注册一个监听器
            public void onClick(View v) {
                CameraResultBean bean = new CameraResultBean();

                bean.text = "0";


                ManagerFactory.getManagerService(DispatchEventManager.class).getBus
                        ().post(bean);
                //销毁这个页面，
                finish();
            }
        });
        mCaptureManager = new CaptureManager(this, mBarcodeView);
        mCaptureManager.initializeFromIntent(getIntent(), savedInstanceState);
        mCaptureManager.decode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCaptureManager.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCaptureManager.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCaptureManager.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mCaptureManager.onSaveInstanceState(outState);
    }

    /**
     * 权限处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        mCaptureManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * 按键处理
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mBarcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}



package com.sddd.tfn.mediacodecdemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Main2Activity extends AppCompatActivity implements View.OnClickListener {

    private Button mTransBtn = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                200);
        mTransBtn = (Button) findViewById(R.id.thread_trans_btn);
        mTransBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.thread_trans_btn:
                transBtnOnClick();
                break;

        }
    }

    private void transBtnOnClick() {
        Toast.makeText(this, "开始转码", Toast.LENGTH_SHORT).show();
//        sView.setVisibility(View.VISIBLE);
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        final Transcode transcode = Transcode.getInstance();
        transcode.setEncodeType(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_AVC);
        transcode.setIOPath(path + "/out_20161114_211554.mp4", path + "/result.mp4");
//        transcode.prepare();
        transcode.startAsync();
//        transcode.setOnCompleteListener(new Transcode.OnCompleteListener() {
//            @Override
//            public void completed() {
//                Toast.makeText(Main2Activity.this, "转码完成", Toast.LENGTH_SHORT).show();
//                transcode.release();
//            }
//        });
    }
}

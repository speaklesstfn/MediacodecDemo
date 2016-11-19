package com.sddd.tfn.mediacodecdemo;

import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class Main2Activity extends AppCompatActivity implements View.OnClickListener {

    private Button mTransBtn = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

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
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        final Transcode transcode = Transcode.getInstance();
        transcode.setEncodeType(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_MPEG);
        transcode.setIOPath(path + "/video_20161111_164706.mp4", path + "/result.mp4");
        transcode.prepare();
        transcode.startAsync();
        transcode.setOnCompleteListener(new Transcode.OnCompleteListener() {
            @Override
            public void completed() {
                transcode.release();
            }
        });
    }
}

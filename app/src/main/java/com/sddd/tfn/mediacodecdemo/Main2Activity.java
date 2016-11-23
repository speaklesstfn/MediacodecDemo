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
    private static final String TAG = "Main2Activity";
    private Button mTransBtn = null;
    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/video_20161117_123330.mp4";
    private TranscodeThread mTranscode = null;


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
        if (null == mTranscode) {
            mTranscode = new TranscodeThread();
            mTranscode.start();
        }
    }

    private class TranscodeThread extends Thread {
        private MediaExtractor extractor;
        private MediaCodec decoder;

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void run() {
            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(SAMPLE);

                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        extractor.selectTrack(i);
                        decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(format, null, null, 0);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (decoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }

            decoder.start();

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean isOutEnd = false;
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();
            int inputChunk = 0;
            while (!isOutEnd) {
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to decoder, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
//                            Log.d("HAHAHAHA", "submitted frame " + inputChunk);
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                            inputChunk++;
                        }
                    } else {
                        Log.d(TAG, "inIndex is -1");
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = decoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "New format " + decoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer timed out!");
                        break;
                    default:
                        Log.e("AFFFFFFFFF", "decoded buffer info ,size :" + info.size);
                        ByteBuffer buffer = decoder.getOutputBuffer(outIndex);
//                        Log.e("AFFFFFFFFF","decoded buffer info ,size :" + info.size);
                        Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                        // We use a very simple clock to keep the video FPS, or the video
                        // playback will be too fast
//                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
//                            try {
//                                sleep(10);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                                break;
//                            }
//                        }

                        byte[] chunkYUV = new byte[info.size];
                        buffer.get(chunkYUV);
                        buffer.clear();
                        if (chunkYUV.length > 0) {
                            Log.e("AFFFFFFFFF", "decoded chunk :" + chunkYUV.length);
                        }
                        decoder.releaseOutputBuffer(outIndex, false);
                        chunkYUV = null;
                        break;
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    isOutEnd = true;
                    break;
                }
            }

            decoder.stop();
            decoder.release();
            extractor.release();

        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void encodeYUV(byte[] chunkYUV) {
        int inputIndex;
        int encoderStatus;
        showLog("doEncode");
        if (chunkYUV == null || chunkYUV.length == 0) {
            return;
        }

        inputIndex = videoEncode.dequeueInputBuffer(10000);
        ByteBuffer inputBuffer = vEncodeInputBuffers[inputIndex];
        inputBuffer.clear();//同解码器
        inputBuffer.limit(chunkYUV.length);
        inputBuffer.put(chunkYUV);//YUV数据填充给inputBuffer
        videoEncode.queueInputBuffer(inputIndex, 0, chunkYUV.length, 0, 0);//通知编码器 编码

        encoderStatus = videoEncode.dequeueOutputBuffer(vEncodeBufferInfo, 10000);//同解码器
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {//-1
            // no output available yet
            Log.d(TAG, "no output from encoder available");
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {//-3
            vEncodeOutputBuffers = videoEncode.getOutputBuffers();
            Log.d(TAG, "encoder output buffers changed");
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//-2
            if (!mMuxerStarted) {
                MediaFormat newFormat = videoEncode.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                videoTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
                mMuxerStarted = true;
            }
        } else if (encoderStatus < 0) {
            Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                    encoderStatus);
            // let's ignore it
        } else {
            while (encoderStatus >= 0) {
                ByteBuffer encodedData = vEncodeOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((vEncodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    vEncodeBufferInfo.size = 0;
                }

                if (vEncodeBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(vEncodeBufferInfo.offset);
                    encodedData.limit(vEncodeBufferInfo.offset + vEncodeBufferInfo.size);

                    mediaMuxer.writeSampleData(videoTrackIndex, encodedData, vEncodeBufferInfo);
                    Log.d(TAG, "sent " + vEncodeBufferInfo.size + " bytes to muxer");
                }

                videoEncode.releaseOutputBuffer(encoderStatus, false);

                if ((vEncodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "end of stream reached");
                }
                encoderStatus = videoEncode.dequeueOutputBuffer(vEncodeBufferInfo, 10000);
            }
        }
    }
}

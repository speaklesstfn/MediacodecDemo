package com.sddd.tfn.mediacodecdemo;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by tfn on 16-11-18.
 */

public class Transcode {
    private static final String TAG = "Transcode";
    private String vEncodeType;//视频编码器
    private String aEncodeType;//音频编码器
    private String srcPath;
    private String dstPath;
    private MediaExtractor mediaExtractor;//分离器对象

    private int videoTrackIndex = -1;//视频的Track号
    private int audioTrackIndex = -1;//音频的Track号

    byte[] header_sps = {0, 0, 0, 1, 103, 100, 0, 40, -84, 52, -59, 1, -32, 17, 31, 120, 11, 80, 16, 16, 31, 0, 0, 3, 3, -23, 0, 0, -22, 96, -108};
    byte[] header_pps = {0, 0, 0, 1, 104, -18, 60, -128};

    private MediaCodec videoDecode;//视频解码对象
    private ByteBuffer[] vDecodeInputBuffers;
    private ByteBuffer[] vDecodeOutputBuffers;
    private MediaCodec.BufferInfo vDecodeBufferInfo;


    private MediaCodec videoEncode;//视频编码对象
    private ByteBuffer[] vEncodeInputBuffers;
    private ByteBuffer[] vEncodeOutputBuffers;
    private MediaCodec.BufferInfo vEncodeBufferInfo;

    private long vDecodeSize;
    private ArrayList<byte[]> chunkYUVDataContainer;//YUV数据块容器


    private OnCompleteListener onCompleteListener;

    private MediaMuxer mediaMuxer;//混合器对象
    private ByteBuffer muxBuffer;

    public static Transcode getInstance() {
        return new Transcode();
    }

    /**
     * 设置编码器类型
     *
     * @param vEncodeType
     * @param aEncodeType
     */
    public void setEncodeType(String vEncodeType, String aEncodeType) {
        this.vEncodeType = vEncodeType;
        this.aEncodeType = aEncodeType;
    }

    /**
     * 设置输入输出文件位置
     *
     * @param srcPath
     * @param dstPath
     */
    public void setIOPath(String srcPath, String dstPath) {
        this.srcPath = srcPath;
        this.dstPath = dstPath;
    }

    /**
     * 此类已经过封装
     * 调用prepare方法 会初始化Decode 、Encode 、输入输出流 等一些列操作
     */
    public void prepare() {

        if (vEncodeType == null) {
            throw new IllegalArgumentException("vEncodeType can't be null");
        }

        if (aEncodeType == null) {
            throw new IllegalArgumentException("aEncodeType can't be null");
        }

        if (srcPath == null) {
            throw new IllegalArgumentException("srcPath can't be null");
        }

        if (dstPath == null) {
            throw new IllegalArgumentException("dstPath can't be null");
        }

        chunkYUVDataContainer = new ArrayList<>();
        initMediaDecode();//初始化解码器

//        initMediaEncode();//初始化编码器

    }

    /**
     * 初始化解码器
     */
    private void initMediaDecode() {
        try {
            mediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(srcPath);//媒体文件的位置
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {//遍历媒体轨道 此处我们传入的是视频文件，只取视频
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {//获取视频轨道
                    videoTrackIndex = i;
                    mediaExtractor.selectTrack(videoTrackIndex);//选择此音频轨道
                    videoDecode = MediaCodec.createDecoderByType(mime);//创建视频解码器
                    videoDecode.configure(format, null, null, 0);
                    continue;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (videoDecode == null) {
            showLog("create videoDecode failed");
            return;
        }
        videoDecode.start();//启动videoDecode ，等待传入数据
        vDecodeInputBuffers = videoDecode.getInputBuffers();//videoDecode在此ByteBuffer[]中获取输入数据
        vDecodeOutputBuffers = videoDecode.getOutputBuffers();//videoDecode将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到YUV数据
        vDecodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        showLog("buffers:" + vDecodeInputBuffers.length);
    }

    /**
     * 初始化编码器
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void initMediaEncode() {
        try {
            MediaFormat vEncodeFormat = MediaFormat.createVideoFormat(vEncodeType, 272, 480);//参数对应-> mime type、宽、高
            vEncodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率

            vEncodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            vEncodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            vEncodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
//            vEncodeFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//            vEncodeFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            videoEncode = MediaCodec.createEncoderByType(vEncodeType);
            videoEncode.configure(vEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaMuxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.addTrack(vEncodeFormat);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (videoEncode == null) {
            Log.e(TAG, "create videoEncode failed");
            return;
        }

        if (mediaMuxer == null) {
            Log.e(TAG, "create mediaMuxer failed");
            return;
        }

        videoEncode.start();
        vEncodeInputBuffers = videoEncode.getInputBuffers();
        vEncodeOutputBuffers = videoEncode.getOutputBuffers();
        vEncodeBufferInfo = new MediaCodec.BufferInfo();
        showLog("init Media Encode complete");
        mediaMuxer.start();
        muxBuffer = ByteBuffer.allocate(500 * 1024);
    }

    private boolean codecOver = false;

    /**
     * 开始转码
     * 视频数据{@link #srcPath}先解码成YUV  YUV数据在编码成想要得到的{@link #vEncodeType}视频格式
     * h264->YUV->h264
     */
    public void startAsync() {
        showLog("start");

        new Thread(new DecodeRunnable()).start();
//        new Thread(new EncodeRunnable()).start();

    }

    /**
     * 解码线程
     */
    private class DecodeRunnable implements Runnable {

        @Override
        public void run() {
            while (!codecOver) {
                srcVideoFormatToYUV();
            }
        }
    }

    /**
     * 解码{@link #srcPath}视频文件 得到YUV数据块
     *
     * @return 是否解码完所有数据
     */
    private void srcVideoFormatToYUV() {
//        for (int i = 0; i < vDecodeInputBuffers.length - 1; i++) {
            showLog("h666：");
        int inputIndex = videoDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
//            showLog("srcVideoFormatToYUV," + i + " length:" + vDecodeInputBuffers.length + " inputIndex:" + inputIndex);
        if (inputIndex < 0) {
            codecOver = true;
            return;
        }

        ByteBuffer inputBuffer = vDecodeInputBuffers[inputIndex];//拿到inputBuffer
//            showLog("inputBuffer:" + i + "  ::::" + inputBuffer.toString());
        inputBuffer.clear();//清空之前传入inputBuffer内的数据
        int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
//            showLog("sampleSize:" + sampleSize + "   i:" + i);
        if (sampleSize < 0) {//小于0 代表所有数据已读取完成
            videoDecode.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            codecOver = true;
        } else {
//                showLog("h1：" + i);
            videoDecode.queueInputBuffer(inputIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);//通知MediaDecode解码刚刚传入的数据

//                showLog("h3：" + i);
            mediaExtractor.advance();//MediaExtractor移动到下一取样处

//                showLog("h4：" + i);
            vDecodeSize += sampleSize;
//                showLog("h5：" + i);
        }
//            showLog("h6：" + i);
//        }

        //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
        //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这等待
        int outputIndex = videoDecode.dequeueOutputBuffer(vDecodeBufferInfo, 10000);

        showLog("decodeOutIndex:" + outputIndex);
        ByteBuffer outputBuffer;
        byte[] chunkYUV;
        if (outputIndex >= 0 && outputIndex < vDecodeOutputBuffers.length) {//每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
            outputBuffer = vDecodeOutputBuffers[outputIndex];//拿到用于存放YUV数据的Buffer
            showLog("outputBuffer:" + outputBuffer.toString());
//            if (null == outputBuffer) {
//                break;
//            }
            chunkYUV = new byte[vDecodeBufferInfo.size];//BufferInfo内定义了此数据块的大小
//            outputBuffer.get(chunkYUV);//将Buffer内的数据取出到字节数组中
//            outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据
            putYUVData(chunkYUV);//自己定义的方法，供编码器所在的线程获取数据,下面会贴出代码
            videoDecode.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
//            outputIndex = videoDecode.dequeueOutputBuffer(vDecodeBufferInfo, 10000);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
        }

    }

    /**
     * 将PCM数据存入{@link #chunkYUVDataContainer}
     *
     * @param yuvChunk PCM数据块
     */
    private void putYUVData(byte[] yuvChunk) {
        synchronized (Transcode.class) {//记得加锁
            chunkYUVDataContainer.add(yuvChunk);
        }
    }

    /**
     * 编码线程
     */
    private class EncodeRunnable implements Runnable {

        @Override
        public void run() {
            long t = System.currentTimeMillis();
            while (!codecOver || !chunkYUVDataContainer.isEmpty()) {
                dstVideoFormatFromYUV();
            }
            if (onCompleteListener != null) {
                onCompleteListener.completed();
            }
            showLog("vDecodeSize:" + vDecodeSize + "time:" + (System.currentTimeMillis() - t));
        }
    }

    /**
     * 编码YUV数据 得到{@link #vEncodeType}格式的视频文件，并保存到{@link #dstPath}
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void dstVideoFormatFromYUV() {
        byte[] chunkYUV;
        int inputIndex;
        int outputIndex;

//        showLog("doEncode");
        for (int i = 0; i < vEncodeInputBuffers.length - 1; i++) {
            chunkYUV = getYUVData();//获取解码器所在线程输出的数据 代码后边会贴上
            if (chunkYUV == null) {
                break;
            }
            inputIndex = videoEncode.dequeueInputBuffer(-1);//同解码器
            ByteBuffer inputBuffer = vEncodeInputBuffers[inputIndex];//同解码器
            inputBuffer.clear();//同解码器
            inputBuffer.limit(chunkYUV.length);
            inputBuffer.put(chunkYUV);//YUV数据填充给inputBuffer
            videoEncode.queueInputBuffer(inputIndex, 0, chunkYUV.length, 0, 0);//通知编码器 编码
        }

        outputIndex = videoEncode.dequeueOutputBuffer(vEncodeBufferInfo, 10000);//同解码器
//        boolean isFinish = false;
        ByteBuffer outBuffer;
        while (outputIndex >= 0) {//同解码器
            outBuffer = vEncodeOutputBuffers[outputIndex];
            mediaMuxer.writeSampleData(videoTrackIndex, outBuffer, vEncodeBufferInfo);
            videoEncode.releaseOutputBuffer(outputIndex, false);
            outputIndex = videoEncode.dequeueOutputBuffer(vEncodeBufferInfo, 10000);
        }
    }

    /**
     * 在Container中{@link #chunkYUVDataContainer}取出YUV数据
     *
     * @return YUV数据块
     */
    private byte[] getYUVData() {
        synchronized (Transcode.class) {//记得加锁
//            showLog("getYUV:" + chunkYUVDataContainer.size());
            if (chunkYUVDataContainer.isEmpty()) {
                return null;
            }

            byte[] pcmChunk = chunkYUVDataContainer.get(0);//每次取出index 0 的数据
            chunkYUVDataContainer.remove(pcmChunk);//取出后将此数据remove掉 既能保证YUV数据块的取出顺序 又能及时释放内存
            return pcmChunk;
        }
    }

    /**
     * 释放资源
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void release() {
        if (videoEncode != null) {
            videoEncode.stop();
            videoEncode.release();
            videoEncode = null;
        }

        if (videoDecode != null) {
            videoDecode.stop();
            videoDecode.release();
            videoDecode = null;
        }

        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }

        if (null != mediaMuxer) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }

        if (onCompleteListener != null) {
            onCompleteListener = null;
        }

        showLog("release");
    }

    /**
     * 转码完成回调接口
     */
    public interface OnCompleteListener {
        void completed();
    }

    /**
     * 设置转码完成监听器
     *
     * @param onCompleteListener
     */
    public void setOnCompleteListener(OnCompleteListener onCompleteListener) {
        this.onCompleteListener = onCompleteListener;
    }

    private void showLog(String msg) {
        Log.e(TAG, msg);
    }

}

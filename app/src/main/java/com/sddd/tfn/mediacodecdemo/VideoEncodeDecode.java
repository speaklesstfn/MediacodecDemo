package com.sddd.tfn.mediacodecdemo;

import android.annotation.TargetApi;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by tfn on 16-11-21.
 */

public class VideoEncodeDecode {
    public int mWidth = -1;
    public int mHeight = -1;

    private static final int BUFFER_SIZE = 30;

    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding

    private static final int DEFAULT_TIMEOUT_US = 20000;


    public static int NUMFRAMES = 590;

    private static final String TAG = "EncodeDecodeTest";
    private static final boolean VERBOSE = true;           // lots of logging


    Queue<byte[]> ImageQueue;


    VideoEncode myencoder;
    VideoDecode mydecoder;


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    void VideoCodecPrepare(String videoInputFilePath) {
        mydecoder = new VideoDecode();
        myencoder = new VideoEncode();
        mydecoder.VideoDecodePrepare(videoInputFilePath);
        myencoder.VideoEncodePrepare();

        ImageQueue = new LinkedList<byte[]>();

    }

    void MyProcessing() {
        //do process
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    void VideoEncodeDecodeLoop() {
        //here is decode flag
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        boolean IsImageBufferFull = false;


        MediaCodec.BufferInfo encodeinfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo decodeinfo = new MediaCodec.BufferInfo();
        mWidth = mydecoder.mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = mydecoder.mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

        byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
        byte[] frameDataYV12 = new byte[mWidth * mHeight * 3 / 2];
        ByteBuffer[] encoderInputBuffers = myencoder.mediaCodec.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = myencoder.mediaCodec.getOutputBuffers();


        int generateIndex = 0;

        boolean encodeinputDone = false;
        boolean encoderDone = false;


        while ((!encoderDone) && (!sawOutputEOS)) {

            while (!sawOutputEOS && (!IsImageBufferFull)) {
                if (!sawInputEOS) {
                    int inputBufferId = mydecoder.decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = mydecoder.decoder.getInputBuffer(inputBufferId);
                        int sampleSize = mydecoder.extractor.readSampleData(inputBuffer, 0); //将一部分视频数据读取到inputbuffer中，大小为sampleSize
                        if (sampleSize < 0) {
                            mydecoder.decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            long presentationTimeUs = mydecoder.extractor.getSampleTime();
                            mydecoder.decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                            mydecoder.extractor.advance();  //移动到视频文件的下一个地址
                        }
                    }
                }
                int outputBufferId = mydecoder.decoder.dequeueOutputBuffer(decodeinfo, DEFAULT_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    if ((decodeinfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }
                    boolean doRender = (decodeinfo.size != 0);
                    if (doRender) {
                        //NUMFRAMES++;
                        Image image = mydecoder.decoder.getOutputImage(outputBufferId);

                        byte[] imagedata = mydecoder.getDataFromImage(image, mydecoder.FILE_TypeNV21);
                        ImageQueue.offer(imagedata);
                        if (ImageQueue.size() == BUFFER_SIZE) {
                            IsImageBufferFull = true;
                        }


                        image.close();
                        mydecoder.decoder.releaseOutputBuffer(outputBufferId, true);
                    }
                }
            }


            //MyProcessing();


            while ((!encoderDone) && IsImageBufferFull) {

                if (!encodeinputDone) {
                    int inputBufIndex = myencoder.mediaCodec.dequeueInputBuffer(DEFAULT_TIMEOUT_US);

                    if (inputBufIndex >= 0) {
                        long ptsUsec = myencoder.computePresentationTime(generateIndex);
                        if (generateIndex == NUMFRAMES) {

                            myencoder.mediaCodec.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            encodeinputDone = true;
                            if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");


                        } else {


                            frameData = ImageQueue.poll();

                            ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                            // the buffer should be sized to hold one full frame

                            inputBuf.clear();
                            inputBuf.put(frameData);
                            myencoder.mediaCodec.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);

                            if (ImageQueue.size() == 0) {
                                IsImageBufferFull = false;
                            }

                            if (VERBOSE) Log.d(TAG, "submitted frame " + generateIndex + " to enc");
                        }
                        generateIndex++;
                    } else {
                        // either all in use, or we timed out during initial setup
                        if (VERBOSE) Log.d(TAG, "input buffer not available");
                    }
                }
                // Check for output from the encoder.  If there's no output yet, we either need to
                // provide more input, or we need to wait for the encoder to work its magic.  We
                // can't actually tell which is the case, so if we can't get an output buffer right
                // away we loop around and see if it wants more input.
                //
                // Once we get EOS from the encoder, we don't need to do this anymore.
                if (!encoderDone) {
                    int encoderStatus = myencoder.mediaCodec.dequeueOutputBuffer(encodeinfo, DEFAULT_TIMEOUT_US);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        encoderOutputBuffers = myencoder.mediaCodec.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // not expected for an encoder
                        MediaFormat newFormat = myencoder.mediaCodec.getOutputFormat();
                        if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);

                        if (myencoder.mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }

                        Log.d(TAG, "encoder output format changed: " + newFormat);

                        // now that we have the Magic Goodies, start the muxer
                        myencoder.mTrackIndex = myencoder.mMuxer.addTrack(newFormat);
                        myencoder.mMuxer.start();
                        myencoder.mMuxerStarted = true;

                    } else if (encoderStatus < 0) {
                        Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            Log.d(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                        }

                        if ((encodeinfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {

                            MediaFormat format =
                                    MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                            format.setByteBuffer("csd-0", encodedData);

                            encodeinfo.size = 0;

                        } else {
                            // Get a decoder input buffer, blocking until it's available

                            if ((encodeinfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                encoderDone = true;
                            if (VERBOSE)
                                Log.d(TAG, "passed " + encodeinfo.size + " bytes to decoder"
                                        + (encoderDone ? " (EOS)" : ""));
                        }


                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                        if (encodeinfo.size != 0) {
                            encodedData.position(encodeinfo.offset);
                            encodedData.limit(encodeinfo.offset + encodeinfo.size);
                            myencoder.mMuxer.writeSampleData(myencoder.mTrackIndex, encodedData, encodeinfo);

                        }
                        myencoder.mediaCodec.releaseOutputBuffer(encoderStatus, false);
                    }
                }


            }


        }
    }


    public void close() {
        myencoder.close();
        mydecoder.close();
    }

}


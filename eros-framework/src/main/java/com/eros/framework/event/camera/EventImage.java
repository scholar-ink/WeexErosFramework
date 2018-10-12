package com.eros.framework.event.camera;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.eros.framework.constant.Constant;
import com.eros.framework.constant.WXEventCenter;
import com.eros.framework.manager.ManagerFactory;
import com.eros.framework.manager.impl.ImageManager;
import com.eros.framework.manager.impl.ParseManager;
import com.eros.framework.manager.impl.PersistentManager;
import com.eros.framework.manager.impl.dispatcher.DispatchEventManager;
import com.eros.framework.model.ScanImageBean;
import com.eros.framework.model.UploadImageBean;
import com.eros.framework.model.UploadResultBean;
import com.eros.framework.model.WeexEventBean;
import com.eros.framework.utils.JsPoster;
import com.eros.framework.utils.PermissionUtils;
import com.eros.wxbase.EventGate;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.common.HybridBinarizer;
import com.squareup.otto.Subscribe;
import com.yanzhenjie.zbar.Image;
import com.yanzhenjie.zbar.ImageScanner;
import com.yanzhenjie.zbar.Symbol;
import com.yanzhenjie.zbar.SymbolSet;
import java.util.Arrays;
import com.taobao.weex.bridge.JSCallback;

/**
 * Created by liuyuanxiao on 18/1/4.
 */

public class EventImage extends EventGate {
    private JSCallback mPickCallback;

    @Override
    public void perform(Context context, WeexEventBean weexEventBean, String type) {

        String params = weexEventBean.getJsParams();

        if (WXEventCenter.EVENT_IMAGE_PICK.equals(type)) {
            pick(weexEventBean.getJsParams(), context, weexEventBean.getJscallback());
        } else if (WXEventCenter.EVENT_IMAGE_SCAN.equals(type)) {
            scan(params, context, weexEventBean.getJscallback());
        }
    }

    public void pick(String json, Context context, JSCallback jsCallback) {
        //Manifest.permission.READ_EXTERNAL_STORAGE 权限申请
        if (!PermissionUtils.checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return;
        }
        mPickCallback = jsCallback;
        UploadImageBean bean = ManagerFactory.getManagerService(ParseManager.class).parseObject
                (json, UploadImageBean.class);
        ManagerFactory.getManagerService(DispatchEventManager.class).getBus().register(this);
        ImageManager imageManager = ManagerFactory.getManagerService(ImageManager.class);
        if (bean.allowCrop && bean.maxCount == 1) {
            //上传头像
            imageManager.pickAvatar(context, bean, Constant.ImageConstants.IMAGE_NOT_UPLOADER_PICKER);
        } else if (bean.maxCount > 0) {
            imageManager.pickPhoto(context, bean, Constant.ImageConstants.IMAGE_NOT_UPLOADER_PICKER);
        }
    }

    public void scan(String json, Context context, JSCallback jsCallback) {

        ScanImageBean bean = ManagerFactory.getManagerService(ParseManager.class).parseObject
                (json, ScanImageBean.class);

        String path = bean.path;

        Bitmap scanBitmap = decodeSampledBitmapFromFile(path, 512, 512);
        // 获取bitmap的宽高，像素矩阵
        int width = scanBitmap.getWidth();
        int height = scanBitmap.getHeight();

        String result = scanByZBar(getYUV420sp(width, height, scanBitmap), width, height);

        JsPoster.postSuccess(result, jsCallback);
    }

    /**
     * 通过zbar识别二维码图片
     * @param data
     * @param width
     * @param height
     * @return
     */
    private String scanByZBar(byte[] data, int width, int height) {
        Image barcode = new Image(width, height, "Y800");
        barcode.setData(data);
        // Set the image capture area.
        // barcode.setCrop(startX, startY, width, height);

        String qrCodeString = "";

        ImageScanner imageScanner = new ImageScanner();
        int result = imageScanner.scanImage(barcode);
        if (result != 0) {
            SymbolSet symSet = imageScanner.getResults();
            for (Symbol sym : symSet)
                qrCodeString = sym.getData();
        }

        if (qrCodeString == null) qrCodeString = "";

        return qrCodeString;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 将图片根据压缩比压缩成固定宽高的Bitmap，实际解析的图片大小可能和#reqWidth、#reqHeight不一样。
     *
     * @param imgPath   图片地址
     * @param reqWidth  需要压缩到的宽度
     * @param reqHeight 需要压缩到的高度
     * @return Bitmap
     */
    public static Bitmap decodeSampledBitmapFromFile(String imgPath, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imgPath, options);
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imgPath, options);
    }

    private byte[] yuvs;

    public byte[] getYUV420sp(int inputWidth, int inputHeight, Bitmap scaled) {
        int[] argb = new int[inputWidth * inputHeight];
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
//        需要转换成偶数的像素点，否则编码YUV420的时候有可能导致分配的空间大小不够而溢出。
        int requiredWidth = inputWidth % 2 == 0 ? inputWidth : inputWidth + 1;
        int requiredHeight = inputHeight % 2 == 0 ? inputHeight : inputHeight + 1;
        int byteLength = requiredWidth * requiredHeight * 3 / 2;
        if (yuvs == null || yuvs.length < byteLength) {
            yuvs = new byte[byteLength];
        } else {
            Arrays.fill(yuvs, (byte) 0);
        }
        encodeYUV420SP(yuvs, argb, inputWidth, inputHeight);
        scaled.recycle();
        return yuvs;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        // 帧图片的像素大小
        final int frameSize = width * height;
        // ---YUV数据---
        int Y, U, V;
        // Y的index从0开始
        int yIndex = 0;
        // UV的index从frameSize开始
        int uvIndex = frameSize;
        // ---颜色数据---
        int R, G, B;
        int rgbIndex = 0;
        // ---循环所有像素点，RGB转YUV---
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = (argb[rgbIndex] & 0xff0000) >> 16;
                G = (argb[rgbIndex] & 0xff00) >> 8;
                B = (argb[rgbIndex] & 0xff);
                //
                rgbIndex++;
                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                Y = Math.max(0, Math.min(Y, 255));
                U = Math.max(0, Math.min(U, 255));
                V = Math.max(0, Math.min(V, 255));
                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the sampling is every other
                // pixel AND every other scan line.
                // ---Y---
                yuv420sp[yIndex++] = (byte) Y;
                // ---UV---
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    //
                    yuv420sp[uvIndex++] = (byte) V;
                    //
                    yuv420sp[uvIndex++] = (byte) U;
                }
            }
        }
    }

    @Subscribe
    public void OnUploadResult(UploadResultBean uploadResultBean) {
        if (uploadResultBean != null && mPickCallback != null) {
            JsPoster.postSuccess(uploadResultBean.data, mPickCallback);
        }

        ManagerFactory.getManagerService(DispatchEventManager.class).getBus().unregister(this);
        mPickCallback = null;
        ManagerFactory.getManagerService(PersistentManager.class).deleteCacheData(Constant
                .ImageConstants.UPLOAD_IMAGE_BEAN);
    }
}

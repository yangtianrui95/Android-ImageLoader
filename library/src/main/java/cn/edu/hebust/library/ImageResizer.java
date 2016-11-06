package cn.edu.hebust.library;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Created by shixi_tianrui1 on 16-11-6.
 * 图片尺寸的调整
 * 根据ImageView的大小来压缩Bitmap,避免OOM问题
 */

public class ImageResizer {

    private static final String TAG = "ImageResizer";

    /**
     * 从资源文件中获取图片,并根据ImageView对Bitmap尺寸进行调整
     */
    public Bitmap decodeBitmapDimenFromRes(Resources resources, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 将inJustDecodeBounds属性设置为true时,不会获取Bitmap对象,但可以获取Bitmap的所有属性
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, options);
        Log.d(TAG, "decodeBitmapDimenFromRes: width=" + options.outWidth + " height=" + options.outHeight);
        Log.d(TAG, "decodeBitmapDimenFromRes: imageType=" + options.outMimeType);
        // 计算采样率(压缩比), 设置在Options的inSampleSize属性值中
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        // 加载Bitmap
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(resources, resId, options);
    }


    /**
     * 计算Bitmap的采样率
     *
     * @return inSampleSize of each bitmap should be the power of 2.
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqHeight == 0 || reqWidth == 0) {
            return 1;
        }
        // raw width and height of this bitmap.
        int width = options.outWidth;
        int height = options.outHeight;
        int sampleSize = 1;

        if (width > reqHeight || height > reqHeight) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;

            while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2;
            }
        }
        // 选用最小的压缩比,可以保证图片会大于ImageView的尺寸,不会失真
        Log.d(TAG, "calculateInSampleSize: inSampleSize=" + sampleSize);
        return sampleSize;
    }
}

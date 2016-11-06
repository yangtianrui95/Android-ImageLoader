package cn.edu.hebust.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by shixi_tianrui1 on 16-11-6.
 * <p>
 * A simple and optimized ImageLoader for Android.
 * Use LruCache{@link android.util.LruCache} and DiskLruCache as Double-Cache policy.
 */

public class ImageLoader {

    private static final String TAG = "ImageLoader";

    private static final int TAG_KEY_URI = R.id.tag_key_uri;
    private static final int MSG_POST_RESULT = 0x1;

    // ThreadPool Arguments.
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;

    public static ImageLoader sInstance;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            switch (msg.what) {
                case MSG_POST_RESULT:
                    // FIXME: 16-11-6 maybe error
                    result.mIvImg.setImageBitmap(result.mBmp);
                    break;
            }
        }
    };

    // for create workerThread.
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "ImageLoader# " + mCount.getAndIncrement());
        }
    };

    // ThreadPool
    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
            KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), sThreadFactory);


    private ImageLoader() {

    }

    public static ImageLoader getInstance() {
        if (sInstance == null) {
            synchronized (ImageLoader.class) {
                if (sInstance == null) {
                    sInstance = new ImageLoader();
                }
            }
        }
        return sInstance;
    }


    /**
     * Async load bitmap from memory-cache or disk-cache or network.
     * Note: Must run in UI-Thread;
     *
     * @param uri       Http url.
     * @param imageView bitmap 's bind object.
     * @param reqWidth  the width imageView desired.
     * @param reqHeight the height imageView desired.
     */
    public void bindBmp(final String uri, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);
        // from Memory-Cache
        final Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        // from Disk-Cache or Network in workerThread
        Runnable loadBmpTask = new Runnable() {
            @Override
            public void run() {
                Bitmap target = loadBitmapAsync(uri, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    Message msg = mMainHandler.obtainMessage(MSG_POST_RESULT, result);
                    msg.sendToTarget();
                }
            }
        };

        THREAD_POOL_EXECUTOR.execute(loadBmpTask);
    }

    // Memory-Cache >> Disk-Cache >> Network
    private Bitmap loadBitmapAsync(String uri, int reqWidth, int reqHeight) {
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null)
            return bitmap;
        bitmap = loadBmpFromDisk(uri, reqWidth, reqWidth);
        if (bitmap != null)
            return bitmap;

        // TODO: 16-11-6
        return null;
    }

    private Bitmap loadBmpFromDisk(String uri, int reqWidth, int reqWidth1) {
        return null;
    }


    /**
     * Load bitmap from LruCache.
     */
    private Bitmap loadBitmapFromMemCache(String uri) {
        // TODO: 16-11-6 finish!!
        return null;
    }


    /**
     * Loading target Bitmap by using {@link HttpURLConnection},
     * this operation can't in MainThread.
     *
     * @param uri An Uri for this bitmap from web.
     * @return Loaded bitmap.
     */
    private Bitmap downloadBmpFromNet(String uri) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        BufferedInputStream bis = null;
        try {
            URL url = new URL(uri);
            connection = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(connection.getInputStream());
            bitmap = BitmapFactory.decodeStream(bis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }


    /**
     * the result of bitmap and binded imageView.
     */
    private static class LoaderResult {
        private ImageView mIvImg;
        private String mUri;
        private Bitmap mBmp;


        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            mIvImg = imageView;
            mUri = uri;
            mBmp = bitmap;
        }
    }
}

package cn.edu.hebust.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    // LruDisk Cache 的参数
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50; // 50MB
    private static final int IO_BUFFER_SIZE = 8 * 1024;           // 8KB
    private static final int DISK_CACHE_INDEX = 0;
    private boolean mIsDiskLruCacheCreated = false;

    private static ImageLoader sInstance;
    private Context mContext;

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


    /**
     * MemoryCache by {@link android.util.LruCache}
     */
    private final LruCache<String, Bitmap> mMemCache;
    private DiskLruCache mDiskLruCache;

    private ImageResizer mResizer = new ImageResizer();

    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        // 获取此进程允许的最大内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;
        mMemCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                // 转换成KB
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        // 初始化磁盘缓存
        File diskCacheDir = getDiskDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        try {
            mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
            mIsDiskLruCacheCreated = true;
        } catch (IOException e) {
            Log.e(TAG, "ImageLoader: DiskLruCache initial fail.");
            e.printStackTrace();
        }
    }


    // 获取缓存目录
    private File getDiskDir(Context context, String name) {
        // 判断SDCard是否挂载
        boolean isMounted = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (isMounted) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + name);
    }

    public static ImageLoader getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ImageLoader.class) {
                if (sInstance == null) {
                    sInstance = new ImageLoader(context);
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
                if (target != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, target);
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
        try {
            bitmap = loadBmpFromDisk(uri, reqWidth, reqHeight);
            if (bitmap != null)
                return bitmap;
            bitmap = loadBmpFromHttp(uri, reqWidth, reqHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 从网络中获取图片
        if (bitmap == null && !mIsDiskLruCacheCreated)
            return downloadBmpFromNet(uri);
        return null;
    }


    /**
     * 从网络中取图片
     */
    private Bitmap loadBmpFromHttp(String uri, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("Can't visit Network in UI-Thread");
        }
        if (mDiskLruCache == null) {
            return null;
        }

        String key = hashKeyFromUri(uri);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(uri, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBmpFromDisk(uri, reqWidth, reqHeight);
    }


    /**
     * 将网络中的Uri资源使用
     */
    private boolean downloadUrlToStream(String urlStr, OutputStream os) {
        HttpURLConnection conn = null;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;
        try {
            final URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(conn.getInputStream());
            bos = new BufferedOutputStream(os, IO_BUFFER_SIZE);

            int buf;
            while ((buf = bis.read()) != -1) {
                bos.write(buf);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            try {
                if (bis != null) {
                    bis.close();
                }
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 从硬盘缓存中加载图片
     */
    private Bitmap loadBmpFromDisk(String uri, int reqWidth, int reqHeight) throws IOException {
        // 判断当前线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "loadBmpFromDisk: in UI-Thread isn't recommend!");
        }
        if (mDiskLruCache == null) {
            return null;
        }

        Bitmap bitmap = null;
        String key = hashKeyFromUri(uri);
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if (snapshot != null) {
            FileInputStream fis = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fd = fis.getFD();
            bitmap = mResizer.decodeSampledBmpFromFD(fd, reqWidth, reqHeight);
            if (bitmap != null) {
                addBmpToMemCache(key, bitmap);
            }
        }
        return bitmap;
    }


    /**
     * 存入Memory-Cache
     */
    private void addBmpToMemCache(String key, Bitmap bitmap) {
        if (mMemCache.get(key) == null) {
            mMemCache.put(key, bitmap);
        }
    }


    /**
     * Load bitmap from LruCache.
     */
    private Bitmap loadBitmapFromMemCache(String uri) {
        final String key = hashKeyFromUri(uri);
        return mMemCache.get(key);
    }

    private String hashKeyFromUri(String uri) {
        String cacheKey;
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(uri.getBytes());
            // bytes transfer to HexString
            cacheKey = byteToHexString(messageDigest.digest());// todo why
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(uri.hashCode());
        }
        return cacheKey;
    }

    private String byteToHexString(byte[] bytes) {

        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xFF & aByte);
            if (hex.length() == 1) {
                sb.append("0");
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    /**
     * Loading target Bitmap by using {@link HttpURLConnection},
     * this operation can't in MainThread.
     *
     * @param uri An Uri for this bitmap from web.
     * @return Loaded bitmap.
     */
    public Bitmap downloadBmpFromNet(String uri) {
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

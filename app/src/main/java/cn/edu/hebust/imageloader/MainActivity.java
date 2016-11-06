package cn.edu.hebust.imageloader;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import cn.edu.hebust.library.ImageLoader;
import cn.edu.hebust.library.ImageResizer;

import static cn.edu.hebust.imageloader.R.id.screen;
import static cn.edu.hebust.imageloader.R.id.test;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ImageResizer";
    private ImageView imageView;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bitmap bitmap = (Bitmap) msg.obj;
            imageView.setImageBitmap(bitmap);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(test);
        final ImageResizer imageResizer = new ImageResizer();
        new Thread() {
            @Override
            public void run() {
                Bitmap bitmap = ImageLoader.getInstance()
                        .downloadBmpFromNet("http://img0.imgtn.bdimg.com/it/u=2274586553,1071941773&fm=21&gp=0.jpg");
                Message msg = mHandler.obtainMessage();
                msg.obj = bitmap;
                msg.sendToTarget();
            }
        }.start();

    }

}

# ImageLoader的简单实现

> 这是一个ImageLoader的简单实现,通过这个项目,可以帮助我们了解Android图片加载框架的工作原理,
会在这个基础上持续维护.

## 效果
![ImageLoader](http://img.blog.csdn.net/20161113182052943)

## 原理分析
 
### 计算`Bitmap`的采样率
防止图片消耗过大的内存,图片根据ImageView的宽高适配,不会造成图片失真.

设置inJustDecodeBounds为true后，decodeFile并不分配空间，但可计算出原始图片的长度和宽度
，即opts.width和opts.height。有了这两个参数，再通过一定的算法，即可得到一个恰当的inSampleSize。
```
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
```

### 缓存策略
使用Memory-Cache >> Disk-Cache >> Network的顺序加载图片,
其中Memory-Cache 和 Disk-Cache 均由Lru实现
```
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
        bitmap = downloadBmpFromNet(uri);
    return bitmap;
}
```

## 简单使用

```
//~ ImageAdapter中
@Override
public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
    ViewHolder viewHolder;
    if (convertView == null) {
        convertView = inflater.inflate(R.layout.item_image, parent, false);
        viewHolder = new ViewHolder();
        viewHolder.mIvImg = (ImageView) convertView.findViewById(R.id.id_iv_img);
        convertView.setTag(viewHolder);
    } else {
        viewHolder = (ViewHolder) convertView.getTag();
    }
    // 判断ImageView是否加载正确的图片
    if (!getItem(position).equals(viewHolder.mIvImg.getTag())) {
        Log.d(TAG, "getView: item " + getItem(position) + " Img " + viewHolder.mIvImg.getTag());
        viewHolder.mIvImg.setImageResource(R.drawable.image_default);
    }
    // 给ImageView 设置Tag, 表示ImageView所要显示的图片
    viewHolder.mIvImg.setTag(mUris[position]);
    ImageLoader.getInstance(MainActivity.this).bindBmp(mUris[position], viewHolder.mIvImg, mImageWidth, mImageWidth);

    return convertView;
}
```

详细请见Blog http://blog.csdn.net/y874961524/article/details/53150480
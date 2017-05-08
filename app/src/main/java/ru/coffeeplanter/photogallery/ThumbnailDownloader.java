package ru.coffeeplanter.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private final int MAX_BITMAPS_IN_CACHE = 50;

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    private Handler mPreloadHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private LruCache<String, Bitmap> mMemoryCache;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        mMemoryCache = new LruCache<String, Bitmap>(MAX_BITMAPS_IN_CACHE) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1;
            }
        };
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
            }
        };
        mPreloadHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    String target = (String) msg.obj;
                    preloadImages(target);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);
        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void loadCache(List<GalleryItem> items) {
        for (GalleryItem item : items) {
            mPreloadHandler.obtainMessage(MESSAGE_DOWNLOAD, item.getUrl()).sendToTarget();
        }
        Log.i(TAG,"Cache preloaded");
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }

    public void clearPreloadQueue() {
        mPreloadHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);
            if (url == null) {
                return;
            }
            final Bitmap bitmap = getBitmap(url);
            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if ((mRequestMap.get(target) != null) && (!mRequestMap.get(target).equals(url) || mHasQuit)) {
                        return;
                    }
                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

    private void preloadImages(String target) {
        if (target ==  null) {
            return;
        }
        try {
            getBitmap(target);
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

    private Bitmap getBitmap(String url) throws IOException {
        Bitmap bitmap = mMemoryCache.get(url);
        if (bitmap == null) {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            mMemoryCache.put(url, bitmap);
            Log.i(TAG, "Bitmap downloaded");
        } else {
            Log.i(TAG, "Bitmap cached out");
        }
        return bitmap;
    }

}

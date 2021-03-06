package tslamic.fancybg;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FancyBackground {

    /**
     * Listens to FancyBackground events.
     */
    public interface FancyListener {

        /**
         * Invoked when the {@link tslamic.fancybg.FancyBackground} starts.
         */
        void onStarted(FancyBackground bg);

        /**
         * Invoked when a new Drawable is loaded.
         */
        void onNew(FancyBackground bg);

        /**
         * Invoked if looping is set to false and the first loop through
         * the set Drawables is complete.
         */
        void onLoopDone(FancyBackground bg);

        /**
         * Invoked when the {@link tslamic.fancybg.FancyBackground}
         * is stopped.
         */
        void onStopped(FancyBackground bg);

    }

    /*
     * The minimum interval duration required to preload Bitmaps,
     * if caching is enabled.
     */
    private static final int PRELOAD_THRESHOLD = 500;

    /**
     * Creates a new {@link tslamic.fancybg.FancyBackground.Builder}
     * instance.
     *
     * @param view a view where {@link tslamic.fancybg.FancyBackground}
     *             should show Drawables.
     * @return {@link tslamic.fancybg.FancyBackground.Builder} instance.
     */
    public static Builder on(final View view) {
        if (null == view) {
            throw new IllegalArgumentException("view is null");
        }
        return new Builder(view);
    }

    public static class Builder {

        private final View mView;

        private ImageView.ScaleType mScale = ImageView.ScaleType.FIT_XY;
        private FancyListener mListener;
        private Animation mOutAnimation;
        private Animation mInAnimation;
        private long mInterval = 3000;
        private boolean mLoop = true;
        private FancyCache mCache;
        private int[] mDrawables;
        private Matrix mMatrix;

        /*
         * Private constructor. Use "on" static factory method to create an
         * instance.
         */
        private Builder(final View view) {
            mView = view;
            mCache = new FancyLruCache(view.getContext());
        }

        /**
         * Sets the Drawable resources to be displayed.
         *
         * @param drawables Drawable resources.
         */
        public Builder set(final int... drawables) {
            mDrawables = drawables;
            return this;
        }

        /**
         * Specifies the animation used to animate a View that enters the
         * screen.
         */
        public Builder inAnimation(final Animation animation) {
            if (null == animation) {
                throw new IllegalArgumentException("in animation is null");
            }
            mInAnimation = animation;
            return this;
        }

        /**
         * Specifies the animation used to animate a View that enters the
         * screen.
         */
        public Builder inAnimation(final int animation) {
            mInAnimation = AnimationUtils.loadAnimation(mView.getContext(),
                    animation);
            return this;
        }

        /**
         * Specifies the animation used to animate a View that exit the screen.
         */
        public Builder outAnimation(final Animation animation) {
            if (null == animation) {
                throw new IllegalArgumentException("out animation is null");
            }
            mOutAnimation = animation;
            return this;
        }

        /**
         * Specifies the animation used to animate a View that exit the screen.
         */
        public Builder outAnimation(final int animation) {
            mOutAnimation = AnimationUtils.loadAnimation(mView.getContext(),
                    animation);
            return this;
        }

        /**
         * Determines if the FancyBackground should continuously loop through
         * the Drawables or stop after the first one.
         *
         * @param loop true to loop, false to stop after the first one
         */
        public Builder loop(final boolean loop) {
            mLoop = loop;
            return this;
        }

        /**
         * Sets the millisecond interval a Drawable will be displayed for.
         *
         * @param millis millisecond mInterval.
         */
        public Builder interval(final long millis) {
            if (millis < 0) {
                throw new IllegalArgumentException("negative interval");
            }
            mInterval = millis;
            return this;
        }

        /**
         * Sets the {@link tslamic.fancybg.FancyBackground.FancyListener}.
         */
        public Builder listener(final FancyListener listener) {
            mListener = listener;
            return this;
        }

        /**
         * Controls how the Drawables should be resized or moved to match the
         * size of the view FancyBackground will be animating on.
         */
        public Builder scale(final ImageView.ScaleType scale) {
            if (null == scale) {
                throw new IllegalArgumentException("scale is null");
            }
            mScale = scale;
            return this;
        }

        /**
         * Controls how the Drawables should be resized or moved to match the
         * size of the view FancyBackground will be animating on.
         *
         * @param matrix a 3x3 matrix for transforming coordinates.
         */
        public Builder scale(final Matrix matrix) {
            if (null == matrix) {
                throw new IllegalArgumentException("matrix is null");
            }
            mScale = ImageView.ScaleType.MATRIX;
            mMatrix = matrix;
            return this;
        }

        /**
         * Sets the {@link tslamic.fancybg.FancyCache}. Use null to disable
         * caching.
         */
        public Builder cache(final FancyCache cache) {
            mCache = cache;
            return this;
        }

        /**
         * Completes the building process, returns a new FancyBackground
         * instance and starts the loop.
         */
        public FancyBackground start() {
            /*
             * The user might not use the set method - make sure the
             * drawables are valid.
             */
            if (null == mDrawables || mDrawables.length < 2) {
                throw new IllegalArgumentException("at least two drawables required");
            }
            return new FancyBackground(this);
        }

    }

    public final ImageView.ScaleType scale;
    public final FancyListener listener;
    public final Animation outAnimation;
    public final Animation inAnimation;
    public final FancyCache cache;
    public final long interval;
    public final Matrix matrix;
    public final boolean loop;
    public final View view;

    private final ScheduledExecutorService mExecutor;
    private final BitmapFactory.Options mOptions;
    private final TypedValue mTypedValue;
    private final Resources mResources;
    private final int[] mDrawables;

    private ImageSwitcher mSwitcher;
    private int mIndex = -1;

    /*
     * Private constructor. Use a Builder to create an instance.
     */
    private FancyBackground(Builder builder) {
        outAnimation = builder.mOutAnimation;
        inAnimation = builder.mInAnimation;
        listener = builder.mListener;
        interval = builder.mInterval;
        cache = builder.mCache;
        scale = builder.mScale;
        loop = builder.mLoop;
        view = builder.mView;

        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mOptions = new BitmapFactory.Options();
        mResources = view.getResources();
        mTypedValue = new TypedValue();
        mDrawables = builder.mDrawables;
        matrix = builder.mMatrix;

        view.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    /*
     * Initializes this FancyBackground. Invoked sometime after the source view
     * has been measured.
     */
    private void init() {
        final ViewGroup group = getViewGroup(view);
        mSwitcher = new FancyImageSwitcher(this);
        group.addView(mSwitcher, 0, view.getLayoutParams());
        start();
    }

    private void start() {
        if (hasListener()) {
            listener.onStarted(this);
        }
        mExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateDrawable();
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the index of currently shown Drawable resource.
     *
     * @return the index of currently shown Drawable resource.
     */
    public final int getCurrentDrawableIndex() {
        return mIndex;
    }

    /**
     * Returns the number of set Drawables.
     *
     * @return the number of set Drawables.
     */
    public final int getDrawablesCount() {
        return mDrawables.length;
    }

    /**
     * Stops the looping and releases the cached resources, if any.
     */
    public void halt() {
        halt(false);
    }

    private void halt(boolean isLoopDone) {
        mExecutor.shutdownNow();
        if (hasCache()) {
            cache.clear();
        }
        if (hasListener()) {
            if (isLoopDone) {
                listener.onLoopDone(this);
            } else {
                listener.onStopped(this);
            }
        }
    }

    /*
     * Runs in a worker thread.
     */
    private void updateDrawable() {
        final Drawable drawable = getNext();
        if (null != drawable) {
            final Message msg = mSwitcher.getHandler().obtainMessage();
            msg.obj = drawable;
            msg.sendToTarget();
        }
    }

    private int getNextDrawableIndex(final int current) {
        int next = current + 1;

        if (next >= mDrawables.length) {
            next = loop ? 0 : -1;
        }

        return next;
    }

    private Drawable getNext() {
        final Drawable drawable;

        mIndex = getNextDrawableIndex(mIndex);
        if (mIndex < 0) {
            drawable = null;
            halt(true);
        } else {
            drawable = getDrawable(mDrawables[mIndex]);
        }

        if (hasCache() && interval > PRELOAD_THRESHOLD) {
            final int next = getNextDrawableIndex(mIndex);
            if (next > 0) {
                preloadNext(next);
            }
        }

        return drawable;
    }

    /*
     * Tries to preload the next image by loading and putting it in the cache.
     * Assumes the cache is present.
     */
    private void preloadNext(final int next) {
        final int resource = mDrawables[next];
        if (isBitmap(resource)) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    getBitmap(resource);
                }
            });
        }
    }

    private Drawable getDrawable(final int resource) {
        Bitmap bitmap;

        if (hasCache()) {
            bitmap = cache.get(resource);
            if (null != bitmap) {
                return new BitmapDrawable(mResources, bitmap);
            }
        }

        final Drawable drawable;
        if (isBitmap(resource)) {
            bitmap = getBitmap(resource);
            drawable = new BitmapDrawable(mResources, bitmap);
        } else {
            drawable = mResources.getDrawable(resource);
        }

        return drawable;
    }

    private synchronized Bitmap getBitmap(final int resource) {
        Bitmap bitmap = null;

        final boolean hasCache = hasCache();
        if (hasCache) {
            bitmap = cache.get(resource);
        }

        if (null == bitmap) {
            final int w = view.getMeasuredWidth();
            final int h = view.getMeasuredHeight();

            mOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(mResources, resource, mOptions);

            mOptions.inSampleSize = getSampleSize(mOptions, w, h);
            mOptions.inJustDecodeBounds = false;

            /*
             * Decoding, but not scaling - it's on scale variable to do that.
             * If OOM error is thrown, try to recover by clearing the cache.
             */
            try {
                bitmap = BitmapFactory.decodeResource(mResources, resource, mOptions);
            } catch (OutOfMemoryError oom) {
                if (hasCache) {
                    cache.clear();
                    bitmap = BitmapFactory.decodeResource(mResources, resource, mOptions);
                }
            }

            if (null != bitmap && hasCache) {
                cache.put(resource, bitmap);
            }
        }

        return bitmap;
    }

    private synchronized boolean isBitmap(final int resource) {
        boolean isBitmap = false;

        mResources.getValue(resource, mTypedValue, true);
        if (TypedValue.TYPE_STRING == mTypedValue.type) {
            final String file = mTypedValue.string.toString();
            if (TextUtils.isEmpty(file)) {
                throw new IllegalArgumentException("not a Drawable id: " +
                        mTypedValue.resourceId);
            }
            isBitmap = !file.endsWith(".xml");
        }

        return isBitmap;
    }

    private boolean hasListener() {
        return null != listener;
    }

    private boolean hasCache() {
        return null != cache;
    }

    private static int getSampleSize(BitmapFactory.Options options,
                                     int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int sampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (reqHeight == 0) {
                sampleSize = (int) ((float) width / (float) reqWidth);
            } else if (reqWidth == 0) {
                sampleSize = (int) ((float) height / (float) reqHeight);
            } else {
                final int wSample = (int) ((float) width / (float) reqWidth);
                final int hSample = (int) ((float) height / (float) reqHeight);
                sampleSize = Math.max(wSample, hSample);
            }
        }

        return sampleSize;
    }

    private static ViewGroup getViewGroup(View source) {
        final ViewGroup group;

        if (source instanceof ViewGroup) {
            group = (ViewGroup) source;
        } else {
            group = (ViewGroup) source.getParent();
        }

        return group;
    }

}
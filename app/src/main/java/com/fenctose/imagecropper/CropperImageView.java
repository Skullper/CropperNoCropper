package com.fenctose.imagecropper;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * Created by Jay Rambhia on 10/29/2015.
 */
public class CropperImageView extends ImageView {

    private static final String TAG = "CropperImageView";

    private float[] mMatrixValues = new float[9];

    protected GestureDetector mGestureDetector;
    private GestureListener mGestureListener;

    protected ScaleGestureDetector mScaleDetector;
    private ScaleListener mScaleListener;

    private float mMinZoom;
    private float mMaxZoom;
    private float mBaseZoom;

    private float mFocusX;
    private float mFocusY;

    private boolean isMaxZoomSet = false;
    private boolean mFirstRender = true;

    private Bitmap mBitmap;
    private boolean doPreScaling = false;
    private float mPreScale;

    private GestureCallback mGestureCallback;

    private boolean showAnimation = true;
    private boolean isAdjusting = false;

    public  boolean DEBUG = true;

    public CropperImageView(Context context) {
        super(context);
        init(context);
    }

    public CropperImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CropperImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CropperImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public void setDEBUG(boolean DEBUG) {
        this.DEBUG = DEBUG;
    }

    private void init(Context context) {
        mGestureListener = new GestureListener();
        mGestureDetector = new GestureDetector(context, mGestureListener, null, true);
        mScaleListener = new ScaleListener();
        mScaleDetector = new ScaleGestureDetector(context, mScaleListener);

        setScaleType(ScaleType.MATRIX);
    }

    // Make Square
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (DEBUG) {
            Log.i(TAG, "onLayout: " + changed + " [" + left + ", " + top + ", " + right + ", " + bottom + "]");
        }

        if (changed || mFirstRender) {

            if (mFirstRender) {
                final Drawable drawable = getDrawable();
                if (drawable == null) {
                    if (DEBUG) {
                        Log.e(TAG, "drawable is null");
                    }
                    return;
                }

                mMinZoom = (float)(bottom - top) / Math.max(drawable.getIntrinsicHeight(),
                        drawable.getIntrinsicWidth());
                mBaseZoom = (float)(bottom - top)/ Math.min(drawable.getIntrinsicHeight(),
                        drawable.getIntrinsicWidth());

                cropToCenter(drawable, bottom - top);
                mFirstRender = false;
            }
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (isAdjusting) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mGestureCallback != null) {
                mGestureCallback.onGestureStarted();
            }
        }

        mScaleDetector.onTouchEvent(event);

        if (!mScaleDetector.isInProgress()) {
            mGestureDetector.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:

                if(mGestureCallback != null) {
                    mGestureCallback.onGestureCompleted();
                }
                return onUp(event);

        }

        return true;
    }

    @Override
    public void setImageIcon(Icon icon) {
        Log.w(TAG, "setImageIcon is not supported yet. Use setImageBitmap");
        mFirstRender = true;
        super.setImageIcon(icon);
    }

    @Override
    public void setImageURI(Uri uri) {
        Log.w(TAG, "setImageURI is not supported yet. Use setImageBitmap");
        mFirstRender = true;
        super.setImageURI(uri);
    }

    @Override
    public void setImageResource(int resId) {
        Log.w(TAG, "setImageResource is not supported yet. Use setImageBitmap");
        mFirstRender = true;
        super.setImageResource(resId);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {

        Log.w(TAG, "setImageDrawable is not supported yet. Use setImageBitmap");

        mFirstRender = true;
        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        mFirstRender = true;
        if (bm == null) {
            mBitmap = null;
            super.setImageBitmap(null);
            return;
        }

        if (bm.getHeight() > 1280 || bm.getWidth() > 1280) {
            Log.w(TAG, "Bitmap size greater than 1280. This might cause memory issues");
        }

        mBitmap = bm;

        if (doPreScaling) {
            int max_param = Math.max(bm.getWidth(), bm.getHeight());
            mPreScale = (float) max_param / (float) getWidth();

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm, (int) (bm.getWidth() / mPreScale),
                    (int) (bm.getHeight() / mPreScale), false);
            super.setImageBitmap(scaledBitmap);
        } else {
            mPreScale = 1f;
            super.setImageBitmap(bm);
        }
        requestLayout();
    }

    private void cropToCenter(@NonNull Drawable drawable, int frameDimen) {

        if (frameDimen == 0) {
            if (DEBUG) {
                Log.e(TAG, "Frame Dimension is 0. I'm quite boggled by it.");
            }
            return;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (DEBUG) {
            Log.i(TAG, "drawable size: (" + width + " ," + height + ")");
        }

        int min_dimen = Math.min(width, height);
        float scaleFactor = (float)min_dimen/(float)frameDimen;

        Matrix matrix = new Matrix();
        matrix.setScale(1f / scaleFactor, 1f / scaleFactor);
        matrix.postTranslate((frameDimen - width / scaleFactor) / 2,
                (frameDimen - height / scaleFactor) / 2);
        setImageMatrix(matrix);
    }

    private void fitToCenter(@NonNull Drawable drawable, int frameDimen) {

        if (frameDimen == 0) {
            if (DEBUG) {
                Log.e(TAG, "Frame Dimension is 0. I'm quite boggled by it.");
            }
            return;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (DEBUG) {
            Log.i(TAG, "drawable size: (" + width + " ," + height + ")");
        }

        int min_dimen = Math.max(width, height);
        float scaleFactor = (float)min_dimen/(float)frameDimen;

        Matrix matrix = new Matrix();
        matrix.setScale(1f / scaleFactor, 1f / scaleFactor);
        matrix.postTranslate((frameDimen - width / scaleFactor) / 2,
                (frameDimen - height / scaleFactor) / 2);
        setImageMatrix(matrix);
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

        Matrix matrix = getImageMatrix();
        matrix.postTranslate(-distanceX, -distanceY);
        setImageMatrix(matrix);

        invalidate();
        return true;
    }

    private boolean onUp(MotionEvent event) {
        // If over scrolled, return back to the place.
        Matrix matrix = getImageMatrix();
        float tx = getMatrixValue(matrix, Matrix.MTRANS_X);
        float ty = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float scaleX = getMatrixValue(matrix, Matrix.MSCALE_X);
        float scaleY = getMatrixValue(matrix, Matrix.MSCALE_Y);

        Drawable drawable = getDrawable();
        if (drawable == null) {
            return false;
        }

        if (DEBUG) {
            Log.i(TAG, "onUp: " + tx + " " + ty);
            Log.i(TAG, scaleX + " " + scaleY);
            Log.i(TAG, getWidth() + " " + getHeight());
            Log.i(TAG, drawable.getIntrinsicWidth() + " " + drawable.getIntrinsicHeight());
            Log.i(TAG, scaleX * drawable.getIntrinsicWidth() + " " + scaleY * drawable.getIntrinsicHeight());
        }

        if (scaleX <= mMinZoom) {
            if (DEBUG) {
                Log.i(TAG, "set scale: " + mMinZoom);
            }

            float xx = getWidth()/2 - mMinZoom * drawable.getIntrinsicWidth()/2;
            float yy = getHeight()/2 - mMinZoom * drawable.getIntrinsicHeight()/2;

            if (showAnimation()) {

                animateAdjustmentWithScale(tx, xx, ty, yy, scaleX, mMinZoom);

            } else {
                matrix.reset();
                matrix.setScale(mMinZoom, mMinZoom);
                matrix.postTranslate(xx, yy);
                setImageMatrix(matrix);
                invalidate();
                if (DEBUG) {
                    Log.i(TAG, "scale after invalidate: " + getScale(matrix));
                }
            }
            return true;
        } else if (scaleX < mBaseZoom) {

            // align to center for the smaller dimension
            int h = drawable.getIntrinsicHeight();
            int w = drawable.getIntrinsicWidth();

            float xTranslate;
            float yTranslate;

            if(h <= w) {
                yTranslate = getHeight()/2 - scaleX * h/2;

                if (tx >= 0) {
                    xTranslate = 0;
                } else {
                    float xDiff = getWidth() - (scaleX) * drawable.getIntrinsicWidth();
                    if (tx < xDiff) {
                        xTranslate = xDiff;
                    } else {
                        xTranslate = tx;
                    }
                }

            } else {
                xTranslate = getWidth()/2 - scaleX * w/2;

                if(ty >= 0) {
                    yTranslate = 0;
                } else {
                    float yDiff = getHeight() - (scaleY) * drawable.getIntrinsicHeight();
                    if (ty < yDiff) {
                        yTranslate = yDiff;
                    } else {
                        yTranslate = ty;
                    }
                }
            }

            if (showAnimation()) {
                matrix.reset();
                matrix.postScale(scaleX, scaleX);
                matrix.postTranslate(tx, ty);
                setImageMatrix(matrix);

                animateAdjustment(xTranslate - tx, yTranslate - ty);

            } else {
                matrix.reset();
                matrix.postScale(scaleX, scaleX);
                matrix.postTranslate(xTranslate, yTranslate);
                setImageMatrix(matrix);
                invalidate();
            }

            return true;

        } else if (isMaxZoomSet && scaleX > mMaxZoom) {

            if (showAnimation()) {
                animateOverMaxZoomAdjustment();
//                adjustToSides();
            } else {
                matrix.postScale(mMaxZoom / scaleX, mMaxZoom / scaleX, mFocusX, mFocusY);
                setImageMatrix(matrix);
                invalidate();
                adjustToSides();
            }
            return true;
        }

        adjustToSides();
        return true;
    }

    private boolean adjustToSides() {
        boolean changeRequired = false;

        Drawable drawable = getDrawable();
        if (drawable == null) {
            return changeRequired;
        }

        Matrix matrix = getImageMatrix();

        float sx = getMatrixValue(matrix, Matrix.MTRANS_X);
        float sy = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float tx = getMatrixValue(matrix, Matrix.MTRANS_X);
        float ty = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float scaleX = getMatrixValue(matrix, Matrix.MSCALE_X);
        float scaleY = getMatrixValue(matrix, Matrix.MSCALE_Y);

        if (tx > 0) {
            tx = -tx;
            changeRequired = true;
        } else {

            // check if scrolled to left
            float xDiff = getWidth() - (scaleX) * drawable.getIntrinsicWidth();
            if (tx < xDiff) {
                tx = xDiff - tx;
                changeRequired = true;
            } else {
                tx = 0;
            }
        }

        if (ty > 0) {
            ty = -ty;
            changeRequired = true;
        } else {

            // check if scrolled to top
            float yDiff = getHeight() - (scaleY) * drawable.getIntrinsicHeight();
            if (ty < yDiff) {
                ty = yDiff - ty;
                changeRequired = true;
            } else {
                ty = 0;
            }
        }

        if (changeRequired) {

            if (showAnimation()) {
                animateAdjustment(tx, ty);
            } else {
                matrix.postTranslate(tx, ty);
                setImageMatrix(matrix);
                invalidate();
            }
        }

        return changeRequired;
    }

    private float getMatrixValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    private float getScale(Matrix matrix) {
        return getMatrixValue(matrix, Matrix.MSCALE_X);
    }

    public boolean isDoPreScaling() {
        return doPreScaling;
    }

    public void setDoPreScaling(boolean doPreScaling) {
        this.doPreScaling = doPreScaling;
    }

    public float getMaxZoom() {
        return mMaxZoom;
    }

    public void setMaxZoom(float mMaxZoom) {
        this.mMaxZoom = mMaxZoom;
        isMaxZoomSet = true;
    }

    public float getMinZoom() {
        return mMinZoom;
    }

    public void setMinZoom(float mMInZoom) {
        this.mMinZoom = mMInZoom;
    }

    public void cropToCenter() {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            cropToCenter(drawable, getWidth());
        }
    }

    public void fitToCenter() {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            fitToCenter(drawable, getWidth());
        }
    }

    public Bitmap getCroppedBitmap() {
        if (mBitmap == null) {
            Log.e(TAG, "original image is not available");
            return null;
        }

        Matrix matrix = getImageMatrix();

        if (doPreScaling) {
            matrix.postScale(1/mPreScale, 1/mPreScale);
        }

        float xTrans = getMatrixValue(matrix, Matrix.MTRANS_X);
        float yTrans = getMatrixValue(matrix, Matrix.MTRANS_Y);
        float scale = getMatrixValue(matrix, Matrix.MSCALE_X);

        if (DEBUG) {
            Log.i(TAG, "x: " + xTrans + ", y: " + yTrans + " , scale: " + scale);
        }

        Bitmap bitmap;
        if (DEBUG) {
            Log.i(TAG, "old bitmap: " + mBitmap.getWidth() + " " + mBitmap.getHeight());
        }

        if (xTrans > 0 && yTrans > 0 && scale <= mMinZoom) {
            // No scale/crop required.
            // Add padding if not square
            bitmap = BitmapUtils.addPadding(mBitmap);
        } else {

            float cropY = - yTrans / scale;
            float Y = getHeight() / scale;
            float cropX = -xTrans / scale;
            float X = getWidth() / scale;

            if (DEBUG) {
                Log.i(TAG, "cropY: " + cropY);
                Log.i(TAG, "Y: " + Y);
                Log.i(TAG, "cropX: " + cropX);
                Log.i(TAG, "X: " + X);
            }

            Matrix matrix1 = new Matrix();
            matrix1.setScale(1/scale, 1/scale);

            if (mBitmap.getHeight() > mBitmap.getWidth()) {
                // Height is greater than width.
                if (xTrans >= 0) {
                    // Image is zoomed. Crop from height and add padding to make square
                    bitmap = Bitmap.createBitmap(mBitmap, 0, (int)cropY, mBitmap.getWidth(), (int)Y,
                            null, true);
                    bitmap = BitmapUtils.addPadding(bitmap);

                } else {
                    // Crop from width and height both
                    bitmap = Bitmap.createBitmap(mBitmap, (int) cropX, (int)cropY, (int)X, (int)Y,
                            null, true);
                }
            } else {
                if (yTrans >= 0) {
                    // Image is zoomed. Crop from width and add padding to make square
                    bitmap = Bitmap.createBitmap(mBitmap, (int)cropX, 0, (int)X, mBitmap.getHeight(),
                            null, true);
                    bitmap = BitmapUtils.addPadding(bitmap);

                } else {
                    // Crop from width and height both.
                    bitmap = Bitmap.createBitmap(mBitmap, (int) cropX, (int)cropY, (int)X, (int)Y,
                            null, true);

                }

                if (DEBUG) {
                    Log.i(TAG, "width should be: " + mBitmap.getWidth());
                    Log.i(TAG, "crop bitmap: " + bitmap.getWidth() + " " + bitmap.getHeight());
                }
            }
        }

        return bitmap;
    }

    public boolean showAnimation() {
        return showAnimation;
    }

    public void setShowAnimation(boolean showAnimation) {
        this.showAnimation = showAnimation;
    }

    // Scroll and Gesture Listeners
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) {
                return false;
            }
            if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) {
                return false;
            }

            CropperImageView.this.onScroll(e1, e2, distanceX, distanceY);
            return false;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        protected boolean mScaled = false;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            Matrix matrix = getImageMatrix();
            // This does the trick!
            mFocusX = detector.getFocusX();
            mFocusY = detector.getFocusY();

            matrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(),
                    detector.getFocusX(), detector.getFocusY());

            setImageMatrix(matrix);
            invalidate();
            return true;
        }
    }

    public void setGestureCallback(GestureCallback mGestureCallback) {
        this.mGestureCallback = mGestureCallback;
    }

    public interface GestureCallback {
        void onGestureStarted();
        void onGestureCompleted();
    }

    private void animateAdjustmentWithScale(final float xStart, final float xEnd,
                                            final float yStart, final float yEnd,
                                            final float scaleStart, final float scaleEnd) {
        ValueAnimator animator = ValueAnimator.ofInt(0, 20);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Matrix matrix = getImageMatrix();
                matrix.reset();

                Integer value = (Integer)animation.getAnimatedValue();

                matrix.postScale((scaleEnd - scaleStart) * value / 20f + scaleStart,
                        (scaleEnd - scaleStart) * value / 20f + scaleStart);
                matrix.postTranslate((xEnd - xStart) * value / 20f + xStart,
                        (yEnd - yStart) * value / 20f + yStart);

                setImageMatrix(matrix);
                invalidate();
            }
        });

        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAdjusting = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isAdjusting = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                isAdjusting = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                isAdjusting = true;
            }
        });

        animator.start();
    }

    private void animateAdjustment(final float xDiff, final float yDiff) {
        ValueAnimator animator = ValueAnimator.ofInt(0, 20);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Matrix matrix = getImageMatrix();
                matrix.postTranslate(xDiff / 20, yDiff / 20);
                setImageMatrix(matrix);
                invalidate();
            }
        });

        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAdjusting = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isAdjusting = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                isAdjusting = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                isAdjusting = true;
            }
        });

        animator.start();
    }

    private void animateOverMaxZoomAdjustment() {

        Matrix matrix = getImageMatrix();
        final float scale = getScale(matrix);

        final ValueAnimator animator = ValueAnimator.ofInt(0, 20);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Matrix matrix = getImageMatrix();

                float currentScale = getScale(matrix);
                if (currentScale <= mMaxZoom) {
//                    animator.cancel();
                    return;
                }

                double expScale = Math.pow(mMaxZoom / scale, 1 / 20f);
                matrix.postScale((float)expScale, (float)expScale, mFocusX, mFocusY);
                setImageMatrix(matrix);
                invalidate();
            }
        });

        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAdjusting = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isAdjusting = false;
                adjustToSides();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                isAdjusting = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                isAdjusting = true;
            }
        });

        animator.start();
    }
}

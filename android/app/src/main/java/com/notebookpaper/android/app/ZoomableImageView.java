package com.notebookpaper.android.app;

import android.content.Context;
import android.graphics.Matrix;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * An ImageView that supports panning (drag) and zooming (pinch + double-tap),
 * so the user can look around a large generated paper image.
 */
public class ZoomableImageView extends ImageView {

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 4.0f;

    private final Matrix mMatrix = new Matrix();
    private final Matrix mTmp = new Matrix();

    private android.graphics.Bitmap mBitmap;
    private float mBaseScale = 1.0f;
    private float mScale = 1.0f;
    private float mLastTouchX;
    private float mLastTouchY;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private OnDoubleTapListener mDoubleTapListener;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        setScaleType(ScaleType.MATRIX);
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        mDoubleTapListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mBitmap != null) {
            mBaseScale = Math.min(w / (float) mBitmap.getWidth(), h / (float) mBitmap.getHeight());
            mScale = mBaseScale;
            mMatrix.postScale(mScale, mScale);
            mMatrix.postTranslate((w - mBitmap.getWidth() * mScale) / 2f,
                    (h - mBitmap.getHeight() * mScale) / 2f);
            setImageMatrix(mMatrix);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        if (!mScaleDetector.isInProgress()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mLastTouchX = ev.getX();
                    mLastTouchY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float x = ev.getX();
                    float y = ev.getY();
                    mMatrix.postTranslate(x - mLastTouchX, y - mLastTouchY);
                    mLastTouchX = x;
                    mLastTouchY = y;
                    constrainMatrix();
                    setImageMatrix(mMatrix);
                    break;
                case MotionEvent.ACTION_UP:
                    break;
            }
        }
        return true;
    }

    private void constrainMatrix() {
        mMatrix.getValues(mTmp);
        float[] v = mTmp;
        float transX = v[Matrix.MTRANS_X];
        float transY = v[Matrix.MTRANS_Y];
        float scale = v[Matrix.MSCALE];

        float dw = mBitmap.getWidth() * scale;
        float dh = mBitmap.getHeight() * scale;
        float w = getWidth();
        float h = getHeight();

        float minX = Math.min(w - dw, 0);
        float maxX = Math.max(w - dw, 0);
        float minY = Math.min(h - dh, 0);
        float maxY = Math.max(h - dh, 0);

        v[Matrix.MTRANS_X] = Math.min(Math.max(transX, minX), maxX);
        v[Matrix.MTRANS_Y] = Math.min(Math.max(transY, minY), maxY);
        mMatrix.setValues(v);
    }

    public void setPaperImage(android.graphics.Bitmap bitmap) {
        mBitmap = bitmap;
        setImageBitmap(bitmap);
        mMatrix.reset();
        mBaseScale = Math.min(getWidth() / (float) bitmap.getWidth(),
                getHeight() / (float) bitmap.getHeight());
        mScale = mBaseScale;
        mMatrix.postScale(mScale, mScale);
        mMatrix.postTranslate(
                (getWidth() - bitmap.getWidth() * mScale) / 2f,
                (getHeight() - bitmap.getHeight() * mScale) / 2f);
        setImageMatrix(mMatrix);
    }

    public void zoomToCenter() {
        mScale = mBaseScale;
        mMatrix.reset();
        mMatrix.postScale(mScale, mScale);
        mMatrix.postTranslate(
                (getWidth() - mBitmap.getWidth() * mScale) / 2f,
                (getHeight() - mBitmap.getHeight() * mScale) / 2f);
        setImageMatrix(mMatrix);
    }

    public void zoomToFullSize() {
        mScale = Math.min(mBitmap.getWidth() / (float) getWidth(),
                mBitmap.getHeight() / (float) getHeight());
        mMatrix.reset();
        mMatrix.postScale(mScale, mScale);
        setImageMatrix(mMatrix);
    }

    public boolean isFullSize() {
        return mScale >= Math.min(mBitmap.getWidth() / (float) getWidth(),
                mBitmap.getHeight() / (float) getHeight()) - 0.01f;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float newScale = mScale * factor;
            if (newScale < MIN_SCALE) newScale = MIN_SCALE;
            if (newScale > MAX_SCALE) newScale = MAX_SCALE;
            mScale = newScale;

            mMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            constrainMatrix();
            setImageMatrix(mMatrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mDoubleTapListener != null) {
                mDoubleTapListener.onDoubleTap();
            }
            return true;
        }
    }

    public interface OnDoubleTapListener {
        void onDoubleTap();
    }
}
package com.example.gsimanager;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class RoundedImageDrawable extends Drawable {

    private final Bitmap mBitmap;
    private final float mRadius;
    private final RectF mRect = new RectF();
    private final Path mPath = new Path();
    private final Matrix mMatrix = new Matrix();
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public RoundedImageDrawable(Bitmap bitmap, float radius) {
        mBitmap = bitmap;
        mRadius = radius;
    }

    @Override
    public void draw(Canvas canvas) {
        float w = getBounds().width();
        float h = getBounds().height();
        if (w <= 0 || h <= 0 || mBitmap == null || mBitmap.isRecycled()) {
            return;
        }
        mRect.set(0, 0, w, h);
        mPath.reset();
        mPath.addRoundRect(mRect, mRadius, mRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mPath);
        float scale = Math.max(w / mBitmap.getWidth(), h / mBitmap.getHeight());
        mMatrix.reset();
        mMatrix.postScale(scale, scale);
        mMatrix.postTranslate((w - mBitmap.getWidth() * scale) / 2f, (h - mBitmap.getHeight() * scale) / 2f);
        canvas.drawBitmap(mBitmap, mMatrix, mPaint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}

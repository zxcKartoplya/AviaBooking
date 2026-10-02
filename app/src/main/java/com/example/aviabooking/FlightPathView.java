package com.example.aviabooking;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.Objects;

public class FlightPathView extends View {

    private static final long FLIGHT_DURATION_MS = 4200;
    private static final long CODES_FADE_DURATION_MS = 360;
    private static final float FLIGHT_PART = 0.8f;
    private static final float FADE_IN_PART = 0.06f;

    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path route = new Path();
    private final Path trail = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] position = new float[2];
    private final float[] tangent = new float[2];
    private final Interpolator easing = new PathInterpolator(0.45f, 0f, 0.25f, 1f);
    private final Drawable plane;

    @Nullable
    private ValueAnimator flight;
    @Nullable
    private ValueAnimator codesFade;
    private float flightFraction;
    private float codesAlpha = 1f;
    private String fromCode = "";
    private String toCode = "";
    private String pendingFromCode = "";
    private String pendingToCode = "";
    private float startX;
    private float endX;
    private float baseY;

    public FlightPathView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(dp(2));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setColor(Color.argb(110, 255, 255, 255));
        routePaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(6)}, 0));

        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(dp(2.5f));
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setColor(Color.WHITE);

        dotPaint.setColor(Color.WHITE);
        glowPaint.setColor(Color.WHITE);

        codePaint.setColor(Color.WHITE);
        codePaint.setTextAlign(Paint.Align.CENTER);
        codePaint.setTypeface(Typeface.DEFAULT_BOLD);
        codePaint.setLetterSpacing(0.08f);
        codePaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 18, getResources().getDisplayMetrics()));

        plane = Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.ic_flight)).mutate();
        plane.setTint(Color.WHITE);
        int half = Math.round(dp(13));
        plane.setBounds(-half, -half, half, half);
    }

    public void setRoute(@NonNull String from, @NonNull String to) {
        pendingFromCode = from;
        pendingToCode = to;
        if (fromCode.isEmpty() && toCode.isEmpty()) {
            fromCode = from;
            toCode = to;
            invalidate();
            return;
        }
        if (codesFade != null && codesFade.isRunning()) {
            return;
        }
        if (from.equals(fromCode) && to.equals(toCode)) {
            return;
        }
        codesFade = ValueAnimator.ofFloat(0f, 1f);
        codesFade.setDuration(CODES_FADE_DURATION_MS);
        codesFade.addUpdateListener(animator -> {
            float fraction = (float) animator.getAnimatedValue();
            if (fraction >= 0.5f) {
                fromCode = pendingFromCode;
                toCode = pendingToCode;
            }
            codesAlpha = Math.abs(1f - 2f * fraction);
            invalidate();
        });
        codesFade.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = dp(28);
        float peakY = dp(16);
        startX = getPaddingLeft() + inset;
        endX = w - getPaddingRight() - inset;
        baseY = h - dp(40);
        route.reset();
        route.moveTo(startX, baseY);
        route.quadTo(w / 2f, 2 * peakY - baseY, endX, baseY);
        measure.setPath(route, false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float length = measure.getLength();
        float progress = easing.getInterpolation(Math.min(1f, flightFraction / FLIGHT_PART));
        float alpha = planeAlpha();

        canvas.drawPath(route, routePaint);

        trail.reset();
        measure.getSegment(0f, length * progress, trail, true);
        trailPaint.setAlpha(Math.round(230 * alpha));
        canvas.drawPath(trail, trailPaint);

        canvas.drawCircle(startX, baseY, dp(5), dotPaint);
        canvas.drawCircle(endX, baseY, dp(5), dotPaint);

        codePaint.setAlpha(Math.round(255 * codesAlpha));
        canvas.drawText(fromCode, startX, baseY + dp(30), codePaint);
        canvas.drawText(toCode, endX, baseY + dp(30), codePaint);

        measure.getPosTan(length * progress, position, tangent);
        float angle = (float) Math.toDegrees(Math.atan2(tangent[1], tangent[0])) + 90f;
        canvas.save();
        canvas.translate(position[0], position[1]);
        glowPaint.setAlpha(Math.round(56 * alpha));
        canvas.drawCircle(0f, 0f, dp(18), glowPaint);
        canvas.rotate(angle);
        plane.setAlpha(Math.round(255 * alpha));
        plane.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isShown()) {
            startFlight();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFlight();
        if (codesFade != null) {
            codesFade.end();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            startFlight();
        } else {
            stopFlight();
        }
    }

    private float planeAlpha() {
        if (flightFraction < FADE_IN_PART) {
            return flightFraction / FADE_IN_PART;
        }
        if (flightFraction <= FLIGHT_PART) {
            return 1f;
        }
        return 1f - (flightFraction - FLIGHT_PART) / (1f - FLIGHT_PART);
    }

    private void startFlight() {
        if (flight != null) {
            return;
        }
        flight = ValueAnimator.ofFloat(0f, 1f);
        flight.setDuration(FLIGHT_DURATION_MS);
        flight.setInterpolator(new LinearInterpolator());
        flight.setRepeatCount(ValueAnimator.INFINITE);
        flight.addUpdateListener(animator -> {
            flightFraction = (float) animator.getAnimatedValue();
            invalidate();
        });
        flight.start();
    }

    private void stopFlight() {
        if (flight != null) {
            flight.cancel();
            flight = null;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

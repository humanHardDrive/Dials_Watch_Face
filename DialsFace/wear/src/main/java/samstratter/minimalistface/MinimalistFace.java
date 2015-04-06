/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package samstratter.minimalistface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MinimalistFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        Paint mBackgroundPaint;
        Paint mHandPaint;
        boolean mAmbient;
        Time mTime;

        IntentFilter intentFilter;
        Intent batteryStatus;

        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MinimalistFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setViewProtection(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .build());

            Resources resources = MinimalistFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setARGB(255, 0, 0, 0);
            mBackgroundPaint.setAntiAlias(true);

            mHandPaint = new Paint();
            mHandPaint.setARGB(255, 255, 255, 255);
            mHandPaint.setStrokeWidth(3);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setStyle(Paint.Style.FILL);

            mTime = new Time();

            //intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            //batteryStatus = getApplicationContext().registerReceiver(null, intentFilter);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            int centerX = width/2;
            int centerY = height/2;

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            int secRot = (int) ((mTime.second/59f)*360f);
            int minRot = (int) ((mTime.minute/59f)*360f);
            int hrRot = (int) (((mTime.hour%12)/12f)*360f);

            float nightRot = (float) (((mTime.hour*60*60 + mTime.minute*60 + mTime.second)/
                                       (23f*60f*60f + 3540.0f + 59))*(2*Math.PI) - (Math.PI/2));
            float dayRot = (float) ((nightRot + Math.PI));

            float nightCenterX = (float)(centerX + 35*Math.cos(nightRot));
            float nightCenterY = (float)(centerY + 35*Math.sin(nightRot));

            float dayCenterX = (float) (centerX + 35*Math.cos(dayRot));
            float dayCenterY = (float) (centerY + 35*Math.sin(dayRot));

            //float batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)/
                                 //(float)batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            //int batteryRot = (int) (batteryLevel*360f);

            if(hrRot == 0)
                hrRot = 360;

            if(mAmbient)
                mHandPaint.setStyle(Paint.Style.STROKE);
            else
                mHandPaint.setStyle(Paint.Style.FILL);

            canvas.drawArc(0 + 25, 0 + 25, width - 25, height - 25, 270, hrRot, true, mHandPaint);
            canvas.drawArc(0 + 45, 0 + 45, width - 45, height - 45, 270, 360, true, mBackgroundPaint);

            canvas.drawArc(0 + 50, 0 + 50, width - 50, height - 50, 270, minRot, true, mHandPaint);
            canvas.drawArc(0 + 70, 0 + 70, width - 70, height - 70, 270, 360, true, mBackgroundPaint);

            if (!mAmbient) {
                canvas.drawArc(0 + 75, 0 + 75, width - 75, height - 75, 270, secRot, true, mHandPaint);
                canvas.drawArc(0 + 95, 0 + 95, width - 95, height - 95, 270, 360, true, mBackgroundPaint);
            }
            else
            {
                canvas.drawArc(0 + 75, 0 + 75, width - 75, height - 75, 270, 360, true, mHandPaint);
                canvas.drawArc(0 + 95, 0 + 95, width - 95, height - 95, 270, 360, true, mBackgroundPaint);
            }

            mHandPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(dayCenterX, dayCenterY, 20, mHandPaint);
            mHandPaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(nightCenterX, nightCenterY, 20, mHandPaint);
            canvas.drawArc(centerX - 60, centerY - 60, centerX + 60, centerY + 60, 0, 180, true, mBackgroundPaint);
            canvas.drawLine(centerX - 60, centerY, centerX + 60, centerY, mHandPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MinimalistFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MinimalistFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}

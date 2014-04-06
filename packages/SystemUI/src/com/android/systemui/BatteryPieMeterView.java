/*
 * Copyright (C) 2012 Sven Dawitz for the CyanogenMod Project
 * This code has been modified. Portions copyright (C) 2013, ParanoidAndroid Project.
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

package com.android.systemui;

import android.view.ViewGroup.LayoutParams;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;

import com.android.systemui.BatteryMeterView;

/***
 * Note about PieBattery Implementation:
 *
 * Unfortunately, we cannot use BatteryController or DockBatteryController here,
 * since communication between controller and this view is not possible without
 * huge changes. As a result, this Class is doing everything by itself,
 * monitoring battery level and battery settings.
 */

public class BatteryPieMeterView extends ImageView {
    final static String QuickSettings = "quicksettings";
    final static String StatusBar = "statusbar";
    private Handler mHandler = new Handler();
    private BatteryReceiver mBatteryReceiver = null;

    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private boolean mActivated;     // whether or not activated due to system settings
    private boolean mPiePercent; // whether or not to show percentage number
    private boolean mIsCharging;    // whether or not device is currently charging
    private int     mLevel;         // current battery level
    private int     mAnimOffset;    // current level of charging animation
    private boolean mIsAnimating;   // stores charge-animation status to reliably remove callbacks
    private int     mDockLevel;     // current dock battery level
    private boolean mDockIsCharging;// whether or not dock battery is currently charging
    private boolean mIsDocked = false;      // whether or not dock battery is connected

    private int     mPieSize;    // draw size of pie. read rather complicated from
                                     // another status bar icon, so it fits the icon size
                                     // no matter the dps and resolution
    private RectF   mRectLeft;      // contains the precalculated rect used in drawArc(), derived from mPieSize
    private RectF   mRectRight;     // contains the precalculated rect used in drawArc() for dock battery
    private RectF   mRectInner;
    private Float   mTextLeftX;     // precalculated x position for drawText() to appear centered
    private Float   mTextY;         // precalculated y position for drawText() to appear vertical-centered
    private Float   mTextRightX;    // precalculated x position for dock battery drawText()

    // quiet a lot of paint variables. helps to move cpu-usage from actual drawing to initialization
    private Paint   mPaintFont;
    private Paint   mPaintGray;
    private Paint   mPaintSystem;
    private Paint   mPaintRed;
    private String  mPieBatteryView;

    private int mPieColor;
    private int mPieTextColor;
    private int mPieTextChargingColor;
    private int mPieAnimSpeed = 4;

    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            if(mActivated && mAttached) {
                invalidate();
            }
        }
    };

    // keeps track of current battery level and charger-plugged-state
    class BatteryReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mIsCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

                if (mActivated && mAttached) {
                    LayoutParams l = getLayoutParams();
                    l.width = mPieSize + getPaddingLeft()
                            + (mIsDocked ? mPieSize + getPaddingLeft() : 0);
                    setLayoutParams(l);

                    invalidate();
                }
            }
        }
    }

    /***
     * Start of PieBattery implementation
     */
    public BatteryPieMeterView(Context context) {
        this(context, null);
    }

    public BatteryPieMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryPieMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray pieBatteryType = context.obtainStyledAttributes(attrs,
            com.android.systemui.R.styleable.BatteryIcon, 0, 0);

        mPieBatteryView = pieBatteryType.getString(
                com.android.systemui.R.styleable.BatteryIcon_batteryView);

        if (mPieBatteryView == null) {
            mPieBatteryView = StatusBar;
        }

        mHandler = new Handler();
        mBatteryReceiver = new BatteryReceiver();
        initializePieVars();
        updateSettings();
    }

    protected boolean isBatteryPresent() {
        // the battery widget always is shown.
        return true;
    }

    private boolean isBatteryStatusUnknown() {
        return false;
    }

    private boolean isBatteryStatusCharging() {
        return mIsCharging; 
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            final Intent sticky = getContext().registerReceiver(mBatteryReceiver, filter);
            if (sticky != null) {
                // preload the battery level
                mBatteryReceiver.onReceive(getContext(), sticky);
            }
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            getContext().unregisterReceiver(mBatteryReceiver);
            mRectLeft = null;
            mPieSize = 0;
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mActivated = visibility == View.VISIBLE;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mPieSize == 0) {
            initSizeMeasureIconHeight();
        }

        setMeasuredDimension(mPieSize + getPaddingLeft()
                + (mIsDocked ? mPieSize + getPaddingLeft() : 0), mPieSize);
    }

    private void drawPie(Canvas canvas, int level, int animOffset, float textX, RectF drawRect) {
        Paint usePaint = mPaintSystem;
        int internalLevel = level;
        boolean unknownStatus = isBatteryStatusUnknown(); 

        if (unknownStatus) {
	    usePaint = mPaintGray;
            internalLevel = 100;
        // turn red at 14% - same level android battery warning appears
        } else if (internalLevel <= 14) {
            usePaint = mPaintRed;
        }

        // pad pie percentage to 100% once it reaches 97%
        // for one, the pie looks odd with a too small gap,
        // for another, some phones never reach 100% due to hardware design
        int padLevel = internalLevel;
        if (padLevel >= 97) {
            padLevel = 100;
        }
        final float sweepAngle = 3.6f * padLevel;

        // draw thin gray ring first
        canvas.drawArc(mRectInner, 270, 360, true, mPaintGray);
        // draw colored arc representing charge level
        canvas.drawArc(drawRect, 270 + animOffset + (360f-sweepAngle) / 2f , sweepAngle, true, usePaint);
        // if chosen by options, draw percentage text in the middle
        // always skip percentage when 100, so layout doesnt break
        if (unknownStatus) {
            canvas.drawText("?", textX, mTextY, mPaintFont);
        } else if (internalLevel < 100 && mPiePercent) {
            if (internalLevel <= 14) {
                mPaintFont.setColor(mPaintRed.getColor());
            } else if (mIsCharging) {
                mPaintFont.setColor(mPieTextChargingColor);
            } else {
                mPaintFont.setColor(mPieTextColor);
            }
            canvas.drawText(Integer.toString(internalLevel), textX, mTextY, mPaintFont);
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRectLeft == null) {
            initSizeBasedStuff();
        }

        updateChargeAnim();

        drawPie(canvas, 
               mLevel, 
              (isBatteryStatusCharging() ? mAnimOffset : 0), mTextLeftX, mRectLeft);
    }

    public void updateSettings() {
        Resources res = getResources();
        ContentResolver resolver = getContext().getContentResolver();

        int defaultColor = res.getColor(com.android.systemui.R.color.batterymeter_charge_color);
        int defaultText = res.getColor(com.android.systemui.R.color.batterymeter_bolt_color);

        mPieTextColor = defaultText;
        mPieTextChargingColor = defaultText;
        mPieColor = defaultColor;

        mPaintSystem.setColor(mPieColor);
        mRectLeft = null;
        mPieSize = 0;

        int batteryStyle = Settings.System.getInt(getContext().getContentResolver(),
                                Settings.System.STATUS_BAR_BATTERY_STYLE, 0);

        Log.v("BatteryPieMeterView","Battery Style number is "+batteryStyle);
        mPiePercent = batteryStyle == 5;
        mActivated = (batteryStyle == 4 || mPiePercent);

        setVisibility(mActivated ? View.VISIBLE : View.GONE);

        if (mActivated && mAttached) {
            invalidate();
        }
    }

    /***
     * Initialize the Pie vars for start
     */
    private void initializePieVars() {
        // initialize and setup all paint variables
        // stroke width is later set in initSizeBasedStuff()

        Resources res = getResources();

        mPaintFont = new Paint();
        mPaintFont.setAntiAlias(true);
        mPaintFont.setDither(true);
        mPaintFont.setStyle(Paint.Style.FILL);

        mPaintGray = new Paint(mPaintFont);
        mPaintSystem = new Paint(mPaintFont);
        mPaintRed = new Paint(mPaintFont);

        // could not find the darker definition anywhere in resources
        // do not want to use static 0x404040 color value. would break theming.
        mPaintFont.setColor(res.getColor(R.color.battery_percentage_text_color));
        mPaintSystem.setColor(res.getColor(R.color.batterymeter_charge_color));
        mPaintGray.setColor(res.getColor(com.android.internal.R.color.darker_gray));
        mPaintRed.setColor(res.getColor(com.android.internal.R.color.holo_red_light));

        // font needs some extra settings
        mPaintFont.setTextAlign(Align.CENTER);
        mPaintFont.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
    }


    /***
     * updates the animation counter
     * cares for timed callbacks to continue animation cycles
     * uses mInvalidate for delayed invalidate() callbacks
     */
    private void updateChargeAnim() {
        if (!isBatteryStatusCharging() || mLevel >= 97 ) {
            if (mIsAnimating) {
                mIsAnimating = false;
                mAnimOffset = 0;
                mHandler.removeCallbacks(mInvalidate);
            }
            return;
        }

        mIsAnimating = true;

        if (mAnimOffset > 360) {
            mAnimOffset = 0;
        } else {
            mAnimOffset += 3;
        }

        mHandler.removeCallbacks(mInvalidate);
        mHandler.postDelayed(mInvalidate, 50);
    }

    /***
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     * YES! i think the method name is appropriate
     */
    private void initSizeBasedStuff() {
        if (mPieSize == 0) {
            initSizeMeasureIconHeight();
        }

        mPaintFont.setTextSize(mPieSize / 1.5f);

        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft, 0, 
                mPieSize+ pLeft, mPieSize);
        final float innerPadding = getResources().getDisplayMetrics().density;
        mRectInner = new RectF(mRectLeft.left + innerPadding, mRectLeft.top+innerPadding, 
                    mRectLeft.right - innerPadding,
                    mRectLeft.bottom - innerPadding);

        // calculate Y position for text
        Rect bounds = new Rect();
        mPaintFont.getTextBounds("99", 0, "99".length(), bounds);
        mTextLeftX = mPieSize / 2.0f + getPaddingLeft();
        // the +1 at end of formular balances out rounding issues. works out on all resolutions
        mTextY = mPieSize / 2.0f + (bounds.bottom - bounds.top) / 2.0f + 1;

        // force new measurement for wrap-content xml tag
        onMeasure(0, 0);
    }

    /***
     * we need to measure the size of the pie battery by checking another
     * resource. unfortunately, those resources have transparent/empty borders
     * so we have to count the used pixel manually and deduct the size from
     * it. quiet complicated, but the only way to fit properly into the
     * statusbar for all resolutions
     */
    private void initSizeMeasureIconHeight() {
        Bitmap measure = null;
        if (mPieBatteryView.equals(QuickSettings)) {
            measure = BitmapFactory.decodeResource(getResources(),
                    com.android.systemui.R.drawable.ic_qs_wifi_full_4);
        } else if (mPieBatteryView.equals(StatusBar)) {
            measure = BitmapFactory.decodeResource(getResources(),
                    com.android.systemui.R.drawable.stat_sys_wifi_signal_4_fully);
        }
        if (measure == null) {
            return;
        }
        final int x = measure.getWidth() / 2;

        mPieSize = measure.getHeight();
    }

}

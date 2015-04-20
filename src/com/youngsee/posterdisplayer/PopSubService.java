/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.posterdisplayer;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.graphics.Typeface;
import com.youngsee.common.AutoScroll;
import com.youngsee.common.TypefaceManager;
import com.youngsee.posterdisplayer.R;

public class PopSubService extends Service
{
	public static final int EVENT_STOPSELF = 0;
	
    private View                       view;
    private boolean                    viewAdded       = false;
    private WindowManager              windowManager;
    private WindowManager.LayoutParams layoutParams;
    private AutoScroll                 auto;
    
    public static final String         DURATION        = "DURATION";
    public static final String         NUMBER          = "NUMBER";
    public static final String         TEXT            = "TEXT";
    public static final String         FONTNAME        = "FONTNAME";
    public static final String         FONTCOLOR       = "FONTCOLOR";
    public static final String         SPEED           = "SPEED";

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate()
    {
        super.onCreate();
        view = LayoutInflater.from(this).inflate(R.layout.autoscroll, null);
        windowManager = (WindowManager) this.getSystemService(WINDOW_SERVICE);
        layoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_SYSTEM_ERROR, LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSPARENT);
        // layoutParams.gravity = Gravity.RIGHT|Gravity.BOTTOM; //���ʼ�����½���ʾ
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        layoutParams.format = PixelFormat.RGBA_8888;
        auto = (AutoScroll) view.findViewById(R.id.TextViewNotice);
    }
    
    private void refresh(int dur, int num, String txt, int sp, int fc, Typeface tf)
    {
        if (viewAdded)
        {
            windowManager.updateViewLayout(view, layoutParams);
        }
        else
        {
            // layoutParams.y = 300;
            windowManager.addView(view, layoutParams);
            viewAdded = true;
            
            auto.setText(txt);
            auto.init(windowManager);
            auto.setOnViewListener(new AutoScroll.OnViewListener() {
				@Override
				public void onStarted() {
					// TODO Auto-generated method stub
				}
				
				@Override
				public void onStopped() {
					// TODO Auto-generated method stub
					mHandler.obtainMessage(EVENT_STOPSELF).sendToTarget();
				}
			});
            auto.startScroll(dur, num, sp, fc, tf);
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId)
    {
        super.onStart(intent, startId);
        int duration = intent.getIntExtra(DURATION, -1);
        int number = intent.getIntExtra(NUMBER, -1);
        String text = intent.getStringExtra(TEXT);
        String fontN = intent.getStringExtra(FONTNAME);
        int fontC = intent.getIntExtra(FONTCOLOR, 0xffffffff);
        int speed = intent.getIntExtra(SPEED, 0);
        TypefaceManager typefaceManager = new TypefaceManager(this);
        Typeface typeface = typefaceManager.getTypeface(TypefaceManager.DEFAULT);
        if (fontN != null)
        {
            typeface = typefaceManager.getTypeface(fontN);
        }
        refresh(duration, number, text, speed, fontC, typeface);
    }
    
    private void removeView()
    {
        if (viewAdded)
        {
            windowManager.removeView(view);
            viewAdded = false;
        }
    }
    
    @Override
    public void onDestroy()
    {
    	auto.stopScroll();
    	removeView();
    	mHandler.removeMessages(EVENT_STOPSELF);
        super.onDestroy();
    }
    
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_STOPSELF:
            	stopSelf();
            	PosterMainActivity.INSTANCE.setPopServiceRunning(false);
            	break;
            default:
                break;
            }
            super.handleMessage(msg);
        }
    };
}

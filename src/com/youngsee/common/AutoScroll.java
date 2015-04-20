package com.youngsee.common;

import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

@SuppressLint("WrongCall") 
public class AutoScroll extends TextView {
    private float textLength = 0f;
    private float viewWidth = 0f;
    private float step = 0f;
    private float y = 0f;
    private float temp_view_plus_text_length = 0.0f;
    private float temp_view_plus_two_text_length = 0.0f;
    public boolean isStarting = false;
    private Paint paint = null;
    private String text = "";
    private final static int LOW_SPEED_LEVEL = 0;
    private final static int MID_SPEED_LEVEL = 1;
    private final static int HIGH_SPEED_LEVEL = 2;
    private int mMoveSpeed = 1;
    
    private Timer timer = null;
    private int number = -1;
    
    private OnViewListener mListener = null;
    
    private boolean mStopByUser = false;
	
	public interface OnViewListener {
        public void onStarted();
        public void onStopped();
    }
	
	public void setOnViewListener(OnViewListener l) {
    	mListener = l;
    }
  
    public AutoScroll(Context context){
        super(context);
    }
 
     public AutoScroll(Context context, AttributeSet attrs){
         super(context, attrs);
     }
 
     public AutoScroll(Context context, AttributeSet attrs, int defStyle){
         super(context, attrs, defStyle);
     }
 
     private void setSpeedLevel(int nlevel){
         switch (nlevel)
         {
         case LOW_SPEED_LEVEL:
             mMoveSpeed = 1;
             break;

         case MID_SPEED_LEVEL:
             mMoveSpeed = 3;
             break;

         case HIGH_SPEED_LEVEL:
             mMoveSpeed = 5;
             break;
         }
     }

     @SuppressWarnings("deprecation")
    public void init(WindowManager windowManager){
         paint = getPaint();
         text = getText().toString();
         textLength = paint.measureText(text);
         viewWidth = getWidth();
         if(viewWidth == 0)
         {
             if(windowManager != null)
             {
                 Display display = windowManager.getDefaultDisplay();
                 viewWidth = display.getWidth();
             }
         }
         step = textLength;
         temp_view_plus_text_length = viewWidth + textLength;
         temp_view_plus_two_text_length = viewWidth + textLength * 2;
         y = getTextSize() + getPaddingTop();
     }
  
     public void startScroll(int duration, int number,
    		 int speed, int color, Typeface typeface)
     {
         isStarting = true;
         paint.setColor(color);
         setSpeedLevel(speed);
         paint.setTypeface(typeface);
         if (duration > 0) {
        	 timer = new Timer();
             timer.schedule(new TimerTask() {
                 public void run() {
                	 isStarting = false;
                 }
             }, duration*1000);
         } else if (number > 0) {
        	 this.number = number;
         }
         if ((timer != null) || (number != -1)) {
        	 invalidate();
         }
     }
   
     public void stopScroll()
     {
         mStopByUser = true;
         if (timer != null) {
        	 timer.cancel();
         }
         number = -1;
         invalidate();
     }
     
     public void onDraw(Canvas canvas) {
         canvas.drawText(text, temp_view_plus_text_length-step, y, paint);
         
         if (mStopByUser) {
        	 return;
         }
         if(!isStarting) {
        	 mListener.onStopped();
             return;
         }
         
         step += mMoveSpeed;
         if(step > temp_view_plus_two_text_length) {
             step = textLength;
             if ((number > 0) && (--number == 0)) {
            	 isStarting = false;
             }
         }
         invalidate();
     }
}


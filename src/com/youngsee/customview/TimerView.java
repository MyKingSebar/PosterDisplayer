
package com.youngsee.customview;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;

import com.youngsee.common.MediaInfoRef;
import com.youngsee.posterdisplayer.R;

public class TimerView extends PosterBaseView
{
    private View mTimerView = null;

    private YSTextView mTimerTxtv = null;
    
    private MediaInfoRef mMediaInfo = null;
    private UpdateTimerThread mUpdateTimerThread = null;
    private int mTimerFormat = TIMER_FORMAT_DAY_HOUR_MIN_SEC;
    private int mTimerMode = TIMER_MODE_COUNTDOWN;
    private long mTimerDeadlineMillis = -1;
    
    private static final long SECOND_MILLIS = 1000;
    private static final long MINUTE_MILLIS = 60*SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60*MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24*HOUR_MILLIS;

    private static final int TIMER_FORMAT_DAY_ONLYNUM = 0;
    private static final int TIMER_FORMAT_DAY = 1;
    private static final int TIMER_FORMAT_DAY_HOUR = 2;
    private static final int TIMER_FORMAT_DAY_HOUR_MIN = 3;
    private static final int TIMER_FORMAT_DAY_HOUR_MIN_SEC = 4;
    
    public static final int TIMER_MODE_COUNTDOWN = 0;
    public static final int TIMER_MODE_ELAPSE = 1;

    public TimerView(Context context)
    {
        super(context);
        initTimerVeiw(context);
    }

    public TimerView(Context context, String viewName)
    {
        super(context, viewName);
        initTimerVeiw(context);
    }
    
    public TimerView(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initTimerVeiw(context);
    }
    
    private void initTimerVeiw(Context context)
    {
        logger.d("Timer View initialize......");

        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mTimerView = inflater.inflate(R.layout.view_timer, null);
        
        // Get widgets from XML file
        if (mTimerView != null)
        {
        	mTimerTxtv = (YSTextView) mTimerView.findViewById(R.id.timer_txtv);
        }
    }

    @Override
    public View getCoverView()
    {
        return mTimerView;
    }

    @Override
    public void viewDestroy()
    {
        cancelUpdateThread();
    }

    @Override
    public void viewPause()
    {
        cancelUpdateThread();
    }

    @Override
    public void viewResume()
    {
        startRunUpdateThread();
    }

    @Override
    public void showMediaList(ArrayList<MediaInfoRef> list)
    {
        if (list == null || list.size() <= 0) 
            return;
        
        cancelUpdateThread();
        mMediaInfo = list.get(0);
        setTimerFormat(getFormat(mMediaInfo));
        setTimerMode(getMode(mMediaInfo));
        setTimerDeadline(getDeadline(mMediaInfo));
        setViewTextSize(getFontSize(mMediaInfo));
        setTypeface(getFont(mMediaInfo));
        setViewTextColor(getFontColor(mMediaInfo));
        startRunUpdateThread();
    }

    private int getFormat(final MediaInfoRef txtInfo)
    {
        if("d".equals(txtInfo.format))
        	return TIMER_FORMAT_DAY_ONLYNUM;
        else if("d天".equals(txtInfo.format))
        	return TIMER_FORMAT_DAY;
        else if("d天H小时".equals(txtInfo.format))
        	return TIMER_FORMAT_DAY_HOUR;
        else if("d天H小时i分".equals(txtInfo.format))
        	return TIMER_FORMAT_DAY_HOUR_MIN;
        else if("d天H小时i分s秒".equals(txtInfo.format))
        	return TIMER_FORMAT_DAY_HOUR_MIN_SEC;
        else
        	return TIMER_FORMAT_DAY_HOUR_MIN_SEC;
    }
    
    private int getMode(final MediaInfoRef txtInfo)
    {
    	return txtInfo.mode;
    }
    
    private long getDeadline(final MediaInfoRef txtInfo)
    {
    	Time t = new Time();
    	t.parse(txtInfo.deadline.replace("-", "").replace(":", "").replace(" ", "T"));
    	return t.toMillis(true);
    }
    
    private void setTimerFormat(int format)
    {
    	mTimerFormat = format;
    }
    
    private void setTimerMode(int mode)
    {
    	mTimerMode = mode;
    }
    
    private void setTimerDeadline(long deadlineMillis)
    {
    	mTimerDeadlineMillis = deadlineMillis;
    }

    private void setViewTextSize(int fSize)
    {
    	mTimerTxtv.setTextSize(fSize);
    }

    private void setTypeface(Typeface typeface)
    {
        if (typeface != null)
        {
        	mTimerTxtv.setTypeface(typeface);
        }
    }

    private void setViewTextColor(int color)
    {
    	mTimerTxtv.setTextColor(color);
    }
    
    private void startRunUpdateThread()
    {
        if (mMediaInfo != null)
        {
            cancelUpdateThread();
            mUpdateTimerThread = new UpdateTimerThread(true);
            mUpdateTimerThread.start();
        }
    }

    private void cancelUpdateThread()
    {
        if (mUpdateTimerThread != null)
        {
        	mUpdateTimerThread.setRunFlag(false);
        	mUpdateTimerThread.interrupt();
        	mUpdateTimerThread = null;
        }
    }

    @SuppressLint("DefaultLocale")
    private final class UpdateTimerThread extends Thread
    {
        private boolean mIsRun = false;

        public UpdateTimerThread(boolean bIsRun)
        {
            setRunFlag(bIsRun);
        }

        public void setRunFlag(boolean bIsRun)
        {
            mIsRun = bIsRun;
        }

        private void updateTextView(YSTextView txtView, String text)
        {
            float yPos = txtView.getTextSize() + txtView.getPaddingTop();
            float xPos = (mMediaInfo.containerwidth - txtView.getPaint().measureText(text)) / 2;
            ArrayList<String> message = new ArrayList<String>();
            message.add(text);
            txtView.setViewAttribute(message, xPos, yPos, txtView.getPaint());
            txtView.postInvalidate();
        }
        
        @Override
        public void run()
        {
            logger.i("New UpdateTimerThread, id is: " + currentThread().getId());

            Time t = new Time();
            long currentMillis, diffMillis;
            long day, hour, minute, second;
            String strTime;
            StringBuilder sb = new StringBuilder();

            while (mIsRun)
            {
                try
                {
                    t.setToNow();
                    currentMillis = t.toMillis(true);
                    
                    switch (mTimerMode)
                    {
                    case TIMER_MODE_COUNTDOWN:
                    	if (currentMillis >= mTimerDeadlineMillis)
                    	{
                    		diffMillis = 0;
                    	}
                    	else
                    	{
                    		diffMillis = mTimerDeadlineMillis-currentMillis;
                    	}
                    	break;
                    case TIMER_MODE_ELAPSE:
                    	if (currentMillis <= mTimerDeadlineMillis)
                    	{
                    		diffMillis = 0;
                    	}
                    	else
                    	{
                    		diffMillis = currentMillis-mTimerDeadlineMillis;
                    	}
                    	break;
                    default:
                    	diffMillis = 0;
                    }
                    
                    day = diffMillis/DAY_MILLIS;
                    hour = (diffMillis%DAY_MILLIS)/HOUR_MILLIS;
                    minute = (diffMillis%HOUR_MILLIS)/MINUTE_MILLIS;
                    second = (diffMillis%MINUTE_MILLIS)/SECOND_MILLIS;
                    
                    switch (mTimerFormat)
                    {
                    case TIMER_FORMAT_DAY_ONLYNUM:
                    	sb.setLength(0);
                    	if ((mTimerMode == TIMER_MODE_COUNTDOWN)
                    			&& ((hour != 0) || (minute != 0) || (second != 0)))
                    	{
                    		day += 1;
                    	}
                    	if (day < 10)
                    	{
                    		sb.append("00");
                    	}
                    	else if (day < 100)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(day);
                    	strTime = sb.toString();
                    	break;
                    case TIMER_FORMAT_DAY:
                    	sb.setLength(0);
                    	if ((mTimerMode == TIMER_MODE_COUNTDOWN)
                    			&& ((hour != 0) || (minute != 0) || (second != 0)))
                    	{
                    		day += 1;
                    	}
                    	if (day < 10)
                    	{
                    		sb.append("00");
                    	}
                    	else if (day < 100)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(day).append("天");
                    	strTime = sb.toString();
                    	break;
                    case TIMER_FORMAT_DAY_HOUR:
                    	sb.setLength(0);
                    	if ((mTimerMode == TIMER_MODE_COUNTDOWN)
                    			&& ((minute != 0) || (second != 0)))
                    	{
                    		if (hour != 23)
                    		{
                    			hour += 1;
                    		}
                    		else
                    		{
                    			day += 1;
                    			hour = 0;
                    		}
                    	}
                    	if (day < 10)
                    	{
                    		sb.append("00");
                    	}
                    	else if (day < 100)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(day).append("天");
                    	if (hour < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(hour).append("小时");
                    	strTime = sb.toString();
                    	break;
                    case TIMER_FORMAT_DAY_HOUR_MIN:
                    	sb.setLength(0);
                    	if ((mTimerMode == TIMER_MODE_COUNTDOWN) && (second != 0))
                    	{
                    		if (minute != 59)
                    		{
                    			minute += 1;
                    		}
                    		else
                    		{
                    			if (hour != 23)
                        		{
                        			hour += 1;
                        		}
                        		else
                        		{
                        			day += 1;
                        			hour = 0;
                        		}
                    			minute = 0;
                    		}
                    	}
                    	if (day < 10)
                    	{
                    		sb.append("00");
                    	}
                    	else if (day < 100)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(day).append("天");
                    	if (hour < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(hour).append("小时");
                    	if (minute < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(minute).append("分");
                    	strTime = sb.toString();
                    	break;
                    case TIMER_FORMAT_DAY_HOUR_MIN_SEC:
                    	sb.setLength(0);
                    	if (day < 10)
                    	{
                    		sb.append("00");
                    	}
                    	else if (day < 100)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(day).append("天");
                    	if (hour < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(hour).append("小时");
                    	if (minute < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(minute).append("分");
                    	if (second < 10)
                    	{
                    		sb.append("0");
                    	}
                    	sb.append(second).append("秒");
                    	strTime = sb.toString();
                    	break;
                    default:
                    	strTime = "";
                    }
                    
                    updateTextView(mTimerTxtv, strTime);
                	
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    logger.i("UpdateTimerThread sleep over, and safe exit, the Thread id is: " + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    logger.e("UpdateTimerThread Catch a error");
                    e.printStackTrace();
                }
            }

            logger.i("UpdateTimerThread is safe Terminate, id is: " + currentThread().getId());
        }
    }
}

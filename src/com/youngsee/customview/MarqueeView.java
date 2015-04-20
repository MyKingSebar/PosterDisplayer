/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import java.util.ArrayList;

import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.posterdisplayer.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

public class MarqueeView extends PosterBaseView
{
    private View                    mMarqueeView            = null;
    private YSTextView              mTextView               = null;
    private FrameLayout             mMarqueeLayout          = null;
    private View                    mProgressBarView        = null;
    
    private float                   mTextLength             = 0.0f;
    private float                   mStep                   = 0.0f;
    private float                   mViewPlusTextLen        = 0.0f; 
    private float                   mViewPlusDoubleTextLen  = 0.0f; 
                                                                        
    private int                     mMoveSpeed             = 1;
    private int                     mMoveDirection         = 0;
    
    private boolean                 mIsMoving              = false;         // 是否滚动
    private int                     mCurrentIndex          = -1;
    private ArrayList<MediaInfoRef> mMediaList             = null;
    
    private boolean                 mIsChangeMedia         = false;
    private UpdateThread            mUpdateThreadHandle    = null;
    private boolean                 mIsShowProgressBar     = false;
    
    private final static int        MOVE_LEFT              = 1;
    private final static int        MOVE_UP                = 2;
    
    private final static int        MOVE_INTERVAL          = 50;
    private final static int        LOW_SPEED_LEVEL        = 0;
    private final static int        MID_SPEED_LEVEL        = 1;
    private final static int        HIGH_SPEED_LEVEL       = 2;
    
    // Define message ID
    private final static int        EVENT_SHOW_PROGRESS_BAR   = 0x6001;
    private final static int        EVENT_HIDE_PROGRESS_BAR   = 0x6002;
                                                                       
    public MarqueeView(Context context)
    {
        super(context);
        initMarqueeVeiw(context);
    }
    
    public MarqueeView(Context context, String viewName)
    {
        super(context, viewName);
        initMarqueeVeiw(context);
    }
    
    public MarqueeView(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initMarqueeVeiw(context);
    }
    
    private void initMarqueeVeiw(Context context)
    {
        logger.d("Marquee View initialize......");
        
        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mMarqueeView = inflater.inflate(R.layout.marquee, null);
        mProgressBarView = inflater.inflate(R.layout.loading_dialog, null);
        
        if (mMarqueeView != null)
        {
            mMarqueeLayout = (FrameLayout) mMarqueeView.findViewById(R.id.marqueelayout);
            mMarqueeLayout.addView(mProgressBarView);
            mProgressBarView.setVisibility(View.GONE);
            
            mTextView = (YSTextView) mMarqueeView.findViewById(R.id.marqueetext);
            mTextView.setGravity((Gravity.LEFT | Gravity.CENTER));
        }
    }
    
    @Override
    public View getCoverView()
    {
        return mMarqueeView;
    }
    
    @Override
    public void viewDestroy()
    {
        mHandler.removeMessages(EVENT_SHOW_PROGRESS_BAR);
        mHandler.removeMessages(EVENT_HIDE_PROGRESS_BAR);
        cancelUpdateThread();
    }
    
    @Override
    public void viewPause()
    {
        cancelUpdateThread();
        try
        {
            if (mMediaList != null && mViewName != null && mCurrentIndex >= 0 && mCurrentIndex < mMediaList.size())
            {
                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mMediaList.get(mCurrentIndex).mid, mViewName, Contants.DOWNMENU);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void viewResume()
    {
        startUpdateThread();
        try
        {
            if (mMediaList != null && mViewName != null && mCurrentIndex >= 0 && mCurrentIndex < mMediaList.size())
            {
                FileUtils.updateFileLastTime(mMediaList.get(mCurrentIndex).filePath);
                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mMediaList.get(mCurrentIndex).mid, mViewName, "");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void showMediaList(ArrayList<MediaInfoRef> list)
    {
        if (list.size() <= 0) return;
        
        mCurrentIndex = 0;
        mMediaList = new ArrayList<MediaInfoRef>(list);
        mIsChangeMedia = true;
        startUpdateThread();
    }
    
    private void startUpdateThread()
    {
        if (mMediaList != null && mMediaList.size() > 0)
        {
            cancelUpdateThread();
            mUpdateThreadHandle = new UpdateThread();
            mUpdateThreadHandle.start();
        }
    }
    
    private void cancelUpdateThread()
    {
        if (mUpdateThreadHandle != null)
        {
            mUpdateThreadHandle.setRunFlag(false);
            mUpdateThreadHandle.interrupt();
            mUpdateThreadHandle = null;
        }
    }
    
    private void loadNextMedia()
    {
        if (mMediaList != null && mMediaList.size() > 0)
        {
            if (++mCurrentIndex >= mMediaList.size())
            {
                mCurrentIndex = 0;
            }
            mIsChangeMedia = true;
        }
    }

    private void setScrollMode(int mode)
    {
        mMoveDirection = MOVE_LEFT;
        switch (mode)
        {
        case 3:
        case 11:
            mMoveDirection = MOVE_LEFT;
            break;
        case 13:
            mMoveDirection = MOVE_UP;
            break;
        default:
            break;
        }
    }
    
    private void setSpeedLevel(int nlevel)
    {
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
    
    private final class UpdateThread extends Thread
    {
        private boolean mIsRun = true;
        
        public UpdateThread()
        {
            mIsRun = true;
        }
        
        public void setRunFlag(boolean bIsRun)
        {
            mIsRun = bIsRun;
        }
        
        @Override
        public void run()
        {
            logger.i("New scroll text thread, id is: " + currentThread().getId());
            
            MediaInfoRef mediaInfo = null;
            
            while (mIsRun)
            {
                try
                {
                    if (mMediaList == null)
                    {
                        logger.i("mMediaList is null, thread exit.");
                        return;
                    }
                    else if (mMediaList.isEmpty())
                    {
                        logger.i("No text info in the list, thread exit.");
                        return;
                    }
                    
                    if (mIsChangeMedia)
                    {
                        // Get the Media Info
                        mediaInfo = mMediaList.get(mCurrentIndex);
                        if (FileUtils.mediaIsFile(mediaInfo) && !FileUtils.isExist(mediaInfo.filePath))
                        {
                            logger.i(mediaInfo.filePath + " didn't exist, skip it.");
                            if (!mIsShowProgressBar && !checkFilesIsValid(mMediaList))
                            {
                                mHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                            }
                            checkIsNeedToDownload(mediaInfo);
                            loadNextMedia();
                            Thread.sleep(100);
                            continue;
                        }
                        else if (FileUtils.mediaIsFile(mediaInfo) && !checkMediaMd5(mediaInfo))
                        {
                            logger.i(mediaInfo.filePath + " verifycode is wrong, skip it.");
                            if (!mIsShowProgressBar && !checkFilesIsValid(mMediaList))
                            {
                                mHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                            }
                            checkIsNeedToDownload(mediaInfo);
                            loadNextMedia();
                            Thread.sleep(100);
                            continue;
                        }
                        else if (!mediaTimeIsValid(mediaInfo))
                        {
                            logger.i(mediaInfo.filePath + " media time is invaild, skip it.");
                            loadNextMedia();
                            Thread.sleep(100);
                            continue;
                        }
                        else
                        {
                            mIsChangeMedia = false;
                            if (mIsShowProgressBar)
                            {
                                mHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                                while (mIsShowProgressBar)
                                {
                                    Thread.sleep(100);
                                }
                            }
                            
                            if (initScrollViewParam(mediaInfo))
                            {
                                mIsMoving = true;
                                Thread.sleep(MOVE_INTERVAL);
                                continue;
                            }
                            else
                            {
                                loadNextMedia();
                            }
                        }
                    }
                    else if (mIsMoving)
                    {
                        movingText(mMoveDirection);
                        Thread.sleep(MOVE_INTERVAL);
                        if (isMoveFinised())
                        {
                            mIsMoving = false;
                            loadNextMedia();
                        }
                        continue;
                    }
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    logger.i("Scroll text Thread sleep over, and safe exit, the Thread id is: "
                            + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    logger.e("Scroll text Thread Catch a error, id is: " + currentThread().getId());
                    e.printStackTrace();
                    LogUtils.getInstance().toAddSLog(Contants.ERROR, Contants.PlayerError, "<CODE>错误号</CODE>");
                    loadNextMedia();
                }
            }
            
            logger.i("Scroll text Thread is safe Terminate, id is: " + currentThread().getId());
        }

        private void movingText(int nDirection)
        {
            if (mTextView != null)
            {
                switch (nDirection)
                {
                case MOVE_LEFT:
                    mStep += mMoveSpeed;
                    mTextView.setXPos(mViewPlusTextLen - mStep);
                    mTextView.postInvalidate();
                    break;
                
                case MOVE_UP:
                    mStep += mMoveSpeed;
                    mTextView.setYPos(mViewPlusTextLen - mStep);
                    mTextView.postInvalidate();
                    break;
                
                default:
                    // Invalid direction, don't start the scroll
                    break;
                }
            }
        }
        
        private boolean initScrollViewParam(MediaInfoRef mediaInfo)
        {
            boolean ret = false;
            if (mTextView != null)
            {
                // 获取文字内容
                String message = getText(mediaInfo);
                if (message != null)
                {
                    // 设置移动方向和速率
                    setScrollMode(mediaInfo.mode);
                    setSpeedLevel(mediaInfo.speed);

                    // 获取字体参数，并创建画笔
                    Paint paint = new Paint();
                    paint.setColor(getFontColor(mediaInfo)); // 颜色
                    paint.setTextSize(getFontSize(mediaInfo)); // 字号
                    paint.setAlpha(0xff); // 字体不透明
                    paint.setTypeface(getFont(mediaInfo)); // 字体
                    paint.setAntiAlias(true); // 去除锯齿
                    paint.setFilterBitmap(true); // 对位图进行滤波处理
                    
                    // 初始化参数
                    float xPos = 0.0f;
                    float yPos = 0.0f;
                    int nViewWidth = mediaInfo.containerwidth;
                    int nViewHeight = mediaInfo.containerheight;
                    ArrayList<String> textList = null;
                    switch (mMoveDirection)
                    {
                    case MOVE_LEFT:
                    {
                        String textMsg = StringFilter(message).replaceAll("\\n+", "");
                        textList = new ArrayList<String>();
                        textList.add(textMsg);
                        mTextLength = (int) paint.measureText(textMsg);
                        mStep = mTextLength;
                        mViewPlusTextLen = nViewWidth + mTextLength;
                        mViewPlusDoubleTextLen = nViewWidth + mTextLength * 2;
                        xPos = nViewWidth;
                        yPos = paint.getTextSize() + mTextView.getPaddingTop();
                        mTextView.setViewAttribute(textList, xPos, yPos, paint); // 设定初始值
                        mTextView.postInvalidate();
                        ret = true;
                    }
                        break;
                    
                    case MOVE_UP:
                    {
                        textList = autoSplit(message, paint, nViewWidth);  // 自动分行
                        FontMetrics fm = paint.getFontMetrics();
                        float fontHeight = (float)Math.ceil(fm.descent - fm.ascent) + fm.leading; // 每行高度
                        mTextLength = textList.size() * fontHeight;
                        mStep = mTextLength;
                        mViewPlusTextLen = nViewHeight + mTextLength;
                        mViewPlusDoubleTextLen = nViewHeight + mTextLength * 2;
                        xPos = mTextView.getPaddingLeft();
                        yPos = nViewHeight;
                        mTextView.setViewAttribute(textList, xPos, yPos, paint); // 设定初始值
                        mTextView.postInvalidate();
                        ret = true;
                    }
                        break;
                    
                    default:
                        // Invalid direction, don't start the scroll
                        break;
                    }
                }
            }
            
            return ret;
        }
        
        private boolean isMoveFinised()
        {
            if (mStep >= mViewPlusDoubleTextLen)
            {
                try
                {
                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mMediaList.get(mCurrentIndex).mid, mViewName, Contants.NOMALSTOP);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        }
    }
    
    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler() {
                               @Override
                               public void handleMessage(Message msg)
                               {
                                   switch (msg.what)
                                   {  
                                   case EVENT_SHOW_PROGRESS_BAR:
                                       if (!mIsShowProgressBar)
                                       {
                                           mTextView.setVisibility(View.GONE);
                                           mProgressBarView.setVisibility(View.VISIBLE);
                                           mIsShowProgressBar = true;
                                       }
                                       return;
                                   
                                   case EVENT_HIDE_PROGRESS_BAR:
                                       if (mIsShowProgressBar)
                                       {
                                           mProgressBarView.setVisibility(View.GONE);
                                           mTextView.setVisibility(View.VISIBLE);
                                           mIsShowProgressBar = false;
                                       }
                                       return;
                                   }
                                   
                                   super.handleMessage(msg);
                               }
                           };
}

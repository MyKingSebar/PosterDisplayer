/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import java.io.IOException;
import java.util.ArrayList;

import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.posterdisplayer.R;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

public class AudioView extends PosterBaseView
{

    private View                    mAudioView                = null;
    private FrameLayout             mAudioLayout              = null;

    private int                     mCurrentIndex             = -1;
    private ArrayList<MediaInfoRef> mMediaList                = null;

    private boolean                 mIsChangeMedia            = false;
    private UpdateThread            mUpdateThreadHandle       = null;

    private MediaPlayer             mMediaPlayer              = null;
    
    private boolean                 mIsStopped                = false;
    
    public AudioView(Context context)
    {
        super(context);
        initMarqueeVeiw(context);
    }
    
    public AudioView(Context context, String viewName)
    {
        super(context, viewName);
        initMarqueeVeiw(context);
    }
    
    public AudioView(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initMarqueeVeiw(context);
    }
    
    private void initMarqueeVeiw(Context context)
    {
        logger.d("Audio View initialize......");
        
        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mAudioView = inflater.inflate(R.layout.audio, null);
        
        if (mAudioView != null)
        {
            mAudioLayout = (FrameLayout) mAudioView.findViewById(R.id.audiolayout);
        }
    }
    
    @Override
    public View getCoverView()
    {
        return mAudioView;
    }
    
    @Override
    public void viewDestroy()
    {
        cancelUpdateThread();
        destroyAudio();
    }
    
    @Override
    public void viewPause()
    {
    	pauseUpdateThread();
    	pauseAudio();
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
    	resumeAudio();
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
        resumeUpdateThread();
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
    
    @Override
    public void viewStart()
    {
    	startAudio();
    }
    
    @Override
    public void viewStop()
    {
    	stopAudio(true);
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
            mUpdateThreadHandle.cancel();
            mUpdateThreadHandle = null;
        }
    }
    
    private void pauseUpdateThread()
    {
        if (mUpdateThreadHandle != null && !mUpdateThreadHandle.isPaused())
        {
            mUpdateThreadHandle.onPause();
        }
    }
    
    private void resumeUpdateThread()
    {
        if (mUpdateThreadHandle != null && mUpdateThreadHandle.isPaused())
        {
            mUpdateThreadHandle.onResume();
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

    private final class UpdateThread extends Thread
    {
        private boolean  mIsRun      = true;
        private Object   mPauseLock  = null;
        private boolean  mPauseFlag  = false;
        
        public UpdateThread()
        {
            mIsRun = true;
            mPauseLock = new Object();
            mPauseFlag = false;
        }

        public void cancel()
        {
            mIsRun = false;
            this.interrupt();
        }
        
        public void onPause()
        {
        	logger.i("Pauses the audio thread.");
            synchronized (mPauseLock)
            {
                mPauseFlag = true;
            }
        }
        
        public void onResume()
        {
        	logger.i("Resumes the audio thread.");
            synchronized (mPauseLock)
            {
            	mPauseFlag = false;
                mPauseLock.notify();
            }
        }
        
        public boolean isPaused()
        {
            return mPauseFlag;
        }
        
        @Override
        public void run()
        {
            logger.i("New audio thread, id is: " + currentThread().getId());
            
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
                        logger.i("No audio info in the list, thread exit.");
                        return;
                    }
                    else if ((mCurrentIndex < 0) || (mCurrentIndex >= mMediaList.size()))
                    {
                    	logger.i("mCurrentIndex is invalid, thread exit.");
                        return;
                    }
                    
                    synchronized (mPauseLock)
                    {
                        if (mPauseFlag)
                        {
                            try
                            {
                                mPauseLock.wait();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }

					if (mIsChangeMedia) {
						// Get the Media Info
						mediaInfo = mMediaList.get(mCurrentIndex);
						if (FileUtils.mediaIsFile(mediaInfo)
								&& !FileUtils.isExist(mediaInfo.filePath))
						{
							logger.i(mediaInfo.filePath + " didn't exist, skip it.");
							checkIsNeedToDownload(mediaInfo);
							loadNextMedia();
							Thread.sleep(100);
							continue;
						}
						else if (FileUtils.mediaIsFile(mediaInfo)
								&& !checkMediaMd5(mediaInfo))
						{
							logger.i(mediaInfo.filePath + " verifycode is wrong, skip it.");
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
							playMusic(mediaInfo.filePath);
						}
					}
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    logger.i("Audio thread sleep over, and safe exit, the thread id is: "
                            + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    logger.e("Audio thread catch a error, id is: " + currentThread().getId());
                    e.printStackTrace();
                    LogUtils.getInstance().toAddSLog(Contants.ERROR, Contants.PlayerError, "<CODE>错误号</CODE>");
                    loadNextMedia();
                }
            }
            
            logger.i("Audio thread is safely terminated, id is: " + currentThread().getId());
        }
    }
    
    private void playMusic(String path)
    {
        if (path == null)
        {
        	logger.i("Music path is null!");
        	loadNextMedia();
            return;
        }
        
        if (mMediaPlayer == null)
        {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
            mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
            mMediaPlayer.setOnErrorListener(mOnErrorListener);
        }
        
        try
        {
        	stopAudio(false);
            mMediaPlayer.reset(); // 重置
            mMediaPlayer.setDataSource(path); // 设置数据源
            mMediaPlayer.prepareAsync(); // 异步准备
            FileUtils.updateFileLastTime(path);
        }
        catch (IOException e)
		{
			e.printStackTrace();
			loadNextMedia();
		}
        
    }
    
    private OnPreparedListener mOnPreparedListener = new OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp)
        {
        	mMediaPlayer.start();
        }
    };
    
    private OnCompletionListener mOnCompletionListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp)
        {
        	loadNextMedia();
        }
    };
    
    private OnErrorListener mOnErrorListener = new OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
            stopAudio(false);
            loadNextMedia();
            return true;
        }
    };
    
    private void pauseAudio()
    {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
        {
            mMediaPlayer.pause();
        }
    }
    
    private void resumeAudio()
    {
    	if (mIsStopped)
    	{
    		return;
    	}
    	
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying())
        {
            mMediaPlayer.start();
        }
        else
        {
        	loadNextMedia();
        }
    }
    
    private void destroyAudio()
    {
    	if (mMediaPlayer != null)
        {
    		if (mMediaPlayer.isPlaying())
    		{
    			mMediaPlayer.stop();
    		}
            mMediaPlayer.release();
        }
    }
    
    private void startAudio()
    {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
        {
        	return;
        }
        loadNextMedia();
        mIsStopped = false;
    }
    
    private void stopAudio(boolean byOther)
    {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
        {
            mMediaPlayer.stop();
        }
        if (byOther)
        {
        	mIsStopped = true;
        }
    }
}

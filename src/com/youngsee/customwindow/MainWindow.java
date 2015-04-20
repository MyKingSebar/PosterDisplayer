/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customwindow;

import java.io.File;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnHoverListener;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;

import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.customview.ControlView;
import com.youngsee.customview.PosterBaseView;
import com.youngsee.customview.SoundView;
import com.youngsee.customview.VideoView;
import com.youngsee.customview.ControlView.OnControlChangedListener;
import com.youngsee.customview.SoundView.OnVolumeChangedListener;
import com.youngsee.customview.VideoView.OnViewSizeChangeListener;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.PosterMainActivity;
import com.youngsee.posterdisplayer.UrgentPlayerActivity;
import com.youngsee.screenmanager.ScreenManager;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.gifdecode.GifDecodeInfo;
import com.youngsee.posterdisplayer.R;

public class MainWindow extends PosterBaseView
{
    private View                    mMainWindow            = null;
    private FrameLayout             mMainWndLayout         = null;
    private VideoView               mVideoView             = null;
    private View                    mProgressBarView       = null;
    
    private ControlView             mControlView           = null;
    private PopupWindow             mControlWindow         = null;
    
    private SoundView               mSoundView             = null;
    private PopupWindow             mSoundWindow           = null;
    
    private int                     mCurrentVolume         = 0;
    private boolean                 mIsOnline              = false;
    private int                     mPlayedTime            = -1;
    
    private int                     mWindowWidth           = 0;
    private int                     mWindowHeight          = 0;
    
    private int                     mControlWidth          = 0;
    private int                     mControlHeight         = 0;
    private int                     mControlXPos           = 0;
    private int                     mControlYPos           = 0;
    
    private boolean                 mIsMsgHandleDone       = false;
    private boolean                 mIsChangeMedia         = false;
    private boolean                 mIsShowProgressBar     = false;
    
    private int                     mCurrentIdx            = -1;
    private ArrayList<MediaInfoRef> mPlayList              = null;
    private UpdateThread            mUpdateThreadHandle    = null;
    
    private final static int        DEFAULT_VOLUME_VALUE   = 3;
    private final static int        CONTROLBAR_STAY_TIME   = 3000;
    
    // Define message ID
    private final static int        EVENT_PROGRESS_CHANGED       = 0x0000;
    private final static int        EVENT_HIDE_CONTROLER         = 0x0001;
    private final static int        EVENT_HANDLE_VIDEO_ABEND     = 0x0002;
    private final static int        EVENT_PLAY_VIDEO_MEDIA       = 0x0003;
    private final static int        EVENT_HIDE_ALL_CONTROLER_BAR = 0x0004;
    private final static int        EVENT_SHOW_PROGRESS_BAR      = 0x0005;
    private final static int        EVENT_HIDE_PROGRESS_BAR      = 0x0006;
    
    public MainWindow(Context context)
    {
        super(context);
        initMainWindow(context);
    }
    
    public MainWindow(Context context, String viewName)
    {
        super(context, viewName);
        initMainWindow(context);
    }
    
    public MainWindow(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initMainWindow(context);
    }
    
    @SuppressLint("NewApi")
    private void initMainWindow(Context context)
    {
        logger.d("main window initialize......");
        
        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mMainWindow = inflater.inflate(R.layout.mainwnd, null);
        mProgressBarView = inflater.inflate(R.layout.loading_dialog, null);
 
        // Get widgets from XML file
        if (mMainWindow != null)
        {
            mMainWndLayout = (FrameLayout) mMainWindow.findViewById(R.id.MainWndlayout);
            mVideoView = new VideoView(mContext);
            mMainWndLayout.addView(mVideoView);
            
            mMainWndLayout.addView(mProgressBarView);
            mProgressBarView.setVisibility(View.GONE);
        }
        
        // Create Sound Window
        mSoundView = new SoundView(mContext);
        mSoundView.setOnVolumeChangeListener(new OnVolumeChangedListener() {
            @Override
            public void setYourVolume(int index)
            {
                myHandler.removeMessages(EVENT_HIDE_CONTROLER);
                updateVolume(index);
                hideControllerDelay();
            }
        });
        mSoundWindow = new PopupWindow(mSoundView);
        mSoundWindow.setOutsideTouchable(true);
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mCurrentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mCurrentVolume = (mCurrentVolume > 0) ? mCurrentVolume : 0;
        
        // Create Control Window
        mControlView = new ControlView(mContext);
        if (mCurrentVolume <= 0)
        {
            mControlView.setVolumeButtonImage(R.drawable.sounddisable);
        }
        mControlView.setOnControlChangedListener(new OnControlChangedListener() {
            @Override
            public void onSeekBarProgressChanged(SeekBar seekbar, int progress, boolean fromUser)
            {
                if (fromUser && !mIsOnline)
                {
                    mVideoView.seekTo(progress);
                }
            }
            
            @Override
            public void onSeekBarStartTrackingTouch(SeekBar arg0)
            {
                myHandler.removeMessages(EVENT_HIDE_CONTROLER);
            }
            
            @Override
            public void onSeekBarStopTrackingTouch(SeekBar mSeekBar)
            {
                hideControllerDelay();
            }
            
            @Override
            public void onPlayButtonClick(View v)
            {
                myHandler.removeMessages(EVENT_HIDE_CONTROLER);
                if (mVideoView.isPlaying())
                {
                    pauseUpdateThread();
                    mVideoView.pause();
                    mControlView.setPlayButtonImage(R.drawable.play);
                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mPlayList.get(mCurrentIdx).mid, mViewName, Contants.DOWNPAUSE);
                }
                else
                {
                    mVideoView.start();
                    mControlView.setPlayButtonImage(R.drawable.pause);
                    resumeUpdateThread();
                    hideControllerDelay();
                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mPlayList.get(mCurrentIdx).mid, mViewName, "");
                }
            }
            
            @Override
            public void onVolumeButtonClick(View v)
            {
                myHandler.removeMessages(EVENT_HIDE_CONTROLER);
                if (mSoundWindow.isShowing())
                {
                    hideSoundView();
                }
                else
                {
                    showSoundView();
                }
                hideControllerDelay();
            }
            
            @Override
            public void onVolumeButtonLongPress(View v)
            {
                myHandler.removeMessages(EVENT_HIDE_CONTROLER);
                
                if (mSoundWindow != null && mSoundWindow.isShowing())
                {
                    hideSoundView();
                }
                
                // Update the system Volume
                if (mCurrentVolume > 0)
                {
                    // change volume to Slient
                    updateVolume(0);
                    mControlView.setVolumeButtonImage(R.drawable.sounddisable);
                }
                else
                {
                    // change volume to Default
                    updateVolume(DEFAULT_VOLUME_VALUE);
                    mControlView.setVolumeButtonImage(R.drawable.soundenable);
                }
                
                hideControllerDelay();
            }
        });
        mControlWindow = new PopupWindow(mControlView.getViewScreen());
        mControlWindow.setOutsideTouchable(true);
        
        // Register call back function for Video View
        if (mVideoView != null)
        {
            // Call back when the media player prepared
            mVideoView.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp)
                {
                    setVideoSize();
                    
                    if (mPlayedTime > 0)
                    {
                        mVideoView.seekTo(mPlayedTime);
                        mPlayedTime = -1;
                    }
                    
                    mVideoView.start();
                    if (mp != null)
                    {
                        mControlView.setSeekBarMax(mp.getDuration());
                    }
                    mControlView.setPlayButtonImage(R.drawable.pause);
                    myHandler.sendEmptyMessage(EVENT_HIDE_CONTROLER);
                }
            });
            
            // Call back when the media player Completed
            mVideoView.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0)
                {
                    mIsOnline = false;

                    if (mPlayList != null && mPlayList.size() > 0)
                    {
                        mVideoView.stopPlayback();
                        mVideoView.releasVideoResource();
                        loadNextMedia();
                    }
                    else
                    {
                        stopPlay();
                    }

                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mPlayList.get(mCurrentIdx).mid, mViewName, Contants.NOMALSTOP);
                }
            });
            
            // Call back when the size change
            mVideoView.setViewSizeChangeListener(new OnViewSizeChangeListener() {
                @Override
                public void OnViewSizeChange()
                {
                    setVideoSize();
                }
            });
            
            // Call back when the media player error happened
            mVideoView.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra)
                {
                    logger.e("video view has error.");
                    
                    resetSurfaceView();
                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mPlayList.get(mCurrentIdx).mid, mViewName, Contants.ERRORSTOP);
                    loadNextMedia();
                    LogUtils.getInstance().toAddSLog(Contants.ERROR, Contants.PlayerError, "<CODE></CODE>");
                    return true;
                }
            });
        }
        
        mMainWindow.setOnHoverListener(new OnHoverListener() {
            @Override
            public boolean onHover(View v, MotionEvent event)
            {
                if ((v == mMainWndLayout)
                		&& ((mMainWndLayout.getX()+event.getX()) > mControlXPos)
                		&& ((mMainWndLayout.getY()+event.getY()) > mControlYPos))
                {
                    showController();
                }
                return false;
            }
        });
    }
    
    @Override
    public View getCoverView()
    {
        return mMainWindow;
    }
    
    @Override
    public void viewDestroy()
    {
        myHandler.removeMessages(EVENT_PROGRESS_CHANGED);
        myHandler.removeMessages(EVENT_HIDE_CONTROLER);
        myHandler.removeMessages(EVENT_HANDLE_VIDEO_ABEND);
        myHandler.removeMessages(EVENT_PLAY_VIDEO_MEDIA);
        myHandler.removeMessages(EVENT_HIDE_ALL_CONTROLER_BAR);
        myHandler.removeMessages(EVENT_SHOW_PROGRESS_BAR);
        myHandler.removeMessages(EVENT_HIDE_PROGRESS_BAR);
        
        stopPlay();
        
        if (mControlWindow != null && mControlWindow.isShowing())
        {
            mControlWindow.dismiss();
        }
        
        if (mSoundWindow != null && mSoundWindow.isShowing())
        {
            mSoundWindow.dismiss();
        }
    }
    
    @Override
    public void viewPause()
    {
        cancelUpdateThread();
        
        if (mVideoView != null && mVideoView.isPlaying())
        {     
            mPlayedTime = mVideoView.getCurrentPosition();
            stopPlay();
            mVideoView.setVisibility(View.GONE);
            mControlView.setPlayButtonImage(R.drawable.play);
            
            try
            {
                if (mPlayList != null && mViewName != null && mCurrentIdx >= 0 && mCurrentIdx < mPlayList.size())
                {
                    LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mPlayList.get(mCurrentIdx).mid, mViewName, Contants.DOWNMENU);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void viewResume()
    {
        if (mVideoView.getVisibility() != View.VISIBLE)
        {
            mVideoView.setVisibility(View.VISIBLE);
        }
        
        if (mVideoView != null && 
            mPlayList != null && 
            !mPlayList.isEmpty() && 
            mCurrentIdx >= 0 && mCurrentIdx < mPlayList.size() &&
            FileUtils.mediaIsVideo(mPlayList.get(mCurrentIdx)) &&
            FileUtils.mediaIsFile(mPlayList.get(mCurrentIdx)) &&
            FileUtils.isExist(mPlayList.get(mCurrentIdx).filePath) &&
            checkMediaMd5(mPlayList.get(mCurrentIdx)))
        {
            MediaInfoRef mediaInfo = mPlayList.get(mCurrentIdx);
            mIsOnline = !FileUtils.mediaIsFile(mediaInfo);
            mVideoView.showVideo(mediaInfo);
            
            mControlView.setPlayButtonImage(R.drawable.pause);
            myHandler.sendEmptyMessage(EVENT_HIDE_CONTROLER);
            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mPlayList.get(mCurrentIdx).mid, mViewName, "");
        }
        
        startUpdateThread();
    }
    
    @Override
    public void showMediaList(ArrayList<MediaInfoRef> list)
    {
        if (list == null || list.size() <= 0)
        {
            return;
        }
        
        stopPlay();
        mCurrentIdx = 0;
        mPlayList = new ArrayList<MediaInfoRef>(list);
        mIsChangeMedia = true;
        startUpdateThread();
    }
    
    /**
     * Set the position of the video window.
     */
    public void setWindowPosition(int xPos, int yPos)
    {
        if (mMainWndLayout != null)
        {
            mMainWndLayout.setX(xPos);
            mMainWndLayout.setY(yPos);
        }
    }
    
    /**
     * Set the size of the video window.
     */
    public void setWindowSize(int nWidth, int nHeight)
    {
        if (mMainWndLayout != null)
        {
            mMainWndLayout.setLayoutParams(new FrameLayout.LayoutParams(nWidth, nHeight, Gravity.TOP));
        }
        
        mWindowWidth = nWidth;
        mWindowHeight = nHeight;
        
        mControlWidth = nWidth;
        mControlHeight = nHeight / 6;
    }
    
    /**
     * Stop playing video or Picture.
     */
    private void stopPlay()
    {
        cancelUpdateThread();
        
        if (mVideoView != null)
        {
            if (mVideoView.isPlaying())
            {
                mVideoView.stopPlayback();
            }
            mVideoView.releasVideoResource();
        }
    }
    
    /**
     * Clean the play list
     */
    public void cleanPlayList()
    {
        mPlayList = null;
    }
    
    private void playVideoMedia(MediaInfoRef mediaInfo)
    {
        if (mVideoView == null)
        {
            logger.e("video View is null.");
            loadNextMedia();
            return;
        }
        else if (mediaInfo == null)
        {
            logger.i("media info is null.");
            loadNextMedia();
            return;
        }
        else if (!FileUtils.mediaIsVideo(mediaInfo))
        {
            logger.i("media info is not video.");
            loadNextMedia();
            return;
        }
        else if (FileUtils.mediaIsFile(mediaInfo) && !FileUtils.isExist(mediaInfo.filePath))
        {
            logger.i("video didn't exist.");
            loadNextMedia();
            return;
        }

        resetSurfaceView();
        mIsOnline = !FileUtils.mediaIsFile(mediaInfo);
        mVideoView.showVideo(mediaInfo);
        LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
    }
    
    private void resetSurfaceView()
    {
        if (mVideoView != null)
        {
            if (mVideoView.isPlaying())
            {
                mVideoView.stopPlayback();
            }
            else
            {
                mVideoView.releasVideoResource();
            }
            mVideoView.setVisibility(View.GONE);
            mVideoView.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideSoundView()
    {
        if (mSoundWindow != null && mSoundWindow.isShowing())
        {
            mSoundWindow.dismiss();
        }
    }
    
    private void showSoundView()
    {
        if (mVideoView != null && mVideoView.isPlaying() && mSoundWindow != null && !mSoundWindow.isShowing())
        {
            int xPos = mWindowWidth - SoundView.MY_WIDTH;
            int yPos = mWindowHeight - mControlHeight - SoundView.MY_HEIGHT;
            mSoundWindow.showAtLocation(mVideoView, Gravity.NO_GRAVITY, xPos, yPos);
            mSoundWindow.update(xPos, yPos, SoundView.MY_WIDTH, SoundView.MY_HEIGHT);
        }
    }
    
    private void hideController()
    {
        myHandler.removeMessages(EVENT_HIDE_CONTROLER);
        
        hideSoundView();
        
        if (mControlWindow != null && mControlWindow.isShowing())
        {
            mControlWindow.dismiss();
        }
    }
    
    private void showController()
    {
        if (mVideoView != null && mVideoView.isPlaying() && mControlWindow != null && !mControlWindow.isShowing())
        {
            mControlXPos = (int) mMainWndLayout.getX();
            mControlYPos = (int) mMainWndLayout.getY() + mWindowHeight - mControlHeight;
            mControlWindow.showAtLocation(mVideoView, Gravity.NO_GRAVITY, mControlXPos, mControlYPos);
            mControlWindow.update(mControlXPos, mControlYPos, mControlWidth, mControlHeight);
            hideControllerDelay();
        }
    }
    
    private void hideControllerDelay()
    {
        myHandler.sendEmptyMessageDelayed(EVENT_HIDE_CONTROLER, CONTROLBAR_STAY_TIME);
    }
    
    private void setVideoSize()
    {
        if (mVideoView != null)
        {
            mVideoView.setVideoScale(mWindowWidth, mWindowHeight);
        }
    }
    
    private void updateVolume(int index)
    {
        // Get System Volume Information
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null)
        {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_PLAY_SOUND);
            mCurrentVolume = index;
        }
    }

    private boolean materaialsIsAllShow()
    {
        for (MediaInfoRef media : mPlayList)
        {
            if (media.playedtimes <= 0)
            {
                return false;
            }
        }
        return true;
    }
    
    private void loadNextMedia()
    {
        if (mPlayList != null && mPlayList.size() > 0)
        {
            if (++mCurrentIdx >= mPlayList.size())
            {
                mCurrentIdx = 0;
                if (materaialsIsAllShow())
                {
                    ScreenManager.getInstance().setPrgFinishedFlag(true);
                }
            }
            mIsChangeMedia = true;
        }
    }
    
    private void startUpdateThread()
    {
        if (mPlayList != null && mPlayList.size() > 0)
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
            // Resume the update thread if it is paused
            mUpdateThreadHandle.onResume();
        }
    }
    
    // Defined a thread to update play progress
    @SuppressLint("DefaultLocale")
    private final class UpdateThread extends Thread
    {
        private boolean    mIsRun        = false;
        private Object     mPauseLock    = null;
        private boolean    mPauseFlag    = false;
        private int        mMonitorCnt   = 0;

        public UpdateThread()
        {
            mIsRun = true;
            mPauseLock = new Object();
            mPauseFlag = false;
            mMonitorCnt = 0;
        }
        
        public void cancel()
        {
            mIsRun = false;
            this.interrupt();
        }
        
        public void onPause()
        {
            synchronized (mPauseLock)
            {
                mPauseFlag = true;
            }
        }
        
        public void onResume()
        {
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
        
        private void checkIsPauseThread()
        {
            synchronized (mPauseLock)
            {
                if (mPauseFlag)
                {
                    try
                    {
                        logger.i("thread <" + currentThread().getId() + "> is Paused");
                        mPauseLock.wait();
                        logger.i("thread <" + currentThread().getId() + "> is Resume");
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        Log.v("updateProgressThread", "thread pause fails");
                    }
                }
            }
        }
        
        private void waitForSurfaceViewDone() throws InterruptedException
        {
            if (mVideoView == null)
            {
                return;
            }
            
            while (mVideoView.isSurfaceDestroyed())
            {
                Thread.sleep(100);
            }
        }
        
        @Override
        public void run()
        {
            logger.i("New Refresh Thread in mainWindow:, thread id is: " + currentThread().getId());

            Bitmap destBmp = null;
            MediaInfoRef mediaInfo = null;
            int mLastShowMediaIdx = -1;
            
            while (mIsRun)
            {
                try
                {
                    checkIsPauseThread();
                    if (mVideoView != null && mPlayList != null && !mPlayList.isEmpty() && mCurrentIdx >= 0 && mCurrentIdx < mPlayList.size())
                    {
                        mediaInfo = mPlayList.get(mCurrentIdx);
                        if (mIsChangeMedia && FileUtils.mediaIsFile(mediaInfo) && !FileUtils.isExist(mediaInfo.filePath))
                        {
                            logger.i(mediaInfo.filePath + " didn't exist, skip it.");
                            if (!mIsShowProgressBar && !checkFilesIsValid(mPlayList))
                            {
                                myHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                            }
                            checkIsNeedToDownload(mediaInfo);
                            loadNextMedia();
                            Thread.sleep(100);
                            continue;
                        }
                        else if (mIsChangeMedia && FileUtils.mediaIsFile(mediaInfo) && !checkMediaMd5(mediaInfo))
                        {
                            logger.i(mediaInfo.filePath + " verifycode is wrong, skip it.");
                            if (!mIsShowProgressBar && !checkFilesIsValid(mPlayList))
                            {
                                myHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                            }
                            checkIsNeedToDownload(mediaInfo);
                            loadNextMedia();
                            Thread.sleep(100);
                            continue;
                        }
                        else if (mIsChangeMedia && !mediaTimeIsValid(mediaInfo))
                        {
                            logger.i(mediaInfo.filePath + " media time is invaild, skip it.");
                            loadNextMedia();
                            continue;
                        }
                        else if (FileUtils.mediaIsVideo(mediaInfo))
                        {
                            if (mIsChangeMedia)
                            {
                                if (mIsShowProgressBar)
                                {
                                    myHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                                }
                                waitForSurfaceViewDone();
                                informActivityStopMusic();
                                
                                mIsChangeMedia = false;
                                mIsMsgHandleDone = false;
                                
                                Message msg = myHandler.obtainMessage();
                                msg.what = EVENT_PLAY_VIDEO_MEDIA;
                                msg.obj = mediaInfo;
                                myHandler.sendMessage(msg);
                                
                                // wait for video load
                                while (!mIsMsgHandleDone)
                                {
                                    Thread.sleep(100);
                                }
                                mMonitorCnt = 0;
                                mediaInfo.playedtimes++;
                                mLastShowMediaIdx = mCurrentIdx;
                            }
                            
                            if (mVideoView.isPlaying() && mControlWindow.isShowing())
                            {
                                updateVideoProgress();
                            }
                            
                            /***********************************************************
                             * Monitor the play progress, if the progress stay at 0 * 
                             * position is too long, then re-star the player. *
                             ***********************************************************/
                            if (mVideoView.getCurrentPosition() <= 0)
                            {
                                mMonitorCnt++;
                            }
                            else
                            {
                                mMonitorCnt = 0;
                            }
                            
                            if (mIsOnline)
                            {
                                // if stay at 0 position longer than 10s, then re-start it.
                                if (mMonitorCnt > 100)
                                {
                                    mMonitorCnt = 0;
                                    myHandler.sendEmptyMessage(EVENT_HANDLE_VIDEO_ABEND);
                                }
                            }
                            else
                            {
                                // if stay at 0 position longer than 5s, then re-start it.
                                if (mMonitorCnt > 50)
                                {
                                    mMonitorCnt = 0;
                                    myHandler.sendEmptyMessage(EVENT_HANDLE_VIDEO_ABEND);
                                }
                            }
                            
                            Thread.sleep(100);
                            continue;
                        }
                        else if (mIsChangeMedia && FileUtils.mediaIsGifFile(mediaInfo))
                        {
                            mIsChangeMedia = false;
                            if (mPlayList != null && mLastShowMediaIdx >= 0 && mLastShowMediaIdx < mPlayList.size() && FileUtils.mediaIsVideo(mPlayList.get(mLastShowMediaIdx)))
                            {
                                sendHideAllControlMsg();
                                informActivityPlayMusic();
                            }
                            
                            if (displayGif(mediaInfo))
                            {
                                mLastShowMediaIdx = mCurrentIdx;
                                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
                            }
                            mediaInfo.playedtimes++;   // if media has error, the played times will increase.
                        }
                        else if (mIsChangeMedia && (FileUtils.mediaIsPicFromFile(mediaInfo) || FileUtils.mediaIsPicFromNet(mediaInfo)))
                        {
                            mIsChangeMedia = false;
                            if (mIsShowProgressBar)
                            {
                                myHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                            }
                            
                            if (mPlayList != null && mLastShowMediaIdx >= 0 && mLastShowMediaIdx < mPlayList.size() && FileUtils.mediaIsVideo(mPlayList.get(mLastShowMediaIdx)))
                            {
                                sendHideAllControlMsg();
                                informActivityPlayMusic();
                            }
                            
                            waitForSurfaceViewDone();
                            if ((destBmp = getBitMap(mediaInfo)) != null)
                            {
                                mVideoView.showPicture(destBmp);
                                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
                                // 图片静止显示时间
                                Thread.sleep(Math.max(mediaInfo.durationPerPage, 1000));
                                mLastShowMediaIdx = mCurrentIdx;
                            }
                            mediaInfo.playedtimes++;   // if media has error, the played times will increase.
                        }
                        else if (mIsChangeMedia && (FileUtils.mediaIsTextFromFile(mediaInfo) || FileUtils.mediaIsTextFromNet(mediaInfo)))
                        {
                            mIsChangeMedia = false;
                            if (mIsShowProgressBar)
                            {
                                myHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                            }
                            
                            if (mPlayList != null && mLastShowMediaIdx >= 0 && mLastShowMediaIdx < mPlayList.size() && FileUtils.mediaIsVideo(mPlayList.get(mLastShowMediaIdx)))
                            {
                                sendHideAllControlMsg();
                                informActivityPlayMusic();
                            }
                            
                            waitForSurfaceViewDone();
                            String strMessage = getText(mediaInfo);
                            if (strMessage != null)
                            {
                                Paint paint = new Paint();
                                paint.setTypeface(getFont(mediaInfo)); // 字体
                                paint.setColor(getFontColor(mediaInfo)); // 颜色
                                paint.setTextSize(getFontSize(mediaInfo)); // 字号
                                paint.setAlpha(0xff); // 字体不透明
                                paint.setAntiAlias(true); // 去除锯齿
                                mVideoView.showText(autoSplit(strMessage, paint, mediaInfo.containerwidth), paint, mediaInfo.durationPerPage);
                                mLastShowMediaIdx = mCurrentIdx;
                                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
                            }
                            mediaInfo.playedtimes++;   // if media has error, the played times will increase.
                        }

                        loadNextMedia();
                        continue;
                    }
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    terminateGifDecode();
                    logger.i("RefreshThread sleep over, and safe exit, it is MainWindow." + " thread id is: " + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    logger.e("MainWindow refresh thread Catch a error." + " thread id is: " + currentThread().getId());
                    e.printStackTrace();
                    loadNextMedia();
                }
            }
            
            terminateGifDecode();
            logger.i("RefreshThread is Terminate, it is MainWindow." + " thread id is: " + currentThread().getId());
        }
        
        private void updateVideoProgress()
        {
            int nCurrentPrg = mVideoView.getCurrentPosition();
            int nTime = nCurrentPrg / 1000;
            int minute = nTime / 60;
            int hour = minute / 60;
            int second = nTime % 60;
            minute %= 60;
            StringBuilder sb = new StringBuilder();
            sb.append(hour).append(":");
            sb.append(minute).append(":");
            sb.append(second);
            String strPlayedTime = sb.toString();
            
            nTime = mVideoView.getDuration() / 1000;
            minute = nTime / 60;
            hour = minute / 60;
            second = nTime % 60;
            minute %= 60;
            sb.setLength(0);
            sb.append(hour).append(":");
            sb.append(minute).append(":");
            sb.append(second);
            String strDurationTime = sb.toString();
            
            /***********************************************************
             * Build the message body, and send Message to UI thread, * 
             * and inform UI thread to update the view content *
             ***********************************************************/
            Bundle bundle = new Bundle();
            bundle.putInt("CurrentPostion", nCurrentPrg);
            bundle.putString("PlayedTime", strPlayedTime);
            bundle.putString("DurationTime", strDurationTime);
            
            Message msg = myHandler.obtainMessage();
            msg.what = EVENT_PROGRESS_CHANGED;
            msg.setData(bundle);
            myHandler.sendMessage(msg);
        }

        private boolean displayGif(MediaInfoRef picInfo) throws InterruptedException
        {            
            // decode for GIF
            boolean isComplated = isDecodeComplated(picInfo);
            GifDecodeInfo decodeInfo = getGifDecodeInfo(picInfo);
            if ((decodeInfo == null) || 
                (isComplated && !gifImgIsExsit(picInfo)))   // 未解码或已解码但文件不存在，则启动解码
            {
                // Start decode
                decodeGifPicture(picInfo);
                
                // Get Current Decode Info
                decodeInfo = getCurrentDecodeInfo(); 
                
                // Wait for decode
                while (decodeInfo.getDecodeState() != GifDecodeInfo.DECODE_COMPLATED)
                {
                    Thread.sleep(100);
                }
                
                // release resource for decoder
                releaseGifDecoder();                 
            }
            else if (decodeInfo != null && !isComplated)
            {
                return false;   // decoding by other view
            }
            
            if (mIsShowProgressBar)
            {
                myHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
            }
            waitForSurfaceViewDone();
            
            // Show GIF
            boolean ret = false;
            if (decodeInfo != null)
            {
                MediaInfoRef tempMedia = new MediaInfoRef();
                tempMedia.mediaType = "Image";
                tempMedia.source = "File";
                tempMedia.aspect = picInfo.aspect;
                tempMedia.duration = picInfo.duration;
                tempMedia.durationPerPage = picInfo.durationPerPage;
                tempMedia.endtime = picInfo.endtime;
                tempMedia.mid = picInfo.mid;
                tempMedia.mode = picInfo.mode;
                tempMedia.md5Key = picInfo.md5Key;
                tempMedia.playedtimes = picInfo.playedtimes;
                tempMedia.playlistmode = picInfo.playlistmode;
                tempMedia.starttime = picInfo.starttime;
                tempMedia.times = picInfo.times;
                tempMedia.timetype = picInfo.timetype;
                tempMedia.containerwidth = picInfo.containerwidth;
                tempMedia.containerheight = picInfo.containerheight;
                
                Bitmap srcBmp = null;
                StringBuilder sbImgFile = new StringBuilder();
                String strPath = PosterApplication.getGifImagePath(picInfo.verifyCode);
                for (int i = 0; i < decodeInfo.getFrameCount(); i++)
                {
                    sbImgFile.setLength(0);
                    sbImgFile.append(strPath);
                    sbImgFile.append(File.separator);
                    sbImgFile.append(i).append(".jpg");
                    tempMedia.filePath = sbImgFile.toString();
                    if ((srcBmp = getBitMap(tempMedia)) != null)
                    {
                        try
                        {
                            ret = true;
                            mVideoView.showPicture(srcBmp);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                            continue;
                        }
                        Thread.sleep(decodeInfo.getFrameDelay(i));
                    }
                }
            }
            
            return ret;
        }
        
        private void informActivityPlayMusic()
        {
            if (mContext instanceof PosterMainActivity)
            {
                ((PosterMainActivity) mContext).playBackgroundMusic();
            }
            else if (mContext instanceof UrgentPlayerActivity)
            {
                ((UrgentPlayerActivity) mContext).playBackgroundMusic();
            }
        }
        
        private void informActivityStopMusic()
        {
            if (mContext instanceof PosterMainActivity)
            {
                ((PosterMainActivity) mContext).stopBackgroundMusic();
            }
            else if (mContext instanceof UrgentPlayerActivity)
            {
                ((UrgentPlayerActivity) mContext).stopBackgroundMusic();
            }
        }
        
        private void sendHideAllControlMsg() throws InterruptedException
        {
            mIsMsgHandleDone = false;
            myHandler.sendEmptyMessage(EVENT_HIDE_ALL_CONTROLER_BAR);
            while (!mIsMsgHandleDone)
            {
                Thread.sleep(100);
            }
        }
    }
    
    @SuppressLint("HandlerLeak")
    final Handler myHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case EVENT_PROGRESS_CHANGED:
                if (mControlWindow.isShowing())
                {
                    // set Seek bar value
                    mControlView.setSeekBarProgress(msg.getData().getInt("CurrentPostion"));
                    // set Seek bar secondary value
                    if (mIsOnline)
                    {
                        int j = mVideoView.getBufferPercentage() * mControlView.getSeekBarMax() / 100;
                        mControlView.setSeekBarSecondaryProgress(j);
                    }
                    else
                    {
                        mControlView.setSeekBarSecondaryProgress(0);
                    }
                                
                    // Update the Played time on Control bar
                    mControlView.setPlayedText(msg.getData().getString("PlayedTime"));
                    mControlView.setDurationText(msg.getData().getString("DurationTime"));
                }
                return;
                                        
            case EVENT_HIDE_CONTROLER:
                hideController();
                return;
                                        
            case EVENT_HANDLE_VIDEO_ABEND:
                if (mPlayList != null && mPlayList.size() > 0)
                {
                    logger.e("Video abend, scheduling restart of crashed player.");
                    if (mVideoView != null)
                    {
                        resetSurfaceView();
                    }                       
                    loadNextMedia();
                  }
                  return;
                                        
             case EVENT_PLAY_VIDEO_MEDIA:
                 playVideoMedia((MediaInfoRef) msg.obj);
                 mIsMsgHandleDone = true;
                 return;
                                        
             case EVENT_HIDE_ALL_CONTROLER_BAR:
                 if (mControlWindow != null && mControlWindow.isShowing())
                 {
                     mControlWindow.dismiss();
                 }
                 if (mSoundWindow != null && mSoundWindow.isShowing())
                 {
                     mSoundWindow.dismiss();
                 }
                 resetSurfaceView();
                 mIsMsgHandleDone = true;
                 return;
                 
             case EVENT_SHOW_PROGRESS_BAR:
                 if (!mIsShowProgressBar)
                 {
                     mVideoView.setVisibility(View.GONE);
                     mProgressBarView.setVisibility(View.VISIBLE);
                     mIsShowProgressBar = true;
                 }
                 return;
             
             case EVENT_HIDE_PROGRESS_BAR:
                 if (mIsShowProgressBar)
                 {
                     mProgressBarView.setVisibility(View.GONE);
                     mVideoView.setVisibility(View.VISIBLE);
                     mIsShowProgressBar = false;
                 }
                 return;
                 
             default:
                 break;
             }
                                    
             super.handleMessage(msg);
        }
    };
    
    public boolean needCombineCap()
    {
        return (mVideoView != null && mVideoView.isPlaying());
    }
    
    public Bitmap getVideoCap()
    {
        Bitmap bitmap = mVideoView.getVideoCap();
        if (bitmap != null)
        {
            int swidth = bitmap.getWidth();
            int sheight = bitmap.getHeight();
            float scaleWidht = (float) mWindowWidth / swidth;
            float scaleHeight = (float) mWindowHeight / sheight;
            Matrix matrix = new Matrix();
            matrix.setScale(scaleWidht, scaleHeight);
            return Bitmap.createBitmap(bitmap, 0, 0, swidth, sheight, matrix, true);
        }
        return null;
    }
}

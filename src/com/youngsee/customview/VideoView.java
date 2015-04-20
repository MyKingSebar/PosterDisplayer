/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.MediaController.MediaPlayerControl;

import java.io.IOException;
import java.util.ArrayList;

import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.common.Logger;
import com.youngsee.common.MediaInfoRef;

/**
 * Displays a video file. The VideoView class can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that it can be used in any layout manager, and
 * provides various display options such as scaling and tinting.
 */
public class VideoView extends SurfaceView implements MediaPlayerControl
{
    private final Logger                   logger                   = new Logger();
    
    private Context                        mContext                 = null;

    // settable by the client
    private Uri                            mUri                     = null;
    private int                            mDuration                = -1;
    
    // all possible internal states
    private static final int               STATE_ERROR              = -1;
    private static final int               STATE_IDLE               = 0;
    private static final int               STATE_PREPARING          = 1;
    private static final int               STATE_PREPARED           = 2;
    private static final int               STATE_PLAYING            = 3;
    private static final int               STATE_PAUSED             = 4;
    
    // mCurrentState is a VideoView object's current state.
    private int                            mCurrentState            = STATE_IDLE;
    
    // All the stuff we need for playing and showing a video
    private MediaPlayer                    mMediaPlayer             = null;
    MediaMetadataRetriever                 mMediaRetriever          = null;
    private SurfaceHolder                  mSurfaceHolder           = null;
    private int                            mVideoWidth              = 0;
    private int                            mVideoHeight             = 0;
    private int                            mSurfaceWidth            = 0;
    private int                            mSurfaceHeight           = 0;
    private int                            mCurrentBufferPercentage = 0;
    private int                            mSeekWhenPrepared        = 0;             // recording the seek position
                                                                                      // while preparing
                                                                                      
    private OnCompletionListener           mOnCompletionListener    = null;
    private MediaPlayer.OnPreparedListener mOnPreparedListener      = null;
    private OnErrorListener                mOnErrorListener         = null;
    private OnViewSizeChangeListener       mOnSizeChangeListener    = null;
    
    public void setVideoScale(int width, int height)
    {
        LayoutParams lp = getLayoutParams();
        lp.height = height;
        lp.width = width;
        setLayoutParams(lp);
    }
    
    public interface OnViewSizeChangeListener
    {
        public void OnViewSizeChange();
    }
    
    public void setViewSizeChangeListener(OnViewSizeChangeListener l)
    {
        mOnSizeChangeListener = l;
    }
    
    /**
     * Register a callback to be invoked when the media file is loaded and ready to go.
     * 
     * @param l
     *            The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l)
    {
        mOnPreparedListener = l;
    }
    
    /**
     * Register a callback to be invoked when the end of a media file has been reached during playback.
     * 
     * @param l
     *            The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l)
    {
        mOnCompletionListener = l;
    }
    
    /**
     * Register a callback to be invoked when an error occurs during playback or setup. If no listener is specified, or
     * if the listener returned false, VideoView will inform the user of any errors.
     * 
     * @param l
     *            The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l)
    {
        mOnErrorListener = l;
    }
    
    public VideoView(Context context)
    {
        super(context);
        mContext = context;
        initVideoView();
    }
    
    public VideoView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
        mContext = context;
        initVideoView();
    }
    
    public VideoView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        mContext = context;
        initVideoView();
    }
    
    @SuppressWarnings("deprecation")
    private void initVideoView()
    {
        logger.d("Video View initialize......");
        
        mVideoWidth = 0;
        mVideoHeight = 0;
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // Log.i("@@@@", "onMeasure");
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
    
    public int resolveAdjustedSize(int desiredSize, int measureSpec)
    {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        
        switch (specMode)
        {
        case MeasureSpec.UNSPECIFIED:
            /*
             * Parent says we can be as big as we want. Just don't be larger than max size imposed on ourselves.
             */
            result = desiredSize;
            break;
        
        case MeasureSpec.AT_MOST:
            /*
             * Parent says we can be as big as we want, up to specSize. Don't be larger than specSize, and don't be
             * larger than the max size imposed on ourselves.
             */
            result = Math.min(desiredSize, specSize);
            break;
        
        case MeasureSpec.EXACTLY:
            // No choice. Do what we are told.
            result = specSize;
            break;
        }
        return result;
    }
    
    public void showText(ArrayList<String> textList, Paint paint, int nDuration) throws Exception
    {
        if (mSurfaceHolder == null)
        {
            return;
        }
        else if (textList == null || textList.size() <= 0)
        {
            logger.e("drawText(): dest text is null.");
            return;
        }
        
        // 计算页数
        FontMetrics fm = paint.getFontMetrics();
        float lineHeight = (float)Math.ceil(fm.descent - fm.ascent); // 每行高度
        int linesPerPage = (int) (mSurfaceHeight / (lineHeight + fm.leading)); // 每一页的行数
        int lineCount = textList.size(); // 总行
        int pages = 1; // 总页�?
        if ((lineCount % linesPerPage) == 0)
        {
            pages = lineCount / linesPerPage;
        }
        else
        {
            pages = lineCount / linesPerPage + 1;
        }
        
        // 画文�?
        Canvas canvas = null;
        float x = 5;
        float y = lineHeight;
        int nIdx = 0;
        for (int i = 0; i < pages; i++)
        {
            x = 5;
            y = lineHeight;
            nIdx = i * linesPerPage;
            
            if (nIdx >= textList.size() || textList.get(nIdx) == null || "".equals(textList.get(nIdx)))
            {
                continue;  // 空白页则跳过
            }
            
            canvas = mSurfaceHolder.lockCanvas();
            if (canvas != null)
            {
                canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                for (int j = 0; j < linesPerPage; j++)
                {
                    nIdx = i * linesPerPage + j;
                    if (nIdx >= textList.size())
                    {
                        break; // 最后一页不满一屏的情况
                    }
                    else if (textList.get(nIdx) != null)
                    {
                        canvas.drawText(textList.get(nIdx), x, y, paint);
                        y = y + lineHeight + fm.leading; // (字高+行间�?
                    }
                }
                mSurfaceHolder.unlockCanvasAndPost(canvas);
            }
            else
            {
                logger.e("Canvas lock failed.");
                break;
            }
            
            // 等待显示下一�?
            Thread.sleep(nDuration);
        }
    }
    
    public void showPicture(Bitmap bmp) throws Exception
    {
        drawPicture(bmp);
    }
    
    public void showVideo(MediaInfoRef mir)
    {
        if (FileUtils.isExist(mir.filePath))
        {
            FileUtils.updateFileLastTime(mir.filePath);
            setVideoURI(Uri.parse(mir.filePath), mir.vType);  
        }
    }
    
    private void setVideoURI(Uri uri, String vtype)
    {
        logger.d("setVideoURI() uri = " + uri);
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo(vtype);
        requestLayout();
        invalidate();
    }
    
    private void drawPicture(Bitmap bmp) throws Exception
    {
        if (mSurfaceHolder == null)
        {
            return;
        }
        else if (bmp == null)
        {
            logger.e("drawPicture(): dest picture is null.");
            return;
        }
        
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        Rect srcRect = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
        Rect dstRect = new Rect(0, 0, mSurfaceWidth, mSurfaceHeight);
        
        Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas != null)
        {
            canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
            canvas.drawBitmap(bmp, srcRect, dstRect, paint);
            mSurfaceHolder.unlockCanvasAndPost(canvas);
        }
        else
        {
            throw new Exception("Canvas lock failed.");
        }
    }
    
    private void openVideo(String vType)
    {
        if (mUri == null || mSurfaceHolder == null)
        {
            // not ready for playback just yet, will try again later
            return;
        }
        
        // Tell the music playback service to pause
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);
        
        // Make Sure Just one Media Player in Client
        if (mMediaPlayer != null)
        {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
        }
        
        // Create new media player
        try
        {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mDuration = -1;
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
            mCurrentState = STATE_PREPARING;
            
            // 截屏用(流媒体不支持截屏)
            if (vType == null || !vType.endsWith("BroadcastVideo"))
            {
                mMediaRetriever = new MediaMetadataRetriever();
                mMediaRetriever.setDataSource(mContext, mUri);
            }
        }
        catch (IOException ex)
        {
            logger.w("Unable to open content: " + mUri);
            ex.printStackTrace();
            
            mCurrentState = STATE_ERROR;
            
            /* If an error handler has been supplied, use it and finish. */
            if (mOnErrorListener != null)
            {
                mOnErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
            return;
        }
        catch (IllegalArgumentException ex)
        {
            logger.w("Unable to open content: " + mUri);
            ex.printStackTrace();
            
            mCurrentState = STATE_ERROR;
            
            /* If an error handler has been supplied, use it and finish. */
            if (mOnErrorListener != null)
            {
                mOnErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
            return;
        }
    }
    
    private MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener     = new MediaPlayer.OnVideoSizeChangedListener() {
                                                                                public void onVideoSizeChanged(
                                                                                        MediaPlayer mp, int width,
                                                                                        int height)
                                                                                {
                                                                                    mVideoWidth = mp.getVideoWidth();
                                                                                    mVideoHeight = mp.getVideoHeight();
                                                                                    
                                                                                    // Tell client video view size
                                                                                    // changed.
                                                                                    if (mOnSizeChangeListener != null)
                                                                                    {
                                                                                        mOnSizeChangeListener
                                                                                                .OnViewSizeChange();
                                                                                    }
                                                                                }
                                                                            };
    
    private MediaPlayer.OnPreparedListener         mPreparedListener        = new MediaPlayer.OnPreparedListener() {
                                                                                public void onPrepared(MediaPlayer mp)
                                                                                {
                                                                                    mVideoWidth = mp.getVideoWidth();
                                                                                    mVideoHeight = mp.getVideoHeight();
                                                                                    
                                                                                    mCurrentState = STATE_PREPARED;
                                                                                    
                                                                                    if (mSeekWhenPrepared != 0)
                                                                                    {
                                                                                        seekTo(mSeekWhenPrepared);
                                                                                    }
                                                                                    
                                                                                    // Tell client media player is
                                                                                    // prepared.
                                                                                    if (mOnPreparedListener != null)
                                                                                    {
                                                                                        mOnPreparedListener
                                                                                                .onPrepared(mMediaPlayer);
                                                                                    }
                                                                                }
                                                                            };
    
    private MediaPlayer.OnCompletionListener       mCompletionListener      = new MediaPlayer.OnCompletionListener() {
                                                                                public void onCompletion(MediaPlayer mp)
                                                                                {
                                                                                    mCurrentState = STATE_IDLE;
                                                                                    
                                                                                    // Tell Client media player is
                                                                                    // completed.
                                                                                    if (mOnCompletionListener != null)
                                                                                    {
                                                                                        mOnCompletionListener
                                                                                                .onCompletion(mMediaPlayer);
                                                                                    }
                                                                                }
                                                                            };
    
    private MediaPlayer.OnErrorListener            mErrorListener           = new MediaPlayer.OnErrorListener() {
                                                                                public boolean onError(MediaPlayer mp,
                                                                                        int framework_err, int impl_err)
                                                                                {
                                                                                    logger.d("Error: " + framework_err
                                                                                            + "," + impl_err);
                                                                                    mCurrentState = STATE_ERROR;
                                                                                    LogUtils.getInstance().toAddSLog(Contants.ERROR,
                                                                                            Contants.PlayerError, "");
                                                                                    /*
                                                                                     * If an error handler has been
                                                                                     * supplied, use it and finish.
                                                                                     */
                                                                                    if (mOnErrorListener != null)
                                                                                    {
                                                                                        if (mOnErrorListener
                                                                                                .onError(mMediaPlayer,
                                                                                                        framework_err,
                                                                                                        impl_err))
                                                                                        {
                                                                                            return true;
                                                                                        }
                                                                                    }
                                                                                    
                                                                                    /*
                                                                                     * Otherwise, pop up an error dialog
                                                                                     * so the user knows that something
                                                                                     * bad has happened. Only try and
                                                                                     * pop up the dialog if we're
                                                                                     * attached to a window. When we're
                                                                                     * going away and no longer have a
                                                                                     * window, don't bother showing the
                                                                                     * user an error.
                                                                                     */
                                                                                    /*
                                                                                     * if (getWindowToken() != null) {
                                                                                     * Resources r =
                                                                                     * mContext.getResources(); int
                                                                                     * messageId;
                                                                                     * 
                                                                                     * if (framework_err == MediaPlayer.
                                                                                     * MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK
                                                                                     * ) { messageId =
                                                                                     * com.android.internal.R.string.
                                                                                     * VideoView_error_text_invalid_progressive_playback
                                                                                     * ; } else { messageId =
                                                                                     * com.android.internal.R.string.
                                                                                     * VideoView_error_text_unknown; }
                                                                                     * 
                                                                                     * new
                                                                                     * AlertDialog.Builder(mContext).
                                                                                     * setMessage(messageId)
                                                                                     * .setPositiveButton
                                                                                     * (com.android.internal
                                                                                     * .R.string.VideoView_error_button,
                                                                                     * new
                                                                                     * DialogInterface.OnClickListener()
                                                                                     * { public void
                                                                                     * onClick(DialogInterface dialog,
                                                                                     * int whichButton) { //If we get
                                                                                     * here, there is no onError
                                                                                     * listener, so at least inform them
                                                                                     * that the video is over. if
                                                                                     * (mOnCompletionListener != null) {
                                                                                     * mOnCompletionListener
                                                                                     * .onCompletion(mMediaPlayer); } }
                                                                                     * }).setCancelable(false).show(); }
                                                                                     */
                                                                                    return true;
                                                                                }
                                                                            };
    
    private MediaPlayer.OnBufferingUpdateListener  mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
                                                                                public void onBufferingUpdate(
                                                                                        MediaPlayer mp, int percent)
                                                                                {
                                                                                    mCurrentBufferPercentage = percent;
                                                                                }
                                                                            };
    
    private SurfaceHolder.Callback                 mSHCallback              = new SurfaceHolder.Callback() {
                                                                                public void surfaceChanged(
                                                                                        SurfaceHolder holder,
                                                                                        int format, int w, int h)
                                                                                {
                                                                                    mSurfaceWidth = w;
                                                                                    mSurfaceHeight = h;
                                                                                    
                                                                                    logger.i("surfaceChanged, mSurfaceWidth = "
                                                                                            + mSurfaceWidth
                                                                                            + " mSurfaceHeight = "
                                                                                            + mSurfaceHeight);
                                                                                    
                                                                                    if (mMediaPlayer != null
                                                                                            && mVideoWidth == mSurfaceWidth
                                                                                            && mVideoHeight == mSurfaceHeight
                                                                                            && (mCurrentState == STATE_PREPARED || mCurrentState == STATE_PAUSED))
                                                                                    {
                                                                                        if (mSeekWhenPrepared != 0)
                                                                                        {
                                                                                            seekTo(mSeekWhenPrepared);
                                                                                        }
                                                                                        
                                                                                        start();
                                                                                    }
                                                                                }
                                                                                
                                                                                public void surfaceCreated(
                                                                                        SurfaceHolder holder)
                                                                                {
                                                                                    mSurfaceHolder = holder;
                                                                                    openVideo(null);
                                                                                    logger.i("call surfaceCreated, holder = "
                                                                                            + holder);
                                                                                }
                                                                                
                                                                                public void surfaceDestroyed(
                                                                                        SurfaceHolder holder)
                                                                                {
                                                                                    // after we return from this we
                                                                                    // can't use the surface any more
                                                                                    mSurfaceHolder = null;
                                                                                    mCurrentState = STATE_IDLE;
                                                                                    if (mMediaPlayer != null)
                                                                                    {
                                                                                        mMediaPlayer.reset();
                                                                                        mMediaPlayer.release();
                                                                                        mMediaPlayer = null;
                                                                                    }
                                                                                    logger.i("call surfaceDestroyed!");
                                                                                }
                                                                            };
    
    public void start()
    {
        if (isInPlaybackState() && mCurrentState != STATE_PLAYING)
        {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            logger.i("media play is start.");
        }
    }
    
    public void pause()
    {
        if (isPlaying())
        {
            mMediaPlayer.pause();
            mCurrentState = STATE_PAUSED;
        }
    }
    
    public void stopPlayback()
    {
        if (mMediaPlayer != null)
        {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
        }
    }
    
    public void releasVideoResource()
    {
        mUri = null;
        mSeekWhenPrepared = 0;
        mCurrentState = STATE_IDLE;
        if (mMediaPlayer != null)
        {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
    
    public int getDuration()
    {
        if (isInPlaybackState())
        {
            mDuration = (mDuration > 0) ? mDuration : mMediaPlayer.getDuration();
        }
        else
        {
            mDuration = -1;
        }
        return mDuration;
    }
    
    public int getCurrentPosition()
    {
        if (isInPlaybackState())
        {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }
    
    public void seekTo(int msec)
    {
        if (isInPlaybackState())
        {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        }
        else
        {
            mSeekWhenPrepared = msec;
        }
    }
    
    public boolean isPlaying()
    {
        return (isInPlaybackState() && mMediaPlayer.isPlaying());
    }
    
    private boolean isInPlaybackState()
    {
        return (mMediaPlayer != null && mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
    }
    
    public int getBufferPercentage()
    {
        if (mMediaPlayer != null)
        {
            return mCurrentBufferPercentage;
        }
        return 0;
    }
    
    public boolean isSurfaceDestroyed()
    {
        return (mSurfaceHolder == null);
    }
    
    public boolean canPause()
    {
        return true;
    }
    
    public boolean canSeekBackward()
    {
        return true;
    }
    
    public boolean canSeekForward()
    {
        return true;
    }
    
    public Bitmap getVideoCap()
    {
        return mMediaRetriever.getFrameAtTime(mMediaPlayer.getCurrentPosition(),
                MediaMetadataRetriever.OPTION_NEXT_SYNC);
    }
}

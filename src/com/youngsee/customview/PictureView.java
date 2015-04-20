/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.FontMetrics;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ViewSwitcher.ViewFactory;

import com.youngsee.gifdecode.GifDecodeInfo;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.R;
import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.common.MediaInfoRef;

public class PictureView extends PosterBaseView
{
    private View                    mPictureView              = null;
    
    private BitmapDrawable          mLastBitmapDrawable       = null;
    private ImageSwitcher           mImgSwitcher              = null;
    private FrameLayout             mPictureLayout            = null;
    private View                    mProgressBarView          = null;
    
    private int                     mCurrentIndex             = -1;
    private ArrayList<MediaInfoRef> mDisplayImageList         = null;
    private boolean                 mIsShowProgressBar        = false;
    
    // Picture change thread
    private LoadPicThread           mRefreshThreadHandler     = null;
    
    // Define message ID
    private final static int        EVENT_SHOW_PICTURE        = 0x3000;
    private final static int        EVENT_SHOW_TEXT           = 0x3001;
    private final static int        EVENT_SHOW_PROGRESS_BAR   = 0x3002;
    private final static int        EVENT_HIDE_PROGRESS_BAR   = 0x3003;
    
    // Define special effects for picture shown
    private static final int        NONE                      = 0;
    private static final int        MOVE_LEFTTORIGHT          = 1;
    private static final int        MOVE_RIGHTTOLEFT          = 2;
    private static final int        MOVE_TOPTOBOTTOM          = 3;
    private static final int        MOVE_BOTTOMTOTOP          = 4;
    private static final int        MOVE_LEFTTOPTORIGHTBOTTOM = 5;
    private static final int        MOVE_RIGHTTOPTOLEFTBOTTON = 6;
    private static final int        INSIDETOSIDE              = 7;
    private static final int        SIDETOINSIDE              = 8;
    private static final int        LAND_LOUVER               = 9;
    private static final int        VERT_LOUBER               = 10;
    private static final int        LAND_PUSH                 = 11;
    private static final int        VERT_PUSH                 = 12;
    private static final int        RANDOM                    = 255;
    
    private static final int        MOVE_TOPTOBOTTOM_TXT      = 1;
    private static final int        MOVE_RIGHTTOLEFT_TXT      = 2;
    private static final int        MOVE_BOTTOMTOTOP_TXT      = 3;
    
    public PictureView(Context context)
    {
        super(context);
        initPictureView(context);
    }
    
    public PictureView(Context context, String viewName)
    {
        super(context, viewName);
        initPictureView(context);
    }
    
    public PictureView(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initPictureView(context);
    }
    
    @SuppressLint("NewApi")
	private void initPictureView(Context context)
    {
        logger.d("Picture View initialize......");
        
        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mPictureView = inflater.inflate(R.layout.picture, null);
        mProgressBarView = inflater.inflate(R.layout.loading_dialog, null);
        
        // Get widgets from XML file
        if (mPictureView != null)
        {
            mPictureLayout = (FrameLayout) mPictureView.findViewById(R.id.picturelayout);
            mPictureLayout.addView(mProgressBarView);
            mProgressBarView.setVisibility(View.GONE);
            
            mImgSwitcher = (ImageSwitcher) mPictureView.findViewById(R.id.imageswitcher);
            ViewFactory factory = new ViewFactory()
            {
                @Override
                public View makeView()
                {
                    ImageView iv = new ImageView(mContext);
                    iv.setScaleType(ScaleType.FIT_XY);
                    iv.setLayoutParams(new ImageSwitcher.LayoutParams(ImageSwitcher.LayoutParams.MATCH_PARENT, ImageSwitcher.LayoutParams.MATCH_PARENT));
                    return iv;
                }
            };
            mImgSwitcher.setFactory(factory);
        }
    }
    
    @Override
    public View getCoverView()
    {
        return mPictureView;
    }
    
    @Override
    public void viewDestroy()
    {
        cancelRefreshThread();
        mHandler.removeMessages(EVENT_SHOW_PICTURE);
        mHandler.removeMessages(EVENT_SHOW_TEXT);
        mHandler.removeMessages(EVENT_SHOW_PROGRESS_BAR);
        mHandler.removeMessages(EVENT_HIDE_PROGRESS_BAR);
    }
    
    @Override
    public void viewPause()
    {
        cancelRefreshThread();
        mHandler.removeMessages(EVENT_SHOW_PICTURE);
        mHandler.removeMessages(EVENT_SHOW_TEXT);
        mHandler.removeMessages(EVENT_SHOW_PROGRESS_BAR);
        mHandler.removeMessages(EVENT_HIDE_PROGRESS_BAR);
        
        try
        {
            if (mDisplayImageList != null && mViewName != null && mCurrentIndex >= 0 && mCurrentIndex < mDisplayImageList.size())
            {
                LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mDisplayImageList.get(mCurrentIndex).mid, mViewName, Contants.DOWNMENU);
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
        startRefreshThread();
    }
    
    @Override
    public void showMediaList(ArrayList<MediaInfoRef> list)
    {
        if (list.isEmpty()) return;
        
        // Cancel the old refresh thread
        cancelRefreshThread();
        
        // Create a new thread to refresh picture, and create a new image List
        mCurrentIndex = -1;
        mLastBitmapDrawable = null;
        mDisplayImageList = new ArrayList<MediaInfoRef>(list);
        startRefreshThread();
    }
    
    private void startRefreshThread()
    {
        if (mDisplayImageList != null && mDisplayImageList.size() > 0)
        {
            cancelRefreshThread();
            mRefreshThreadHandler = new LoadPicThread(true);
            mRefreshThreadHandler.start();
        }
    }
    
    private void cancelRefreshThread()
    {
        if (mRefreshThreadHandler != null)
        {
            mRefreshThreadHandler.cancel();
            mRefreshThreadHandler = null;
        }
    }
    
    // Defined a thread to load picture
    private final class LoadPicThread extends Thread
    {
        private boolean    mIsRecycleRun    = true;
        private int        mRefreshInterval = 1000;
        
        public LoadPicThread(boolean bIsRecycleRun)
        {
            mIsRecycleRun = bIsRecycleRun;
        }
        
        public void cancel()
        {
            mIsRecycleRun = false;
            this.interrupt();
        }
        
        @Override
        public void run()
        {
            logger.i("New loadPicThread, id is: " + currentThread().getId());
            
            Bitmap destBmp = null;
            MediaInfoRef mediaInfo = null; 
            
            while (mIsRecycleRun)
            {
                try
                {
                    if (mDisplayImageList == null)
                    {
                        logger.i("mDisplayImageList is null, thread exit.");
                        return;
                    }
                    else if (mDisplayImageList.isEmpty())
                    {
                        logger.i("No Picture info in the list, thread exit.");
                        return;
                    }
                    
                    // Get the Media Info
                    if (++mCurrentIndex >= mDisplayImageList.size())
                    {
                        mCurrentIndex = 0;
                    }
                    mediaInfo = mDisplayImageList.get(mCurrentIndex);
                    if (mViewName.startsWith("Weather"))
                    {
                        mRefreshInterval = Math.max(mediaInfo.duration, 1000);
                    }
                    else
                    {
                        mRefreshInterval = Math.max(mediaInfo.durationPerPage, 1000);
                    }
                    
                    // Load and Show the Bitmap
                    if (FileUtils.mediaIsFile(mediaInfo) && !FileUtils.isExist(mediaInfo.filePath))
                    {
                        logger.i(mediaInfo.filePath + " didn't exist, skip it.");
                        if (!mIsShowProgressBar && !checkFilesIsValid(mDisplayImageList))
                        {
                            mHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                        }
                        checkIsNeedToDownload(mediaInfo);
                        Thread.sleep(100);
                        continue;
                    }
                    else if (FileUtils.mediaIsFile(mediaInfo) && !checkMediaMd5(mediaInfo))
                    {
                        logger.i(mediaInfo.filePath + " verifycode is wrong, skip it.");
                        if (!mIsShowProgressBar && !checkFilesIsValid(mDisplayImageList))
                        {
                            mHandler.sendEmptyMessage(EVENT_SHOW_PROGRESS_BAR);
                        }
                        checkIsNeedToDownload(mediaInfo);
                        Thread.sleep(100);
                        continue;
                    }
                    else if (!mediaTimeIsValid(mediaInfo))
                    {
                        logger.i(mediaInfo.filePath + " media time is invaild, skip it.");
                        Thread.sleep(100);
                        continue;
                    }
                    else if (FileUtils.mediaIsGifFile(mediaInfo))
                    {
                        if (displayGif(mediaInfo))
                        {
                            mediaInfo.playedtimes++;
                        }
                        continue;
                    }
                    else if (FileUtils.mediaIsPicFromFile(mediaInfo) || FileUtils.mediaIsPicFromNet(mediaInfo))
                    {
                        if (mIsShowProgressBar)
                        {
                            mHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                            while (mIsShowProgressBar)
                            {
                                Thread.sleep(100);
                            }
                        }
                        
                        if ((destBmp = getBitMap(mediaInfo)) != null)
                        {
                            // Send SHOW_PICTURE message to UI thread
                            sendShowPictureMsg(destBmp, mediaInfo.mode);
                            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
                            Thread.sleep(mRefreshInterval);
                            mediaInfo.playedtimes++;
                            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mediaInfo.mid, mViewName, Contants.NOMALSTOP);
                            
                            continue;
                        }
                    }
                    else if (FileUtils.mediaIsTextFromFile(mediaInfo) || FileUtils.mediaIsTextFromNet(mediaInfo))
                    {
                        if (mIsShowProgressBar)
                        {
                            mHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                            while (mIsShowProgressBar)
                            {
                                Thread.sleep(100);
                            }
                        }
                        
                        drawText(mediaInfo);
                        continue;
                    }
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    terminateGifDecode();
                    logger.i("loadPicThread sleep over, and safe exit, the Thread id is: " + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    logger.e("loadPicThread Catch a error");
                    LogUtils.getInstance().toAddSLog(Contants.ERROR, Contants.PlayerError, "<CODE>错误号</CODE>");
                    e.printStackTrace();
                }
            }
            
            terminateGifDecode();
            logger.i("loadPicThread is safe Terminate, id is: " + currentThread().getId());
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
                mHandler.sendEmptyMessage(EVENT_HIDE_PROGRESS_BAR);
                while (mIsShowProgressBar)
                {
                    Thread.sleep(100);  // wait for progress bar dismiss
                }
            }

            // Show GIF
            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, picInfo.mid, mViewName, "");
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
                        ret = true;
                        sendShowPictureMsg(srcBmp, 0); 
                        Thread.sleep(decodeInfo.getFrameDelay(i));
                    }
                }
            }
            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, picInfo.mid, mViewName, Contants.NOMALSTOP);
            return ret;
        }
        
        private void drawText(final MediaInfoRef mediaInfo) throws Exception
        {
            String strText = getText(mediaInfo);
            if (strText == null || strText == "")
            {
                logger.e("drawText(): dest text is null.");
                return;
            }
            
            int fSize = getFontSize(mediaInfo);
            int fColor = getFontColor(mediaInfo);
            Typeface font = getFont(mediaInfo);
            int durationPerPage = mediaInfo.durationPerPage;
            int nWidth = mediaInfo.containerwidth;
            int nHeight = mediaInfo.containerheight;
            
            // 创建画笔
            Paint paint = new Paint();
            paint.setTextSize(fSize); // 字号
            paint.setColor(fColor); // 颜色
            paint.setAlpha(0xff); // 字体不透明
            paint.setTypeface(font); // 字体
            paint.setAntiAlias(true); // 去除锯齿
            paint.setFilterBitmap(true); // 对位图进行滤波处理
            
            // 计算页数
            FontMetrics fm = paint.getFontMetrics();
            float lineHeight = (float)Math.ceil(fm.descent - fm.ascent); // 每行高度
            int linesPerPage = (int) (nHeight / (lineHeight + fm.leading)); // 每一页的行数
            ArrayList<String> textList = autoSplit(strText, paint, nWidth); // 自动分行
            int lineCount = textList.size(); // 总行数
            int pages = 1; // 总页数
            if ((lineCount % linesPerPage) == 0)
            {
                pages = lineCount / linesPerPage;
            }
            else
            {
                pages = lineCount / linesPerPage + 1;
            }
            
            // 创建canvas
            Bitmap bmp = Bitmap.createBitmap(nWidth, nHeight, Bitmap.Config.ARGB_4444);
            Canvas canvas = new Canvas(bmp);
            
            // 画文本
            float x = 5; 
            float y = lineHeight;
            int nIdx = 0;
            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaStart, mediaInfo.mid, mViewName, "");
            for (int i = 0; i < pages; i++)
            {
                x = 5;
                y = lineHeight;
                nIdx = i * linesPerPage; // 页的起始行
                
                if (nIdx >= textList.size() || textList.get(nIdx) == null)
                {
                    continue; // 空白页则跳过
                }
                
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
                        y = y + lineHeight + fm.leading; // (字高+行间距)
                    }
                }
                
                // 显示文字
                sendShowTextMsg(bmp, mediaInfo.mode);
                
                // 等待显示下一页
                Thread.sleep(durationPerPage);
            }
            LogUtils.getInstance().toAddPLog(Contants.INFO, Contants.PlayMediaEnd, mediaInfo.mid, mViewName, Contants.NOMALSTOP);
        }
    }
    
    private void setPicImgAnimation(ImageSwitcher iSwitcher, int animId)
    {
        TranslateAnimation move_in = null;
        ScaleAnimation scale_in = null;
        ScaleAnimation scale_out = null;
        switch (animId)
        {
        case NONE:
            iSwitcher.setInAnimation(null);
            iSwitcher.setOutAnimation(null);
            break;
        case MOVE_LEFTTORIGHT:
            move_in = new TranslateAnimation(2, -1.0f, 2, 0, 2, 0, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_RIGHTTOLEFT:
            move_in = new TranslateAnimation(2, 1.0f, 2, 0, 2, 0, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_TOPTOBOTTOM:
            move_in = new TranslateAnimation(2, 0, 2, 0, 2, -1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_BOTTOMTOTOP:
            move_in = new TranslateAnimation(2, 0, 2, 0, 2, 1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_LEFTTOPTORIGHTBOTTOM:
            move_in = new TranslateAnimation(2, -1.0f, 2, 0, 2, -1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_RIGHTTOPTOLEFTBOTTON:
            move_in = new TranslateAnimation(2, 1.0f, 2, 0, 2, -1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case INSIDETOSIDE:
            scale_in = new ScaleAnimation(0f, 1.0f, 0f, 1.0f, ScaleAnimation.RELATIVE_TO_PARENT, 0.5f, ScaleAnimation.RELATIVE_TO_PARENT, 0.5f);
            scale_in.setDuration(1000);
            iSwitcher.setInAnimation(scale_in);
            break;
        case SIDETOINSIDE:
            scale_out = new ScaleAnimation(4.0f, 1.0f, 4.0f, 1.0f, ScaleAnimation.RELATIVE_TO_PARENT, 0.5f, ScaleAnimation.RELATIVE_TO_PARENT, 0.5f);
            scale_out.setDuration(1000);
            iSwitcher.setInAnimation(scale_out);
            break;
        
        case LAND_LOUVER:
            
            break;
        case VERT_LOUBER:
            
            break;
        case LAND_PUSH:
            move_in = new TranslateAnimation(2, -1.0f, 2, 0, 2, 0, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case VERT_PUSH:
            move_in = new TranslateAnimation(2, 0, 2, 0, 2, -1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case RANDOM:
            setPicImgAnimation(this.mImgSwitcher, (new Random()).nextInt(VERT_PUSH));
            break;
        }
    }
    
    private void setTxtImgAnimation(ImageSwitcher iSwitcher, int animId)
    {
        TranslateAnimation move_in = null;
        switch (animId)
        {
        case NONE:
            iSwitcher.setInAnimation(null);
            iSwitcher.setOutAnimation(null);
            break;
        case MOVE_TOPTOBOTTOM_TXT:
            move_in = new TranslateAnimation(2, 0, 2, 0, 2, -1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_RIGHTTOLEFT_TXT:
            move_in = new TranslateAnimation(2, 1.0f, 2, 0, 2, 0, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        case MOVE_BOTTOMTOTOP_TXT:
            move_in = new TranslateAnimation(2, 0, 2, 0, 2, 1.0f, 2, 0);
            move_in.setDuration(1000);
            iSwitcher.setInAnimation(move_in);
            break;
        }
    }
    
    private void sendShowPictureMsg(Bitmap bmp, int animId)
    {
        Bundle bundle = new Bundle();
        bundle.putInt("Animation", animId);
        
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_SHOW_PICTURE;
        msg.obj = bmp;
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    private void sendShowTextMsg(Bitmap bmp, int animId)
    {
        Bundle bundle = new Bundle();
        bundle.putInt("Animation", animId);
        
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_SHOW_TEXT;
        msg.obj = bmp;
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler()
    {
        @SuppressLint("NewApi")
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case EVENT_SHOW_PICTURE:
                Bitmap currentPicBmp = (Bitmap) msg.obj;
                int nPicAnimaId = msg.getData().getInt("Animation");
                BitmapDrawable bdPic = new BitmapDrawable(mContext.getResources(), currentPicBmp);
                setPicImgAnimation(mImgSwitcher, nPicAnimaId);
                mImgSwitcher.setBackground(mLastBitmapDrawable);
                mImgSwitcher.setImageDrawable(bdPic);
                mLastBitmapDrawable = bdPic;
                return;

            case EVENT_SHOW_TEXT:
                Bitmap currentTxtBmp = (Bitmap) msg.obj;
                int nTxtAnimaId = msg.getData().getInt("Animation");
                BitmapDrawable bdTxt = new BitmapDrawable(mContext.getResources(), currentTxtBmp);
                setTxtImgAnimation(mImgSwitcher, nTxtAnimaId);
                mImgSwitcher.setBackground(null);
                mImgSwitcher.setImageDrawable(bdTxt);
                mLastBitmapDrawable = bdTxt;
                return;

            case EVENT_SHOW_PROGRESS_BAR:
                if (!mIsShowProgressBar)
                {
                    mImgSwitcher.setVisibility(View.GONE);
                    mProgressBarView.setVisibility(View.VISIBLE);
                    mIsShowProgressBar = true;
                }
                return;
            
            case EVENT_HIDE_PROGRESS_BAR:
                if (mIsShowProgressBar)
                {
                    mProgressBarView.setVisibility(View.GONE);
                    mImgSwitcher.setVisibility(View.VISIBLE);
                    mIsShowProgressBar = false;
                }
                return;
            }
                                   
            super.handleMessage(msg);
        }
    };
}

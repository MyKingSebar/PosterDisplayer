/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.screenmanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.youngsee.common.Contants;
import com.youngsee.common.FileUtils;
import com.youngsee.common.LogUtils;
import com.youngsee.common.Logger;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.common.SubWindowInfoRef;
import com.youngsee.common.TypefaceManager;
import com.youngsee.customview.AudioView;
import com.youngsee.customview.DateTimeView;
import com.youngsee.customview.GalleryView;
import com.youngsee.customview.PosterBaseView;
import com.youngsee.customview.TimerView;
import com.youngsee.customview.YSWebView;
import com.youngsee.customview.MarqueeView;
import com.youngsee.customview.PictureView;
import com.youngsee.customwindow.MainWindow;
import com.youngsee.customwindow.SubWindow;
import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class ProgramFragment extends Fragment
{
    private Logger                      logger              = new Logger();
    private LinearLayout                mMainLayout         = null;
    
    // 屏幕布局信息
    private Set<SubWindow>              mSubWndCollection   = null;
    private ArrayList<SubWindowInfoRef> mSubWndInfoList     = null;
    
    // 背景图片信息
    private int                         mBgScreenXPos       = 0;
    private int                         mBgScreenYPos       = 0;
    private MediaInfoRef                mBgImgInfo          = null;
    
    // 主屏幕信息
    private int                         mMainScreenWidth    = 0;
    private int                         mMainScreenHeight   = 0;
    private int                         mMainScreenXPos     = 0;
    private int                         mMainScreenYPos     = 0;
    private MainWindow                  mMainWindow         = null;
    
    // 待机界面
    private boolean                     mShowStandbyScreen  = false;
    private final Handler               mHandler            = new Handler();
    
    // Tag String
    public static final String          NORMAL_FRAGMENT_TAG = "NormalProgramTag";
    public static final String          URGENT_FRAGMENT_TAG = "UrgentProgramTag";
    
    private boolean                     mNeedResume         = false;
                                                                    
    /**
     * During creation, if arguments have been supplied to the fragment then parse those out.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Bundle args = getArguments();
        if (args != null)
        {
            mSubWndInfoList = (ArrayList<SubWindowInfoRef>) args.getSerializable("SubWindowCollection");
        }
    }
    
    /**
     * Create the view for this fragment, using the arguments given to it.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // 不能将Fragment的视图附加到此回调的容器元素，因此attachToRoot参数必须为false
        return inflater.inflate(R.layout.fragment_program, container, false);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        
        // Get the Main View
        mMainLayout = (LinearLayout) getActivity().findViewById(R.id.programroot);

        // 创建subwindow
        if (mSubWndInfoList != null)
        {
            logger.i("Window number is: " + mSubWndInfoList.size());
            
            // initialize
            int xPos = 0;
            int yPos = 0;
            int width = 0;
            int height = 0;
            String wndName = null;
            String wndType = null;
            SubWindow tempSubWnd = null;
            SubWindowInfoRef subWndInfo = null;
            mSubWndCollection = new HashSet<SubWindow>();
            
            // Through the sub window list, and create the correct view for it.
            for (int i = 0; i < mSubWndInfoList.size(); i++)
            {
                tempSubWnd = null;
                subWndInfo = mSubWndInfoList.get(i);
                
                // 窗体类型和名称
                if ((wndType = subWndInfo.getSubWindowType()) == null)
                {
                    continue;
                }
                wndName = subWndInfo.getSubWindowName();
                
                // 窗体位置
                xPos = subWndInfo.getXPos();
                yPos = subWndInfo.getYPos();
                width = subWndInfo.getWidth();
                height = subWndInfo.getHeight();
                
                // 创建窗口
                if (wndType.contains("StandbyScreen"))
                {
                    mShowStandbyScreen = true;
                    continue;
                }
                else if (wndType.contains("Background"))
                {
                    // 背景图片
                    List<MediaInfoRef> mediaList = subWndInfo.getSubWndMediaList();
                    if (mediaList != null && mediaList.size() > 0 && "File".equals(mediaList.get(0).source))
                    {
                        mBgScreenXPos = xPos;
                        mBgScreenYPos = yPos;
                        mBgImgInfo = mediaList.get(0);
                    }
                    continue;
                }
                else if (wndType.contains("Main"))
                {
                    mMainScreenXPos = xPos;
                    mMainScreenYPos = yPos;
                    mMainScreenWidth = width;
                    mMainScreenHeight = height;
                    mMainWindow = new MainWindow(getActivity(), wndName);
                    continue;
                }
                else if (wndType.contains("Audio"))
                {
                    width = 0;
                    height = 0;
                	tempSubWnd = new SubWindow(wndName, new AudioView(getActivity(), wndName));
                }
                else if (wndType.contains("Image"))
                {
                    tempSubWnd = new SubWindow(wndName, new PictureView(getActivity(), wndName));
                }
                else if (wndType.contains("Weather"))
                {
                    tempSubWnd = new SubWindow(wndName, new PictureView(getActivity(), wndName, false));
                }
                else if (wndType.contains("Scroll"))
                {
                    tempSubWnd = new SubWindow(wndName, new MarqueeView(getActivity(), wndName));
                }
                else if (wndType.contains("Clock"))
                {
                    tempSubWnd = new SubWindow(wndName, new DateTimeView(getActivity(), wndName));
                }
                else if (wndType.contains("Gallery"))
                {
                    tempSubWnd = new SubWindow(wndName, new GalleryView(getActivity(), wndName));
                }
                else if (wndType.contains("Web"))
                {
                    tempSubWnd = new SubWindow(wndName, new YSWebView(getActivity(), wndName));
                }
                else if (wndType.contains("Timer"))
                {
                    tempSubWnd = new SubWindow(wndName, new TimerView(getActivity(), wndName));
                }
                
                if (tempSubWnd != null)
                {
                    tempSubWnd.setPositionValue(xPos, yPos);
                    tempSubWnd.setSizeValue(width, height);
                    mSubWndCollection.add(tempSubWnd);
                }
            }
        }
        
        // 显示主窗口
        if (mMainLayout != null && mMainWindow != null && mMainWindow.getCoverView() != null)
        {
            mMainWindow.setWindowSize(mMainScreenWidth, mMainScreenHeight);
            mMainLayout.addView(mMainWindow.getCoverView());
            mMainWindow.setWindowPosition(mMainScreenXPos, mMainScreenYPos);
        }
        
        // 显示其他窗口
        if (mMainLayout != null && mSubWndCollection != null)
        {
            for (SubWindow subWnd : mSubWndCollection)
            {
				subWnd.displayOnLocation(mMainLayout, subWnd.getXPos(),
						subWnd.getYPos(), subWnd.getWidth(), subWnd.getHeight());
            }
        }
        
        // 触发窗口工作
        subWndStartWork();
    }
    
    @Override
    public void onResume()
    {
        if (mShowStandbyScreen)
        {
            setStandbyScreen();
        }
        else
        {
            if (mBgImgInfo != null)
            {
                setWindowBackgroud();
            }
            
            if (mNeedResume)
            {
	            if (mMainWindow != null)
	            {
	                mMainWindow.viewResume();
	            }
	            
	            if (mSubWndCollection != null)
	            {
	                for (SubWindow wnd : mSubWndCollection)
	                {
	                    wnd.onResumeWindow();
	                }
	            }
            }
            
            if (!"".equals(ScreenManager.getInstance().getPlayingPgmId()))
            {
                LogUtils.getInstance().toAddPLog(0, Contants.PlayProgramStart, ScreenManager.getInstance().getPlayingPgmId(), "", "");
            }
        }
        mNeedResume = true;
        super.onResume();
    }
    
    @Override
    public void onPause()
    {
        mHandler.removeCallbacks(rSetWndBgDelay);
        mHandler.removeCallbacks(rSetStandbyDelay);

        if (mMainWindow != null)
        {
            mMainWindow.viewPause();
        }
        
        if (mSubWndCollection != null)
        {
            for (SubWindow wnd : mSubWndCollection)
            {
                wnd.onPauseWindow();
            }
        }
        
        // 清空缓存
        PosterApplication.clearMemoryCache();
        System.gc();
        
        if (!mShowStandbyScreen && "".equals(ScreenManager.getInstance().getPlayingPgmId()))
        {
            LogUtils.getInstance().toAddPLog(0, Contants.PlayProgramEnd, ScreenManager.getInstance().getPlayingPgmId(), "", "");
        }
        
        super.onPause();
    }
    
    @Override
    public void onDestroy()
    {
        mHandler.removeCallbacks(rSetWndBgDelay);
        mHandler.removeCallbacks(rSetStandbyDelay);
        
        if (mMainWindow != null)
        {
            mMainWindow.viewDestroy();
        }
        
        if (mSubWndCollection != null)
        {
            for (SubWindow wnd : mSubWndCollection)
            {
                wnd.onCloseWindow();
            }
        }
        
        // 清空缓存
        PosterApplication.clearMemoryCache();
        PosterApplication.clearDiskCache();
        System.gc();
        
        super.onDestroy();
    }
    
    private void subWndStartWork()
    {
        if (mSubWndInfoList != null)
        {
            String wndType = null;
            String wndName = null;
            SubWindowInfoRef subWndInfo = null;
            List<MediaInfoRef> mediaList = null;
            MediaInfoRef mediaInfo = null;
            SubWindow tempSubWnd = null;
            
            // 遍历所有窗体信息
            for (int i = 0; i < mSubWndInfoList.size(); i++)
            {
                mediaInfo = null;
                tempSubWnd = null;
                subWndInfo = mSubWndInfoList.get(i);
                
                // 获取窗体类型、名称、素材列表
                wndType = subWndInfo.getSubWindowType();
                wndName = subWndInfo.getSubWindowName();
                mediaList = subWndInfo.getSubWndMediaList();
                
                // 素材列表不正确，则跳过
                if (mediaList == null || mediaList.size() <= 0)
                {
                    if (!wndType.contains("StandbyScreen"))
                    {
                        logger.i("No media info for this Window, window name is: " + wndName + " window type is: " + wndType);
                    }
                    
                    if ("Main".equals(wndType))
                    {
                        ScreenManager.getInstance().setPrgFinishedFlag(true);
                        logger.i("No media info for main window, Program play finished.");
                    }
                    continue;
                }
                
                // 获取与窗体信息对应的子窗口
                if (mSubWndCollection != null)
                {
                    for (SubWindow wnd : mSubWndCollection)
                    {
                        if (wnd.getWindowName().equals(wndName))
                        {
                            tempSubWnd = wnd;
                            break;
                        }
                    }
                }
                
                // 添加素材到对应的子窗口，并启动工作
                if ("Main".contains(wndType) && mMainWindow != null)
                {
                    // 主窗口
                    ArrayList<MediaInfoRef> playList = new ArrayList<MediaInfoRef>();
                    
                    // 遍历素材列表
                    for (int j = 0; j < mediaList.size(); j++)
                    {
                        mediaInfo = mediaList.get(j);
                        
                        // 过滤不合法的素材
                        if (!"Image".contains(mediaInfo.mediaType) && 
                            !"Video".contains(mediaInfo.mediaType) && 
                            !"Text".contains(mediaInfo.mediaType))
                        {
                            logger.w("media file is not image or video or Text, skip it.");
                            continue;
                        }
                        else if (mediaInfo.filePath == null)
                        {
                            logger.w("media has no file path, skip it.");
                            continue;
                        }
                        
                        // 添加新的素材信息到PlayList
                        playList.add(mediaInfo);
                    }
                    
                    // 启动播放
                    mMainWindow.showMediaList(playList);
                }
                else if (tempSubWnd != null)
                {
                    // 子窗口
                    ArrayList<MediaInfoRef> playList = new ArrayList<MediaInfoRef>();
                    
                    // 遍历素材列表
                    for (int j = 0; j < mediaList.size(); j++)
                    {
                        mediaInfo = mediaList.get(j);
                        
                        // 素材合法性检测
                        if (wndType.contains("Image") || wndType.contains("Weather"))
                        {
                            if (!"Image".equals(mediaInfo.mediaType) && !"Text".equals(mediaInfo.mediaType))
                            {
                                logger.d("This media cannot display in this view");
                                continue;
                            }
                            else if (mediaInfo.filePath == null)
                            {
                                logger.w("media has no file path, skip it.");
                                continue;
                            }
                        }
                        else if (wndType.contains("Scroll"))
                        {
                            if (!"Text".equals(mediaInfo.mediaType))
                            {
                                logger.d("This media cannot display in this view");
                                continue;
                            }
                            else if (mediaInfo.filePath == null)
                            {
                                logger.w("media has no file path, skip it.");
                                continue;
                            }
                        }
                        else if (wndType.contains("Clock"))
                        {
                            if (mediaInfo.format == null)
                            {
                                mediaInfo.format = "Y年m月d日\\n周D H:i:s";
                            }
                            if (mediaInfo.fontName == null)
                            {
                                mediaInfo.fontName = TypefaceManager.DEFAULT;
                            }
                            if (mediaInfo.fontColor == null)
                            {
                                mediaInfo.fontColor = "0xffffffff";
                            }
                            if (mediaInfo.fontSize == null)
                            {
                                mediaInfo.fontSize = "40";
                            }
                        }
                        else if (wndType.contains("Gallery"))
                        {
                            if (!"Image".equals(mediaInfo.mediaType))
                            {
                                logger.d("media file is not image");
                                continue;
                            }
                            else if (mediaInfo.filePath == null)
                            {
                                logger.w("media has no file path, skip it.");
                                continue;
                            }
                        }
                        else if (wndType.contains("Web"))
                        {
                            if (mediaInfo.filePath == null)
                            {
                                logger.w("media has no URL path, skip it.");
                                continue;
                            }
                        }
                        
                        // 添加新的素材信息到PlayList
                        playList.add(mediaInfo);
                    }
                    
                    // 启动播放
                    tempSubWnd.startPlayFromList(playList);
                }
            }
        }
    }
    
    /**
     * Set the picture for standby screen when has no program.
     */
    @SuppressWarnings("deprecation")
    private boolean setStandbyScreen()
    {
        mHandler.removeCallbacks(rSetStandbyDelay);
        
        Bitmap destBmp = PosterApplication.getInstance().getStandbyScreenImage();
        if (mMainLayout == null || destBmp == null)
        {
            mHandler.postDelayed(rSetStandbyDelay, 500);
            return false;
        }
        else
        {
            mMainLayout.setBackgroundDrawable(new BitmapDrawable(getResources(), destBmp));
            mMainLayout.setX(0);
            mMainLayout.setY(0);
        }
        
        return true;
    }
    
    /**
     * Set the background picture of the window.
     */
    @SuppressWarnings("deprecation")
    private boolean setWindowBackgroud()
    {
        mHandler.removeCallbacks(rSetWndBgDelay);
        
        if (mMainLayout == null)
        {
            logger.i("Main layout didn't ready, can't load background image.");
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else if (!FileUtils.isExist(mBgImgInfo.filePath))
        {
            logger.i("Background Image [" + mBgImgInfo.filePath + "] didn't exist.");
            PosterBaseView.checkIsNeedToDownload(mBgImgInfo);
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else if (!PosterBaseView.checkMediaMd5(mBgImgInfo))
        {
            logger.i("Background Image [" + mBgImgInfo.filePath + "] verifycode is wrong.");
            PosterBaseView.checkIsNeedToDownload(mBgImgInfo);
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }

        // 读取图片
        Bitmap destBmp = loadBgPicture(mBgImgInfo);
        
        // 图片生成失败
        if (destBmp == null)
        {
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else
        {
            // 设置背景
            mMainLayout.setBackgroundDrawable(new BitmapDrawable(getResources(), destBmp));
            mMainLayout.setX(mBgScreenXPos);
            mMainLayout.setY(mBgScreenYPos);
        }
        
        return true;
    }
    
    private Bitmap loadBgPicture(final MediaInfoRef picInfo)
    {
        Bitmap srcBmp = null;
        StringBuilder sbKey = new StringBuilder();
        sbKey.append("Background").append(File.separator).append(picInfo.filePath);

        try
        {
            if (picInfo == null || FileUtils.mediaIsPicFromNet(picInfo))
            {
                Log.e("load picture error", "picture info is error");
                return null;
            }
            else if ((srcBmp = PosterApplication.getBitmapFromMemoryCache(sbKey.toString())) != null)
            {
                return srcBmp;
            }

            // Create the Stream
            InputStream isImgBuff = PosterBaseView.createImgInputStream(picInfo);
            if (isImgBuff == null)
            {
                return null;
            }

            try
            {
                // Create the bitmap for BitmapFactory
                srcBmp = BitmapFactory.decodeStream(isImgBuff, null, PosterBaseView.setBitmapOption(picInfo));
            }
            catch (java.lang.OutOfMemoryError e)
            {
                logger.e("picture is too big, out of memory!");

                if (srcBmp != null && !srcBmp.isRecycled())
                {
                    srcBmp.recycle();
                    srcBmp = null;
                }
                
                System.gc();
            }
            finally
            {
                if (isImgBuff != null)
                {
                    isImgBuff.close();
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        // Will be stored the new image to LruCache
        if (srcBmp != null)
        {
            PosterApplication.addBitmapToMemoryCache(sbKey.toString(), srcBmp);
        }
        
        return srcBmp;
    }

    public void startAudio()
    {
    	if (mSubWndCollection != null)
        {
            for (SubWindow wnd : mSubWndCollection)
            {
                if (wnd.getWindowName().startsWith("Audio"))
                {
                	wnd.getContentView().viewStart();
                }
            }
        }
    }
    
    public void stopAudio()
    {
    	if (mSubWndCollection != null)
        {
            for (SubWindow wnd : mSubWndCollection)
            {
                if (wnd.getWindowName().startsWith("Audio"))
                {
                	wnd.getContentView().viewStop();
                }
            }
        }
    }

    /**
     * 如果背景图片不存在，则轮循检测图片文件是否下载完成.
     */
    private Runnable rSetWndBgDelay   = new Runnable() {
                                          @Override
                                          public void run()
                                          {
                                              setWindowBackgroud();
                                          }
                                      };
    
    private Runnable rSetStandbyDelay = new Runnable() {
                                          @Override
                                          public void run()
                                          {
                                              setStandbyScreen();
                                          }
                                      };
    
    public Bitmap combineScreenCap(Bitmap bitmap)
    {
        if (mMainWindow != null && mMainWindow.needCombineCap())
        {
            Bitmap videoCap = mMainWindow.getVideoCap();
            if (videoCap != null)
            {
                Bitmap newb = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
                Canvas cv = new Canvas(newb);
                cv.drawBitmap(bitmap, 0, 0, null);
                Paint paint = new Paint();
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
                cv.drawBitmap(videoCap, mMainScreenXPos, mMainScreenYPos, paint);
                cv.save(Canvas.ALL_SAVE_FLAG);
                cv.restore();
                return newb;
            }
        }
        return bitmap;
    }
}

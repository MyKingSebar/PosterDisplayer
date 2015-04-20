/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.youngsee.common.FileUtils;
import com.youngsee.common.Logger;
import com.youngsee.common.Md5;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.common.TypefaceManager;
import com.youngsee.ftpoperation.FtpFileInfo;
import com.youngsee.ftpoperation.FtpHelper;
import com.youngsee.gifdecode.GifDecodeInfo;
import com.youngsee.gifdecode.GifDecoder;
import com.youngsee.gifdecode.GifAction;
import com.youngsee.gifdecode.GifFrame;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.screenmanager.ScreenManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;

public abstract class PosterBaseView
{
    protected Logger                         logger               = new Logger();
    protected Context                        mContext             = null;
    protected String                         mViewName            = "";
    protected boolean                        mUseCacheForNetMedia = true;
    
    private GifDecoder                     mGifDecoder          = null;
    private GifDecodeInfo                  mDecodeInfo          = null;
    
    // 保存所有的GIF decoder信息
    private static HashMap<String, GifDecodeInfo> mGifDecodeInfoMap    = new HashMap<String, GifDecodeInfo>();

    public PosterBaseView(Context context)
    {
        mContext = context;
    }

    public PosterBaseView(Context context, String viewName)
    {
        mContext = context;
        mViewName = viewName;
    }
    
    public PosterBaseView(Context context, String viewName, boolean isUseCache)
    {
        mContext = context;
        mViewName = viewName;
        mUseCacheForNetMedia = isUseCache;
    }
    
    // Abstract functions
    public abstract View getCoverView();
    public abstract void viewDestroy();
    public abstract void viewPause();
    public abstract void viewResume();
    public abstract void showMediaList(ArrayList<MediaInfoRef> list);
    
    protected void decodeGifPicture(final MediaInfoRef picInfo)
    {
        // add decode info to the hash map
        if ((mDecodeInfo = getGifDecodeInfo(picInfo)) == null)
        {
            mDecodeInfo = new GifDecodeInfo();
            mGifDecodeInfoMap.put(picInfo.verifyCode, mDecodeInfo);
        }
        
        mDecodeInfo.clear();
        releaseGifDecoder();
        
        // Create the Stream
        InputStream isImgBuff = createImgInputStream(picInfo);
        
        // start decoder GIF file
        if (isImgBuff != null)
        {
            mGifDecoder = new GifDecoder(isImgBuff, new GifAction(){
                @Override
                public void parseOk(boolean parseStatus, int frameIndex)
                {
                    if (mDecodeInfo != null && mGifDecoder != null)
                    {
                        if (frameIndex == -1)
                        {
                            mDecodeInfo.setFrameCount(mGifDecoder.getFrameCount());
                            mDecodeInfo.setDecodeState(GifDecodeInfo.DECODE_COMPLATED);
                        }
                        else
                        {
                            mDecodeInfo.setDecodeState(GifDecodeInfo.DECODING_STATE);
                            if (parseStatus)
                            {
                                GifFrame frame = mGifDecoder.getFrame(frameIndex - 1);
                                if (frame != null && frame.image != null)
                                {
                                    Bitmap tmpBmp = frame.image;
                                    mDecodeInfo.addFrameDelay(frame.delay);
                                    StringBuilder sbFileName = new StringBuilder();
                                    sbFileName.append(PosterApplication.getGifImagePath(picInfo.verifyCode));
                                    sbFileName.append(File.separator);
                                    sbFileName.append(frameIndex - 1).append(".jpg");
                                    PosterApplication.resizeImage(tmpBmp, sbFileName.toString(), tmpBmp.getWidth(), tmpBmp.getHeight());
                                }
                            }
                        }
                    }
                } 
            });
            
            mGifDecoder.start();
            mDecodeInfo.setDecodeState(GifDecodeInfo.DECODE_START);
        }
    }
    
    protected GifDecodeInfo getGifDecodeInfo(MediaInfoRef gifInfo)
    {
        synchronized (mGifDecodeInfoMap)
        {
            if (mGifDecodeInfoMap != null && !mGifDecodeInfoMap.isEmpty())
            {
                return mGifDecodeInfoMap.get(gifInfo.verifyCode);
            }
        }
        return null;
    }
    
    protected boolean isDecodeComplated(MediaInfoRef gifInfo)
    {
        GifDecodeInfo decodeInfo = getGifDecodeInfo(gifInfo);
        return (decodeInfo != null && decodeInfo.getDecodeState() == GifDecodeInfo.DECODE_COMPLATED);
    }
    
    protected void releaseGifDecoder()
    {
        if (mGifDecoder != null)
        {
            mGifDecoder.free();
            mGifDecoder = null;
        }
    }

    protected boolean gifImgIsExsit(MediaInfoRef gifInfo)
    {
        GifDecodeInfo decodeInfo = getGifDecodeInfo(gifInfo);
        String imgPath = PosterApplication.getGifImagePath(gifInfo.verifyCode);
        if (decodeInfo != null && FileUtils.isExist(imgPath))
        {
            int nFrameCnt = decodeInfo.getFrameCount();
            if (new File(imgPath).listFiles().length == nFrameCnt)
            {
                return true;
            }
        }
        return false;
    }
    
    protected void terminateGifDecode()
    {
        if (mGifDecoder != null)
        {
            mGifDecoder.terminate();
        }
    }
    
    protected GifDecodeInfo getCurrentDecodeInfo()
    {
        return mDecodeInfo;
    }
    
    protected boolean mediaTimeIsValid(MediaInfoRef media)
    {
        if (media.starttime == null || media.endtime == null)
        {
            return true;
        }
        
        return (PosterApplication.beforeCurrentTime(media.starttime) && PosterApplication.afterCurrentTime(media.endtime));
    }
    
    public static boolean checkMediaMd5(MediaInfoRef media)
    {
        String md5Value = new Md5(media.md5Key).ComputeFileMd5(media.filePath);
        
        if (md5Value != null && 
            md5Value.equals(media.verifyCode))
        {
            return true;
        }
        
        return false;
    }
    
    public static void checkIsNeedToDownload(MediaInfoRef media)
    {
        if (media != null)
        {
            FtpFileInfo ftpFileInfo = new FtpFileInfo();
            ftpFileInfo.setRemotePath(media.remotePath);
            ftpFileInfo.setVerifyCode(media.verifyCode);
            ftpFileInfo.setVerifyKey(media.md5Key);
            ftpFileInfo.setLocalPath(FileUtils.getFileAbsolutePath(media.filePath));
            
            if (!FtpHelper.getInstance().materialIsOnDownload(ftpFileInfo))
            {
                if (FileUtils.isExist(media.filePath))
                {
                    FileUtils.delFile(new File(media.filePath));
                }
                ScreenManager.getInstance().needToDownload(ftpFileInfo);
            }
        }
    }
    
    protected String getText(final MediaInfoRef txtInfo)
    {
        String strText = null;
        if (FileUtils.mediaIsTextFromFile(txtInfo))
        {
            strText = readTextFile(txtInfo.filePath);
        }
        else if (FileUtils.mediaIsTextFromNet(txtInfo))
        {
            strText = downloadText(txtInfo.filePath);
        }
        return strText;
    }
    
    protected String downloadText(String urlPath)
    {
        if (!PosterApplication.getInstance().isNetworkConnected())
        {
            logger.i("Net link is down, can't dowload the Text.");
            return "";
        }
        
        InputStream in = null;
        BufferedReader br = null;
        StringBuffer sb = new StringBuffer();
        try
        {
            String line = "";
            URL url = new URL(urlPath);
            in = url.openStream();
            br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            while ((line = br.readLine()) != null) 
            {
                sb.append(line + "\n");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (in != null)
                {
                    in.close();
                }
                
                if (br != null)
                {
                    br.close();
                }
            }
            catch (Exception e)
            {
                
            }
        }
        
        return sb.toString();
    }
    
    protected String readTextFile(String filePath)
    {
        FileUtils.updateFileLastTime(filePath);
        String dest = "";
        InputStream is = null;
        BufferedReader reader = null;
        try 
        {
            String str = "";
            StringBuffer sb = new StringBuffer();
            is = new FileInputStream(filePath);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            while ((str = reader.readLine()) != null) 
            {
                sb.append(str + "\n");
            }
            
            // 去掉非法字符
            Pattern p = Pattern.compile("(\ufeff)");
            Matcher m = p.matcher(sb.toString());
            dest = m.replaceAll("");
        } 
        catch (FileNotFoundException e) 
        {
            e.printStackTrace();
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
        finally
        {
            try 
            {
                if (is != null)
                {
                    is.close();
                }
                
                if (reader != null)
                {
                    reader.close();
                }
            } 
            catch (IOException e) 
            {
                e.printStackTrace();
            }
        }
        
        return dest;
    }
    
    /**
     * 自动分割文本
     * @param content 需要分割的文本
     * @param p  画笔，用来根据字体测量文本的宽度
     * @param width 最大的可显示像素（一般为控件的宽度）
     * @return 一个字符串数组，保存每行的文本
     */
    protected ArrayList<String> autoSplit(String content, Paint p, float width)
    {
        String strText = StringFilter(content);
        ArrayList<String> retList = new ArrayList<String>();
        if (strText == null)
        {
            return retList;
        }

        char ch = 0;
        int w = 0;
        int istart = 0;
        int length = strText.length();
        float[] widths = new float[1];
        for (int i = 0; i < length; i++) 
        {
            ch = strText.charAt(i);
            p.getTextWidths(String.valueOf(ch), widths);
            if (ch == '\n') 
            {
                retList.add(strText.substring(istart, i));
                istart = i + 1;
                w = 0;
            } 
            else 
            {
                w += (int) Math.ceil(widths[0]);
                if (w > width) 
                {
                    retList.add(strText.substring(istart, i));
                    istart = i;
                    i--;
                    w = 0;
                } 
                else 
                {
                    if (i == length - 1) 
                    {
                        retList.add(strText.substring(istart));
                    }
                }
            }
        }
        
        return retList;
    }

    // 替换、过滤特殊字符
    protected String StringFilter(String str)
    {
        if (str == null)
        {
            return null;
        }
        
        str = str.replaceAll("【", "[").replaceAll("】", "]").replaceAll("！", "!");// 替换中文标号
        String regEx = "[『』]"; // 清除掉特殊字符
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }

    public static BitmapFactory.Options setBitmapOption(final MediaInfoRef picInfo)
    {
        BitmapFactory.Options opt = new BitmapFactory.Options();

        InputStream in = createImgInputStream(picInfo);
        if (in != null)
        {
            // 获取图片实际大小
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opt);

            int outWidth = opt.outWidth;
            int outHeight = opt.outHeight;
            int nWndWidth = picInfo.containerwidth;
            int nWndHeight = picInfo.containerheight;

            // 设置压缩比例
            opt.inSampleSize = 1;
            if (outWidth > nWndWidth || outHeight > nWndHeight)
            {
                opt.inSampleSize = (outWidth / nWndWidth + outHeight / nWndHeight) / 2;
            }
            opt.inPreferredConfig = Bitmap.Config.RGB_565;
            opt.inDither = false;
            opt.inJustDecodeBounds = false;

            // 关闭输入�?
            try
            {
                in.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return opt;
    }

    public static InputStream createImgInputStream(final MediaInfoRef picInfo)
    {
        InputStream is = null;

        try
        {
            if (FileUtils.mediaIsGifFile(picInfo) ||
                FileUtils.mediaIsPicFromFile(picInfo) || 
                FileUtils.mediaIsTextFromFile(picInfo))
            {
                String strFileName = picInfo.filePath;
                if (FileUtils.isExist(strFileName))
                {
                    is = new FileInputStream(strFileName);
                    FileUtils.updateFileLastTime(picInfo.filePath);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return is;
    }
    
    protected int getFontSize(final MediaInfoRef txtInfo)
    {
        int nFontSize = 0;
        if (txtInfo.fontSize != null)
        {
            nFontSize = Integer.parseInt(txtInfo.fontSize);
        }
        else
        {
            nFontSize = 50;
        }
        return nFontSize;
    }
    
    protected int getFontColor(final MediaInfoRef txtInfo)
    {
        int nFontColor = 0;
        if (txtInfo.fontColor != null)
        {
            nFontColor = PosterApplication.stringHexToInt(txtInfo.fontColor);
        }
        else
        {
            nFontColor = Color.WHITE;
        }
        return (nFontColor | 0xFF000000);
    }
    
    protected Typeface getFont(final MediaInfoRef txtInfo)
    {
        Typeface typeface = null;
        TypefaceManager typefaceManager = new TypefaceManager(mContext);
        if (txtInfo.fontName != null)
        {
            typeface = typefaceManager.getTypeface(txtInfo.fontName);
        }
        else
        {
            typeface = typefaceManager.getTypeface(TypefaceManager.DEFAULT);
        }
        return typeface;
    }
    
    protected Bitmap getBitMap(final MediaInfoRef picInfo)
    {
        Bitmap retBmp = null;
        if (!mUseCacheForNetMedia && FileUtils.mediaIsPicFromNet(picInfo))
        {
            retBmp = loadNetPicture(picInfo);
        }
        else
        {
            String strKey = getImgCacheKey(picInfo);
            if ((retBmp = PosterApplication.getBitmapFromMemoryCache(strKey)) == null)
            {
                if (FileUtils.mediaIsPicFromNet(picInfo))
                {
                    // Check whether the picture save in disk cache.
                    // if so, then load the picture to memory cache and show it.
                    if ((retBmp = PosterApplication.getBitmapFromDiskCache(strKey)) != null)
                    {
                        PosterApplication.addBitmapToMemoryCache(strKey, retBmp);
                    }
                    else
                    {
                        retBmp = loadNetPicture(picInfo);
                    }
                }
                else if (FileUtils.mediaIsPicFromFile(picInfo))
                {
                    retBmp = loadLocalPicture(picInfo);
                }
            }
        }

        return retBmp;
    }
    
    protected Bitmap loadNetPicture(final MediaInfoRef picInfo)
    {
        String newFilePath;
        if (picInfo.filePath.contains("$MAC$")) {
            newFilePath = picInfo.filePath.replace("$MAC$", PosterApplication.getEthMacStr());
        } else if (picInfo.filePath.contains("$TERMNAME$")) {
            String termNames = (PosterApplication.getInstance().getSysParam(false, false).termvalue != null) ? new String(PosterApplication.getInstance().getSysParam(false, false).termvalue) : "";
            newFilePath = picInfo.filePath.replace("$TERMNAME$", termNames);
        } else if (picInfo.filePath.contains("$TERMGRP$")) {
            String termGrps = (PosterApplication.getInstance().getSysParam(false, false).termGrpvalue != null) ? new String(PosterApplication.getInstance().getSysParam(false, false).termGrpvalue) : "";
            newFilePath = picInfo.filePath.replace("$TERMGRP$", termGrps);
        } else {
            newFilePath = picInfo.filePath;
        }
        Bitmap srcBmp = downloadBitmap(newFilePath);
        
        // Save the bitmap to cache
        if (srcBmp != null)
        {
            String strKey = getImgCacheKey(picInfo);
            PosterApplication.addBitmapToMemoryCache(strKey, srcBmp);
            PosterApplication.addBitmapToDiskCache(strKey, srcBmp);
        }
        
        return srcBmp;
    }

    protected Bitmap loadLocalPicture(final MediaInfoRef picInfo)
    {
        Bitmap srcBmp = null;

        try
        {
            if (picInfo == null || FileUtils.mediaIsPicFromNet(picInfo))
            {
                Log.e("load picture error", "picture info is error");
                return null;
            }

            // Create the Stream
            InputStream isImgBuff = createImgInputStream(picInfo);
            if (isImgBuff == null)
            {
                return null;
            }

            try
            {
                // Create the bitmap for BitmapFactory
                srcBmp = BitmapFactory.decodeStream(isImgBuff, null, setBitmapOption(picInfo));
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
            PosterApplication.addBitmapToMemoryCache(getImgCacheKey(picInfo), srcBmp);
        }

        return srcBmp;
    }
    
    protected Bitmap downloadBitmap(String imageUrl)
    {
        if (!PosterApplication.getInstance().isNetworkConnected())
        {
            logger.i("Net link is down, can't dowload the bitmap.");
            return null;
        }
        
        Bitmap bitmap = null;
        HttpURLConnection con = null;
        InputStream in = null;
        System.setProperty("http.keepAlive", "false");
        try
        {
            URL url = new URL(imageUrl);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(60 * 1000);
            con.setReadTimeout(90 * 1000);
            con.setDoInput(true);
            in = con.getInputStream();
            bitmap = BitmapFactory.decodeStream(in);
        }
        catch (Exception e)
        {
            logger.e("load net picture fail");
            e.printStackTrace();
        }
        finally
        {
        	if (in != null)
        	{
        		try
        		{
					in.close();
				}
        		catch (IOException e)
				{
					e.printStackTrace();
				}
        	}
        	
            if (con != null)
            {
                con.disconnect();
            }
        }

        return bitmap;
    }
    
    protected String getImgCacheKey(MediaInfoRef pInfo)
    {
        String retKey = null;
        if (mViewName == null || "".equals(mViewName))
        {
            retKey = pInfo.filePath;
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            sb.append(mViewName).append(File.separator).append(pInfo.filePath);
            retKey = sb.toString();
        }
        return retKey;
    }
    
    protected boolean checkFilesIsValid(ArrayList<MediaInfoRef> playlist)
    {
    	if (playlist != null)
    	{
	        for (MediaInfoRef mediaInfo : playlist)
	        {
	            if (FileUtils.mediaIsFile(mediaInfo) && FileUtils.isExist(mediaInfo.filePath))
	            {              
	            	return true;
	            }
	        }
    	}
        return false;
    }
}

/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.posterdisplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xmlpull.v1.XmlSerializer;

import com.youngsee.common.Contants;
import com.youngsee.common.DiskLruCache;
import com.youngsee.common.FileUtils;
import com.youngsee.common.Logger;
import com.youngsee.common.MediaInfoRef;
import com.youngsee.common.ReflectionUtils;
import com.youngsee.common.RuntimeExec;
import com.youngsee.common.SysOnOffTimeInfo;
import com.youngsee.customview.PosterBaseView;
import com.youngsee.ftpoperation.FtpFileInfo;
import com.youngsee.ftpoperation.FtpHelper;
import com.youngsee.ftpoperation.FtpOperationInterface;
import com.youngsee.posterdisplayer.R;
import com.youngsee.screenmanager.ProgramFragment;
import com.youngsee.screenmanager.ScreenManager;
import com.youngsee.webservices.SysParam;
import com.youngsee.webservices.WsClient;
import com.youngsee.webservices.XmlCmdInfoRef;
import com.youngsee.webservices.XmlParser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.util.LruCache;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Xml;
import android.view.View;

public class PosterApplication extends Application
{
    private static Logger                   logger                         = new Logger();
    private static PosterApplication        INSTANCE                       = null;
    
    private static byte[]                   mEthMac                        = null;
    private static String                   mCpuId                         = null;
    private static int                      mScreenWidth                   = 0;
    private static int                      mScreenHeight                  = 0;
    
    private static String                   mSysParamFullPath              = null;
    private static String                   mAPKUpdateFullPath             = null;
    private static String                   mSysParamBackupFullPath        = null;
    private static String                   mStandbyScreenImgFullPath      = null;
    private static String                   mStartUpScreenImgFullPath      = null;
    private static String                   mCaptureScreenImgFullPath      = null;
    private static String                   mTempFolderFullPath            = null;
    
    private static SysParam                 mSysParam                      = null;
    public static Object                    mSysParamLock                  = new Object();
    
    // Define the image cache (per APP instance)
    private static LruCache<String, Bitmap> mImgMemoryCache                = null;
    
    // Define the image disk cache (per APP instance)
    private static DiskLruCache             mImgDiskCache                  = null;
    private static final int                DISK_CACHE_SIZE                = 1024 * 1024 * 40; // 40MB
                                                                                                
    /* Defined file name */
    public static final String              LOCAL_CONFIG_FILENAME          = "isconfig.xml";
    public static final String              LOCAL_CAST_FILENAME_T          = "playlist_t.xml";
    public static final String              LOCAL_NORMAL_PLAYLIST_FILENAME = "playlist.xml";
    public static final String              LOCAL_CASTE_FILENAME_T         = "playliste_t.xml";
    public static final String              LOCAL_EMGCY_PLAYLIST_FILENAME  = "playliste.xml";
    public static final String              LOCAL_SYSTEM_LOGNAME           = "common_";
    public static final String              LOCAL_PLAY_LOGNAME             = "play_";
    public static final String              DEVICE_ID_FILE_NAME            = "/system/devinfo/devid.ys";
    public static final String              CPU_ID_FILE_NAME               = "/system/devinfo/cpuid.ys";

    private Timer                           mDelPeriodFileTimer            = null;
    private Timer                           mUploadLogTimer                = null;
    
    private AlarmManager mAlarmManager = null;
    
    private final String SYSPROP_HWROTATION_CLASS = "android.os.SystemProperties";
	private final String SYSPROP_HWROTATION_GETMETHOD = "getInt";
	private final String SYSPROP_HWROTATION = "persist.sys.hwrotation";
	private final int SYSPROP_HWROTATION_DEFAULT = -1;
    
    public static PosterApplication getInstance()
    {
        return INSTANCE;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        INSTANCE = this;
        
        // Allocate memory for the image cache space
        int cacheSize = (int) Runtime.getRuntime().maxMemory() / 10;
        mImgMemoryCache = new LruCache<String, Bitmap>(cacheSize)
        {
            @Override
            protected int sizeOf(String key, Bitmap bitmap)
            {
                int nSize = 0;
                if (bitmap != null)
                {
                    nSize = bitmap.getByteCount();
                }
                return nSize;
            }
        };
        
        // Allocate disk size for the image disk cache space
        File cacheDir = DiskLruCache.getDiskCacheDir(this, "thumbnails");
        if ((mImgDiskCache = DiskLruCache.openCache(this, cacheDir, DISK_CACHE_SIZE)) != null)
        {
            mImgDiskCache.clearCache();
        }
        
        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    }
    
    public void initAppParam()
    {
        getEthMacAddress();
        getSysParam(true, false);
    }
    
    public String getKernelVersion()
    {
        String strVersion = null;
        RandomAccessFile reader = null;
        try
        {
            reader = new RandomAccessFile("/proc/version", "r");
            strVersion = reader.readLine();
        }
        catch (Exception e)
        {
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        
        if (strVersion != null)
        {
            String verisonBuf[] = strVersion.split("\\s+");
            for (String strVer : verisonBuf)
            {
                if (strVer.contains("."))
                {
                    return strVer;
                }
            }
        }
        
        return strVersion;
    }
    
    public int getVerCode()
    {
        int verCode = -1;
        try
        {
            verCode = getPackageManager().getPackageInfo(Contants.POSTER_PACKAGENAME, 0).versionCode;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return verCode;
    }
    
    public String getVerName()
    {
        String verName = "";
        try
        {
            verName = getPackageManager().getPackageInfo(Contants.POSTER_PACKAGENAME, 0).versionName;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return verName;
    }
    
    public static void setScreenHeight(int nHeight)
    {
        mScreenHeight = nHeight;
    }
    
    public static void setScreenWidth(int nWidth)
    {
        mScreenWidth = nWidth;
    }
    
    public static int getScreenHeigth()
    {
        return mScreenHeight;
    }
    
    public static int getScreenWidth()
    {
        return mScreenWidth;
    }
    
    public static void addBitmapToMemoryCache(String key, Bitmap bitmap)
    {
        if (mImgMemoryCache == null)
        {
            return;
        }
        
        // if cache didn't have the bitmap, then save it.
        if (mImgMemoryCache.get(key) == null)
        {
            mImgMemoryCache.put(key, bitmap);
        }
    }
    
    public static Bitmap getBitmapFromMemoryCache(String key)
    {
        if (mImgMemoryCache == null)
        {
            return null;
        }
        
        return mImgMemoryCache.get(key);
    }
    
    public static void clearMemoryCache()
    {
        if (mImgMemoryCache != null)
        {
            mImgMemoryCache.evictAll();
        }
    }
    
    public static void addBitmapToDiskCache(String key, Bitmap bitmap)
    {
        if (mImgDiskCache == null)
        {
            return;
        }
        
        // if cache didn't have the bitmap, then save it.
        if (mImgDiskCache.get(key) == null)
        {
            mImgDiskCache.put(key, bitmap);
        }
    }
    
    public static Bitmap getBitmapFromDiskCache(String key)
    {
        if (mImgDiskCache == null)
        {
            return null;
        }
        
        return mImgDiskCache.get(key);
    }
    
    public static void clearDiskCache()
    {
        if (mImgDiskCache != null)
        {
            mImgDiskCache.clearCache();
        }
    }
    
    public static int resizeImage(Bitmap bitmap, String destPath, int width, int height)
    {
        int swidth = bitmap.getWidth();
        int sheight = bitmap.getHeight();
        float scaleWidht = (float) width / swidth;
        float scaleHeight = (float) height / sheight;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleWidht, scaleHeight);
        Bitmap newbm = Bitmap.createBitmap(bitmap, 0, 0, swidth, sheight, matrix, true);
        File saveFile = new File(destPath);
        FileOutputStream fileOutputStream = null;
        
        try
        {
            saveFile.createNewFile();
            fileOutputStream = new FileOutputStream(saveFile);
            if (fileOutputStream != null)
            {
                // 把位图的压缩信息写入到一个指定的输出流中
                // 第一个参数format为压缩的格式
                // 第二个参数quality为图像压缩比的值,0-100.0 意味着小尺寸压缩,100意味着高质量压缩
                // 第三个参数stream为输出流
                newbm.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            }
            fileOutputStream.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return 1;
        }
        finally
        {
            if (fileOutputStream != null)
            {
                try
                {
                    fileOutputStream.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        
        return 0;
    }
    
    public Bitmap getStandbyScreenImage()
    {
        Bitmap dstImg = null;
        
        // 从缓存中获取
        String strImagePath = getStandbyScreenImgPath();
        if ((dstImg = getBitmapFromMemoryCache(strImagePath)) == null)
        {
            // 从磁盘中获取 (待机画面保存位置, 外部可修改)
            if ((dstImg = loadPicture(strImagePath, mScreenWidth, mScreenHeight)) == null)
            {
                // 从Resource中获取 (默认的待机画面)
                dstImg = BitmapFactory.decodeResource(getResources(), R.drawable.daiji);
            }
            
            // Will be stored the new image to LruCache
            if (dstImg != null)
            {
                addBitmapToDiskCache(strImagePath, dstImg);
            }
        }
        
        return dstImg;
    }
    
    /***********************************************/
    /***** 从当前全局变量中获取系统参数 ******/
    /** isFromXML: true 表示从文件中获取， false 表示从内存中获取
    /** isNewInstance: true 表示新建一个实例， false 表示使用内存的指针
    /***********************************************/
    /***********************************************/
    public SysParam getSysParam(boolean isFromXML, boolean isNewInstance)
    {
        if (isFromXML || mSysParam == null)
        {
            // 从文件中获取系统参数
            mSysParam = getSysParamFromXML();
        }
        
        // 生成新实例
        if (isNewInstance)
        {
            SysParam sysParam = new SysParam();
            sysParam.autoupgradevalue = mSysParam.autoupgradevalue;
            sysParam.brightnessvalue = mSysParam.brightnessvalue;
            sysParam.certNumvalue = new String(mSysParam.certNumvalue);
            sysParam.cfevervalue = new String(mSysParam.cfevervalue);
            sysParam.cycleTimevalue = mSysParam.cycleTimevalue;
            sysParam.delFilePeriodtime = mSysParam.delFilePeriodtime;
            sysParam.devSelvalue = mSysParam.devSelvalue;
            sysParam.dispScalevalue = mSysParam.dispScalevalue;
            sysParam.dwnLdrSpdvalue = mSysParam.dwnLdrSpdvalue;
            sysParam.getTaskPeriodtime = mSysParam.getTaskPeriodtime;
            sysParam.hwVervalue = new String(mSysParam.hwVervalue);
            sysParam.kernelvervalue = new String(mSysParam.kernelvervalue);
            sysParam.netConn = new ConcurrentHashMap<String, String>(mSysParam.netConn);
            sysParam.offdlTime = new ConcurrentHashMap<String, String>(mSysParam.offdlTime);
            sysParam.onOffTime = new ConcurrentHashMap<String, String>(mSysParam.onOffTime);
            sysParam.osdLangSetosd_lang = mSysParam.osdLangSetosd_lang;
            sysParam.passwdvalue = new String(mSysParam.passwdvalue);
            sysParam.runmodevalue = mSysParam.runmodevalue;
            sysParam.scrnRotatevalue = mSysParam.scrnRotatevalue;
            sysParam.serverSet = new ConcurrentHashMap<String, String>(mSysParam.serverSet);
            sysParam.setBit = mSysParam.setBit;
            sysParam.sigOutSet = new ConcurrentHashMap<String, String>(mSysParam.sigOutSet);
            sysParam.swVervalue = new String(mSysParam.swVervalue);
            sysParam.syspasswdvalue = new String(mSysParam.syspasswdvalue);
            sysParam.termGrpvalue = new String(mSysParam.termGrpvalue);
            sysParam.termmodelvalue = new String(mSysParam.termmodelvalue);
            sysParam.termvalue = new String(mSysParam.termvalue);
            sysParam.timeZonevalue = new String(mSysParam.timeZonevalue);
            sysParam.volumevalue = mSysParam.volumevalue;
            sysParam.wifiSet = new ConcurrentHashMap<String, String>(mSysParam.wifiSet);
            return sysParam;
        }

        return mSysParam;
    }
    
    /***********************************************/
    /***** 保存系统参数 ******/
    /***********************************************/
    /***********************************************/
    public synchronized boolean saveSysParam(SysParam sysParam)
    {
        boolean ret = false;
        if (sysParam != null)
        {
            // 更新内存参数
            if (mSysParam != sysParam)
            {
                mSysParam = sysParam;
            }
            
            // 保存参数到文件
            ret = saveSysParamToXML(sysParam);
        }
        else
        {
            logger.e("Save param error: system param is null.");
        }

        return ret;
    }

    /***********************************************/
    /***** 从XML文件中获取系统参数 ******/
    /***********************************************/
    /***********************************************/
    private SysParam getSysParamFromXML()
    {
        SysParam sysParam = null;
        String strFileName = getSysParamFullPath();
        String strBkFileName = getSysParamBackupFullPath();
        if (!FileUtils.isExist(strFileName) && !FileUtils.isExist(strBkFileName))
        {
            // 若没有系统参数文件，则使用出厂设置参数
            return factoryRest();
        }
        else if (!FileUtils.isExist(strFileName) && FileUtils.isExist(strBkFileName))
        {
            try
            {
                FileUtils.copyFileTo(new File(strBkFileName), new File(strFileName));
            }
            catch (IOException e)
            {
                return factoryRest();
            }
        }
        
        synchronized (mSysParamLock)
        {
            try
            {
                FileInputStream fIn = new FileInputStream(strFileName);
                XmlParser xml = new XmlParser();
                sysParam = (SysParam) xml.getXmlObject(fIn, SysParam.class);
                if (sysParam != null && sysParam.cfevervalue != null &&
                		!sysParam.cfevervalue.equals(android.os.Build.VERSION.RELEASE))
                {
                    sysParam.cfevervalue = android.os.Build.VERSION.RELEASE;
                }
                if (sysParam != null && sysParam.kernelvervalue != null &&
                		!sysParam.kernelvervalue.equals(getKernelVersion()))
                {
                    sysParam.kernelvervalue = getKernelVersion();
                }
                if (sysParam != null && sysParam.swVervalue != null &&
                		!sysParam.swVervalue.equals(getVerName()))
                {
                    sysParam.swVervalue = getVerName();
                }
                FileUtils.copyFileTo(new File(strFileName), new File(strBkFileName));
            }
            catch (FileNotFoundException e)
            {
                logger.e("get sysparam from XML has error.");
                e.printStackTrace();
                sysParam = factoryRest();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        
        return sysParam;
    }
    
    /***********************************************/
    /***** 将参数保存到XML文件中 ******/
    /***********************************************/
    /***********************************************/
    private boolean saveSysParamToXML(SysParam sysParam)
    {
        boolean ret = false;
        if (sysParam != null)
        {
            // 判断磁盘空间是否足够
            String strFileName = getSysParamFullPath();
            File win = new File(FileUtils.getFileAbsolutePath(strFileName));
            if (win.getFreeSpace() < 2048)
            {
                logger.e("There is not enough disk space to save system param.");
                return false;
            }
            
            synchronized (mSysParamLock)
            {
                // 初始化变量
                FileOutputStream fos = null;
                
                try
                {
                    // 初始化引擎
                    XmlSerializer serializer = Xml.newSerializer();
                    
                    // 根据文件创建一个文件的输出流对象
                    fos = new FileOutputStream(strFileName);
                    
                    // 设置输出的流以及编码格式
                    serializer.setOutput(fos, FileUtils.ENCODING);
                    
                    /** 设置文件的开始 **/
                    serializer.startDocument(FileUtils.ENCODING, true);
                    serializer.startTag(null, XmlCmdInfoRef.SYSSETDATAFILE);
                    
                    /** 1为设置状态，0为读取状态 **/
                    serializer.startTag(null, XmlCmdInfoRef.SETBIT);
                    serializer.text(Integer.toString(sysParam.setBit));
                    serializer.endTag(null, XmlCmdInfoRef.SETBIT);
                    
                    /** 网络登录方式参数 **/
                    if (sysParam.netConn != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.NETCONN);
                        for (String key : sysParam.netConn.keySet())
                        {
                            serializer.attribute(null, key, sysParam.netConn.get(key));
                        }
                        serializer.endTag(null, XmlCmdInfoRef.NETCONN);
                    }
                    
                    /** 服务器设置参数 **/
                    if (sysParam.serverSet != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.SRVERSET);
                        for (String key : sysParam.serverSet.keySet())
                        {
                            serializer.attribute(null, key, sysParam.serverSet.get(key));
                        }
                        serializer.endTag(null, XmlCmdInfoRef.SRVERSET);
                    }
                    
                    /** 信号输出参数 **/
                    serializer.startTag(null, XmlCmdInfoRef.SIGOUTSET);
                    serializer.attribute(null, "mode", "3"); // HDMI
                    serializer.attribute(null, "value", "10"); // 1080P
                    if (sysParam.sigOutSet != null)
                    {
                        serializer.attribute(null, "repratio", (sysParam.sigOutSet.get("repratio") == null) ? "" : sysParam.sigOutSet.get("repratio"));
                    }
                    serializer.endTag(null, XmlCmdInfoRef.SIGOUTSET);
                    
                    /** WIFI配置参数 **/
                    if (sysParam.wifiSet != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.WIFISET);
                        for (String key : sysParam.wifiSet.keySet())
                        {
                            serializer.attribute(null, key, sysParam.wifiSet.get(key));
                        }
                        serializer.endTag(null, XmlCmdInfoRef.WIFISET);
                    }
                    
                    /** OSD语言设置，0 为中文，1为英文，其它值另定 **/
                    serializer.startTag(null, XmlCmdInfoRef.OSDLANGSET);
                    serializer.attribute(null, XmlCmdInfoRef.OSDLANG, Integer.toString(sysParam.osdLangSetosd_lang));
                    serializer.endTag(null, XmlCmdInfoRef.OSDLANGSET);
                    
                    /** 自动获取任务时间，1-30天 **/
                    serializer.startTag(null, XmlCmdInfoRef.GETTASKPERIOD);
                    serializer.attribute(null, XmlCmdInfoRef.TIME, Integer.toString(sysParam.getTaskPeriodtime));
                    serializer.endTag(null, XmlCmdInfoRef.GETTASKPERIOD);
                    
                    /** 自动删除文件时间，1-30天 **/
                    serializer.startTag(null, XmlCmdInfoRef.DELFILEPERIOD);
                    serializer.attribute(null, XmlCmdInfoRef.TIME, Integer.toString(sysParam.delFilePeriodtime));
                    serializer.endTag(null, XmlCmdInfoRef.DELFILEPERIOD);
                    
                    /** 时区设置，如GMT+8 **/
                    if (sysParam.timeZonevalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.TIMEZONE);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.timeZonevalue);
                        serializer.endTag(null, XmlCmdInfoRef.TIMEZONE);
                    }
                    
                    /** 屏幕旋转，0 屏幕不旋转，1屏幕要旋转 **/
                    serializer.startTag(null, XmlCmdInfoRef.SCRNROTATE);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.scrnRotatevalue));
                    serializer.endTag(null, XmlCmdInfoRef.SCRNROTATE);
                    
                    /** 用户密码设置参数 **/
                    if (sysParam.passwdvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.PASSWD);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.passwdvalue);
                        serializer.endTag(null, XmlCmdInfoRef.PASSWD);
                    }
                    
                    /** 系统登录密码参数 **/
                    if (sysParam.syspasswdvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.SYSPASSWD);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.syspasswdvalue);
                        serializer.endTag(null, XmlCmdInfoRef.SYSPASSWD);
                    }
                    
                    /** 开关机时间设置参数 **/
                    if (sysParam.onOffTime != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.ONOFFTIME);
                        for (String key : sysParam.onOffTime.keySet())
                        {
                            serializer.attribute(null, key, sysParam.onOffTime.get(key));
                        }
                        serializer.endTag(null, XmlCmdInfoRef.ONOFFTIME);
                    }
                    
                    /** 设备选择参数，0 USB, 1 磁盘 **/
                    serializer.startTag(null, XmlCmdInfoRef.DEVSEL);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.devSelvalue));
                    serializer.endTag(null, XmlCmdInfoRef.DEVSEL);
                    
                    /** 亮度设置参数 **/
                    serializer.startTag(null, XmlCmdInfoRef.BRIGHTNESS);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.brightnessvalue));
                    serializer.endTag(null, XmlCmdInfoRef.BRIGHTNESS);
                    
                    /** 音量设置参数 **/
                    serializer.startTag(null, XmlCmdInfoRef.VOLUME);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.volumevalue));
                    serializer.endTag(null, XmlCmdInfoRef.VOLUME);
                    
                    /** 软件版本号 **/
                    if (sysParam.swVervalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.SWVER);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.swVervalue);
                        serializer.endTag(null, XmlCmdInfoRef.SWVER);
                    }
                    
                    /** 硬件版本号 **/
                    if (sysParam.hwVervalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.HWVER);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.hwVervalue);
                        serializer.endTag(null, XmlCmdInfoRef.HWVER);
                    }
                    
                    /** kernel软件版本号 **/
                    if (sysParam.kernelvervalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.KERNELVER);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.kernelvervalue);
                        serializer.endTag(null, XmlCmdInfoRef.KERNELVER);
                    }
                    
                    /** cfe软件版本号 **/
                    if (sysParam.cfevervalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.CFEVER);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.cfevervalue);
                        serializer.endTag(null, XmlCmdInfoRef.CFEVER);
                    }
                    
                    /** 入网许可证号 **/
                    if (sysParam.certNumvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.CERTNUM);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.certNumvalue);
                        serializer.endTag(null, XmlCmdInfoRef.CERTNUM);
                    }
                    
                    /** 终端型号 **/
                    if (sysParam.termmodelvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.TERMMDL);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.termmodelvalue);
                        serializer.endTag(null, XmlCmdInfoRef.TERMMDL);
                    }
                    
                    /** 终端名称 **/
                    if (sysParam.termvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.TERM);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.termvalue);
                        serializer.endTag(null, XmlCmdInfoRef.TERM);
                    }
                    
                    /** 终端组名称 **/
                    if (sysParam.termGrpvalue != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.TERMGRP);
                        serializer.attribute(null, XmlCmdInfoRef.VALUE, sysParam.termGrpvalue);
                        serializer.endTag(null, XmlCmdInfoRef.TERMGRP);
                    }
                    
                    /** 下载速度 **/
                    serializer.startTag(null, XmlCmdInfoRef.DWNLDRSPD);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.dwnLdrSpdvalue));
                    serializer.endTag(null, XmlCmdInfoRef.DWNLDRSPD);
                    
                    /** 心跳时间 **/
                    serializer.startTag(null, XmlCmdInfoRef.CYCLETIME);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.cycleTimevalue));
                    serializer.endTag(null, XmlCmdInfoRef.CYCLETIME);
                    
                    /** 显示比例 1: 4:3;2: 16:9, 3: LetterBox, 4:Pan&Scan **/
                    serializer.startTag(null, XmlCmdInfoRef.DISPSCALE);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.dispScalevalue));
                    serializer.endTag(null, XmlCmdInfoRef.DISPSCALE);
                    
                    /** 自动升级标志 default 0;0:disable ;1,enable **/
                    serializer.startTag(null, XmlCmdInfoRef.AUTOGRADE);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.autoupgradevalue));
                    serializer.endTag(null, XmlCmdInfoRef.AUTOGRADE);
                    
                    /** 播放模式 default 0; 0:网络播放 ;1,单机播放 **/
                    serializer.startTag(null, XmlCmdInfoRef.RUNMODER);
                    serializer.attribute(null, XmlCmdInfoRef.VALUE, Integer.toString(sysParam.runmodevalue));
                    serializer.endTag(null, XmlCmdInfoRef.RUNMODER);
                    
                    /** 禁止下载的时间段设置参数 **/
                    if (sysParam.offdlTime != null)
                    {
                        serializer.startTag(null, XmlCmdInfoRef.OFFDLTIME);
                        for (String key : sysParam.offdlTime.keySet())
                        {
                            serializer.attribute(null, key, sysParam.offdlTime.get(key));
                        }
                        serializer.endTag(null, XmlCmdInfoRef.OFFDLTIME);
                    }
                    
                    /** 设置文件结束 **/
                    serializer.endTag(null, XmlCmdInfoRef.SYSSETDATAFILE);
                    serializer.endDocument();
                    
                    // 保存文件
                    fos.flush();
                    
                    ret = true;
                }
                catch (Exception e)
                {
                    ret = false;
                    logger.e("Save sysparam has error.");
                    e.printStackTrace();
                }
                finally
                {
                    if (fos != null)
                    {
                        try
                        {
                            fos.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    
                    if (ret = true)
                    {
                        try
                        {
                            FileUtils.copyFileTo(new File(getSysParamFullPath()), new File(getSysParamBackupFullPath()));
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        
        return ret;
    }
    
    public synchronized SysParam factoryRest()
    {
        SysParam sysParam = new SysParam();
        sysParam.setBit = 0;
        
        sysParam.netConn = new ConcurrentHashMap<String, String>();
        sysParam.netConn.put("mode", "DHCP");
        sysParam.netConn.put("ip", "0.0.0.0");
        
        sysParam.serverSet = new ConcurrentHashMap<String, String>();
        sysParam.serverSet.put("weburl", "http://123.56.146.48/dn2/services/Heart.asmx");
        sysParam.serverSet.put("ftpip", "123.56.146.48");
        sysParam.serverSet.put("ftpport", "21");
        sysParam.serverSet.put("ftpname", "dn4");
        sysParam.serverSet.put("ftppasswd", "dn4");
        sysParam.serverSet.put("ntpip", "123.56.146.48");
        sysParam.serverSet.put("ntpport", "123");
        
        sysParam.sigOutSet = new ConcurrentHashMap<String, String>();
        sysParam.sigOutSet.put("mode", "3");
        sysParam.sigOutSet.put("value", "10");
        sysParam.sigOutSet.put("repratio", "100");
        
        sysParam.wifiSet = new ConcurrentHashMap<String, String>();
        sysParam.wifiSet.put("ssid", "");
        sysParam.wifiSet.put("wpapsk", "");
        sysParam.wifiSet.put("authmode", "");
        sysParam.wifiSet.put("encryptype", "");
        
        sysParam.onOffTime = new ConcurrentHashMap<String, String>();
        sysParam.onOffTime.put("group", "0");

        sysParam.getTaskPeriodtime = 30;
        sysParam.delFilePeriodtime = 30;
        sysParam.timeZonevalue = "-8";
        sysParam.passwdvalue = "";
        sysParam.syspasswdvalue = "";
        sysParam.brightnessvalue = 60;
        sysParam.volumevalue = 60;
        sysParam.swVervalue = "4.1.0.0";
        sysParam.hwVervalue = "1.0.0.0";
        sysParam.kernelvervalue = getKernelVersion();
        sysParam.cfevervalue = android.os.Build.VERSION.RELEASE;
        sysParam.certNumvalue = "";
        sysParam.termmodelvalue = "JWA-YS200";
        sysParam.termvalue = "悦视显示终端";
        sysParam.termGrpvalue = "无";
        sysParam.dispScalevalue = 2; /* 16:9 */
        
        saveSysParamToXML(sysParam);
        
        return sysParam;
    }
    
    // //////////////////////////////////////////////////////////////////////////////////////
    
    /*
     * 获取节目存储的路径 选用外部最大的存储空间做为节目的存储介质 注：路径有可能实时变化，因为U盘和SD卡随时可能插拔
     */
    public static String getProgramPath()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(FileUtils.getExternalStorage());
        sb.append(File.separator);
        sb.append("pgm");
        if (!FileUtils.isExist(sb.toString()))
        {
            FileUtils.createDir(sb.toString());
        }

        return sb.toString();
    }
    
    public static boolean existsPgmInUdisk(String path) {
    	if ((path == null)
    			|| (path.length() < 13)
    			|| !path.substring(5).startsWith(Contants.UDISK_NAME_PREFIX)) {
    		return false;
    	}
    	File udisk = new File(path);
    	if (udisk.getTotalSpace() > 0) {
    		File[] files = udisk.listFiles();
    		for (File file : files) {
    			if (file.isDirectory() && file.getName().endsWith(".pgm")) {
    				return true;
    			}
    		}
    	} else {
    		File[] files = udisk.listFiles();
    		if (files != null) {
    			for (File file : files) {
    				if (file.getTotalSpace() > 0) {
    					File[] subFiles = file.listFiles();
    					for (File subFile : subFiles) {
    		    			if (subFile.isDirectory() && subFile.getName().endsWith(".pgm")) {
    		    				return true;
    		    			}
    		    		}
    				}
    			}
    		}
    	}
    	return false;
    }
    
    public static String getLatestPgmPathFromUdisk(String path) {
    	if ((path == null)
    			|| (path.length() < 13)
    			|| !path.substring(5).startsWith(Contants.UDISK_NAME_PREFIX)) {
    		return null;
    	}
    	File latestPgmFile = null;
    	File udisk = new File(path);
    	if (udisk.getTotalSpace() > 0) {
    		File[] files = udisk.listFiles();
    		for (File file : files) {
    			if (file.isDirectory() && file.getName().endsWith(".pgm")) {
    				if ((latestPgmFile == null)
    						|| (file.lastModified() > latestPgmFile.lastModified())) {
    					latestPgmFile = file;
    				}
    			}
    		}
    	} else {
    		File[] files = udisk.listFiles();
    		if (files != null) {
    			for (File file : files) {
    				if (file.getTotalSpace() > 0) {
    					File[] subFiles = file.listFiles();
    					for (File subFile : subFiles) {
    		    			if (subFile.isDirectory() && subFile.getName().endsWith(".pgm")) {
    		    				if ((latestPgmFile == null)
    		    						|| (subFile.lastModified() > latestPgmFile.lastModified())) {
    		    					latestPgmFile = subFile;
    		    				}
    		    			}
    		    		}
    				}
    			}
    		}
    	}
    	return (latestPgmFile == null) ? null : latestPgmFile.getAbsolutePath();
    }
    
    public static String getGifImagePath(String subDirName)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getProgramPath());
        sb.append(File.separator);
        sb.append("Gif");
        if (subDirName != null)
        {
            sb.append(File.separator);
            sb.append(subDirName);
        }
        if (!FileUtils.isExist(sb.toString()))
        {
            FileUtils.createDir(sb.toString());
        }
        
        return sb.toString();
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getSysParamFullPath()
    {
        if (mSysParamFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("sysparam");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            // 加上默认文件名
            mSysParamFullPath = sb.append(LOCAL_CONFIG_FILENAME).toString();
        }
        
        return mSysParamFullPath;
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getAPKUpdateFullPath()
    {
        if (mAPKUpdateFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("apkupdate");
            mAPKUpdateFullPath = sb.toString();
            
            if (!FileUtils.isExist(mAPKUpdateFullPath))
            {
                FileUtils.createDir(mAPKUpdateFullPath);
            }
        }
        
        return mAPKUpdateFullPath;
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getSysParamBackupFullPath()
    {
        if (mSysParamBackupFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("sysparambk");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            // 加上默认文件名
            mSysParamBackupFullPath = sb.append(LOCAL_CONFIG_FILENAME).toString();
        }
        
        return mSysParamBackupFullPath;
    }

    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getStandbyScreenImgPath()
    {
        if (mStandbyScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("bgImg");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            mStandbyScreenImgFullPath = sb.append("background.jpg").toString();
        }
        
        return mStandbyScreenImgFullPath;
    }
    
    /*
     * 获取开机画面系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getStartUpScreenImgPath()
    {
        if (mStartUpScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("starupImg");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            mStartUpScreenImgFullPath = sb.append("startup.jpg").toString();
        }
        return mStartUpScreenImgFullPath;
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getScreenCaptureImgPath()
    {
        if (mCaptureScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("cpImg");
            sb.append(File.separator);
            mCaptureScreenImgFullPath = sb.toString();
            
            // 创建目录
            if (!FileUtils.isExist(mCaptureScreenImgFullPath))
            {
                FileUtils.createDir(mCaptureScreenImgFullPath);
            }
        }
        
        return mCaptureScreenImgFullPath;
    }
    
    /*
     * 获取系统参数临时文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getTempFolderPath()
    {
        if (mTempFolderFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("tmp");
            sb.append(File.separator);
            mTempFolderFullPath = sb.toString();
            
            // 创建目录
            if (!FileUtils.isExist(mTempFolderFullPath))
            {
                FileUtils.createDir(mTempFolderFullPath);
            }
        }
        
        return mTempFolderFullPath;
    }

    /**
     * 获取日志文件存储路径
     * 
     * @param type
     *            0：播放日志 ;1：系统日志
     * @param date
     *            0:今天；1：昨天
     * @return 昨天的日志文件路径
     */
    public String getLogFileFullPath(int type, int date)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(FileUtils.getExternalStorage());
        sb.append(File.separator);
        sb.append("pgmlog");
        sb.append(File.separator);
        
        // 创建目录
        if (!FileUtils.isExist(sb.toString()))
        {
            FileUtils.createDir(sb.toString());
        }
        
        if (type == 0)
        {
            sb.append(PosterApplication.LOCAL_PLAY_LOGNAME);
        }
        else if (type == 1)
        {
            sb.append(PosterApplication.LOCAL_SYSTEM_LOGNAME);
        }
        sb.append(getEthMacStr()).append("_");
        
        if (date == 0)
        {
            sb.append(PosterApplication.getCurrentDate());
        }
        else if (date == 1)
        {
            sb.append(PosterApplication.getYesterdayDate());
        }
        sb.append(".log");
        
        return sb.toString();
    }
    
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static String getCpuId() {
		if (mCpuId == null) {
			if ((mCpuId = FileUtils.readCpuIdFromSysFile()) == null) {
				BufferedReader reader = null;
				String line = null;
				try {
					reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
					while ((line = reader.readLine()) != null) {
						String[] subStr = line.split(":");
						if (subStr[0].trim().equals("Serial")) {
							mCpuId = subStr[1].trim();
							FileUtils.writeCpuIdToSysFile(mCpuId);
							break;
						}
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (reader != null) {
						try {
							reader.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		return mCpuId;
	}
    
    public static String getLocalIpAddress()
    {
        try
        {
            String strIpAddress = null;
            NetworkInterface intf = null;
            if ((intf = NetworkInterface.getByName("eth0")) != null && (strIpAddress = getIpv4Address(intf)) != null)
            {
                return strIpAddress;
            }
            else if ((intf = NetworkInterface.getByName("wlan0")) != null && (strIpAddress = getIpv4Address(intf)) != null)
            {
                return strIpAddress;
            }
        }
        catch (SocketException ex)
        {
            logger.e("Get IpAddress has error, the msg is: " + ex.toString());
        }
        
        return "";
    }
    
    // 固定用网口的MAC地址做为与服务器通信的Device_ID
    public static synchronized byte[] getEthMacAddress()
    {
        if (mEthMac == null)
        {
            if ((mEthMac = FileUtils.readDevIdFromSysFile()) == null)
            {
                try
                {
                    NetworkInterface intf = null;
                    if ((intf = NetworkInterface.getByName("eth0")) != null)
                    {
                        mEthMac = intf.getHardwareAddress();
                        FileUtils.writeDevIdToSysFile(mEthMac);
                    }
                }
                catch (SocketException ex)
                {
                    logger.e("Get MacAddress has error, the msg is: " + ex.toString());
                }
            }
        }
        return mEthMac;
    }
    
    public static String getEthFormatMac()
    {
        byte[] mac = getEthMacAddress();
        if (mac != null)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++)
            {
                sb.append(String.format("%02x", mac[i]));
                if (i < 5)
                {
                    sb.append(":");
                }
            }
            return sb.toString();
        }
        return "";
    }
    
    /**
     * 获取mac
     * 
     * @return 数字格式mac
     */
    public static String getEthMacStr()
    {
        return getEthFormatMac().replace(":", "");
    }
    
    private static String getIpv4Address(NetworkInterface netIF)
    {
        for (Enumeration<InetAddress> enumIpAddr = netIF.getInetAddresses(); enumIpAddr.hasMoreElements();)
        {
            InetAddress inetAddress = enumIpAddr.nextElement();
            if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address))
            {
                return inetAddress.getHostAddress().toString();
            }
        }
        return null;
    }

    public boolean isNetworkConnected()
    {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isAvailable())
            {
                return info.isConnected();
            }
        }
        return false;
    }
    
    public boolean isNetReached(String strHostName)
    {
        boolean bRet = false;
        try
        {
            bRet = InetAddress.getByName(strHostName).isReachable(3000);
        }
        catch (UnknownHostException e)
        {
            bRet = false;
            logger.w(e.toString());
        }
        catch (IOException e)
        {
            bRet = false;
            logger.w(e.toString());
        }
        
        return bRet;
    }
    
    public boolean httpServerIsReady(String httpUri)
    {
    	HttpGet httpRequest = null;
        HttpResponse response = null;
        System.setProperty("http.keepAlive", "false");
        try
        {
            httpRequest = new HttpGet(httpUri);
            response = new DefaultHttpClient().execute(httpRequest);
        }
        catch (ClientProtocolException e)
        {
            response = null;
            logger.w(e.toString());
        }
        catch (IOException e)
        {
            response = null;
            logger.w(e.toString());
        }
        catch (Exception e)
        {
            response = null;
            logger.w(e.toString());
        }
        finally
        {
        	if (httpRequest != null)
        	{
        		httpRequest.abort();
        	}
        }
        
        if (response != null)
        {
            int code = response.getStatusLine().getStatusCode();
            return (HttpStatus.SC_OK == code);
        }
        
        return false;
    }
    
    /*
     * Determinate the time download is forbidden
     */
    public static boolean isBeForbidTime()
    {
        if (mSysParam != null && mSysParam.offdlTime != null)
        {
            int nweek = Calendar.getInstance(Locale.CHINA).get(Calendar.DAY_OF_WEEK) - 1;
            int oweekMask = (0x01 << nweek) & 0xFF; // 当天的星期数掩码
            int group = Integer.parseInt(mSysParam.offdlTime.get("group")); // 时间段的数目
            int sweek = 0;
            for (int i = 1; i < group + 1; i++)
            {
                sweek = Integer.parseInt(mSysParam.offdlTime.get("week" + i)); // 该组时间段属于的星期数
                if ((sweek & oweekMask) == oweekMask && 
                     mSysParam.offdlTime.get("on_time" + i) != null &&
                     beforeCurrentTime(new String(mSysParam.offdlTime.get("on_time" + i))) &&
                     mSysParam.offdlTime.get("off_time" + i) != null &&
                     afterCurrentTime(new String(mSysParam.offdlTime.get("off_time" + i))))
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public void setTimeZone(String tz) {
    	Pattern p = Pattern.compile("^(-?)(\\d{1,2})$");
    	Matcher m = p.matcher(tz);
    	
    	if (m.find()) {
    		StringBuilder sb = new StringBuilder();
    		sb.append("GMT");
    		char firstChar = tz.charAt(0);
    		if (firstChar == '-') {
    			sb.append("+").append(m.group(2));
    		} else if (firstChar != '0') {
    			sb.append("-").append(m.group(2));
    		}
    		mAlarmManager.setTimeZone(sb.toString());
    	}
    }
    
    public void setTime(String time) {
    	Pattern p = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})$");
    	Matcher m = p.matcher(time);
    	
    	if (m.find()) {
    		String tzStr = (mSysParam.timeZonevalue != null) ? new String(mSysParam.timeZonevalue) : "-8";
    		int diffHour = -1;
    		char sign = tzStr.charAt(0);
    		if (sign != '-') {
    			sign = '+';
    			diffHour = Integer.parseInt(tzStr)*2;
    		} else {
    			sign = '-';
    			diffHour = Integer.parseInt(tzStr.substring(1))*2;
    		}
    		
    		Time t = new Time();
    		int year = Integer.parseInt(m.group(1));
    		int month = Integer.parseInt(m.group(2));
    		int day = Integer.parseInt(m.group(3));
    		int hour = Integer.parseInt(m.group(4));
    		int minute = Integer.parseInt(m.group(5));
    		int second = Integer.parseInt(m.group(6));
    		if (sign == '-') {
    			t.set(second, minute, hour-diffHour, day, month-1, year);
    		} else {
    			t.set(second, minute, hour+diffHour, day, month-1, year);
    		}
    		t.normalize(true);
    		
    		StringBuilder sb = new StringBuilder();
    		sb.append("date -s ").append(String.format("%d%02d%02d.%02d%02d%02d", t.year, t.month+1, t.monthDay,
                    t.hour, t.minute, t.second));
            RuntimeExec.getInstance().runRootCmd(sb.toString());
    	}
    }
    
    /*
     * 设置屏幕亮度
     */
    public static void setScreenBright(int bright)
    {
        Settings.System.putInt(PosterMainActivity.INSTANCE.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);// 设为手动调节亮度
        Uri uri = android.provider.Settings.System.getUriFor("color_brightness");
        android.provider.Settings.System.putInt(PosterMainActivity.INSTANCE.getContentResolver(), "color_brightness", (int) (bright * 2.55));
        PosterMainActivity.INSTANCE.getContentResolver().notifyChange(uri, null);
        logger.i("亮度设置成功，亮度大小为" + bright);
    }
    
    /*
     * 设置音量
     */
    public void setDeviceVol(int vol)
    {
        AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float factor = (float) maxVolume / 100;
        float volume = vol * factor;
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) volume, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND);
        logger.i("音量设置成功，音量大小为" + vol);
    }
    
    /*
     * 删除过期文件
     */
    @SuppressLint("DefaultLocale")
    public void deleteExpiredFiles()
    {
        logger.i("开始清理播出文件");
        HashMap<String, String> normalFileList = ScreenManager.getInstance().getCurrentNormalFilelist();
        HashMap<String, String> urgentFileList = ScreenManager.getInstance().getCurrentEmergencyFilelist();
        HashMap<String, String> fileNameList = new HashMap<String, String>();
        if (FileUtils.getFileList(fileNameList, getProgramPath(), true) == true)
        {
            for (String filename : fileNameList.keySet())
            {
                logger.i("文件系统中的文件名：" + filename);
                if (normalFileList.containsKey(filename))
                {
                    logger.i("文件：" + filename + "，本地地址：" + fileNameList.get(filename));
                    logger.i("文件：" + filename + "属于普通节目列表" + normalFileList.get(filename));
                    if (fileNameList.get(filename).toLowerCase().equals(normalFileList.get(filename).toLowerCase()))
                    {
                        continue;
                    }
                }
                else if (urgentFileList.containsKey(filename))
                {
                    logger.i("文件：" + filename + "，本地地址：" + fileNameList.get(filename));
                    logger.i("文件：" + filename + "属于紧急节目列表" + urgentFileList.get(filename));
                    if (fileNameList.get(filename).toLowerCase().equals(urgentFileList.get(filename).toLowerCase()))
                    {
                        continue;
                    }
                }
                
                if (!filename.endsWith(".xml"))
                {
                    File file = new File(fileNameList.get(filename));
                    if (FileUtils.delFile(file))
                        logger.i("过期文件：" + filename + "已经被删除！");
                    else
                        logger.i("过期文件：" + filename + "删除失败！");
                }
            }
        }
    }
    
    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    
    public static int dip2px(Context context, int dpValue)
    {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
    
    public static int px2dip(Context context, int pxValue)
    {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }
    
    public static int stringHexToInt(String sring)
    { // 12 ->0x12
        if (sring == null || sring.length() <= 0)
        {
            return 0;
        }
        
        int resut = 0;
        String src = sring.substring(2);
        int len = src.length();
        for (int i = 1; i < (len + 1); i++)
        {
            if ((src.charAt(len - i) >= '0') && (src.charAt(len - i) <= '9'))
            {
                resut += (src.charAt(len - i) - 0x30) << (4 * (i - 1));
            }
            else if ((src.charAt(len - i) >= 'a') && (src.charAt(len - i) <= 'f'))
            {
                resut += (src.charAt(len - i) - 'a' + 0x0A) << (4 * (i - 1));
            }
            else if ((src.charAt(len - i) >= 'A') && (src.charAt(len - i) <= 'F'))
            {
                resut += (src.charAt(len - i) - 'A' + 0x0A) << (4 * (i - 1));
            }
        }
        
        return resut;
    }
    
    @SuppressLint("InlinedApi")
    public static void setSystemBarVisible(Context context, boolean visible)
    {
        if (context instanceof Activity)
        {
            int flag = ((Activity) context).getWindow().getDecorView().getSystemUiVisibility(); // 获取当前SystemUI显示状态
            int fullScreenFlag = 0x00000008; // platform的源码里面新增的常量SYSTEM_UI_FLAG_SHOW_FULLSCREEN.
            
            if (visible) // 显示系统栏
            {
                if ((flag & fullScreenFlag) != 0)
                {
                    ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }
            else
            {
                // 隐藏系统栏
                if ((flag & fullScreenFlag) == 0)
                {
                    ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(flag | fullScreenFlag);
                }
            }
        }
    }
    
    public static void startApplication(Context context, String appPackageName)
    {
        Intent appStartIntent = context.getPackageManager().getLaunchIntentForPackage(appPackageName);
        if (null != appStartIntent)
        {
            context.startActivity(appStartIntent);
        }
        else
        {
            logger.i(appPackageName + " application didn't found.");
        }
    }
    
    public Bitmap loadPicture(String picFileName, int nPicWidth, int nPicHeight)
    {
        if (!FileUtils.isExist(picFileName))
        {
            return null;
        }
        
        Bitmap srcBmp = null;
        
        try
        {
            // Create the Stream
            InputStream isImgBuff = new FileInputStream(picFileName);
            
            try
            {
                // Set the options for BitmapFactory
                if (isImgBuff != null)
                {
                    MediaInfoRef picInfo = new MediaInfoRef();
                    picInfo.filePath = picFileName;
                    picInfo.containerwidth = nPicWidth;
                    picInfo.containerheight = nPicHeight;
                    srcBmp = BitmapFactory.decodeStream(isImgBuff, null, PosterBaseView.setBitmapOption(picInfo));
                }
            }
            catch (java.lang.OutOfMemoryError e)
            {
                logger.e("background picture is too big, out of memory!");
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
        
        return srcBmp;
    }

    public Bitmap combineScreenCap(Bitmap bitmap)
    {
        ProgramFragment pf = null;
        
        if (UrgentPlayerActivity.INSTANCE != null) {
        	pf = (ProgramFragment) (UrgentPlayerActivity.INSTANCE.getFragmentManager()
        			.findFragmentByTag(ProgramFragment.URGENT_FRAGMENT_TAG));
        } else if (PosterMainActivity.INSTANCE != null) {
        	pf = (ProgramFragment) (PosterMainActivity.INSTANCE.getFragmentManager()
        			.findFragmentByTag(ProgramFragment.NORMAL_FRAGMENT_TAG));
        }
        
        if (pf != null)
        {
            return pf.combineScreenCap(bitmap);
        }
        return bitmap;
    }

    public SysOnOffTimeInfo[] getSysOnOffTime() {
    	SysOnOffTimeInfo[] info = null;
    	
    	synchronized (mSysParamLock) {
    		if ((mSysParam != null) && (mSysParam.onOffTime != null)) {
		    	int group = Integer.parseInt(mSysParam.onOffTime.get("group"));
		    	if (group != 0) {
		    		info = new SysOnOffTimeInfo[group];
		    		String[] timearray = null;
		    		for (int i = 0; i < group; i++) {
		    			info[i] = new SysOnOffTimeInfo();
						info[i].week = Integer.parseInt(mSysParam.onOffTime.get("week"+(i+1)));
						timearray = mSysParam.onOffTime.get("on_time"+(i+1)).split(":");
		    			info[i].onhour = Integer.parseInt(timearray[0]);
		    			info[i].onminute = Integer.parseInt(timearray[1]);
		    			info[i].onsecond = Integer.parseInt(timearray[2]);
		    			timearray = mSysParam.onOffTime.get("off_time"+(i+1)).split(":");
		    			info[i].offhour = Integer.parseInt(timearray[0]);
		    			info[i].offminute = Integer.parseInt(timearray[1]);
		    			info[i].offsecond = Integer.parseInt(timearray[2]);
		    		}
		    	}
    		}
    	}
    	
    	return info;
    }

    /**
     * 获取当前时间
     * 
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String getCurrentTime()
    {
        Time time = new Time();
        time.setToNow();
        StringBuilder sb = new StringBuilder();
        sb.append(time.year).append("-");
        sb.append(time.month + 1).append("-");
        sb.append(time.monthDay);
        sb.append(" ");
        sb.append(time.hour).append(":");
        sb.append((time.minute < 10) ? ("0" + time.minute) : time.minute).append(":");
        sb.append((time.second < 10) ? ("0" + time.second) : time.second);
        return sb.toString();
    }
    
    /**
     * 获取当前日期
     * 
     * @return yyyymmdd
     */
    public static String getCurrentDate()
    {
        Time time = new Time();
        time.setToNow();
        StringBuilder sb = new StringBuilder();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append(time.monthDay < 10 ? ("0" + time.monthDay) : time.monthDay);
        return sb.toString();
    }
    
    /**
     * 获取昨天的日期
     * 
     * @return yyyymmdd
     */
    public static String getYesterdayDate()
    {
        Time time = new Time();
        time.set(System.currentTimeMillis() - 24 * 3600 * 1000);
        StringBuilder sb = new StringBuilder();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append(time.monthDay < 10 ? ("0" + time.monthDay) : time.monthDay);
        return sb.toString();
    }

    public void initLanguage()
    {
        int languagesetnum = mSysParam.osdLangSetosd_lang;
        Resources resources = getResources();// 获得res资源对象
        Configuration config = resources.getConfiguration();// 获得设置对象
        DisplayMetrics dm = resources.getDisplayMetrics();// 获得屏幕参数：主要是分辨率，像素等。
        
        switch (languagesetnum)
        {
        case 0:
            if (config.locale != Locale.SIMPLIFIED_CHINESE)
            {
                config.locale = Locale.SIMPLIFIED_CHINESE;
                resources.updateConfiguration(config, dm);
            }
            break;
        case 1:
            if (config.locale != Locale.US)
            {
                config.locale = Locale.US;
                resources.updateConfiguration(config, dm);
            }
            break;
        default:
            break;
        }
    }
    
    public void cancelTimingDel()
    {
        if (mDelPeriodFileTimer != null)
        {
            mDelPeriodFileTimer.cancel();
            mDelPeriodFileTimer = null;
        }
    }
    
    public void startTimingDel()
    {
        cancelTimingDel();
        int deltime = mSysParam.delFilePeriodtime;
        if (deltime > 0)
        {
            mDelPeriodFileTimer = new Timer();
            TimerTask task = new TimerTask()
            {
                @Override
                public void run()
                {
                    FileUtils.deleteTimeOutFile();
                }
            };
            mDelPeriodFileTimer.schedule(task, 12000, 24 * 60 * 60 * 1000);
        }
    }
    
    public void cancelTimingUploadLog()
    {
        if (mUploadLogTimer != null)
        {
            mUploadLogTimer.cancel();
            mUploadLogTimer = null;
        }
    }
    
    public void startTimingUploadLog()
    {
        cancelTimingUploadLog();
        mUploadLogTimer = new Timer();
        TimerTask task = new TimerTask()
        {
            @Override
            public void run()
            {
                String plocalpath = PosterApplication.getInstance().getLogFileFullPath(0, 1);
                if (plocalpath == null || !FileUtils.isExist(plocalpath))
                {
                    WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_CPEPLAYLOGFTPUP, 0, 1, "");
                }
                else
                {
                    // 启动FTP上传文件列表
                    FtpFileInfo upldFile = new FtpFileInfo();
                    upldFile.setRemotePath("/logs");
                    upldFile.setLocalPath(plocalpath);
                    List<FtpFileInfo> uploadlist = new ArrayList<FtpFileInfo>();
                    uploadlist.add(upldFile);
                    FtpHelper.getInstance().uploadFileList(uploadlist, new FtpOperationInterface()
                    {
                        @Override
                        public void started(String file, long size)
                        {
                        }
                        
                        @Override
                        public void aborted()
                        {
                        }
                        
                        @Override
                        public void progress(long length)
                        {
                        }
                        
                        @Override
                        public void completed()
                        {
                            String slogname = PosterApplication.getInstance().getLogFileFullPath(0, 1);
                            StringBuilder sbr = new StringBuilder();
                            sbr.append("<FILE>/logs/");
                            sbr.append(slogname);
                            sbr.append("</FILE><VERCODE>0</VERCODE><SIZE>");
                            sbr.append(FileUtils.getFileLength(slogname));
                            sbr.append("</SIZE><TYPE>2</TYPE>");
                            WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_CPEPLAYLOGFTPUP, 0, 0, sbr.toString());
                        }
                        
                        @Override
                        public void failed()
                        {
                        }
                    });
                }
            }
        };
        mUploadLogTimer.schedule(task, 60000, 24 * 60 * 60 * 1000);
    }
    
    /**
     * Returns true if the time represented by this Time object occurs before current time.
     * 
     * @param t
     *            that a given Time object to compare against (Format is "HH:MM:SS")
     * @return true if the given time is less than current time
     */
    public static boolean beforeCurrentTime(String t)
    {
        StringBuilder sb = new StringBuilder();
        Time time = new Time();
        time.setToNow();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
        sb.append("T");
        sb.append(t.replace(":", ""));
        time.parse(sb.toString());
        
        return (time.toMillis(false) <= System.currentTimeMillis());
    }
    
    /**
     * Returns true if the time represented by this Time object occurs after current time.
     * 
     * @param t
     *            that a given Time object to compare against (Format is "HH:MM:SS")
     * @return true if the given time is greater than current time
     */
    public static boolean afterCurrentTime(String t)
    {
        StringBuilder sb = new StringBuilder();
        Time time = new Time();
        time.setToNow();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
        sb.append("T");
        sb.append(t.replace(":", ""));
        time.parse(sb.toString());
        
        return (time.toMillis(false) >= System.currentTimeMillis());
    }
    
    /**
     * Compare two Time objects and return a negative number if t1 is less than t2, a positive number if t1 is greater
     * than t2, or 0 if they are equal.
     * 
     * @param t1
     *            first {@code Time} instance to compare (Format is "hh:mm:ss")
     * @param t2
     *            second {@code Time} instance to compare (Format is "hh:mm:ss")
     * @return 0:= >0：> <0:<
     */
    public static int compareTwoTime(String t1, String t2)
    {
        String strTime1[] = t1.split(":");
        String strTime2[] = t2.split(":");
        if (strTime1.length < 3 || strTime2.length < 3)
        {
            logger.i("The given time format is invaild.");
            return -1;
        }
        Time currentTime = new Time();
        currentTime.setToNow();
        
        Time time1 = new Time();
        time1.set(Integer.parseInt(strTime1[2]), Integer.parseInt(strTime1[1]), Integer.parseInt(strTime1[0]), currentTime.monthDay, currentTime.month, currentTime.year);
        
        Time time2 = new Time();
        time2.set(Integer.parseInt(strTime2[2]), Integer.parseInt(strTime2[1]), Integer.parseInt(strTime2[0]), currentTime.monthDay, currentTime.month, currentTime.year);
        
        return Time.compare(time1, time2);
    }
    
    public static long getTimeMillis(String time)
    {
        StringBuilder sb = new StringBuilder();
        Time t = new Time();
        t.setToNow();
        sb.append(t.year);
        sb.append(((t.month + 1) < 10) ? ("0" + (t.month + 1)) : (t.month + 1));
        sb.append((t.monthDay < 10) ? ("0" + t.monthDay) : t.monthDay);
        sb.append("T");
        sb.append(time.replace(":", ""));
        t.parse(sb.toString());
        return t.toMillis(false);
    }
    
    public int getHwRotation() {
		Object hwRotation = ReflectionUtils.invokeStaticMethod(
				SYSPROP_HWROTATION_CLASS, SYSPROP_HWROTATION_GETMETHOD, new Object[] {
				SYSPROP_HWROTATION, SYSPROP_HWROTATION_DEFAULT}, new Class[] {String.class, int.class});
		if (hwRotation != null) {
			return ((Integer)hwRotation).intValue();
		}
		return -1;
	}
}

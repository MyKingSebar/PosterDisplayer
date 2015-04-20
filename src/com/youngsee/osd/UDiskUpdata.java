/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.youngsee.common.FileUtils;
import com.youngsee.common.Logger;
import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.screenmanager.ScreenManager;

@SuppressLint({ "SdCardPath", "DefaultLocale" })
public class UDiskUpdata
{
    private final static String USB_COMMON_NAME            = "usbhost";
    private final static Logger logger                     = new Logger();
    
    private Context             mContext                   = null;
    private ProgressDialog      mProgressDlg               = null;
    
    // Define message ID
    private final static int    PROGRAM_UPDATING           = 0x6000;
    private final static int    PROGRAM_UPDATE_FAILED      = 0x6001;
    private final static int    PROGRAM_UPDATE_SUCCESS     = 0x6002;
    private final static int    STARTUP_IMG_UPDATE_SUCCESS = 0x6003;
    private final static int    STARTUP_IMG_UPDATE_FAILED  = 0x6004;
    private final static int    STANDBY_IMG_UPDATE_SUCCESS = 0x6005;
    private final static int    STANDBY_IMG_UPDATE_FAILED  = 0x6006;    
    private final static int    APK_SW_UPDATE_FAILED  = 0x6008;
    public UDiskUpdata(Context context)
    {
        mContext = context;
        mProgressDlg = new ProgressDialog(mContext);
        mProgressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER); // 设置进度条风格，风格为圆形，旋转的
        mProgressDlg.setTitle(R.string.tools_dialog_u_disk_update_titles); // 设置ProgressDialog 标题
        mProgressDlg.setMessage(mContext.getString(R.string.tools_dialog_u_disk_update_message)); // 设置ProgressDialog
                                                                                                  // 提示信息
        mProgressDlg.setIndeterminate(false);  // 设置ProgressDialog 的进度条是否不明确
        mProgressDlg.setCancelable(false);  // 设置ProgressDialog 是否可以按退回按键取消
    }
    
    public void updateProgram()
    {
        new UDiskUpdateThread().start();
    }
    
    /*
     * 更新开机画面
     */
    public void updateStartupPic()
    {
        String strFileSavePath = PosterApplication.getStartUpScreenImgPath();
        updateImgFromUDisk("startup.jpg", strFileSavePath);
    }
    
    /*
     * 更新待机画面
     */
    public void updateStandbyPic()
    {
        String strFileSavePath = PosterApplication.getStandbyScreenImgPath();
        updateImgFromUDisk("background.jpg", strFileSavePath);
    }
    
    private String findSWPath() {
    	String strExtStorageRoot = Environment.getExternalStorageDirectory().getParent();
    	if (strExtStorageRoot != null) {
    		File extFilePath = new File(strExtStorageRoot);
    		File[] extFiles = extFilePath.listFiles();
    		if (extFiles != null) {
	    		for (File extFile : extFiles) {
	    			if (extFile.isDirectory() && extFile.getName().contains(USB_COMMON_NAME)) {
	    				if (extFile.getTotalSpace() > 0) {
	    					File[] usbFiles = extFile.listFiles();
	    					if (usbFiles != null) {
		        				for (File usbFile : usbFiles) {
		        					if (usbFile.isFile()
		        							&& usbFile.getName().startsWith("YSPosterDisplayer")
	            							&& "apk".equalsIgnoreCase(FileUtils
	            							.getFileExtensionName(usbFile.getName()))) {
		        						return usbFile.getAbsolutePath();
		        					}
		        				}
	    					}
	    				} else {
	    					File[] extSubFiles = extFile.listFiles();
	    					if (extSubFiles != null) {
		    					for (File extSubFile : extSubFiles) {
		    						File[] subUsbFiles = extSubFile.listFiles();
		    						if (subUsbFiles != null) {
			            				for (File subUsbFile : subUsbFiles) {
			            					if (subUsbFile.isFile()
			            							&& subUsbFile.getName().startsWith("YSPosterDisplayer")
			            							&& "apk".equalsIgnoreCase(FileUtils
			    	            							.getFileExtensionName(subUsbFile.getName()))) {
			            						return subUsbFile.getAbsolutePath();
			            					}
			            				}
		    						}
		    					}
	    					}
	    				}
	    			}
	    		}
    		}
        }
    	return null;
    }
    
    public void updateSW() {    	
        String strSWPath = findSWPath();
        if (strSWPath != null) {
        	Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(strSWPath)),
                    "application/vnd.android.package-archive");
            mContext.startActivity(intent);
        } else {
        	mHandler.sendEmptyMessage(APK_SW_UPDATE_FAILED);
        }
    }
    
    private void updateImgFromUDisk(String imgname, String strFileSavePath)
    {
        List<File> listPgmFile = new ArrayList<File>();
        String strExtStorageRoot = Environment.getExternalStorageDirectory().getParent();
        if (strExtStorageRoot == null)
        {
            if (imgname.equals("startup.jpg"))
            {
                mHandler.sendEmptyMessage(STARTUP_IMG_UPDATE_FAILED);
            }
            else
            {
                mHandler.sendEmptyMessage(STANDBY_IMG_UPDATE_FAILED);
            }
        }
        
        try
        {
            File[] extDirs = new File(strExtStorageRoot).listFiles();
            for (int i = 0; i < extDirs.length; i++)
            {
                if (extDirs[i].isDirectory() && extDirs[i].getUsableSpace() > 0
                        && extDirs[i].getName().contains(USB_COMMON_NAME))
                {
                    for (File f : extDirs[i].listFiles())
                    {
                        if (f.getName().trim().toLowerCase().endsWith(imgname))
                        {
                            FileUtils.copyFileTo(f, new File(strFileSavePath));
                            listPgmFile.add(f);
                            if (imgname.equals("startup.jpg"))
                            {
                                mHandler.sendEmptyMessage(STARTUP_IMG_UPDATE_SUCCESS);
                            }
                            else
                            {
                                mHandler.sendEmptyMessage(STANDBY_IMG_UPDATE_SUCCESS);
                            }
                        }
                    }
                }
            }
            if (listPgmFile.size() == 0)
            {
                if (imgname.equals("startup.jpg"))
                {
                    mHandler.sendEmptyMessage(STARTUP_IMG_UPDATE_FAILED);
                }
                else
                {
                    mHandler.sendEmptyMessage(STANDBY_IMG_UPDATE_FAILED);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private final class UDiskUpdateThread extends Thread
    {
        @Override
        public void run()
        {
            mHandler.sendEmptyMessage(PROGRAM_UPDATING);
            
            if (updatePgm())
            {
                ScreenManager.getInstance().osdNotify(ScreenManager.UPDATE_PGM_OPERATE);
                mHandler.sendEmptyMessage(PROGRAM_UPDATE_SUCCESS);
            }
            else
            {
                mHandler.sendEmptyMessage(PROGRAM_UPDATE_FAILED);
            }
        }
        
        // 遍历所有U盘中.pgm文件夹
        private List<File> getUsbProgramList()
        {
            List<File> listPgmFile = new ArrayList<File>();
            String strExtStorageRoot = Environment.getExternalStorageDirectory().getParent();
            
            if (strExtStorageRoot == null)
            {
                return null;
            }
            File[] extDirs = new File(strExtStorageRoot).listFiles();
            for (int i = 0; i < extDirs.length; i++)
            {
                if (extDirs[i].isDirectory()
                        && extDirs[i].getName().contains(USB_COMMON_NAME))
                {
                	if (extDirs[i].getUsableSpace() > 0)
                	{
	                    for (File f : extDirs[i].listFiles())
	                    {
	                        if (f.getName().trim().toLowerCase().endsWith(".pgm"))
	                        {
	                            listPgmFile.add(f);
	                        }
	                    }
                	} else {
                		File[] subDirs = extDirs[i].listFiles();
                		if (subDirs != null)
                		{
                			for (File subDir : subDirs)
                			{
                				if (subDir.getTotalSpace() > 0)
                				{
                					File[] subFiles = subDir.listFiles();
                					for (File subFile : subFiles) {
                						if (subFile.getName().trim().toLowerCase().endsWith(".pgm"))
            	                        {
            	                            listPgmFile.add(subFile);
            	                        }
                					}
                				}
                			}
                		}
                	}
                }
            }
            
            return listPgmFile;
        }
        
        // 获取U盘上最近更新的节目
        private File findNewestPgm()
        {
            List<File> pgmList = getUsbProgramList();
            if (pgmList == null || pgmList.size() <= 0)
            {
                logger.i("Didn't found program in th Usb.");
                return null;
            }
            
            File newestFile = pgmList.get(0);
            for (int i = 1; i < pgmList.size(); i++)
            {
                if (pgmList.get(i).lastModified() > newestFile.lastModified())
                {
                    newestFile = pgmList.get(i);
                }
            }
            
            return newestFile;
        }
        
        // 将最近更新的节目.pgm格式的文件夹内容复制到目标磁盘上
        private boolean updatePgm()
        {
            boolean retValue = false;
            File newestFile = findNewestPgm();
            if (newestFile != null)
            {
                try
                {
                    String destDir = PosterApplication.getProgramPath();
                    retValue = FileUtils.copyDirFilesTo(newestFile.getAbsolutePath(), destDir);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    retValue = false;
                }
            }
            return retValue;
        }
    }
    
    @SuppressLint({ "HandlerLeak", "ShowToast" })
    final Handler mHandler = new Handler() {
                               @Override
                               public void handleMessage(Message msg)
                               {
                                   switch (msg.what)
                                   {
                                   case PROGRAM_UPDATING:
                                       if (mProgressDlg != null && !mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.show();
                                       }
                                       return;
                                       
                                   case PROGRAM_UPDATE_SUCCESS:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_u_disk_update_success)
                                               .setPositiveButton(R.string.enter,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                       
                                   case PROGRAM_UPDATE_FAILED:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_u_disk_update_failure)
                                               .setPositiveButton(R.string.retry,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                               updateProgram();
                                                           }
                                                       })
                                               .setNegativeButton(R.string.cancel,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   case STARTUP_IMG_UPDATE_FAILED:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_boot_update_failure)
                                               .setPositiveButton(R.string.retry,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                               updateStartupPic();
                                                           }
                                                       })
                                               .setNegativeButton(R.string.cancel,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   case STARTUP_IMG_UPDATE_SUCCESS:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_boot_update_success)
                                               .setPositiveButton(R.string.enter,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   case STANDBY_IMG_UPDATE_FAILED:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_standby_update_failure)
                                               .setPositiveButton(R.string.retry,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                               updateStandbyPic();
                                                           }
                                                       })
                                               .setNegativeButton(R.string.cancel,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   case STANDBY_IMG_UPDATE_SUCCESS:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_standby_update_success)
                                               .setPositiveButton(R.string.enter,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   case APK_SW_UPDATE_FAILED:
                                       if (mProgressDlg != null && mProgressDlg.isShowing())
                                       {
                                           mProgressDlg.dismiss();
                                       }
                                       
                                       new AlertDialog.Builder(mContext)
                                               .setTitle(R.string.tools_dialog_sw_update_failure)
                                               .setPositiveButton(R.string.retry,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                               updateSW();
                                                           }
                                                       })
                                               .setNegativeButton(R.string.cancel,
                                                       new DialogInterface.OnClickListener() {
                                                           @Override
                                                           public void onClick(DialogInterface dialog, int which)
                                                           {
                                                           }
                                                       }).show();
                                       return;
                                   }
                                   
                                   super.handleMessage(msg);
                               }
                           };
}

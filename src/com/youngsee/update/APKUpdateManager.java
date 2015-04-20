/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author CaiJian
 */

package com.youngsee.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.youngsee.common.FileUtils;
import com.youngsee.common.Md5;
import com.youngsee.ftpoperation.FtpFileInfo;
import com.youngsee.ftpoperation.FtpHelper;
import com.youngsee.ftpoperation.FtpOperationInterface;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.PosterMainActivity;
import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.UrgentPlayerActivity;
import com.youngsee.webservices.WsClient;
import com.youngsee.webservices.XmlCmdInfoRef;
import com.youngsee.webservices.XmlParser;

public class APKUpdateManager {
	private static APKUpdateManager INSTANCE = null;
	
	private final int EVENT_LAUNCH_DLPROGRESSBAR = 0;
	private final int EVENT_UPDATE_DLPROGRESSBAR_ONSTARTED = 1;
	private final int EVENT_UPDATE_DLPROGRESSBAR_ONPROGRESS = 2;
	private final int EVENT_CANCEL_DLPROGRESSBAR = 3;
	
	private final long DEFAULT_AUTO_DETECT_PERIOD = 10*60*1000;
	
	private final int SUCCESS = 0;
    private final int FAIL = 1;
	
	private boolean mIsInProgress = false;
	private boolean mShowProgressBar = false;
	
	Thread mDlThread = null;
	private ProgressDialog mDlProgressBar = null;
	private long mDlFileSize = 0;
	private String mLocalDlFile = null;
	
	private HandlerThread mHandlerThread = null;
	private MyHandler mHandler = null;
	
	private AutoDetectorThread mAutoDetectorThread = null;
	Thread mAutoDetectorDlThread = null;
	
	public static APKUpdateManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new APKUpdateManager();
		}
		return INSTANCE;
	}
	
	private APKUpdateManager() {
		mHandlerThread = new HandlerThread("apkupdatemgr_ui_thread");
		mHandlerThread.start();
		mHandler = new MyHandler(mHandlerThread.getLooper());
	}
	
	public void destroy() {
		if (mAutoDetectorThread != null) {
			mAutoDetectorThread.interrupt();
			mAutoDetectorThread = null;
		}
		if (mAutoDetectorDlThread != null) {
			FtpHelper.getInstance()
					.cancelDownload(mAutoDetectorDlThread);
			mAutoDetectorDlThread = null;
		}
		if (mDlThread != null) {
			FtpHelper.getInstance()
					.cancelDownload(mDlThread);
			mDlThread = null;
		}
		if (mHandler != null) {
			mHandler.removeMessages(EVENT_LAUNCH_DLPROGRESSBAR);
			mHandler.removeMessages(EVENT_UPDATE_DLPROGRESSBAR_ONSTARTED);
			mHandler.removeMessages(EVENT_UPDATE_DLPROGRESSBAR_ONPROGRESS);
			mHandler.removeMessages(EVENT_CANCEL_DLPROGRESSBAR);
			mHandler = null;
		}
		if (mHandlerThread != null) {
			mHandlerThread.getLooper().quit();
			mHandlerThread = null;
		}
		if ((mLocalDlFile != null) && FileUtils.isExist(mLocalDlFile)) {
			FileUtils.delFile(new File(mLocalDlFile));
			mLocalDlFile = null;
		}
		if (mDlProgressBar != null) {
			mDlProgressBar.dismiss();
			mDlProgressBar = null;
		}
		setInProgress(false);
	}
	
	public boolean isInProgress() {
		synchronized (this) {
			return mIsInProgress;
		}
    }
	
	private void setInProgress(boolean value) {
		synchronized (this) {
			mIsInProgress = value;
		}
	}
	
	private final class AutoDetectorThread extends Thread {
		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					if (!isInProgress()) {
						checkAndUpdateApk();
					}
					Thread.sleep(DEFAULT_AUTO_DETECT_PERIOD);
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private long getApkVersionFromStr(String str) {
    	Pattern pattern = Pattern.compile("[1-9]?\\d{1}\\.[1-9]?\\d{1}\\.[1-9]?\\d{1}\\.[1-9]?\\d{1}");
    	Matcher matcher = pattern.matcher(str);
    	if (matcher.find()) {
    		return Long.parseLong(matcher.group().replaceAll("\\.", ""));
    	}
    	return 0;
    }
	
	private void checkAndUpdateApk() {
		String[] remoteXmlFileArray = FtpHelper.getInstance()
				.getRemoteFilesName("/0/soft/android/DMA_YS*.xml");
		long currentVersion = getApkVersionFromStr(
				PosterApplication.getInstance().getVerName());
		String latestXml = null;
		long latestVersion = 0;
		if (remoteXmlFileArray != null) {
			long xmlFileVersion = 0;
			for (String xmlFile : remoteXmlFileArray) {
				xmlFileVersion = getApkVersionFromStr(xmlFile);
				if (xmlFileVersion > latestVersion) {
					latestVersion = xmlFileVersion;
					latestXml = (new StringBuilder()).append("/0/soft/android/")
							.append(xmlFile).toString();
				}
			}
			if ((latestVersion != 0) && (latestVersion > currentVersion)) {
				startAutoDetectUpdate(latestXml);
			}
		}
	}
	
	public void startAutoDetector() {
		if (mAutoDetectorThread == null) {
			mAutoDetectorThread = new AutoDetectorThread();
			mAutoDetectorThread.start();
		}
	}
	
	public void stopAutoDetector() {
		if (mAutoDetectorThread != null) {
			mAutoDetectorThread.interrupt();
			mAutoDetectorThread = null;
		}
		if (mAutoDetectorDlThread != null) {
			FtpHelper.getInstance()
					.cancelDownload(mAutoDetectorDlThread);
			mAutoDetectorDlThread = null;
			
			mHandler.removeMessages(EVENT_LAUNCH_DLPROGRESSBAR);
			mHandler.removeMessages(EVENT_UPDATE_DLPROGRESSBAR_ONSTARTED);
			mHandler.removeMessages(EVENT_UPDATE_DLPROGRESSBAR_ONPROGRESS);
			mHandler.removeMessages(EVENT_CANCEL_DLPROGRESSBAR);
			
			if ((mLocalDlFile != null) && FileUtils.isExist(mLocalDlFile)) {
				FileUtils.delFile(new File(mLocalDlFile));
				mLocalDlFile = null;
			}
			if (mDlProgressBar != null) {
				mDlProgressBar.dismiss();
				mDlProgressBar = null;
			}
			
			setInProgress(false);
		}
		
	}
	
	private void startAutoDetectUpdate(String xmlFile) {
		synchronized (this) {
			if (mIsInProgress) {
				return;
			} else {
				mIsInProgress = true;
			}
		}
		
		String apkUpdateFullPath = PosterApplication.getAPKUpdateFullPath();
		final String xmlLocalPath = (new StringBuilder()).append(apkUpdateFullPath)
				.append(File.separator).append(FileUtils.getFilename(xmlFile)).toString();

		delAllFilesByExt(apkUpdateFullPath,
				FileUtils.getFileExtensionName(xmlLocalPath));

		sendMsgToLaunchDlProgressBar();

		FtpFileInfo toDownloadFile = new FtpFileInfo();
		toDownloadFile.setRemotePath(xmlFile);
		toDownloadFile.setLocalPath(apkUpdateFullPath);
		List<FtpFileInfo> toDownloadList = new ArrayList<FtpFileInfo>();
		toDownloadList.add(toDownloadFile);

		mAutoDetectorDlThread = FtpHelper.getInstance().downloadFileList(toDownloadList,
				new FtpOperationInterface() {
					@Override
					public void started(String file, long size) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnStarted(
								FileUtils.getFilename(file), size);
					}

					@Override
					public void aborted() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						setInProgress(false);
					}

					@Override
					public void progress(long length) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnProgress(length);
					}

					@Override
					public void completed() {
						// TODO Auto-generated method stub
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						handleAutoDetectXml(xmlLocalPath);
					}

					@Override
					public void failed() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						setInProgress(false);
					}
				});
		if (mAutoDetectorDlThread != null) {
			mLocalDlFile = xmlLocalPath;
		} else {
			sendMsgToCancelDlProgressBar();
			setInProgress(false);
		}
	}
	
	private void handleAutoDetectXml(String xmlPath) {
		UpdateApkInfo apkInfo = getApkParamFromXml(xmlPath);
		
		if (Integer.parseInt(apkInfo.getVerCode()) > PosterApplication.getInstance().getVerCode()) {
			String apkUpdateFullPath = PosterApplication.getAPKUpdateFullPath();
			final String apkLocalPath =
					(new StringBuilder()).append(apkUpdateFullPath).append(File.separator)
					.append(FileUtils.getFilename(apkInfo.getPath())).toString();
			
			if (FileUtils.isExist(apkLocalPath) &&
					apkInfo.getVerify().equals(new Md5(PosterApplication.stringHexToInt(
					apkInfo.getVerifyKey())).ComputeFileMd5(apkLocalPath))) {
				sendMsgToCancelDlProgressBar();
				updateApk(apkLocalPath);
				setInProgress(false);
			} else {
				//if (FileUtils.isExist(apkLocalPath)) {
					//FileUtils.delFile(new File(apkLocalPath));
				//}
				delAllFilesByExt(apkUpdateFullPath,
						FileUtils.getFileExtensionName(apkLocalPath));
				
				sendMsgToLaunchDlProgressBar();
				
				FtpFileInfo toDownloadFile = new FtpFileInfo();
		        toDownloadFile.setRemotePath(apkInfo.getPath());
		        toDownloadFile.setLocalPath(apkUpdateFullPath);
		        List<FtpFileInfo> toDownloadList = new ArrayList<FtpFileInfo>();
		        toDownloadList.add(toDownloadFile);
		        
		        mAutoDetectorDlThread = FtpHelper.getInstance().downloadFileList(toDownloadList, new FtpOperationInterface() {
					@Override
					public void started(String file, long size) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnStarted(FileUtils.getFilename(file), size);
					}
		
					@Override
					public void aborted() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						setInProgress(false);
					}
		
					@Override
					public void progress(long length) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnProgress(length);
					}
		
					@Override
					public void completed() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						updateApk(apkLocalPath);
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						setInProgress(false);
					}
		
					@Override
					public void failed() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						mLocalDlFile = null;
						mAutoDetectorDlThread = null;
						setInProgress(false);
					}
		        });
		        if (mAutoDetectorDlThread != null) {
		        	mLocalDlFile = apkLocalPath;
				} else {
					sendMsgToCancelDlProgressBar();
					setInProgress(false);
				}
			}
		} else {
			sendMsgToCancelDlProgressBar();
			setInProgress(false);
		}
	}
	
	public void startUpdate(String xmlFile, String verifyKey, String verifyCode, int regCode) {
		synchronized (this) {
			if (mIsInProgress) {
				return;
			} else {
				mIsInProgress = true;
			}
		}
		
		String apkUpdateFullPath = PosterApplication.getAPKUpdateFullPath();
		final String xmlLocalPath = (new StringBuilder()).append(apkUpdateFullPath)
				.append(File.separator).append(FileUtils.getFilename(xmlFile)).toString();
		final int finRegCode = regCode;
		
		if (FileUtils.isExist(xmlLocalPath) &&
				verifyCode.equals(new Md5(PosterApplication.stringHexToInt(verifyKey))
				.ComputeFileMd5(xmlLocalPath))) {
			handleXml(xmlLocalPath, finRegCode);
		} else {
			//if (FileUtils.isExist(xmlLocalPath)) {
				//FileUtils.delFile(new File(xmlLocalPath));
				//FileUtils.cleanupDir(apkUpdateFullPath);
			//}
			delAllFilesByExt(apkUpdateFullPath,
					FileUtils.getFileExtensionName(xmlLocalPath));
			
			sendMsgToLaunchDlProgressBar();
			
			FtpFileInfo toDownloadFile = new FtpFileInfo();
	        toDownloadFile.setRemotePath(xmlFile);
	        toDownloadFile.setLocalPath(apkUpdateFullPath);
	        List<FtpFileInfo> toDownloadList = new ArrayList<FtpFileInfo>();
	        toDownloadList.add(toDownloadFile);

	        mDlThread = FtpHelper.getInstance().downloadFileList(toDownloadList, new FtpOperationInterface() {
				@Override
				public void started(String file, long size) {
					// TODO Auto-generated method stub
					sendMsgToUpdateDlProgressBarOnStarted(FileUtils.getFilename(file), size);
				}
	
				@Override
				public void aborted() {
					// TODO Auto-generated method stub
					sendMsgToCancelDlProgressBar();
					postResult(finRegCode, FAIL);
					mLocalDlFile = null;
					mDlThread = null;
					setInProgress(false);
				}
	
				@Override
				public void progress(long length) {
					// TODO Auto-generated method stub
					sendMsgToUpdateDlProgressBarOnProgress(length);
				}
	
				@Override
				public void completed() {
					// TODO Auto-generated method stub
					mLocalDlFile = null;
					mDlThread = null;
					handleXml(xmlLocalPath, finRegCode);
				}
	
				@Override
				public void failed() {
					// TODO Auto-generated method stub
					sendMsgToCancelDlProgressBar();
					postResult(finRegCode, FAIL);
					mLocalDlFile = null;
					mDlThread = null;
					setInProgress(false);
				}
	        });
	        if (mDlThread != null) {
	        	mLocalDlFile = xmlLocalPath;
	        } else {
	        	sendMsgToCancelDlProgressBar();
				postResult(finRegCode, FAIL);
				setInProgress(false);
	        }
		}
	}
	
	private boolean delAllFilesByExt(String dir, String suffix) {
        File dirfile = new File(dir);
        
        if (dirfile == null || suffix == null || !dirfile.exists() || !dirfile.isDirectory()) {
            return false;
        }
        
        for (File file : dirfile.listFiles()) {
            if (file.isFile() && suffix.equalsIgnoreCase(
            		FileUtils.getFileExtensionName(file.getName()))) {
                file.delete();
            }
        }
        
        return true;
    }
	
	private void sendMsgToLaunchDlProgressBar() {
		if (!mShowProgressBar) {
			return;
		}
		mHandler.obtainMessage(EVENT_LAUNCH_DLPROGRESSBAR).sendToTarget();
	}
	
	private void sendMsgToUpdateDlProgressBarOnStarted(String file, long size) {
		if (!mShowProgressBar) {
			return;
		}
		Bundle bundle = new Bundle();
        bundle.putString("file", file);
        bundle.putLong("size", size);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_UPDATE_DLPROGRESSBAR_ONSTARTED;
        msg.setData(bundle);
        msg.sendToTarget();
	}
	
	private void sendMsgToUpdateDlProgressBarOnProgress(long length) {
		if (!mShowProgressBar) {
			return;
		}
		Bundle bundle = new Bundle();
        bundle.putLong("length", length);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_UPDATE_DLPROGRESSBAR_ONPROGRESS;
        msg.setData(bundle);
        msg.sendToTarget();
	}
	
	private void sendMsgToCancelDlProgressBar() {
		if (!mShowProgressBar) {
			return;
		}
		mHandler.obtainMessage(EVENT_CANCEL_DLPROGRESSBAR).sendToTarget();
	}
	
	private void launchDlProgressBar() {
		if (mDlProgressBar != null) {
			return;
		}

		if (UrgentPlayerActivity.INSTANCE != null) {
            mDlProgressBar = new ProgressDialog(UrgentPlayerActivity.INSTANCE);
        } else {
        	mDlProgressBar = new ProgressDialog(PosterMainActivity.INSTANCE);
        }
		mDlProgressBar.setTitle("应用程序更新");
		mDlProgressBar.setMessage("下载中，请稍后。。。");
		mDlProgressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mDlProgressBar.setProgressDrawable(PosterApplication.getInstance()
				.getResources().getDrawable(R.drawable.dlpbarhorizontal));
		mDlProgressBar.setCancelable(false);
		mDlProgressBar.setButton(DialogInterface.BUTTON_POSITIVE, "取消",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dlg, int wbtn) {
						// TODO Auto-generated method stub
						if (mDlThread != null) {
							FtpHelper.getInstance()
									.cancelDownload(mDlThread);
							mDlThread = null;
						}
						if (mAutoDetectorDlThread != null) {
							FtpHelper.getInstance()
									.cancelDownload(mAutoDetectorDlThread);
							mAutoDetectorDlThread = null;
						}
						if ((mLocalDlFile != null) && FileUtils.isExist(mLocalDlFile)) {
							FileUtils.delFile(new File(mLocalDlFile));
							mLocalDlFile = null;
						}
						dlg.dismiss();
						mDlProgressBar = null;
						setInProgress(false);
					}
				});
		mDlProgressBar.show();
    }
	
	private void updateDlProgressBarOnStarted(String file, long size) {
		if (mDlProgressBar != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("正在下载文件：").append(file);
			mDlProgressBar.setMessage(sb.toString());
			mDlProgressBar.setMax(100);
			mDlProgressBar.setProgress(0);
			mDlFileSize = size;
		}
	}
	
	private void updateDlProgressBarOnProgress(long length) {
		if (mDlProgressBar != null) {
			mDlProgressBar.setProgress((int)((length*100f)/mDlFileSize));
		}
	}
	
	private void cancelDlProgressBar() {
		if (mDlProgressBar != null) {
			mDlProgressBar.cancel();
			mDlProgressBar = null;
		}
	}
	
	private class MyHandler extends Handler {
		public MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
            case EVENT_LAUNCH_DLPROGRESSBAR:
            	launchDlProgressBar();
            	break;
            case EVENT_UPDATE_DLPROGRESSBAR_ONSTARTED:
            	updateDlProgressBarOnStarted(msg.getData().getString("file"),
            			msg.getData().getLong("size"));
            	break;
            case EVENT_UPDATE_DLPROGRESSBAR_ONPROGRESS:
            	updateDlProgressBarOnProgress(msg.getData().getLong("length"));
            	break;
            case EVENT_CANCEL_DLPROGRESSBAR:
            	cancelDlProgressBar();
            	break;
            default:
                break;
            }
            super.handleMessage(msg);
		}
	}
	
	private UpdateApkInfo getApkParamFromXml(String file) {
        if (!FileUtils.isExist(file)) {
            return null;
        }
        
        UpdateApkInfo apkInfo = null;
        try {
            FileInputStream fIn = new FileInputStream(file);
            XmlParser xml = new XmlParser();
            apkInfo = (UpdateApkInfo)xml.getXmlObject(fIn, UpdateApkInfo.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        
        return apkInfo;
    }
	
	private void handleXml(String xmlPath, int regCode) {
		UpdateApkInfo apkInfo = getApkParamFromXml(xmlPath);
		
		if (Integer.parseInt(apkInfo.getVerCode()) > PosterApplication.getInstance().getVerCode()) {
			String apkUpdateFullPath = PosterApplication.getAPKUpdateFullPath();
			final String apkLocalPath =
					(new StringBuilder()).append(apkUpdateFullPath).append(File.separator)
					.append(FileUtils.getFilename(apkInfo.getPath())).toString();
			final int finRegCode = regCode;
			
			if (FileUtils.isExist(apkLocalPath) &&
					apkInfo.getVerify().equals(new Md5(PosterApplication.stringHexToInt(
					apkInfo.getVerifyKey())).ComputeFileMd5(apkLocalPath))) {
				sendMsgToCancelDlProgressBar();
				updateApk(apkLocalPath);
				postResult(finRegCode, SUCCESS);
				setInProgress(false);
			} else {
				//if (FileUtils.isExist(apkLocalPath)) {
					//FileUtils.delFile(new File(apkLocalPath));
				//}
				delAllFilesByExt(apkUpdateFullPath,
						FileUtils.getFileExtensionName(apkLocalPath));
				
				sendMsgToLaunchDlProgressBar();
				
				FtpFileInfo toDownloadFile = new FtpFileInfo();
		        toDownloadFile.setRemotePath(apkInfo.getPath());
		        toDownloadFile.setLocalPath(apkUpdateFullPath);
		        List<FtpFileInfo> toDownloadList = new ArrayList<FtpFileInfo>();
		        toDownloadList.add(toDownloadFile);
		        
		        mDlThread = FtpHelper.getInstance().downloadFileList(toDownloadList, new FtpOperationInterface() {
					@Override
					public void started(String file, long size) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnStarted(FileUtils.getFilename(file), size);
					}
		
					@Override
					public void aborted() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						postResult(finRegCode, FAIL);
						mLocalDlFile = null;
						mDlThread = null;
						setInProgress(false);
					}
		
					@Override
					public void progress(long length) {
						// TODO Auto-generated method stub
						sendMsgToUpdateDlProgressBarOnProgress(length);
					}
		
					@Override
					public void completed() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						updateApk(apkLocalPath);
						postResult(finRegCode, SUCCESS);
						mLocalDlFile = null;
						mDlThread = null;
						setInProgress(false);
					}
		
					@Override
					public void failed() {
						// TODO Auto-generated method stub
						sendMsgToCancelDlProgressBar();
						postResult(finRegCode, FAIL);
						mLocalDlFile = null;
						mDlThread = null;
						setInProgress(false);
					}
		        });
		        if (mDlThread != null) {
		        	mLocalDlFile = apkLocalPath;
		        } else {
		        	sendMsgToCancelDlProgressBar();
					postResult(finRegCode, FAIL);
					setInProgress(false);
		        }
			}
		} else {
			sendMsgToCancelDlProgressBar();
			postResult(regCode, FAIL);
			setInProgress(false);
		}
	}
	
	private void updateApk(String file) {
        if (file != null) {
        	Intent intent = new Intent(Intent.ACTION_VIEW);
        	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(Uri.fromFile(new File(file)),
                    "application/vnd.android.package-archive");
            PosterApplication.getInstance().startActivity(intent);
        }
    }
	
	private void postResult(final int regCode, final int result) {
		new Thread() {
			@Override
	        public void run() {
				WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_SYSUPDATE, regCode, result, "");
			}
		}.start();
	}
}

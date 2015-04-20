package com.youngsee.authorization;

import java.io.File;
import com.youngsee.common.FileUtils;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.R;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

@SuppressLint("DefaultLocale")
public class AuthorizationHelper {
	private final static String USB_COMMON_NAME = "usbhost";
	
	private final static int EVENT_START = 0x8000;
    private final static int EVENT_SUCCESS = 0x8001;
    private final static int EVENT_FAILURE = 0x8002;
    
    private final static int PDLGTYPE_GETDEVINFO = 0;
    private final static int PDLGTYPE_IMPORTAUTHCODE = 1;
    private final static int PDLGTYPE_IMPORTKEY = 2;
    private final static int PDLGTYPE_UPDATEKEY = 3;
	
	private Context mContext = null;
    private ProgressDialog mProgressDlg = null;
	
	private OnStatusListener mListener = null;
	
	public interface OnStatusListener {
        public void onCompleted();
    }
	
	public AuthorizationHelper(Context context, String pdlgtitle, String pdlgmsg) {
		mContext = context;
		mProgressDlg = new ProgressDialog(mContext);
		mProgressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDlg.setTitle(pdlgtitle);
		mProgressDlg.setMessage(pdlgmsg);
		mProgressDlg.setIndeterminate(false);
		mProgressDlg.setCancelable(false);
	}
	
	public void setOnStatusListener(OnStatusListener l) {
		mListener = l;
	}
	
	private String getDevInfoSavePath() {
    	String savePath = null;
    	String externalRootPath = Environment.getExternalStorageDirectory().getParent();
    	if (externalRootPath != null) {
    		File exRootPath = new File(externalRootPath);
    		File[] exRootPathFiles = exRootPath.listFiles();
    		if (exRootPathFiles != null) {
	    		long currMaxSpace = 0;
	    		for (File rootFile : exRootPathFiles) {
	    			if (rootFile.isDirectory() && rootFile.getName().contains(USB_COMMON_NAME)) {
	    				if (rootFile.getTotalSpace() > 0) {
	    					if (rootFile.getUsableSpace() > currMaxSpace) {
	    						savePath = rootFile.getAbsolutePath();
	    	    				currMaxSpace = rootFile.getUsableSpace();
	    					}
	    				} else {
	    					File[] exRootSubPathFiles = rootFile.listFiles();
	    					if (exRootSubPathFiles != null) {
		    					for (File rootSubFile : exRootSubPathFiles) {
		    						if (rootSubFile.getUsableSpace() > currMaxSpace) {
		        						savePath = rootSubFile.getAbsolutePath();
		        	    				currMaxSpace = rootSubFile.getUsableSpace();
		        					}
		    					}
	    					}
	    				}
	    			}
	    		}
    		}
        }
    	return savePath;
    }
	
	private boolean doExportDevInfo() {
		String path = getDevInfoSavePath();
		if (path != null) {
			String mac = AuthorizationCommon.getMac(PosterApplication.getEthMacAddress()).toUpperCase();
			String cpuid = PosterApplication.getCpuId().toUpperCase();
			StringBuilder sb = new StringBuilder();
			sb.append(path);
			sb.append(File.separator);
			sb.append(AuthorizationCommon.PREFIX_DEVINFO_FILE);
			sb.append("-");
			sb.append(cpuid);
			sb.append(".");
			sb.append(AuthorizationCommon.EXT_DEVINFO_FILE);
			String file = sb.toString();
			
			sb.setLength(0);
			sb.append("MAC : ");
			sb.append(mac);
			sb.append("\r\n");
			sb.append("CPUID : ");
			sb.append(cpuid);
			String content = sb.toString();
			
			FileUtils.writeTextToFile(file, content);
			return true;
		}
		return false;
	}
	
	public void exportDevInfoToUDisk() {
		new Thread() {
			@Override
	        public void run() {
				sendEventStart(PDLGTYPE_GETDEVINFO);
				if (doExportDevInfo()) {
					sendEventSuccess(PDLGTYPE_GETDEVINFO);
				} else {
					sendEventFailure(PDLGTYPE_GETDEVINFO);
				}
			}
		}.start();
	}
	
	private String findFilePath(String filename) {
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
		        							&& usbFile.getName().equals(filename)) {
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
			            							&& subUsbFile.getName().equals(filename)) {
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
	
	private boolean doImportAuthCode() {
		String cpuid = PosterApplication.getCpuId().toUpperCase();
		StringBuilder sb = new StringBuilder();
		sb.append(AuthorizationCommon.PREFIX_AUTHCODE_FILE);
		sb.append("-");
		sb.append(cpuid);
		sb.append(".");
		sb.append(AuthorizationCommon.EXT_AUTHCODE_FILE);
		String filename = sb.toString();
		
		String path = findFilePath(filename);
		if (path != null) {
            FileUtils.copyAuthCodeToSysFile(path);
			return true;
		}
		return false;
	}
	
	public void importAuthCodeFromUDisk() {
		new Thread() {
			@Override
	        public void run() {
				sendEventStart(PDLGTYPE_IMPORTAUTHCODE);
				if (doImportAuthCode()) {
					sendEventSuccess(PDLGTYPE_IMPORTAUTHCODE);
				} else {
					sendEventFailure(PDLGTYPE_IMPORTAUTHCODE);
				}
			}
		}.start();
	}
	
	private boolean doImportOrUpdateKey() {
		String path = findFilePath(AuthorizationCommon.PUBLICKEY_FILE);
		if (path != null) {
			FileUtils.copyKeyToSysFile(path);
			return true;
		} else {
			path = findFilePath(AuthorizationCommon.PRIVATEKEY_FILE);
			if (path != null) {
			    FileUtils.copyKeyToSysFile(path);
				return true;
			}
		}
		return false;
	}
	
	public void importKeyFromUDisk() {
		new Thread() {
			@Override
	        public void run() {
				sendEventStart(PDLGTYPE_IMPORTKEY);
				if (doImportOrUpdateKey()) {
					sendEventSuccess(PDLGTYPE_IMPORTKEY);
				} else {
					sendEventFailure(PDLGTYPE_IMPORTKEY);
				}
			}
		}.start();
	}
	
	public void updateKeyFromUDisk() {
		new Thread() {
			@Override
	        public void run() {
				sendEventStart(PDLGTYPE_UPDATEKEY);
				if (doImportOrUpdateKey()) {
					sendEventSuccess(PDLGTYPE_UPDATEKEY);
				} else {
					sendEventFailure(PDLGTYPE_UPDATEKEY);
				}
			}
		}.start();
	}
	
	private void sendEventStart(int type) {
		Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_START;
        msg.setData(bundle);
        msg.sendToTarget();
	}
	
	private void sendEventSuccess(int type) {
		Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_SUCCESS;
        msg.setData(bundle);
        msg.sendToTarget();
	}
	
	private void sendEventFailure(int type) {
		Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_FAILURE;
        msg.setData(bundle);
        msg.sendToTarget();
	}
	
	private Handler mHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
			int residtitle = -1;
			final int type = msg.getData().getInt("type");
            switch (msg.what) {
            case EVENT_START:
                if (mProgressDlg != null && !mProgressDlg.isShowing()) {
                    mProgressDlg.show();
                }
                break;
            case EVENT_SUCCESS:
            	if (mListener != null) {
            		mListener.onCompleted();
            	}
                if (mProgressDlg != null && mProgressDlg.isShowing()) {
                    mProgressDlg.dismiss();
                }
                switch(type) {
                case PDLGTYPE_GETDEVINFO:
                	residtitle = R.string.exportdevinfo_success_title;
                	break;
                case PDLGTYPE_IMPORTAUTHCODE:
                	residtitle = R.string.importauthcode_success_title;
                	break;
                case PDLGTYPE_IMPORTKEY:
                	residtitle = R.string.importkey_success_title;
                	break;
                case PDLGTYPE_UPDATEKEY:
                	residtitle = R.string.updatekey_success_title;
                	break;
                }
                if (residtitle != -1) {
	                new AlertDialog.Builder(mContext).setTitle(residtitle)
	                        .setPositiveButton(R.string.enter, null).create().show();
                }
                break;
            case EVENT_FAILURE:
                if (mProgressDlg != null && mProgressDlg.isShowing()) {
                    mProgressDlg.dismiss();
                }
                switch(type) {
                case PDLGTYPE_GETDEVINFO:
                	residtitle = R.string.exportdevinfo_failure_title;
                	break;
                case PDLGTYPE_IMPORTAUTHCODE:
                	residtitle = R.string.importauthcode_failure_title;
                	break;
                case PDLGTYPE_IMPORTKEY:
                	residtitle = R.string.importkey_failure_title;
                	break;
                case PDLGTYPE_UPDATEKEY:
                	residtitle = R.string.updatekey_failure_title;
                	break;
                }
                if (residtitle != -1) {
	                new AlertDialog.Builder(mContext)
	                        .setTitle(residtitle)
	                        .setPositiveButton(R.string.retry,
	                                new DialogInterface.OnClickListener() {
	                                    @Override
	                                    public void onClick(DialogInterface dialog, int which) {
	                                    	switch(type) {
	                                        case PDLGTYPE_GETDEVINFO:
	                                        	exportDevInfoToUDisk();
	                                        	break;
	                                        case PDLGTYPE_IMPORTAUTHCODE:
	                                        	importAuthCodeFromUDisk();
	                                        	break;
	                                        case PDLGTYPE_IMPORTKEY:
	                                        	importKeyFromUDisk();
	                                        	break;
	                                        case PDLGTYPE_UPDATEKEY:
	                                        	updateKeyFromUDisk();
	                                        	break;
	                                        }
	                                    }
	                                })
	                        .setNegativeButton(R.string.cancel, null).create().show();
                }
                return;
            }
            super.handleMessage(msg);
        }
	};
}

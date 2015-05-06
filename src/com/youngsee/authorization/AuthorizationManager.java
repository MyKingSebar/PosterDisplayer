package com.youngsee.authorization;

import java.io.File;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.youngsee.common.Base64Utils;
import com.youngsee.common.FileUtils;
import com.youngsee.common.RSAUtils;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.webservices.WsClient;
import com.youngsee.webservices.XmlCmdInfoRef;

@SuppressLint("DefaultLocale")
public class AuthorizationManager {
	private static AuthorizationManager INSTANCE = null;

	private final int EVENT_DELAY_AUTH = 0x8000;

	private final int DEFAULT_DELAY_MILLISECOND_LOCAL =
			AuthorizationCommon.DEFAULT_DELAY_SECOND_LOCAL * 1000;
	
	private final int DEFAULT_DELAY_MILLISECOND_SERVER =
			AuthorizationCommon.DEFAULT_DELAY_SECOND_SERVER * 1000;

	public static final int STATUS_IDLE = 0;
	public static final int STATUS_UNAUTHORIZED = 1;
	public static final int STATUS_AUTHORIZED = 2;

	public static final int MODE_IMMEDIATE = 0;
	public static final int MODE_DELAY_LOCAL = 1;
	public static final int MODE_DELAY_SERVER = 2;
	
	public static final int RSAKEYTYPE_PRI = 0;
	public static final int RSAKEYTYPE_PUB = 1;
	
	private int mRsaKeyType = RSAKEYTYPE_PUB;
	
	private int mCurrentStatus = STATUS_IDLE;
	
	private HandlerThread mHandlerThread = null;
	private MyHandler mHandler = null;
	
	private AuthorizationThread mAuthorizationThread = null;
	
	private final long DEFAULT_AUTH_DETECT_PERIOD = 10*1000;
	
	private OnAuthStatusListener mListener = null;
	
	public interface OnAuthStatusListener {
		public void onAuthSucceeded();
		public void onKeyDownloaded();
	}
	
	public void setOnViewListener(OnAuthStatusListener l) {
    	mListener = l;
    }
	
	public static AuthorizationManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new AuthorizationManager();
		}
		return INSTANCE;
	}
	
	private AuthorizationManager() {
		mHandlerThread = new HandlerThread("authmgr_ui_thread");
		mHandlerThread.start();
		mHandler = new MyHandler(mHandlerThread.getLooper());
	}
	
	public void destroy() {
		stopAuth();
		if (mHandler != null) {
			mHandler.removeMessages(EVENT_DELAY_AUTH);
			mHandler = null;
		}
		if (mHandlerThread != null) {
			mHandlerThread.getLooper().quit();
			mHandlerThread = null;
		}
	}
	
	public int getStatus() {
		synchronized (this) {
			return mCurrentStatus;
		}
	}
	
	private void setStatus(int status) {
		synchronized (this) {
			mCurrentStatus = status;
		}
	}
	
	public boolean checkAuthStatus(int mode) {
		String authcode = FileUtils.readTextFromFile(AuthorizationCommon.SYS_AUTHCODE_FILE);
		if (authcode != null) {
			String authinfo = parseAuthInfo(authcode);
			if (authinfo != null) {
				if (authinfo.equals(PosterApplication.getCpuId().toUpperCase())) {
					if (mode == MODE_IMMEDIATE) {
						setStatus(STATUS_AUTHORIZED);
					} else if (mode == MODE_DELAY_LOCAL) {
						sendEventDelayedToAuth(DEFAULT_DELAY_MILLISECOND_LOCAL);
					} else if (mode == MODE_DELAY_SERVER) {
						sendEventDelayedToAuth(DEFAULT_DELAY_MILLISECOND_SERVER);
					}
					return true;
				}
			}
		}
		setStatus(STATUS_UNAUTHORIZED);
		return false;
	}
	
	private void sendEventDelayedToAuth(long delayMillis) {
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_DELAY_AUTH;
        mHandler.sendMessageDelayed(msg, delayMillis);
	}
	
	private String parseAuthInfo(String ac) {
		String authinfo = null;
		String key = FileUtils.readTextFromFile(AuthorizationCommon.SYS_RSAKEY_FILE);
		if (key != null) {
			try {
				if (mRsaKeyType == RSAKEYTYPE_PRI) {
					authinfo = new String(RSAUtils.decryptByPrivateKey(
							Base64Utils.decode(ac), key));
				} else {
					authinfo = new String(RSAUtils.decryptByPublicKey(
							Base64Utils.decode(ac), key));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return authinfo;
	}
	
	public void startAuth() {
		if (mAuthorizationThread == null) {
			mAuthorizationThread = new AuthorizationThread();
			mAuthorizationThread.start();
		}
	}
	
	public void stopAuth() {
		if (mAuthorizationThread != null) {
			mAuthorizationThread.interrupt();
			mAuthorizationThread = null;
		}
	}
	
	private String getRemoteUrl(String file) {
		if ((file == null) || (file.length() == 0)) {
			return null;
		}
		StringBuilder urlsb = new StringBuilder();
    	urlsb.append(WsClient.getServerURLPrefix());
    	urlsb.append("/");
    	urlsb.append(AuthorizationCommon.SERVERFILE_URL_MIDDLE);
    	urlsb.append(file);
    	return urlsb.toString();
	}
	
    public void updateKey(final int regCode) {
        new Thread() {
            @Override
            public void run() {
            	boolean success = false;
                File file = FileUtils.getServerFile(
                		getRemoteUrl(AuthorizationCommon.REMOTE_KEY_FILE_PATH),
                		AuthorizationCommon.TEM_RSAKEY_FILE);
                if (file != null && file.length() > 0) {
                    if (FileUtils.copyKeyToSysFile(file.getAbsolutePath())) {
                    	success = true;
                    }
                    file.delete();
                }
                if (success) {
                	postResult(regCode, 0);
                } else {
                	postResult(regCode, 1);
                }
            }
        }.start();
    }

    private final class AuthorizationThread extends Thread {
        @Override
        public void run() {
        	try {
				while (!isInterrupted()) {
					if (getKeyAndAuthCodeFromServer()) {
						if (checkAuthStatus(MODE_DELAY_SERVER)) {
							if (mListener != null) {
								mListener.onAuthSucceeded();
							}
							mAuthorizationThread = null;
							break;
						}
					}
					Thread.sleep(DEFAULT_AUTH_DETECT_PERIOD);
				}
        	} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    }

    private boolean getKeyFromServer() {
    	if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
        	File file = FileUtils.getServerFile(
            		getRemoteUrl(AuthorizationCommon.REMOTE_KEY_FILE_PATH),
            		AuthorizationCommon.TEM_RSAKEY_FILE);
            if (file != null && file.length() > 0) {
                FileUtils.copyKeyToSysFile(file.getAbsolutePath());
                file.delete();
            }
        }
    	if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
            return false;
        }
        return true;
    }
    
    private boolean getAuthCodeFromServer() {
    	StringBuilder acsb = new StringBuilder();
    	acsb.append(AuthorizationCommon.REMOTE_AUTHCODE_FILE_PARENTPATH);
    	acsb.append(AuthorizationCommon.PREFIX_AUTHCODE_FILE);
    	acsb.append("-");
    	acsb.append(PosterApplication.getCpuId().toUpperCase());
    	acsb.append(".");
    	acsb.append(AuthorizationCommon.EXT_AUTHCODE_FILE);
    	File file = FileUtils.getServerFile(
        		getRemoteUrl(acsb.toString()),
        		AuthorizationCommon.TEM_AUTHCODE_FILE);
        if (file != null && file.length() > 0) {
            FileUtils.copyAuthCodeToSysFile(file.getAbsolutePath());
            file.delete();
        }
        if (!FileUtils.isExist(AuthorizationCommon.SYS_AUTHCODE_FILE)) {
            return false;
        }
        return true;
    }

    private boolean getKeyAndAuthCodeFromServer() {
    	if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
			if (getKeyFromServer()) {
				if (mListener != null) {
					mListener.onKeyDownloaded();
				}
				if (getAuthCodeFromServer()) {
					return true;
				}
			}
		} else {
			if (getAuthCodeFromServer()) {
				return true;
			}
		}
        return false;
    }
    
    private void postResult(final int regCode, final int result) {
        WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_AUTHKEYUPDATE, regCode, result, "");
    }
    
	private class MyHandler extends Handler {
		public MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_DELAY_AUTH:
				setStatus(STATUS_AUTHORIZED);
				break;
			default:
                break;
            }
            super.handleMessage(msg);
		}
	}
}

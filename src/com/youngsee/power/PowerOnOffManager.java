package com.youngsee.power;

import com.youngsee.common.Actions;
import com.youngsee.common.SysOnOffTimeInfo;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.PosterMainActivity;
import com.youngsee.posterdisplayer.PosterOsdActivity;
import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.UrgentPlayerActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.format.Time;
import android.view.Gravity;
import android.widget.Toast;

public class PowerOnOffManager {
	private static PowerOnOffManager INSTANCE = null;
	
	private final int EVENT_SET_SCREEN_OFF = 0x9000;
	private final int EVENT_ALERTDIALOG_TIMEOUT = 0x9001;
	
	private final long MILLISECOND_DAY = 24*60*60*1000;
	private final long MILLISECOND_HOUR = 60*60*1000;
	private final long MILLISECOND_MINUTE = 60*1000;
	private final long MILLISECOND_SECOND = 1000;
	
	public static final int STATUS_IDLE = 0;
	public static final int STATUS_ONLINE = 1;
	public static final int STATUS_STANDBY = 2;
	
	public static final int AUTOSCREENOFF_IMMEDIATE = 0;
	public static final int AUTOSCREENOFF_COMMON = 1;
	public static final int AUTOSCREENOFF_URGENT = 2;
	
	private final int COMMON_AUTOSCREENOFF_MINUTE = 3;
	private final int COMMON_AUTOSCREENOFF_MILLISECOND = COMMON_AUTOSCREENOFF_MINUTE*60*1000;
	private final int URGENT_AUTOSCREENOFF_MINUTE = 1;
	private final int URGENT_AUTOSCREENOFF_MILLISECOND = URGENT_AUTOSCREENOFF_MINUTE*60*1000;
	
	@SuppressWarnings("unused")
    private final int DEFAULT_ALERTDIALOG_TIMEOUT = 30*1000;
	
	private HandlerThread mHandlerThread = null;
	private MyHandler mHandler = null;
	
	private int mCurrentStatus = STATUS_IDLE;
	
	private Dialog mAlertDialog = null;
	
	public static PowerOnOffManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new PowerOnOffManager();
		}
		return INSTANCE;
	}
	
	private PowerOnOffManager() {
		mHandlerThread = new HandlerThread("poweronoffmgr_ui_thread");
		mHandlerThread.start();
		mHandler = new MyHandler(mHandlerThread.getLooper());
		setCurrentStatus(STATUS_ONLINE);
	}
	
	public void destroy() {
		dismissPromptDialog();
		
		if (mHandler != null) {
			mHandler.removeMessages(EVENT_SET_SCREEN_OFF);
			mHandler.removeMessages(EVENT_ALERTDIALOG_TIMEOUT);
			mHandler = null;
		}
		if (mHandlerThread != null) {
			mHandlerThread.getLooper().quit();
			mHandlerThread = null;
		}
	}
	
	public int getCurrentStatus() {
		synchronized (this) {
			return mCurrentStatus;
		}
	}
	
	public void setCurrentStatus(int status) {
		synchronized (this) {
			mCurrentStatus = status;
		}
	}
	
	private int getNextWeekDay(final int currWeekDay) {
		return (currWeekDay != 6) ? currWeekDay+1 : 0;
	}
	
	private long getMillisFromTime(int day, int hour, int minute, int second) {
		return MILLISECOND_DAY*day+MILLISECOND_HOUR*hour
				+MILLISECOND_MINUTE*minute+MILLISECOND_SECOND*second;
	}
	
	private long getNextScreenOffTime(Time currtime, SysOnOffTimeInfo[] systimeinfo) {
		long nextOffTime = -1;

        if (systimeinfo != null) {
            long currTimeMillis = getMillisFromTime(0, currtime.hour,
            		currtime.minute, currtime.second);
            int i, j, k;
        	for (i = 0; i < systimeinfo.length; i++) {
        		long tmpTimeMillis = -1;
        		long latestNextOffTime = -1;
                for (j = currtime.weekDay, k = 0; k < 7; j = getNextWeekDay(j), k++) {
                    if (((systimeinfo[i].week&(1<<j)) != 0) &&
                    		(systimeinfo[i].offhour != 0xFF)) {
                    	tmpTimeMillis = getMillisFromTime(k, systimeinfo[i].offhour,
                    			systimeinfo[i].offminute, systimeinfo[i].offsecond);
                    	if ((j == currtime.weekDay) && (currTimeMillis > tmpTimeMillis)) {
                    		latestNextOffTime = (MILLISECOND_DAY*7)-(currTimeMillis-tmpTimeMillis);
                    	} else { // Found the next off time for this group.
                    		latestNextOffTime = tmpTimeMillis-currTimeMillis;
                    		break;
                    	}
                    }
                }
                if ((latestNextOffTime != -1) &&
                		((nextOffTime == -1) || (nextOffTime > latestNextOffTime))) {
                	nextOffTime = latestNextOffTime;
                }
            }
        }

        return nextOffTime;
    }
	
	private long getNextScreenOnTime(Time currtime, SysOnOffTimeInfo[] systimeinfo) {
		long nextOnTime = -1;
		
        if (systimeinfo != null) {
            long currTimeMillis = getMillisFromTime(0, currtime.hour,
            		currtime.minute, currtime.second);
            int i, j, k;
        	for (i = 0; i < systimeinfo.length; i++) {
        		long tmpTimeMillis = -1;
        		long latestNextOnTime = -1;
                for (j = currtime.weekDay, k = 0; k < 7; j = getNextWeekDay(j), k++) {
                    if (((systimeinfo[i].week&(1<<j)) != 0) &&
                    		(systimeinfo[i].onhour != 0xFF)) {
                    	tmpTimeMillis = getMillisFromTime(k, systimeinfo[i].onhour,
                    			systimeinfo[i].onminute, systimeinfo[i].onsecond);
                    	if ((j == currtime.weekDay) && (currTimeMillis > tmpTimeMillis)) {
                    		latestNextOnTime = (MILLISECOND_DAY*7)-(currTimeMillis-tmpTimeMillis);
                    	} else { // Found the next on time for this group.
                    		latestNextOnTime = tmpTimeMillis-currTimeMillis;
                    		break;
                    	}
                    }
                }
                if ((latestNextOnTime != -1) &&
                		((nextOnTime == -1) || (nextOnTime > latestNextOnTime))) {
                	nextOnTime = latestNextOnTime;
                }
            }
        }

        return nextOnTime;
    }
	
	public void checkAndSetOnOffTime(int type) {
		SysOnOffTimeInfo[] systimeinfo = PosterApplication.getInstance().getSysOnOffTime();
		Time currTime = new Time();
        currTime.setToNow();
        dismissPromptDialog();
        cancelScreenOffTimer();
        if ((systimeinfo != null) && (systimeinfo.length != 0)) {
	        long nextOnTime = getNextScreenOnTime(currTime, systimeinfo);
	        if (nextOnTime > 0) {
		        long nextOffTime = getNextScreenOffTime(currTime, systimeinfo);
		        if (nextOffTime > 0) {
					if (nextOnTime > nextOffTime) {
						if (getCurrentStatus() == STATUS_STANDBY) {
							sendEventToSetScreenOff(false, 0l); // Screen on immediately
						} else {
							sendEventToSetScreenOff(true, nextOffTime);
						}
					} else if (nextOffTime > nextOnTime) {
						if (getCurrentStatus() == STATUS_ONLINE) {
							switch (type) {
							case AUTOSCREENOFF_IMMEDIATE:
								sendEventToSetScreenOff(true, 0l); // Screen off immediately
								break;
							case AUTOSCREENOFF_COMMON:
								showPromptDialog(
										getCurrentContext(type),
										String.format(PosterApplication
										.getInstance().getResources()
										.getString(R.string.autoscreenoff_prompt_msg),
										COMMON_AUTOSCREENOFF_MINUTE));
								sendEventToSetScreenOff(true,
										COMMON_AUTOSCREENOFF_MILLISECOND);
								break;
							case AUTOSCREENOFF_URGENT:
								showPromptDialog(
										getCurrentContext(type),
										String.format(PosterApplication
										.getInstance().getResources()
										.getString(R.string.autoscreenoff_prompt_msg),
										URGENT_AUTOSCREENOFF_MINUTE));
								sendEventToSetScreenOff(true,
										URGENT_AUTOSCREENOFF_MILLISECOND);
								break;
							default:
								break;
							}
						} else {
							sendEventToSetScreenOff(false, nextOnTime);
						}
					}
		        }
	        }
        } else {
        	if (getCurrentStatus() == STATUS_STANDBY) {
        		sendEventToSetScreenOff(false, 0l);
        	}
        }
	}
	
	@SuppressWarnings("unused")
    private void showToast(String msg) {
		Toast tst = Toast.makeText(PosterApplication.getInstance(), msg, Toast.LENGTH_LONG);
		tst.setGravity(Gravity.CENTER, 0, 0);
		tst.show();
	}
	
	private Context getCurrentContext(int screenofftype) {
		if (PosterOsdActivity.INSTANCE != null) {
			return PosterOsdActivity.INSTANCE;
		} else if (UrgentPlayerActivity.INSTANCE != null) {
			return UrgentPlayerActivity.INSTANCE;
		} else {
			return PosterMainActivity.INSTANCE;
		}
	}
	
	private void showPromptDialog(Context context, String msg) {
		if (context != null) {
			mAlertDialog = new AlertDialog.Builder(context)
					.setIcon(android.R.drawable.ic_dialog_alert)
	        		.setTitle(R.string.autoscreenoff_prompt_title)
	        		.setMessage(msg)
	        		.setCancelable(true)
	        		.setPositiveButton(R.string.autoscreenoff_prompt_positive,
	                new DialogInterface.OnClickListener() {
	                    @Override
	                    public void onClick(DialogInterface dialog, int which) {
	                    	mHandler.removeMessages(EVENT_ALERTDIALOG_TIMEOUT);
	                    	mAlertDialog = null;
	                    }
	                }).create();
			mAlertDialog.show();
			//mHandler.sendEmptyMessageDelayed(EVENT_ALERTDIALOG_TIMEOUT, DEFAULT_ALERTDIALOG_TIMEOUT);
		}
	}
	
	public void dismissPromptDialog() {
		if ((mAlertDialog != null) && mAlertDialog.isShowing()) {
    		mAlertDialog.dismiss();
    		mAlertDialog = null;
    	}
	}
	
	public void wakeUp() {
		dismissPromptDialog();
		if (getCurrentStatus() == STATUS_STANDBY) {
			cancelScreenOffTimer();
			sendEventToSetScreenOff(false, 0l);
		}
	}
	
	public void shutDown() {
		if (getCurrentStatus() == STATUS_ONLINE) {
			cancelScreenOffTimer();
			sendEventToSetScreenOff(true, 0l);
		}
	}
	
	private void cancelScreenOffTimer() {
		mHandler.removeMessages(EVENT_SET_SCREEN_OFF);
	}
	
	private void sendEventToSetScreenOff(boolean off, long delayMillis) {
		Bundle bundle = new Bundle();
        bundle.putBoolean("screenoff", off);
        Message msg = mHandler.obtainMessage();
        msg.what = EVENT_SET_SCREEN_OFF;
        msg.setData(bundle);
        mHandler.sendMessageDelayed(msg, delayMillis);
	}
	
	private void setScreenOff(boolean off) {
    	Intent intent = new Intent(Actions.SCREEN_ACTION);
        intent.putExtra("screenoff", off);
        PosterApplication.getInstance().sendBroadcast(intent);
    }
	
	private class MyHandler extends Handler {
		public MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
            case EVENT_SET_SCREEN_OFF:
            	boolean screenoff = msg.getData().getBoolean("screenoff");
            	setScreenOff(screenoff);
            	SysOnOffTimeInfo[] systimeinfo = PosterApplication.getInstance().getSysOnOffTime();
            	Time currTime = new Time();
                currTime.setToNow();
                dismissPromptDialog();
            	if (screenoff) {
            		setCurrentStatus(STATUS_STANDBY);
            		long ontime = getNextScreenOnTime(currTime, systimeinfo);
            		if (ontime > 0) {
            			sendEventToSetScreenOff(false, ontime);
            		}
            	} else {
            		setCurrentStatus(STATUS_ONLINE);
            		long offtime = getNextScreenOffTime(currTime, systimeinfo);
            		if (offtime > 0) {
            			sendEventToSetScreenOff(true, offtime);
            		}
            	}
            	break;
            case EVENT_ALERTDIALOG_TIMEOUT:
            	dismissPromptDialog();
            	break;
            default:
                break;
            }
            super.handleMessage(msg);
		}
	}
}

/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.posterdisplayer;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.youngsee.authorization.AuthorizationManager;
import com.youngsee.common.Contants;
import com.youngsee.common.LogUtils;
import com.youngsee.common.Logger;
import com.youngsee.common.SubWindowInfoRef;
import com.youngsee.osd.UDiskUpdata;
import com.youngsee.posterdisplayer.R;
import com.youngsee.power.PowerOnOffManager;
import com.youngsee.screenmanager.ProgramFragment;
import com.youngsee.screenmanager.ScreenManager;
import com.youngsee.update.APKUpdateManager;
import com.youngsee.webservices.WsClient;

@SuppressLint("Wakelock")
public class PosterMainActivity extends Activity {
	private Logger logger = new Logger();
	Intent popService = null;
	private WakeLock mWklk = null;
	private PopupWindow mOsdPupupWindow = null; // OSD 弹出菜单
	// private final Handler mHandler = new Handler();

	private boolean isPopServiceRunning = false;
	public static PosterMainActivity INSTANCE = null;

	private static final int EVENT_CHECK_SET_ONOFFTIME = 0;

	private Dialog mUpdateProgramDialog = null;
	
	private InternalReceiver mInternalReceiver = null;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PosterApplication.setSystemBarVisible(this, false);
		setContentView(R.layout.activity_main);
		getWindow().setFormat(PixelFormat.TRANSLUCENT);

		// 初始化背景颜色
		((LinearLayout) findViewById(R.id.root)).setBackgroundColor(Color.BLACK);

		INSTANCE = this;

		logger.d("====>PosterMainActivity onCreate: " + getIntent().toString());

		if (mWklk == null) {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWklk = pm
					.newWakeLock(
							(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP),
							"PosterMain");
		}

		// 唤醒屏幕
		if (mWklk != null) {
			mWklk.acquire();
		}

		// 初始化系统参数
		PosterApplication.getInstance().initAppParam();

		// 获取屏幕实际大小(以像素为单位)
		DisplayMetrics metric = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metric);
		PosterApplication.setScreenWidth(metric.widthPixels); // 屏幕宽度（像素）
		PosterApplication.setScreenHeight(metric.heightPixels); // 屏幕高度（像素）
		
		if(!AuthorizationManager.getInstance().checkAuthStatus(AuthorizationManager.MODE_IMMEDIATE)) {
			AuthorizationManager.getInstance().startAuth();
		}
		
		// 启动屏幕管理线程
		if (ScreenManager.getInstance() == null) {
			ScreenManager.createInstance(this).startRun();
		}

		// 启动网络管理线程
		if (WsClient.getInstance() == null) {
			WsClient.createInstance(this).startRun();
		}

		// 启动日志输出线程
		if (LogUtils.getInstance() == null) {
			LogUtils.createInstance(this).startRun();
		}

		// 弹出OSD菜单
		((LinearLayout) findViewById(R.id.root))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (mOsdPupupWindow != null) {
							if (mOsdPupupWindow.isShowing()) {
								mOsdPupupWindow.dismiss();
							} else {
								mOsdPupupWindow.showAtLocation(v, Gravity.TOP
										| Gravity.LEFT, 0, 0);
								mHandler.postDelayed(rHideOsdPopWndDelay, 6000);
							}
						}
					}
				});

		// 初始化OSD菜单
		initOSD();

		PosterApplication.getInstance().startTimingDel();
		PosterApplication.getInstance().startTimingUploadLog();

		PowerOnOffManager.getInstance().checkAndSetOnOffTime(
				PowerOnOffManager.AUTOSCREENOFF_COMMON);

		if (PosterApplication.getInstance().getSysParam(false, false).autoupgradevalue == 1) {
			APKUpdateManager.getInstance().startAutoDetector();
		}
	}

	private void initReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
		filter.addDataScheme("file");
		mInternalReceiver = new InternalReceiver();
		registerReceiver(mInternalReceiver, filter);
	}

	public void showOsd() {
		if (mOsdPupupWindow != null) {
			if (mOsdPupupWindow.isShowing()) {
				mOsdPupupWindow.dismiss();
			} else {
				mOsdPupupWindow.showAtLocation(getCurrentFocus(), Gravity.TOP
						| Gravity.LEFT, 0, 0);
				mHandler.postDelayed(rHideOsdPopWndDelay, 30000);
			}
		}
	}
	
	@Override
    public void onStart(){
		super.onStart();
		initReceiver();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (PowerOnOffManager.getInstance().getCurrentStatus() == PowerOnOffManager.STATUS_STANDBY) {
			PowerOnOffManager.getInstance().setCurrentStatus(
					PowerOnOffManager.STATUS_ONLINE);
			PowerOnOffManager.getInstance().checkAndSetOnOffTime(
					PowerOnOffManager.AUTOSCREENOFF_URGENT);
		}
	}

	@Override
	protected void onPause() {
		mHandler.removeCallbacks(rHideOsdPopWndDelay);
		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}

		super.onPause();
	}
	
	@Override
    public void onStop(){
    	unregisterReceiver(mInternalReceiver);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mHandler.removeCallbacks(rHideOsdPopWndDelay);
		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}

		synchronized (this) {
			if (isPopServiceRunning) {
				stopService(popService);
				isPopServiceRunning = false;
			}
		}

		// 结束屏幕管理线程
		if (ScreenManager.getInstance() != null) {
			ScreenManager.getInstance().stopRun();
		}

		// 结束网络管理线程
		if (WsClient.getInstance() != null) {
			WsClient.getInstance().stopRun();
		}

		// 结束日志输出线程
		if (LogUtils.getInstance() != null) {
			LogUtils.getInstance().stopRun();
		}

		// 结束APK更新
		if (APKUpdateManager.getInstance() != null) {
			APKUpdateManager.getInstance().destroy();
		}

		if (PowerOnOffManager.getInstance() != null) {
			PowerOnOffManager.getInstance().destroy();
		}
		
		if (AuthorizationManager.getInstance() != null) {
			AuthorizationManager.getInstance().destroy();
		}

		// 终止定时器
		PosterApplication.getInstance().cancelTimingDel();
		PosterApplication.getInstance().cancelTimingUploadLog();

		dismissUpdateProgramDialog();

		// 恢复屏幕
		if (mWklk != null) {
			mWklk.release();
			mWklk = null;
		}

		INSTANCE = null;
		super.onDestroy();
		System.exit(0);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			// Log.i("dddd", "back--");
			return true; // 不响应Back键

		case KeyEvent.KEYCODE_MENU:
			enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
			return true; // 打开OSD主菜单

		case KeyEvent.KEYCODE_PAGE_UP:
			return true; // 主窗口中上一个素材

		case KeyEvent.KEYCODE_PAGE_DOWN:
			return true; // 主窗口中下一个素材

		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			return true; // 主窗口视频播放

		case KeyEvent.KEYCODE_MEDIA_STOP:
			return true; // 主窗口视频暂停
		}

		return super.onKeyDown(keyCode, event);
	}

	private void showUpdateProgramDialog() {
		if ((mUpdateProgramDialog != null) && mUpdateProgramDialog.isShowing()) {
			mUpdateProgramDialog.dismiss();
		}
		mUpdateProgramDialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info).setTitle("节目更新")
				.setMessage("检测到U盘中存在节目素材，是否更新节目？").setCancelable(true)
				.setPositiveButton("更新", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						UDiskUpdata diskUpdate = new UDiskUpdata(PosterMainActivity.this);
                        diskUpdate.updateProgram();
						mUpdateProgramDialog = null;
					}
				})
				.setNegativeButton("取消", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mUpdateProgramDialog = null;
					}
				}).create();
		mUpdateProgramDialog.show();
	}

	public void dismissUpdateProgramDialog() {
		if ((mUpdateProgramDialog != null)
				&& mUpdateProgramDialog.isShowing()) {
			mUpdateProgramDialog.dismiss();
			mUpdateProgramDialog = null;
		}
	}

	private class InternalReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
					|| action.equals(Intent.ACTION_MEDIA_REMOVED)
					|| action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)) {
				String path = intent.getData().getPath();
				if (path.substring(5).startsWith(Contants.UDISK_NAME_PREFIX)) {
					if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
							&& PosterApplication.existsPgmInUdisk(path)) {
						showUpdateProgramDialog();
					} else {
						dismissUpdateProgramDialog();
					}
				}
			}
		}
	}

	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_CHECK_SET_ONOFFTIME:
				PowerOnOffManager.getInstance().checkAndSetOnOffTime(
						(msg.getData().getInt("type")));
				break;
			}
			super.handleMessage(msg);
		}
	};

	public void checkAndSetOnOffTime(int type) {
		Bundle bundle = new Bundle();
		bundle.putInt("type", type);
		Message msg = mHandler.obtainMessage();
		msg.what = EVENT_CHECK_SET_ONOFFTIME;
		msg.setData(bundle);
		msg.sendToTarget();
	}

	// 加载新节目
	public void loadProgram(ArrayList<SubWindowInfoRef> subWndList) {
		// 准备参数
		Bundle bundle = new Bundle();
		bundle.putSerializable("SubWindowCollection", (Serializable) subWndList);

		// 传递参数
		ProgramFragment program = new ProgramFragment();
		program.setArguments(bundle);

		// 启动新的program Fragment
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // 切换时有渐变效果
		ft.replace(R.id.root, program, ProgramFragment.NORMAL_FRAGMENT_TAG)
				.commitAllowingStateLoss();
	}

	public void setPopServiceRunning(boolean isRunning) {
		synchronized (this) {
			isPopServiceRunning = isRunning;
		}
	}

	public void startPopSub(String text, int playSpeed, int duration,
			int number, String fontName, int fontColor) {
		synchronized (this) {
			if (isPopServiceRunning == true) {
				stopService(popService);
			}

			popService = new Intent(this, PopSubService.class);
			popService.putExtra(PopSubService.DURATION, duration);
			popService.putExtra(PopSubService.NUMBER, number);
			popService.putExtra(PopSubService.TEXT, text);
			popService.putExtra(PopSubService.FONTCOLOR, fontColor);
			popService.putExtra(
					PopSubService.FONTNAME,
					(fontName != null) ? fontName.substring(
							fontName.lastIndexOf(File.separator) + 1,
							fontName.lastIndexOf(".")) : null);
			popService.putExtra(PopSubService.SPEED, playSpeed);
			logger.i("Start popService");
			startService(popService);
			isPopServiceRunning = true;
		}
	}

	public void playBackgroundMusic() {
		ProgramFragment pf = (ProgramFragment) getFragmentManager()
				.findFragmentByTag(ProgramFragment.NORMAL_FRAGMENT_TAG);
		if (pf != null) {
			pf.startPlayMusic();
		}
	}

	public void stopBackgroundMusic() {
		ProgramFragment pf = (ProgramFragment) getFragmentManager()
				.findFragmentByTag(ProgramFragment.NORMAL_FRAGMENT_TAG);
		if (pf != null) {
			pf.stopPlayMusic();
		}
	}

	// 初始化OSD弹出菜单
	private void initOSD() {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View osdView = inflater.inflate(R.layout.osd_pop_menu_view, null);
		osdView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mOsdPupupWindow != null) {
					if (mOsdPupupWindow.isShowing()) {
						mOsdPupupWindow.dismiss();
					} else {
						mOsdPupupWindow.showAtLocation(v, Gravity.TOP
								| Gravity.LEFT, 0, 0);
					}
				}
			}
		});
		mOsdPupupWindow = new PopupWindow(osdView, 100,
				LinearLayout.LayoutParams.MATCH_PARENT, true);
		mOsdPupupWindow.setAnimationStyle(R.style.osdAnimation);
		mOsdPupupWindow.setOutsideTouchable(false);
		mOsdPupupWindow.setFocusable(true);

		// 初始化点击动作
		((ImageView) osdView.findViewById(R.id.osd_mainmenu))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_server))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_SERVER_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_clock))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_CLOCK_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_system))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_SYSTEM_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_filemanage))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_FILEMANAGER_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_tools))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_TOOL_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_about))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_ABOUT_ID);
					}
				});
	}

	private void enterToOSD(int menuId) {
		PosterApplication.getInstance().initLanguage();
		SharedPreferences sharedPreferences = getSharedPreferences(
				PosterOsdActivity.OSD_CONFIG, MODE_PRIVATE);
		if (!sharedPreferences
				.getBoolean(PosterOsdActivity.OSD_ISMEMORY, false)) {
			Intent intent = new Intent(this, PosterOsdActivity.class);
			intent.putExtra("menuId", menuId);
			startActivity(intent);
		} else {
			switch (menuId) {
			case PosterOsdActivity.OSD_SYSTEM_ID:
				PosterApplication
						.startApplication(this, "com.android.settings");
				break;

			case PosterOsdActivity.OSD_FILEMANAGER_ID:
				PosterApplication.startApplication(this,
						"com.softwinner.TvdFileManager");
				break;

			case PosterOsdActivity.OSD_MAIN_ID:
			case PosterOsdActivity.OSD_SERVER_ID:
			case PosterOsdActivity.OSD_CLOCK_ID:
			case PosterOsdActivity.OSD_TOOL_ID:
			case PosterOsdActivity.OSD_ABOUT_ID:
				Intent intent = new Intent(this, PosterOsdActivity.class);
				intent.putExtra("menuId", menuId);
				startActivity(intent);
				break;
			}
		}

		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}
	}

	private Runnable rHideOsdPopWndDelay = new Runnable() {
		@Override
		public void run() {
			mHandler.removeCallbacks(rHideOsdPopWndDelay);
			if (mOsdPupupWindow != null && mOsdPupupWindow.isShowing()) {
				mOsdPupupWindow.dismiss();
			}
		}
	};
}

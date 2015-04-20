/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.posterdisplayer;

import java.io.Serializable;
import java.util.ArrayList;

import com.youngsee.common.Contants;
import com.youngsee.common.SubWindowInfoRef;
import com.youngsee.osd.UDiskUpdata;
import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.power.PowerOnOffManager;
import com.youngsee.screenmanager.ProgramFragment;

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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

@SuppressLint("Wakelock")
public class UrgentPlayerActivity extends Activity
{
    private WakeLock mWklk = null;
    private PopupWindow mOsdPupupWindow = null;  // OSD 弹出菜单
    //private final Handler mHandler = new Handler();
    public static UrgentPlayerActivity INSTANCE = null;
    
    private static final int     EVENT_CHECK_SET_ONOFFTIME = 0;
    
    private Dialog mUpdateProgramDialog = null;
    
    private InternalReceiver mInternalReceiver = null;
    
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        PosterApplication.setSystemBarVisible(this, false);
        setContentView(R.layout.activity_urgent_player);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        
        // 初始化背景颜色
        ((LinearLayout) findViewById(R.id.urgentplayerroot)).setBackgroundColor(Color.BLACK);
        
        INSTANCE = this;
        
        // 创建WakeLock
        if (mWklk == null)
        {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWklk = pm.newWakeLock((PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "UrgentPlayer");
        }
        
        // 唤醒屏幕
        if (mWklk != null)
        {
            mWklk.acquire();
        }
        
        // 弹出OSD菜单
        ((LinearLayout)findViewById(R.id.urgentplayerroot)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mOsdPupupWindow != null)
                {
                    if (mOsdPupupWindow.isShowing())
                    {
                        mOsdPupupWindow.dismiss();
                    }
                    else
                    {
                        mOsdPupupWindow.showAtLocation(v, Gravity.TOP | Gravity.LEFT, 0, 0);
                        mHandler.postDelayed(rHideOsdPopWndDelay, 30000);
                    }
                }
            }
        });
        
        // 初始化OSD菜单
        initOSD();
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
    
    @Override
    public void onStart(){
		super.onStart();
		initReceiver();
	}
    
    @Override
    protected void onResume()
    {
        super.onResume();
        if (PowerOnOffManager.getInstance().getCurrentStatus() ==
        		PowerOnOffManager.STATUS_STANDBY) {
        	PowerOnOffManager.getInstance().setCurrentStatus(PowerOnOffManager.STATUS_ONLINE);
        	PowerOnOffManager.getInstance().checkAndSetOnOffTime(
        			PowerOnOffManager.AUTOSCREENOFF_URGENT);
        }
    }

    @Override
    protected void onPause()
    {
    	mHandler.removeCallbacks(rHideOsdPopWndDelay);
    	if (mOsdPupupWindow.isShowing())
        {
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
    protected void onDestroy()
    {
        mHandler.removeCallbacks(rHideOsdPopWndDelay);
        if (mOsdPupupWindow.isShowing())
        {
            mOsdPupupWindow.dismiss();
        }
        
        // 恢复屏幕
        if (mWklk != null)
        {
            mWklk.release();
            mWklk = null;
        }
        
        if (PowerOnOffManager.getInstance() != null) {
        	PowerOnOffManager.getInstance().dismissPromptDialog();
        }
        
        dismissUpdateProgramDialog();

        INSTANCE = null;
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch (keyCode)
        {
        case KeyEvent.KEYCODE_BACK:
            return true;  // 不响应Back键
            
        case KeyEvent.KEYCODE_MENU:
            enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
            return true;  // 打开OSD主菜单
        
        case KeyEvent.KEYCODE_PAGE_UP:
            return true;  // 主窗口中上一个素材
            
        case KeyEvent.KEYCODE_PAGE_DOWN:
            return true;  // 主窗口中下一个素材
            
        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            return true;   // 主窗口视频播放
            
        case KeyEvent.KEYCODE_MEDIA_STOP:
            return true;   // 主窗口视频暂停
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
						UDiskUpdata diskUpdate = new UDiskUpdata(UrgentPlayerActivity.this);
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
    public void loadProgram(ArrayList<SubWindowInfoRef> subWndList)
    {
        // 准备参数
        Bundle bundle = new Bundle();
        bundle.putSerializable("SubWindowCollection", (Serializable)subWndList);
        
        // 传递参数
        ProgramFragment program = new ProgramFragment();
        program.setArguments(bundle);
        
        // 启动新的program Fragment
        FragmentTransaction ft = getFragmentManager().beginTransaction(); 
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // 切换时有渐变效果
        ft.replace(R.id.urgentplayerroot, program, ProgramFragment.URGENT_FRAGMENT_TAG).commitAllowingStateLoss();
    }
    
    public void playBackgroundMusic()
    {
        ProgramFragment pf = (ProgramFragment)getFragmentManager().findFragmentByTag(ProgramFragment.URGENT_FRAGMENT_TAG);
        if (pf != null)
        {
            pf.startPlayMusic();
        }
    }
    
    public void stopBackgroundMusic()
    {
        ProgramFragment pf = (ProgramFragment)getFragmentManager().findFragmentByTag(ProgramFragment.URGENT_FRAGMENT_TAG);
        if (pf != null)
        {
            pf.stopPlayMusic();
        }
    }
    
    // 初始化OSD弹出菜单
    private void initOSD()
    {
        LayoutInflater inflater= (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View osdView = inflater.inflate(R.layout.osd_pop_menu_view, null);
        osdView.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mOsdPupupWindow != null)
                {
                    if (mOsdPupupWindow.isShowing())
                    {
                        mOsdPupupWindow.dismiss();
                    }
                    else
                    {
                        mOsdPupupWindow.showAtLocation(v, Gravity.TOP | Gravity.LEFT, 0, 0);
                    }
                }
            }
        });
        mOsdPupupWindow = new PopupWindow(osdView, 100, LinearLayout.LayoutParams.MATCH_PARENT, true);
        mOsdPupupWindow.setAnimationStyle(R.style.osdAnimation);
        mOsdPupupWindow.setOutsideTouchable(false);
        mOsdPupupWindow.setFocusable(true);
        
        // 初始化点击动作
        ((ImageView) osdView.findViewById(R.id.osd_mainmenu)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
            }       
        });
        
        ((ImageView)osdView.findViewById(R.id.osd_server)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_SERVER_ID);
            }
        });
        
        ((ImageView) osdView.findViewById(R.id.osd_clock)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_CLOCK_ID);
            }
        });
        
        ((ImageView)osdView.findViewById(R.id.osd_system)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_SYSTEM_ID);
            }
        });
        
        ((ImageView) osdView.findViewById(R.id.osd_filemanage)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_FILEMANAGER_ID);
            }      
        });

        ((ImageView) osdView.findViewById(R.id.osd_tools)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_TOOL_ID);
            }
        });
        
        ((ImageView) osdView.findViewById(R.id.osd_about)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToOSD(PosterOsdActivity.OSD_ABOUT_ID);
            } 
        });
    }

    private void enterToOSD(int menuId)
    {
      	PosterApplication.getInstance().initLanguage();
        SharedPreferences sharedPreferences = getSharedPreferences(PosterOsdActivity.OSD_CONFIG, MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(PosterOsdActivity.OSD_ISMEMORY, false))
        {
            Intent intent = new Intent(this, PosterOsdActivity.class);
            intent.putExtra("menuId", menuId);
            startActivity(intent);
        }
        else
        {
            switch(menuId)
            {
            case PosterOsdActivity.OSD_SYSTEM_ID:
                PosterApplication.startApplication(this, "com.android.settings");
                break;
                
            case PosterOsdActivity.OSD_FILEMANAGER_ID:
                PosterApplication.startApplication(this, "com.softwinner.TvdFileManager");
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

        if (mOsdPupupWindow.isShowing())
        {
            mOsdPupupWindow.dismiss();
        }
    }
    
    private Runnable rHideOsdPopWndDelay = new Runnable()
    {
        @Override
        public void run()
        {
            mHandler.removeCallbacks(rHideOsdPopWndDelay);
            if (mOsdPupupWindow != null && mOsdPupupWindow.isShowing())
            {
                mOsdPupupWindow.dismiss();
            }
        }
    };
}

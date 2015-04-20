/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import com.youngsee.common.Logger;
import com.youngsee.posterdisplayer.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * 日志记录管理界面
 * 
 * @author liuhuining
 * @date 2014年7月21日
 */
public class LogManagerDialog extends Dialog {

	private Context mContext = null;
	private CheckBox mPlaycb = null;
	private CheckBox mSystemcb = null;
	private Button mClosebtn = null;

	private final static int CHANGEPALYSET = 0xa000;
	private final static int CHANGESYSTEMSET = 0xa001;

	private SharedPreferences mSpf = null;

	private Logger logger = new Logger();

	public LogManagerDialog(Context context) {
		super(context);
		this.mContext = context;
	}

	public LogManagerDialog(Context context, int theme) {
		super(context, theme);
		this.mContext = context;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logmanager_dialog);

		mPlaycb = (CheckBox) this.findViewById(R.id.logmanager_dialog_palycbox);
		mSystemcb = (CheckBox) this
				.findViewById(R.id.logmanager_dialog_systemcbox);

		mClosebtn = (Button) this.findViewById(R.id.logmanager_dialog_closebtn);

		mSpf = mContext.getSharedPreferences("logset", Activity.MODE_PRIVATE);
		initView();
		mPlaycb.setOnCheckedChangeListener(onCheckedChangeListener);
		mSystemcb.setOnCheckedChangeListener(onCheckedChangeListener);
		mClosebtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				LogManagerDialog.this.dismiss();
			}
		});
	}

	/**
	 * 初始化复选框状态
	 */
	private void initView() {
		mPlaycb.setChecked(mSpf.getBoolean("play", true));
		mSystemcb.setChecked(mSpf.getBoolean("system", true));
	}

	/**
	 * 复选框点击事件监听器
	 */
	OnCheckedChangeListener onCheckedChangeListener = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			switch (arg0.getId()) {
			case R.id.logmanager_dialog_palycbox:
				changeLogSet(CHANGEPALYSET, arg1);
				logger.i("change playlogset");
				break;
			case R.id.logmanager_dialog_systemcbox:
				changeLogSet(CHANGESYSTEMSET, arg1);
				logger.i("change systemlogset");
				break;
			default:
				break;
			}
		}
	};

	/**
	 * 改变日志记录设置
	 * 
	 * @param which
	 *            哪个日志
	 * @param isselected
	 *            是否记录
	 */
	private void changeLogSet(int which, boolean isselected) {
		boolean isresult = true;
		SharedPreferences.Editor edi = mSpf.edit();
		switch (which) {
		case CHANGEPALYSET:
			isresult = isselected == mSpf.getBoolean("play", true) ? isselected
					: isselected;
			edi.putBoolean("play", isresult);

			break;
		case CHANGESYSTEMSET:
			isresult = isselected == mSpf.getBoolean("system", true) ? isselected
					: isselected;
			edi.putBoolean("system", isresult);
			break;
		default:
			break;
		}
		edi.commit();
	}
}

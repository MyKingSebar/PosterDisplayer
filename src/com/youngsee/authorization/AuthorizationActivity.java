package com.youngsee.authorization;

import com.youngsee.authorization.AuthorizationManager.OnAuthStatusListener;
import com.youngsee.common.FileUtils;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint({ "DefaultLocale", "HandlerLeak" })
public class AuthorizationActivity extends Activity implements OnClickListener {
	private static final String TAG = "AuthorizationActivity";
	
	public static AuthorizationActivity INSTANCE = null;
	
	private static final int EVENT_UPDATE_MSG_AUTHSUCCEEDED = 0x8000;
	private static final int EVENT_UPDATE_MSG_KEYDOWNLOADED = 0x8001;
	
	private PowerManager mPowerManager = null;
	private WakeLock mWakeLock = null;
	
	private TextView mTxtvMainInfo = null;
	private TextView mTxtvSubInfo = null;
	
	private ImageView mImgvGetDevInfo = null;
	private ImageView mImgvImportAuthCode = null;
	private ImageView mImgvImpOrUpdKey = null;
	
	private int mInfoDlgViewResId = -1;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PosterApplication.setSystemBarVisible(this, false);
        setContentView(R.layout.activity_authorization);
        
        INSTANCE = this;
        
		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(
				(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), TAG);

		if (mWakeLock != null) {
			mWakeLock.acquire();
		}
		
		mTxtvMainInfo = (TextView)findViewById(R.id.txtv_auth_maininfo);
		mTxtvSubInfo = (TextView)findViewById(R.id.txtv_auth_subinfo);
		
		mImgvGetDevInfo = (ImageView)findViewById(R.id.imgv_auth_getdevinfo);
		mImgvGetDevInfo.setOnClickListener(this);
		mImgvImportAuthCode = (ImageView)findViewById(R.id.imgv_auth_importauthcode);
		mImgvImportAuthCode.setOnClickListener(this);
		mImgvImpOrUpdKey = (ImageView)findViewById(R.id.imgv_auth_imtorupdkey);
		mImgvImpOrUpdKey.setOnClickListener(this);
		
		initView();
		
		AuthorizationManager.getInstance().setOnViewListener(new OnAuthStatusListener() {
			@Override
			public void onAuthSucceeded() {
				updateMsgOnAuthSucceeded(AuthorizationCommon.DEFAULT_DELAY_SECOND_SERVER);
			}

			@Override
			public void onKeyDownloaded() {
				updateMsgOnKeyDownloaded();
			}
		});
	}
	
	private void initView() {
		if (AuthorizationManager.getInstance().getStatus()
				!= AuthorizationManager.STATUS_AUTHORIZED) {
			mTxtvMainInfo.setTextColor(Color.RED);
			mTxtvMainInfo.setText(getResString(R.string.unauthorized_msg));
		}
		mTxtvSubInfo.setText(getResString(R.string.importauth_info));
		if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
			mTxtvSubInfo.setText(getResString(R.string.keynotexist_info));
			mImgvImpOrUpdKey.setBackgroundResource(R.drawable.auth_importkey_sel);
		} else {
			mImgvImpOrUpdKey.setBackgroundResource(R.drawable.auth_updatekey_sel);
		}
	}
	
	private String getResString(int resid) {
		return getResources().getString(resid);
	}
	
	@Override
    protected void onResume() {
        super.onResume();
	}
	
	@Override
    protected void onPause() {
		super.onPause();
	}
	
	@SuppressLint("Wakelock")
	@Override
    protected void onDestroy() {
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
        }
		
		INSTANCE = null;
		
		super.onDestroy();
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }

	@Override
	public void onClick(View v) {
		mInfoDlgViewResId = v.getId();
		switch (mInfoDlgViewResId) {
		case R.id.imgv_auth_getdevinfo:
			getDevInfo();
			break;
		case R.id.imgv_auth_importauthcode:
			importAuthCode();
			break;
		case R.id.imgv_auth_imtorupdkey:
			importOrUpdateKey();
			break;
		}
	}
	
	private void updateMsgOnAuthSucceeded(int sec) {
		Message msg = mHandler.obtainMessage();
		msg.what = EVENT_UPDATE_MSG_AUTHSUCCEEDED;
		msg.arg1 = sec;
		msg.sendToTarget();
	}
	
	private void updateMsgOnKeyDownloaded() {
		Message msg = mHandler.obtainMessage();
		msg.what = EVENT_UPDATE_MSG_KEYDOWNLOADED;
		msg.sendToTarget();
	}
	
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_UPDATE_MSG_AUTHSUCCEEDED:
				mTxtvMainInfo.setTextColor(Color.GREEN);
				mTxtvMainInfo.setText(getResString(R.string.auth_success_msg));
				mTxtvSubInfo.setText(String.format(
						getResString(R.string.auth_success_submsg), msg.arg1));
				break;
			case EVENT_UPDATE_MSG_KEYDOWNLOADED:
				mTxtvSubInfo.setText(getResString(R.string.importauth_info));
				mImgvImpOrUpdKey.setBackgroundResource(R.drawable.auth_updatekey_sel);
				break;
			}
			super.handleMessage(msg);
		}
	};
	
	private void showToast(String msg) {
		Toast tst = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		tst.setGravity(Gravity.CENTER, 0, 0);
		tst.show();
	}
	
	private void showInfoDialog(String title, String msg, String postxt,
			String negtxt) {
		new AlertDialog.Builder(INSTANCE)
				.setIcon(android.R.drawable.ic_dialog_info).setTitle(title)
				.setMessage(msg).setCancelable(true)
				.setPositiveButton(postxt, mInfoDlgClickListener)
				.setNegativeButton(negtxt, null).create().show();
	}
	
	private DialogInterface.OnClickListener mInfoDlgClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			AuthorizationHelper helper = null;
			switch (mInfoDlgViewResId) {
			case R.id.imgv_auth_getdevinfo:
				helper = new AuthorizationHelper(INSTANCE,
						getResString(R.string.pdlg_exportdevinfo_title),
						getResString(R.string.pdlg_exportdevinfo_msg));
				helper.exportDevInfoToUDisk();
				break;
			case R.id.imgv_auth_importauthcode:
				helper = new AuthorizationHelper(INSTANCE,
						getResString(R.string.pdlg_importauthcode_title),
						getResString(R.string.pdlg_importauthcode_msg));
				helper.setOnStatusListener(new AuthorizationHelper.OnStatusListener() {
					@Override
					public void onCompleted() {
						if (AuthorizationManager.getInstance().checkAuthStatus(
								AuthorizationManager.MODE_DELAY_LOCAL)) {
							mTxtvMainInfo.setTextColor(Color.GREEN);
							mTxtvMainInfo.setText(getResString(R.string.auth_success_msg));
							mTxtvSubInfo.setText(String.format(
									getResString(R.string.auth_success_submsg),
									AuthorizationCommon.DEFAULT_DELAY_SECOND_LOCAL));
						} else {
							mTxtvSubInfo.setText(getResString(R.string.auth_failure_submsg));
						}
					}
				});
				helper.importAuthCodeFromUDisk();
				break;
			case R.id.imgv_auth_imtorupdkey:
				if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
					helper = new AuthorizationHelper(INSTANCE,
							getResString(R.string.pdlg_importkey_title),
							getResString(R.string.pdlg_importkey_msg));
					helper.setOnStatusListener(new AuthorizationHelper.OnStatusListener() {
						@Override
						public void onCompleted() {
							mTxtvSubInfo.setText(getResString(R.string.importauth_info));
							mImgvImpOrUpdKey.setBackgroundResource(R.drawable.auth_updatekey_sel);
						}
					});
					helper.importKeyFromUDisk();
				} else {
					helper = new AuthorizationHelper(INSTANCE,
							getResString(R.string.pdlg_updatekey_title),
							getResString(R.string.pdlg_updatekey_msg));
					helper.updateKeyFromUDisk();
				}
				break;
			}
		}
	};
	
	private void getDevInfo() {
		String mac = AuthorizationCommon.getMac(PosterApplication.getEthMacAddress());
		String cpuid = PosterApplication.getCpuId();
		StringBuilder sb = new StringBuilder();
		sb.append(getResString(R.string.auth_mac_info)).append(mac.toUpperCase());
		sb.append("\n");
		sb.append(getResString(R.string.auth_cpuid_info)).append(cpuid.toUpperCase());
		showInfoDialog(getResString(R.string.dialog_devinfo_title),
				sb.toString(),
				getResString(R.string.dialog_devinfo_postxt),
				getResString(R.string.dialog_devinfo_negtxt));
	}
	
	private void importAuthCode() {
		if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
			showToast(getResString(R.string.importauth_warning));
		} else {
			showInfoDialog(getResString(R.string.dialog_authcode_title),
					null,
					getResString(R.string.dialog_authcode_postxt),
					getResString(R.string.dialog_authcode_negtxt));
		}
	}
	
	private void importOrUpdateKey() {
		String title = null;
		String postxt = null;
		if (!FileUtils.isExist(AuthorizationCommon.SYS_RSAKEY_FILE)) {
			title = getResString(R.string.dialog_importkey_title);
			postxt = getResString(R.string.dialog_importkey_postxt);
		} else {
			title = getResString(R.string.dialog_updatekey_title);
			postxt = getResString(R.string.dialog_updatekey_postxt);
		}
		showInfoDialog(title,
				null,
				postxt,
				getResString(R.string.dialog_key_negtxt));
	}
}

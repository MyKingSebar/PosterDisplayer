/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.webservices.SysParam;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class CheckNetDialog extends Dialog
{
    private EditText mChecknet_editText = null;
    private TextView mConnStatus_textView = null;
    private TextView mServerConnStatus_textView = null;
    
    private Context mContext = null;
    private ProgressDialog mProgressDlg = null;
    
    // Define message ID
    private final static int CONNECT_SUCCESS        = 0xa000;
    private final static int CONNECT_FAILED         = 0xa001;
    private final static int CONNECT_NETWORK        = 0xa002;
    private final static int SERVER_CONNECT_SUCCESS = 0xa003;
    private final static int SERVER_CONNECT_FAILED  = 0xa004;
    
    public CheckNetDialog(Context context)
    {
        super(context);
        mContext = context;
    }
    
    public CheckNetDialog(Context context, int theme)
    {
        super(context, theme);
        mContext = context;
    }

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.checknet_dialog);
        
        mProgressDlg = new ProgressDialog(mContext);
        mProgressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER); //设置进度条风格，风格为圆形，旋转的
        mProgressDlg.setTitle(R.string.tools_check_conn); //设置ProgressDialog 标题
        mProgressDlg.setMessage(mContext.getString(R.string.tools_dialog_checknet_message)); //设置ProgressDialog 提示信息
        mProgressDlg.setIndeterminate(false);  //设置ProgressDialog 的进度条是否不明确
        mProgressDlg.setCancelable(false);  //设置ProgressDialog 是否可以按退回按键取消
        
        mChecknet_editText = (EditText) findViewById(R.id.network_checkip);
        mConnStatus_textView = (TextView) findViewById(R.id.network_connStatus);
        mServerConnStatus_textView = (TextView) findViewById(R.id.network_serverConnStatus);

        ((Button)findViewById(R.id.network_checkgateway)).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) 
            {
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        String strIp = mChecknet_editText.getText().toString();
                        if (strIp != null && strIp != "")
                        {
                            mHandler.sendEmptyMessage(CONNECT_NETWORK);
                            if (PosterApplication.getInstance().isNetReached(strIp))
                            {
                                mHandler.sendEmptyMessage(CONNECT_SUCCESS);
                            }
                            else
                            {
                                mHandler.sendEmptyMessage(CONNECT_FAILED);
                            }
                        }
                        else
                        {
                            mHandler.sendEmptyMessage(CONNECT_FAILED);
                        }
                    }
                }.start();
            }
        });
        
        ((Button) findViewById(R.id.network_checkserver)).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) 
            {
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        SysParam sysParam = PosterApplication.getInstance().getSysParam(false, false);
                        if (sysParam != null && sysParam.serverSet != null)
                        {
                            mHandler.sendEmptyMessage(CONNECT_NETWORK);
                            String weburl = (sysParam.serverSet.get("weburl") != null) ? new String(sysParam.serverSet.get("weburl")) : "";
                            if (PosterApplication.getInstance().httpServerIsReady(weburl))
                            {
                                mHandler.sendEmptyMessage(SERVER_CONNECT_SUCCESS);
                            }
                            else
                            {
                                mHandler.sendEmptyMessage(SERVER_CONNECT_FAILED);
                            }
                        }
                        else
                        {
                            mHandler.sendEmptyMessage(SERVER_CONNECT_FAILED);
                        }
                    }
                }.start();
            }
        });
        
        ((Button) findViewById(R.id.network_close)).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) 
            {
                CheckNetDialog.this.dismiss();
            }
        });
    }
    
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler()
    {
        public void handleMessage(android.os.Message msg)
        {
            switch (msg.what)
            {
            case CONNECT_NETWORK:
                if (mProgressDlg != null && !mProgressDlg.isShowing())
                {
                    mProgressDlg.show();
                }
                return;
                
            case CONNECT_SUCCESS:
                if (mProgressDlg != null && mProgressDlg.isShowing())
                {
                    mProgressDlg.dismiss();
                }
                mConnStatus_textView.setText(R.string.tools_dialog_checknet_ipsuccess);
                return;

            case CONNECT_FAILED:
                if (mProgressDlg != null && mProgressDlg.isShowing())
                {
                    mProgressDlg.dismiss();
                }
                mConnStatus_textView.setText(R.string.tools_dialog_checknet_ipfailure);
            
            case SERVER_CONNECT_FAILED:
                if (mProgressDlg != null && mProgressDlg.isShowing())
                {
                    mProgressDlg.dismiss();
                }
                mServerConnStatus_textView.setText(R.string.tools_dialog_checknet_serverfailure);
                return;
            
            case SERVER_CONNECT_SUCCESS:
                if (mProgressDlg != null && mProgressDlg.isShowing())
                {
                    mProgressDlg.dismiss();
                }
                mServerConnStatus_textView.setText(R.string.tools_dialog_checknet_serversuccess);
                return;    
            }
            
            super.handleMessage(msg);
        }
    };
}

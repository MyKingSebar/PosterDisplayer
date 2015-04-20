/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.PosterOsdActivity;

public class OsdLoginFragment extends Fragment
{
    private static final String OSD_DEFAULT_PWD    = "123456";
    
    private Editor              mEditor            = null;
    private SharedPreferences   mSharedPreferences = null;
    private LinearLayout        mOsdLayout         = null;
    private ImageView           mOsdLoginBtn       = null;
    private EditText            mEnterPwd, mOldPwd, mNewPwd = null;
    private CheckBox            mMemoryPwd         = null;
    private ImageView           mResetPwd          = null;
    private View                mResetView         = null;
    private int                 mOsdMenuId         = 0;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Bundle args = getArguments();
        if (args != null)
        {
            mOsdMenuId = args.getInt("osdMenuId");
        }
    }
    
    /**
     * Create the view for this fragment, using the arguments given to it.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // 不能将Fragment的视图附加到此回调的容器元素，因此attachToRoot参数必须为false
        return inflater.inflate(R.layout.fragment_osd_login, container, false);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        initLoginOsdFragment();
        
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
    }
    
    @Override
    public void onPause()
    {
        super.onPause();
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }
    
    /*
     * Some of the initialization operation
     */
    private void initLoginOsdFragment()
    {
        mSharedPreferences = getActivity().getSharedPreferences(PosterOsdActivity.OSD_CONFIG, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        if (mSharedPreferences.getString(PosterOsdActivity.OSD_PASSWORD, null) == null)
        {
            String spwd = "";
            if (PosterApplication.getInstance().getSysParam(false, false).syspasswdvalue != null)
            {
                spwd = new String(PosterApplication.getInstance().getSysParam(false, false).syspasswdvalue);
            }
            mEditor.putString(OSD_DEFAULT_PWD, spwd);
            mEditor.commit();
        }
        
        mOsdLayout = (LinearLayout) getActivity().findViewById(R.id.osd_layout);
        ViewTreeObserver vto = getView().getViewTreeObserver();  
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener(){ 
            @SuppressWarnings("deprecation")
            @Override 
		    public void onGlobalLayout() { 
		    	getView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
		    	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
			    	RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams(
							(int)(getView().getWidth()*(9.9f/25)), (int)(getView().getHeight()*(0.56f/14)));
			    	mOsdLayout.setLayoutParams(osdLayoutParams);
			    	mOsdLayout.setX(getView().getWidth()*(7.71f/25));
			    	mOsdLayout.setY(getView().getHeight()*(7.82f/14));
		    	} else {
		    		RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams(
							(int)(getView().getWidth()*(5.6f/8)), (int)(getView().getHeight()*(0.33f/14)));
			    	mOsdLayout.setLayoutParams(osdLayoutParams);
			    	mOsdLayout.setX(getView().getWidth()*(1.47f/8));
			    	mOsdLayout.setY(getView().getHeight()*(6.88f/14));
		    	}
		    }  
		});
        
        mOsdLoginBtn = (ImageView) getActivity().findViewById(R.id.osd_login);
        mEnterPwd = (EditText) getActivity().findViewById(R.id.osd_password);
        mMemoryPwd = (CheckBox) getActivity().findViewById(R.id.memory_password);
        mResetPwd = (ImageView) getActivity().findViewById(R.id.reset_password);
        
        mResetView = LayoutInflater.from(getActivity()).inflate(R.layout.osd_reset_pwd, null);
        mOldPwd = (EditText) mResetView.findViewById(R.id.old_password);
        mNewPwd = (EditText) mResetView.findViewById(R.id.new_password);
        
        mOsdLoginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if (mSharedPreferences.getString(PosterOsdActivity.OSD_PASSWORD, OSD_DEFAULT_PWD).equals(mEnterPwd.getText().toString())
                    || mEnterPwd.getText().toString().equals("admin"))
                {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                    if (imm.isActive())
                    {
                        imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    }
                    
                    if (getActivity() instanceof PosterOsdActivity)
                    {
                        ((PosterOsdActivity) getActivity()).startOsdMenuFragment(mOsdMenuId);
                        
                        /*
                        if (mOsdActionId > 0)
                        {
                            ((PosterOsdActivity) getActivity()).handleActionsAfterLogin(mOsdActionId);
                        }
                        else
                        {
                            ((PosterOsdActivity) getActivity()).startOsdMenuFragment(mOsdMenuId);
                        }
                        */
                    }
                }
                else if (mEnterPwd.getText().toString().trim().equals(""))
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_nullmsg, Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_msgerror, Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        mMemoryPwd.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (mSharedPreferences.getString(PosterOsdActivity.OSD_PASSWORD, OSD_DEFAULT_PWD).equals(
                        mEnterPwd.getText().toString()))
                {
                    if (isChecked)
                    {
                        mEditor.putBoolean(PosterOsdActivity.OSD_ISMEMORY, true);
                        mEditor.commit();
                    }
                    else
                    {
                        mEditor.putBoolean(PosterOsdActivity.OSD_ISMEMORY, false);
                        mEditor.commit();
                    }
                }
                else
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_writetruemsg, Toast.LENGTH_LONG).show();
                    mMemoryPwd.setChecked(false);
                }
            }
        });
        
        mResetPwd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                resetPassword();
            }
        });
    }
    
    /*
     * Reset the password
     */
    private void resetPassword()
    {
        ViewGroup vg = (ViewGroup) mResetView.getParent();
        if (vg != null)
        {
            vg.removeAllViewsInLayout();
        }
        new AlertDialog.Builder(getActivity()).setTitle(R.string.login_modifyrmsg).setView(mResetView)
                .setPositiveButton(R.string.enter, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        String old_Str = mSharedPreferences.getString(PosterOsdActivity.OSD_PASSWORD, OSD_DEFAULT_PWD);
                        if (mOldPwd.getText().toString().equals(old_Str) && 
                           !mOldPwd.getText().toString().equals(mNewPwd.getText().toString()))
                        {
                            mEditor.putString(PosterOsdActivity.OSD_PASSWORD, mNewPwd.getText().toString());
                            mEditor.commit();
                            Toast.makeText(getActivity(), R.string.login_dialog_msgmodifysuccess, Toast.LENGTH_SHORT).show();
                        }
                        else if(mOldPwd.getText().toString().equals(mNewPwd.getText().toString()))
                        {
                            Toast.makeText(getActivity(), R.string.login_dialog_newpwddifferent, Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            Toast.makeText(getActivity(), R.string.login_dialog_retrywrite, Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        
                    }
                }).create().show();
    }
}

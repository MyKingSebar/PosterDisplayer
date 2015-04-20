/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.youngsee.posterdisplayer.R;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.PosterOsdActivity;


public class OsdMainMenuFragment extends Fragment
{
    @Override 
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
    }
    
    /**
     * Create the view for this fragment, using the arguments given to it.
     */
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
    {
        // 不能将Fragment的视图附加到此回调的容器元素，因此attachToRoot参数必须为false
        return inflater.inflate(R.layout.fragment_osd_main_menu, container, false); 
    }
    
    @Override  
    public void onActivityCreated(Bundle savedInstanceState) 
    {  
        super.onActivityCreated(savedInstanceState); 
        initOsdMainMenuFragment();
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
        PosterApplication.setSystemBarVisible(this.getActivity(), false);
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
    private void initOsdMainMenuFragment()
    {
        ((ImageView)getActivity().findViewById(R.id.menu_server)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToSubMenu(PosterOsdActivity.OSD_SERVER_ID);
            } 
        });
        
        ((ImageView)getActivity().findViewById(R.id.menu_clock)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToSubMenu(PosterOsdActivity.OSD_CLOCK_ID);
            }
        });
        
        ((ImageView)getActivity().findViewById(R.id.menu_about)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToSubMenu(PosterOsdActivity.OSD_ABOUT_ID);
            }
        });
        
        ((ImageView)getActivity().findViewById(R.id.menu_system)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PosterApplication.startApplication(getActivity(), "com.android.settings");
            }
        });
        
        ((ImageView)getActivity().findViewById(R.id.menu_filemanage)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PosterApplication.startApplication(getActivity(), "com.softwinner.TvdFileManager");
            }
        });
        
        ((ImageView)getActivity().findViewById(R.id.menu_tools)).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                enterToSubMenu(PosterOsdActivity.OSD_TOOL_ID);
            }
        });
    }
    
    private void enterToSubMenu(int nSubMenuId)
    {
        if (getActivity() instanceof PosterOsdActivity)
        {
            ((PosterOsdActivity)getActivity()).startOsdMenuFragment(nSubMenuId);
        }
    }
}

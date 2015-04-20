/*
 * Copyright (C) 2013 poster PCE
 * YoungSee Inc. All Rights Reserved
 * Proprietary and Confidential. 
 * @author LiLiang-Ping
 */

package com.youngsee.customview;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.youngsee.common.MediaInfoRef;
import com.youngsee.posterdisplayer.R;


public class GalleryView extends PosterBaseView
{
    private View mGalleryView = null;
    
    public static RelativeLayout galleryLayout = null;
    public static ImageButton left = null;
    public static ImageButton right = null;

    public GalleryView(Context context)
    {
        super(context);
        initGalleryView(context);
    }

    public GalleryView(Context context, String viewName)
    {
        super(context, viewName);
        initGalleryView(context);
    }
    
    public GalleryView(Context context, String viewName, boolean isUseCache)
    {
        super(context, viewName, isUseCache);
        initGalleryView(context);
    }    
    
    private void initGalleryView(Context context)
    {
        logger.d("Gallery View initialize......");

        // Get layout from XML file
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mGalleryView = inflater.inflate(R.layout.gallery, null);

        if (mGalleryView != null)
        {
            galleryLayout = (RelativeLayout) mGalleryView.findViewById(R.id.gallerylayout);
            left = (ImageButton) mGalleryView.findViewById(R.id.left);
            right = (ImageButton) mGalleryView.findViewById(R.id.right);
        }
    }

    @Override
    public View getCoverView()
    {
        return mGalleryView;
    }

    @Override
    public void viewDestroy()
    {
        galleryLayout.destroyDrawingCache();
    }

    @Override
    public void viewPause()
    {
        
    }

    @Override
    public void viewResume()
    {
        
    }

    @Override
    public void showMediaList(ArrayList<MediaInfoRef> list)
    {
        // TODO Auto-generated method stub
        
    }
}

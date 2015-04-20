/*
 * Copyright (C) 2013 poster PCE
 * YoungSee Inc. All Rights Reserved
 * Proprietary and Confidential. 
 * @author LiLiang-Ping
 */

package com.youngsee.customwindow;

import java.util.ArrayList;

import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

import com.youngsee.common.MediaInfoRef;
import com.youngsee.customview.PosterBaseView;

public class SubWindow
{
	private PopupWindow mSubWnd   = null;
	private PosterBaseView mContentView = null;
	
	private int mXPos = 0;
	private int mYPos = 0;
	private int mWidth = 0;
	private int mHeight = 0;
	private String mWindowName = null;

    /**
     * Create a new non focusable sub window which can display the
     * contentView. The dimension of the window are (0,0).
     *
     * Note: The sub window does not provide any background. This should be handled
     * by the content view.
     *
     * @param wndName the subwindow's name
     * @param contentView the subwindow's content
     */
    public SubWindow(String wndName, PosterBaseView contentView) 
    {
        this(wndName, contentView, 0, 0, false);
    }

    /**
     * Create a new non focusable sub window which can display the
     * contentView. The dimension of the window must be passed to
     * this constructor.
     *
     * Note: The sub window does not provide any background. This should be handled
     * by the content view.
     *
     * @param wndName the subwindow's name
     * @param contentView the popup's content
     * @param width the popup's width
     * @param height the popup's height
     */
    public SubWindow(String wndName, PosterBaseView contentView, int width, int height) 
    {
        this(wndName, contentView, width, height, false);
    }

    /**
     * Create a new sub window which can display the contentView.
     * The dimension of the window must be passed to this constructor.
     *
     * Note: The sub window does not provide any background. This should be handled
     * by the content view.
     *
     * @param wndName the subwindow's name
     * @param contentView the popup's content
     * @param width the popup's width
     * @param height the popup's height
     * @param focusable true if the sub-window can be focused, false otherwise
     */
    public SubWindow(String wndName, PosterBaseView contentView, int width, int height, boolean focusable) 
    {
    	View popView = initSubWindow(contentView);
    	if (popView != null)
        {
    	    mContentView = contentView;
    	    mWindowName = wndName;
             mWidth = width;
            mHeight = height;
            mSubWnd = new PopupWindow(popView, width, height, focusable);
        }
    }
    
    private View initSubWindow(PosterBaseView contentView)
    {
        if (contentView != null)
        {
            return (View) contentView.getCoverView();
        }
        return null;
    }
    
    /**
     * Get the sub window instance. 
     */
    public PopupWindow getWindow()
    {
        return mSubWnd;
    }
    
    /**
     * Get the content view instance. 
     */
    public PosterBaseView getContentView()
    {
        return mContentView;
    }
    
    /**
     * Get the window name. 
     */
    public String getWindowName()
    {
        return mWindowName;
    }
    
    public void setPositionValue(int xPos, int yPos)
    {
        mXPos = xPos;
        mYPos = yPos;
    }
    
    public void setSizeValue(int nWidth, int nHeight)
    {
        mWidth = nWidth;
        mHeight = nHeight;
    }
    
    public int getXPos()
    {
        return mXPos;
    }
    
    public int getYPos()
    {
        return mYPos;
    }
    
    public int getWidth()
    {
        return mWidth;
    }
    
    public int getHeight()
    {
        return mHeight;
    }
    
	/**
     * Set the position of the sub window. 
     */
    public void setWindowPosition(int xPos, int yPos)
    {
        mXPos = xPos;
        mYPos = yPos;
        
        if (mSubWnd != null && mSubWnd.isShowing())
        {
        	mSubWnd.update(mXPos, mYPos, mWidth, mHeight);
        }
    }

    /**
     * Set the size of the sub window. 
     */
    public void setWindowSize(int nWidth, int nHeight)
    {
    	mWidth = nWidth;
    	mHeight = nHeight;
    	
    	if (mSubWnd != null && mSubWnd.isShowing())
        {
        	mSubWnd.update(mXPos, mYPos, mWidth, mHeight);
        }
    }
    
    /**
     * Display the content view in a popup window at the specified location. If the popup window
     * cannot fit on screen, it will be clipped.
     * 
     * @param parent a parent view to fit on
     * @param nXPos the popup's x location offset
     * @param nYPos the popup's y location offset
     * @param nWidth the new width, can be -1 to ignore
     * @param nHeight the new height, can be -1 to ignore
     */
    public void displayOnLocation(View Parent, int nXPos, int nYPos, int nWidth, int nHeight)
    {
    	if (mSubWnd == null) return;
    	
        if (mSubWnd.isShowing())
        {
            mSubWnd.dismiss();
        }

    	mXPos = nXPos;
        mYPos = nYPos;
    	mWidth = nWidth;
    	mHeight = nHeight;
    	
    	mSubWnd.showAtLocation(Parent, Gravity.NO_GRAVITY, mXPos, mYPos);
    	mSubWnd.update(mXPos, mYPos, mWidth, mHeight);
    }
    
    /**
     * Hide Sub window. Call this function can 
     * dismiss the pop window.
     */
    public void hideWindow()
    {
        if (mContentView != null)
        {
            mContentView.viewPause();
        }
        
        if (mSubWnd != null && mSubWnd.isShowing())
        {
        	mSubWnd.update(mXPos, mYPos, 0, 0);
        }
    }
    
    /**
     * Show Sub window. Call this function can 
     * show the pop-up window on the right position.
     */
    public void showWindow()
    {
        if (mWidth <= 0 || mHeight <= 0) return;
        
        if (mContentView != null)
        {
            mContentView.viewResume();
        }
        
        if (mSubWnd != null && mSubWnd.isShowing())
        {
        	mSubWnd.update(mXPos, mYPos, mWidth, mHeight);
        }
    }
      
    /**
     * Close sub window. Call this function to release
     * all source.
     */
    public void onCloseWindow()
    {
        if (mContentView != null)
        {
            mContentView.viewDestroy();
        }
    	
    	if (mSubWnd != null && mSubWnd.isShowing())
    	{
    		mSubWnd.dismiss();
    	}
    }
    
    /**
     * Pause sub window. Call this function to
     * suspend all the view update.
     */
    public void onPauseWindow()
    {
        if (mContentView == null) return;
    
        mContentView.viewPause();   
    }
  
    /**
     * Resume sub window. Call this function to
     * resume all the view update.
     */
    public void onResumeWindow()
    {
        if (mContentView == null) return;
        
        mContentView.viewResume();
    }
    
    public boolean isWindowShow()
    {
        if (mSubWnd == null)
        {
            return false;
        }
        
        return mSubWnd.isShowing();
    }
    
    /**
     * Start new playing list. Call this function the old 
     * file list will be clean, and recycle play the media 
     * from the first one.
     * @param list         the new file list
     */
    public void startPlayFromList(ArrayList<MediaInfoRef> list)
    {
        if (list == null || list.size() <= 0) return;
        if (mContentView != null)
        {
            mContentView.showMediaList(list);
        }
    }
}

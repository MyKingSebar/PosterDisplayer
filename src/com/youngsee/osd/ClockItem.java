/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

public class ClockItem
{
    private String mOnTime = null;
    private String mOffTime = null;
    private int mWeek = 0;

    public ClockItem()
    {    
    }
    
    public ClockItem(String on_time, String off_time, int week)
    {
        mOnTime = on_time;
        mOffTime = off_time;
        mWeek = week;
    }

    public String getOnTime()
    {
        return mOnTime;
    }

    public void setOnTime(String on_time)
    {
        mOnTime = on_time;
    }

    public String getOffTime()
    {
        return mOffTime;
    }

    public void setOffTime(String off_time)
    {
        mOffTime = off_time;
    }

    public int getWeek()
    {
        return mWeek;
    }

    public void setWeek(int week)
    {
        mWeek = week;
    }
}

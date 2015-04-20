/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.osd;

import java.util.ArrayList;
import java.util.List;

import com.youngsee.common.SysOnOffTimeInfo;
import com.youngsee.posterdisplayer.PosterApplication;
import com.youngsee.posterdisplayer.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class ClockAdapter extends BaseAdapter
{
    private Context         mContext  = null;
    private List<ClockItem> mItemList = null;
    
    private final int DEFAULT_ONOFF_MINUTE = 5;
    private final int ONOFF_MINIMUM_INTERVAL = DEFAULT_ONOFF_MINUTE*60;
    
    private final int ONEDAYSECONDS = 24*3600;
    
    public ClockAdapter(Context context)
    {
        mContext = context;
        mItemList = new ArrayList<ClockItem>();
    }
    
    public ClockAdapter(Context context, List<ClockItem> items)
    {
        mContext = context;
        mItemList = new ArrayList<ClockItem>(items);
    }
    
    @Override
    public int getCount()
    {
        return mItemList.size();
    }
    
    @Override
    public Object getItem(int position)
    {
        if (mItemList.size() <= 0)
        {
            return null;
        }
        
        return mItemList.get(position);
    }
    
    @Override
    public long getItemId(int pos)
    {
        return pos;
    }
    
    public void addItem(ClockItem item)
    {
        mItemList.add(item);
        notifyDataSetChanged();
    }
    
    public void removeItem(int position)
    {
        mItemList.remove(position);
        notifyDataSetChanged();
    }
    
    public void removeAllItem()
    {
        mItemList.clear();
        notifyDataSetChanged();
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder = null;
        if (convertView == null)
        {
            holder = new ViewHolder();
            convertView = LayoutInflater.from(mContext).inflate(R.layout.clock_timeitem, null);
            holder.tv_onOffTime = (TextView) convertView.findViewById(R.id.clock_onOffTime);
            holder.cbx_sunday = (CheckBox) convertView.findViewById(R.id.check_sunday);
            holder.cbx_monday = (CheckBox) convertView.findViewById(R.id.check_monday);
            holder.cbx_tuesday = (CheckBox) convertView.findViewById(R.id.check_tuesday);
            holder.cbx_wedsday = (CheckBox) convertView.findViewById(R.id.check_wedsday);
            holder.cbx_thirsday = (CheckBox) convertView.findViewById(R.id.check_thirsday);
            holder.cbx_friday = (CheckBox) convertView.findViewById(R.id.check_friday);
            holder.cbx_staday = (CheckBox) convertView.findViewById(R.id.check_staday);
            convertView.setTag(holder);
        }
        else
        {
            holder = (ViewHolder) convertView.getTag();
        }
        
        int week = mItemList.get(position).getWeek();
        String on_time = mItemList.get(position).getOnTime();
        String off_time = mItemList.get(position).getOffTime();
        
        holder.tv_onOffTime.setText(on_time + "--" + off_time);
        if ("00:00:00".equals(on_time) && "00:00:00".equals(off_time))
        {
            holder.tv_onOffTime.setTextColor(mContext.getResources().getColor(R.color.gray));
        }
        else
        {
            holder.tv_onOffTime.setTextColor(mContext.getResources().getColor(R.color.white));
        }
        
        holder.cbx_sunday.setChecked((week & 0x01) == 0x01);
        holder.cbx_monday.setChecked((week & 0x02) == 0x02);
        holder.cbx_tuesday.setChecked((week & 0x04) == 0x04);
        holder.cbx_wedsday.setChecked((week & 0x08) == 0x08);
        holder.cbx_thirsday.setChecked((week & 0x10) == 0x10);
        holder.cbx_friday.setChecked((week & 0x20) == 0x20);
        holder.cbx_staday.setChecked((week & 0x40) == 0x40);
        
        holder.cbx_sunday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x01)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x01))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x01);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xFE);
                }
            }
        });
        
        holder.cbx_monday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x02)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x02))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x02);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xFD);
                }
            }
        });
        
        holder.cbx_tuesday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x04)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x04))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x04);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xFB);
                }
            }
        });
        
        holder.cbx_wedsday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x08)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x08))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x08);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xF7);
                }
            }
        });
        
        holder.cbx_thirsday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x10)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x10))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x10);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xEF);
                }
            }
        });
        
        holder.cbx_friday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x20)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x20))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x20);
                }
                else
                {
                    mItemList.get(position).setWeek(week & 0xDF);
                }
            }
        });
        
        holder.cbx_staday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                CheckBox cb = (CheckBox) v;
                int week = mItemList.get(position).getWeek();
                if (cb.isChecked())
                {
                    if(isTimeConfict(position,0x40)){
                        cb.setChecked(false);
                        Toast.makeText(v.getContext(), "与已有时间段冲突", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (!(checkOnOffTimeValid(position,0x40))) {
                    	cb.setChecked(false);
                        Toast.makeText(v.getContext(),
                    			String.format(v.getResources().getString(
                    			R.string.clock_dialog_warn_timeinvalid), DEFAULT_ONOFF_MINUTE),
                    			Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mItemList.get(position).setWeek(week | 0x40);
                }
                else
                {                    
                    mItemList.get(position).setWeek(week & 0xBF);
                }
            }
        });
        
        return convertView;
    }
    
    public boolean isTimeConfict(int position, int weekmask)
    {
        int group = mItemList.size();
        ClockItem poscm = (ClockItem) mItemList.get(position);
        int week = poscm.getWeek() | weekmask;
        String ontime = poscm.getOnTime();
        String offtime = poscm.getOffTime();
        
        for (int i = 0; i < group; i++)
        {
            if (i == position)
            {
                continue;
            }
            ClockItem cm = (ClockItem) mItemList.get(i);
            
            for (int j = 0; j < 7; j++) 
            {
            	if (((week&(1<<j)) != 0) && ((cm.getWeek()&(1<<j)) != 0))
            	{
            		if ((PosterApplication.compareTwoTime(ontime, cm.getOnTime()) >= 0 &&
            				PosterApplication.compareTwoTime(ontime, cm.getOffTime()) <= 0) ||
            				(PosterApplication.compareTwoTime(offtime, cm.getOnTime()) >= 0 &&
            				PosterApplication.compareTwoTime(offtime, cm.getOffTime()) <= 0) ||
            				(PosterApplication.compareTwoTime(ontime, cm.getOnTime()) < 0 &&
            				PosterApplication.compareTwoTime(offtime, cm.getOnTime()) > 0))
            		{
            			return true;
            		}
            	}
            }
        }
        return false;
    }
    
    private boolean checkOnOffTimeValid(int position, int weekmask) {
        ClockItem poscm = (ClockItem) mItemList.get(position);
        int week = poscm.getWeek() | weekmask;
        String[] strOnTime = poscm.getOnTime().split(":");
        String[] strOffTime = poscm.getOffTime().split(":");
        
        SysOnOffTimeInfo currInfo = new SysOnOffTimeInfo();
        currInfo.week = week;
        currInfo.onhour = Integer.parseInt(strOnTime[0]);
        currInfo.onminute = Integer.parseInt(strOnTime[1]);
        currInfo.onsecond = Integer.parseInt(strOnTime[2]);
        currInfo.offhour = Integer.parseInt(strOffTime[0]);
        currInfo.offminute = Integer.parseInt(strOffTime[1]);
        currInfo.offsecond = Integer.parseInt(strOffTime[2]);
        
        int currOnSeconds = currInfo.onhour*3600+currInfo.onminute*60+currInfo.onsecond;
        int currOffSeconds = currInfo.offhour*3600+currInfo.offminute*60+currInfo.offsecond;
        if ((currOffSeconds-currOnSeconds) <= ONOFF_MINIMUM_INTERVAL) {
        	return false;
        }
        
        if ((24*3600-(currOffSeconds-currOnSeconds)) <= ONOFF_MINIMUM_INTERVAL) {
        	for (int i = 0; i < 7; i++) {
        		if (i != 6) {
	        		if (((currInfo.week&(1<<i)) != 0) && ((currInfo.week&(1<<(i+1))) != 0)) {
	        			return false;
	        		}
        		} else {
        			if (((currInfo.week&(1<<6)) != 0) && ((currInfo.week&1) != 0)) {
	        			return false;
	        		}
        		}
        	}
        }
        
        int group = mItemList.size();
        if (group > 0) {
        	SysOnOffTimeInfo[] sysInfo = new SysOnOffTimeInfo[group];
        	for (int i = 0; i < group; i++) {
        		ClockItem cm = (ClockItem) mItemList.get(i);
        		sysInfo[i] = new SysOnOffTimeInfo();
        		sysInfo[i].week = cm.getWeek();
        		String[] onTimeStr = cm.getOnTime().split(":");
        		sysInfo[i].onhour = Integer.parseInt(onTimeStr[0]);
        		sysInfo[i].onminute = Integer.parseInt(onTimeStr[1]);
        		sysInfo[i].onsecond = Integer.parseInt(onTimeStr[2]);
        		String[] offTimeStr = cm.getOffTime().split(":");
        		sysInfo[i].offhour = Integer.parseInt(offTimeStr[0]);
        		sysInfo[i].offminute = Integer.parseInt(offTimeStr[1]);
        		sysInfo[i].offsecond = Integer.parseInt(offTimeStr[2]);
        	}
        	
			for (int i = 0; i < 7; i++) {
				if ((currInfo.week & (1 << i)) != 0) {
					for (int j = 0; j < group; j++) {
						if (j != position) {
							int dayIdx;
							int tmpCurrOnSeconds, tmpCurrOffSeconds;
							int onSeconds, offSeconds;
							
							// Check on time
							dayIdx = i;
							for (int k = 0; k < 2; k++) {
								if ((sysInfo[j].week & (1 << dayIdx)) != 0) {
									if (k != 0) {
										tmpCurrOnSeconds = ONEDAYSECONDS*k+currOnSeconds;
									} else {
										tmpCurrOnSeconds = currOnSeconds;
									}
									offSeconds = sysInfo[j].offhour*3600
											+sysInfo[j].offminute*60
											+sysInfo[j].offsecond;
									if (tmpCurrOnSeconds > offSeconds) {
										if ((tmpCurrOnSeconds-offSeconds) <= ONOFF_MINIMUM_INTERVAL) {
											return false;
										} else {
											break;
										}
									}
								}
								dayIdx = (dayIdx != 6) ? dayIdx + 1 : 0;
							}
							
							// Check off time
							dayIdx = i;
							for (int k = 0; k < 2; k++) {
								if ((sysInfo[j].week & (1 << dayIdx)) != 0) {
									tmpCurrOffSeconds = currOffSeconds;
									if (k != 0) {
										onSeconds = ONEDAYSECONDS*k
												+sysInfo[j].onhour*3600
												+sysInfo[j].onminute*60
												+sysInfo[j].onsecond;
									} else {
										onSeconds = sysInfo[j].onhour*3600
												+sysInfo[j].onminute*60
												+sysInfo[j].onsecond;
									}
									
									if (onSeconds > tmpCurrOffSeconds) {
										if ((onSeconds-tmpCurrOffSeconds) <= ONOFF_MINIMUM_INTERVAL) {
											return false;
										} else {
											break;
										}
									}
								}
								dayIdx = (dayIdx != 0) ? dayIdx - 1 : 6;
							}
						}
					}
				}
			}
        }
        
    	return true;
    }
    
    private final class ViewHolder
    {
        public TextView tv_onOffTime;
        public CheckBox cbx_sunday;
        public CheckBox cbx_monday;
        public CheckBox cbx_tuesday;
        public CheckBox cbx_wedsday;
        public CheckBox cbx_thirsday;
        public CheckBox cbx_friday;
        public CheckBox cbx_staday;
    }
}

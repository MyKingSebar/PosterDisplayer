/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */
package com.youngsee.common;

import java.util.HashMap;
import java.util.Map;

import com.youngsee.posterdisplayer.PosterApplication;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

public class TypefaceManager
{
    // 默认
    public static final String DEFAULT = "默认";
    // 宋体
    public static final String SONGTI = "宋体";
    // 中体
    public static final String ZHONGSONG = "中宋";
    // 仿宋
    public static final String FANGSONG = "仿宋";
    // 黑体
    public static final String HEITI = "黑体";
    // 细黑
    public static final String XIHEI = "细黑";
    // 粗黑
    public static final String CUHEI = "粗黑";
    // 楷体
    public static final String KAITI = "楷体";
    // 幼圆
    public static final String YOUYUAN = "幼圆";
    // 隶属
    public static final String LITI = "隶书";
    // 魏凯
    public static final String WEIBEI = "魏碑";
    // 行楷
    public static final String XINGKAI = "行楷";
    // 姚体
    public static final String YAOTI = "姚体";
    // 舒体
    public static final String SHUTI = "舒体";

    private Context context;
    
    private static TypefaceManager INSTANCE = null;
    
    private Map<String, Typeface> mTypefaceMap = new HashMap<String, Typeface>();

    public TypefaceManager(Context context)
    {
        this.context = context;
    }
    
    public static TypefaceManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new TypefaceManager(
					PosterApplication.getInstance().getApplicationContext());
			INSTANCE.initTypefaceMap();
		}
		return INSTANCE;
	}
    
    public void initTypefaceMap() {
    	mTypefaceMap.put(DEFAULT, getTypeface(DEFAULT));
		mTypefaceMap.put(SONGTI, getTypeface(SONGTI));
		mTypefaceMap.put(ZHONGSONG, getTypeface(ZHONGSONG));
		mTypefaceMap.put(FANGSONG, getTypeface(FANGSONG));
		mTypefaceMap.put(HEITI, getTypeface(HEITI));
		mTypefaceMap.put(XIHEI, getTypeface(XIHEI));
		mTypefaceMap.put(CUHEI, getTypeface(CUHEI));
		mTypefaceMap.put(KAITI, getTypeface(KAITI));
		mTypefaceMap.put(YOUYUAN, getTypeface(YOUYUAN));
		mTypefaceMap.put(LITI, getTypeface(LITI));
		mTypefaceMap.put(WEIBEI, getTypeface(WEIBEI));
		mTypefaceMap.put(XINGKAI, getTypeface(XINGKAI));
		mTypefaceMap.put(YAOTI, getTypeface(YAOTI));
		mTypefaceMap.put(SHUTI, getTypeface(SHUTI));
    }
    
    public Typeface getTF(String type) {
    	if (mTypefaceMap != null) {
    		return mTypefaceMap.get(type);
    	}
    	return null;
    }

    public Typeface getTypeface(String type)
    {
        Typeface typeface = null;
        AssetManager assetManager = context.getAssets();
        try
        {
            typeface = Typeface.createFromAsset(assetManager, getPath(type));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return typeface;
    }

    private static String getPath(String typeface)
    {
        String fontname = null;
        if(typeface.equals(DEFAULT))
        {
            fontname = "fonts/default.ttf";
        }
        else if(typeface.equals(SONGTI))
        {
            fontname = "fonts/songti.ttf";
        }
        else if(typeface.equals(FANGSONG))
        {
            fontname = "fonts/fangsong.ttf";   
        }
        else if(typeface.equals(ZHONGSONG))
        {
            fontname = "fonts/zhongsong.ttf";
        }
        else if(typeface.equals(HEITI))
        {
            fontname = "fonts/heiti.ttf";
        }
        else if(typeface.equals(XIHEI))
        {
            fontname = "fonts/xihei.ttf";
        }
        else if(typeface.equals(CUHEI))
        {
            fontname = "fonts/cuhei.ttf";
        }
        else if(typeface.equals(KAITI))
        {
            fontname = "fonts/kaiti.ttf";
        }
        else if(typeface.equals(YOUYUAN))
        {
            fontname = "fonts/youyuan.ttf";
        }
        else if(typeface.equals(LITI))
        {
            fontname = "fonts/lishu.ttf";
        }
        else if(typeface.equals(WEIBEI))
        {
            fontname = "fonts/weibei.ttf";
        }
        else if(typeface.equals(XINGKAI))
        {
            fontname = "fonts/xingkai.ttf";
        }
        else if(typeface.equals(YAOTI))
        {
            fontname = "fonts/yaoti.ttf";
        }
        else if(typeface.equals(SHUTI))
        {
            fontname = "fonts/shuti.ttf";
        }
        else
        {
            fontname = "fonts/default.ttf";
        }
        return fontname;
    }
}

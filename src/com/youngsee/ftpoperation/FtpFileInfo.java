/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.ftpoperation;

public class FtpFileInfo
{
    private String mRemoteObj = null;
    private String mLocalPath = null;
    private String mVerifyCode = null;
    private int mVerifyKey = 0;
    private int mRetryTimes = 0;
    
    public void setRemotePath(String strRemotePath)
    {
        mRemoteObj = strRemotePath;
    }
    
    public void setLocalPath(String strLocalPath)
    {
        mLocalPath = strLocalPath;
    }
    
    public void setVerifyCode(String vrfCode)
    {
        mVerifyCode = vrfCode;
    }
    
    public void setVerifyKey(int vrfKey)
    {
        mVerifyKey = vrfKey;
    }
    
    public void setRetyTimes(int nTimes)
    {
        mRetryTimes = nTimes;
    }
    
    public void addTimes()
    {
        mRetryTimes++;
    }
    
    public String getRemotePath()
    {
        return mRemoteObj;
    }
    
    public String getLocalPath()
    {
        return mLocalPath;
    }
    
    public String getVerifyCode()
    {
        return mVerifyCode;
    }
    
    public int getVerifyKey()
    {
        return mVerifyKey;
    }
    
    public boolean isReachMaxRetryTimes(int nMaxTimes)
    {
        return (mRetryTimes >= nMaxTimes);
    }
    
    @Override
    public boolean equals(Object obj) 
    {
        if (obj instanceof FtpFileInfo) 
        {
            FtpFileInfo fileInfo = (FtpFileInfo) obj;
            
            if (fileInfo.mLocalPath != null && 
                fileInfo.mRemoteObj != null &&
                fileInfo.mVerifyCode != null)
            {
                return (fileInfo.mLocalPath.equals(this.mLocalPath) && 
                        fileInfo.mRemoteObj.equals(this.mRemoteObj) && 
                        fileInfo.mVerifyCode.equals(this.mVerifyCode));
            }
        }  
        return false;
    }
}

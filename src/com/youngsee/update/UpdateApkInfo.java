/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author CaiJian
 */

package com.youngsee.update;

public class UpdateApkInfo {
	/*String appname;
	String apkname;*/
	String path;
	String verName;
	String verCode;
	String verifyKey;
	String verify;
	
	/*public String getApkname() {
		return apkname;
	}
	public void setApkname(String apkname) {
		this.apkname = apkname;
	}
	public String getAppname() {
		return appname;
	}
	public void setAppname(String appname) {
		this.appname = appname;
	}*/
	
	public String getPath() {
		return path;
	}

	public String getVerName() {
		return verName;
	}

	public void setVerName(String verName) {
		this.verName = verName;
	}

	public String getVerCode() {
		return verCode;
	}

	public void setVerCode(String verCode) {
		this.verCode = verCode;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getVerifyKey() {
		return verifyKey;
	}

	public void setVerifyKey(String verifyKey) {
		this.verifyKey = verifyKey;
	}

	public String getVerify() {
		return verify;
	}

	public void setVerify(String verify) {
		this.verify = verify;
	}
}

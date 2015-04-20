package com.youngsee.authorization;

public class AuthorizationCommon {
	public static final String SYS_RSAKEY_FILE = "/system/devauth/key.ys";
	
	public static final String SYS_AUTHCODE_FILE = "/system/devauth/authcode.ys";
	
	public static final String TEM_RSAKEY_FILE="/mnt/sdcard/kys";
	
	public static final String TEM_AUTHCODE_FILE="/mnt/sdcard/ays";
	
	public static final String PREFIX_DEVINFO_FILE = "devinfo";
	
	public static final String EXT_DEVINFO_FILE = "txt";
	
	public static final String PREFIX_AUTHCODE_FILE = "authcode";
	
	public static final String EXT_AUTHCODE_FILE = "ca";
	
	public static final String PRIVATEKEY_FILE = "pri.key";
	
	public static final String PUBLICKEY_FILE = "pub.key";
	
	public static final int DEFAULT_DELAY_SECOND_LOCAL = 5;
	
	public static final int DEFAULT_DELAY_SECOND_SERVER = 3;
	
	public static final String SERVERFILE_URL_MIDDLE="dn.ashx?do=getfilecontent&filepath=";
	
	public static final String REMOTE_KEY_FILE_PATH="AuthCodeFiles/pub.key";
	
	public static final String REMOTE_AUTHCODE_FILE_PARENTPATH="AuthCodeFiles/";
	
	public static String getMac(byte[] mac) {
		String ret = null;
		if (mac != null) {
			StringBuilder sb = new StringBuilder();
            for (byte i : mac) {
                sb.append(String.format("%02X", i));
            }
            ret = sb.toString();
		}
		return ret;
	}
}

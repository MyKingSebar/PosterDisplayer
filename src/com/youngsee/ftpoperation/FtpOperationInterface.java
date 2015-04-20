/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 */

package com.youngsee.ftpoperation;

public interface FtpOperationInterface {

    
	/**
	 * Called to notify the listener that the transfer operation has been
	 * initialized.
	 */
    public void started(String file, long size);
	public void aborted();

	/**
	 * Called to notify the listener that some bytes have been transmitted.
	 * 
	 * @param length
	 *            The number of the bytes transmitted since the last time the
	 *            method was called (or since the begin of the operation, at the
	 *            first call received).
	 * @throws InterruptedException 
	 */
	public void progress(long length);

	/**
	 * Called to notify the listener that the transfer operation has been
	 * successfully complete.
	 */
	public void completed();

	/**
	 * Called to notify the listener that the transfer operation has failed due
	 * to an error.
	 */
	public void failed();
	
	
}

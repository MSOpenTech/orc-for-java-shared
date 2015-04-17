/*******************************************************************************
 * Copyright (c) Microsoft Open Technologies, Inc.
 * All Rights Reserved
 * See License.txt in the project root for license information.
 ******************************************************************************/
package com.microsoft.services.orc.impl.http;

/**
 * A thread that can release resources when stopped
 */
public abstract class NetworkThread extends Thread {
	
	/**
	 * Initializes the NetworkThread
	 * @param target runnable to execute
	 */
	public NetworkThread(Runnable target) {
    	super(target);
    }
	
	/**
	 * Releases resources and stops the thread
	 */
	public abstract void releaseAndStop();
}

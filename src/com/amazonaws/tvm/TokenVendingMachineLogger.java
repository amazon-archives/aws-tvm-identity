/*
 * Copyright 2010-2012 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.tvm;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Utility used to enable logging for applications launched in AWS Elastic BeanStalk.
 */
public class TokenVendingMachineLogger {
	
	private static Logger logger;
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
	
	public synchronized static Logger getLogger() {
		if ( null != logger ) {
			return logger;
		}
		
		logger = Logger.getLogger( "TokenVendingMachineLogger" );
		FileHandler handler;
		try {
			handler = new FileHandler( "MyLogFile.txt", true );
			
			SimpleFormatter formatter = new SimpleFormatter();
			handler.setFormatter( formatter );
			
			logger.addHandler( handler );
			logger.setLevel( Level.ALL );
			
		}
		catch ( SecurityException e ) {
			System.err.println( "Security exception while initialising logger : " + e.getMessage() );
		}
		catch ( IOException e ) {
			System.err.println( "IO exception while initialising logger : " + e.getMessage() );
		}
		
		return logger;
	}
	
	private TokenVendingMachineLogger() {}
	
}

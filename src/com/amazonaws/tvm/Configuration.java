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

import java.util.logging.Logger;
import java.util.logging.Level;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;

/**
 * This class captures all of the configuration settings. These environment properties are defined in the BeanStalk container configuration tab.
 */
public class Configuration {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	
	/**
	 * The AWS Access Key Id for the AWS account from which to generate sessions.
	 */
	public static final String AWS_ACCESS_KEY_ID = System.getProperty( "AWS_ACCESS_KEY_ID" );

    /**
     * The AWS Secret Key for the AWS account from which to generate sessions.
     */
	public static final String AWS_SECRET_KEY = System.getProperty( "AWS_SECRET_KEY" );
	
	/**
	 * The AWS Account Id for the AWS account from which to generate sessions.
	 */
	public static final String AWS_ACCOUNT_ID = getAWSAccountID();
	
	/**
	 * The application name
	 */
	public static final String APP_NAME = getAppName();
	
	/**
	 * The duration for which the session is valid. Default is 24 hours = 86400 secs
	 */
	public static final String SESSION_DURATION = "86400";
	
	/**
	 * The SimpleDB endpoint to connect to.
	 */
	public static final String SIMPLEDB_ENDPOINT = "sdb.amazonaws.com";
	
	/**
	 * The SimpleDB region the user domain is stored.
	 */
	public static final String SIMPLEDB_REGION = "us-east-1";
	
	/**
	 * The name of the SimpleDB Domain used to store user info if using the custome authentication mechanisms.
	 */
	public static final String USERS_DOMAIN = getUsersDomain();
	
	/**
	 * The name of the SimpleDB Domain used to store device info if using the custome authentication mechanisms.
	 */
	public static final String DEVICE_DOMAIN = getDeviceDomain();
	
	private static String getAppName() {
		String param1 = System.getProperty( "PARAM1" );
		return ( Utilities.isEmpty( param1 ) ) ? "MyMobileAppName".toLowerCase() : param1.toLowerCase();
	}
	
	private static String getUsersDomain() {
		return "TokenVendingMachine_" + APP_NAME + "_USERS";
	}
	
	private static String getDeviceDomain() {
		return "TokenVendingMachine_" + APP_NAME + "_DEVICES";
	}
	
	private static String getAWSAccountID() {
		try {
			String accessKey = AWS_ACCESS_KEY_ID;
			String secretKey = AWS_SECRET_KEY;
			
			if ( Utilities.isEmpty( accessKey ) || Utilities.isEmpty( secretKey ) ) {
				return null;
			}
			
			AWSCredentials creds = new BasicAWSCredentials( accessKey, secretKey );
			AmazonIdentityManagementClient iam = new AmazonIdentityManagementClient( creds );
			return iam.getUser().getUser().getArn().split( ":" )[4];
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during getAWSAccountID", exception );
			return null;
		}
	}
	
}

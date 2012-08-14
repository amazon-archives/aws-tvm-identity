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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;

public class TemporaryCredentialManagement {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	
	public static Credentials getTemporaryCredentials( String username ) {
		if ( ( Configuration.AWS_ACCESS_KEY_ID == null ) || ( Configuration.AWS_SECRET_KEY == null ) || username == null ) {
			return null;
		}
		else {
			try {
				BasicAWSCredentials creds = new BasicAWSCredentials( Configuration.AWS_ACCESS_KEY_ID, Configuration.AWS_SECRET_KEY );
				AWSSecurityTokenServiceClient sts = new AWSSecurityTokenServiceClient( creds );
				
				GetFederationTokenRequest getFederationTokenRequest = new GetFederationTokenRequest();
				getFederationTokenRequest.setName( username );
				getFederationTokenRequest.setPolicy( TemporaryCredentialManagement.getPolicyObject( username ) );
				getFederationTokenRequest.setDurationSeconds( new Integer( Configuration.SESSION_DURATION ) );
				
				GetFederationTokenResult getFederationTokenResult = sts.getFederationToken( getFederationTokenRequest );
				return getFederationTokenResult.getCredentials();
			}
			catch ( Exception exception ) {
				log.log( Level.SEVERE, "Exception during getTemporaryCredentials", exception );
				return null;
			}
		}
	}
	
	protected static String getPolicyObject( String username ) throws Exception {
        // Ensure the username is valid to prevent injection attacks.
        if ( !Utilities.isValidUsername( username ) ) {
            throw new Exception( "Invalid Username" );
        }
        else {
    		return Utilities.getRawPolicyFile().replaceAll( "__USERNAME__", username ).replaceAll( "__REGION__", Configuration.SIMPLEDB_REGION ).replaceAll( "__ACCOUNT_ID__", Configuration.AWS_ACCOUNT_ID )
	    			.replaceAll( "__USERS_DOMAIN__", Configuration.USERS_DOMAIN ).replaceAll( "__DEVICE_DOMAIN__", Configuration.DEVICE_DOMAIN );
        }
	}
	
}

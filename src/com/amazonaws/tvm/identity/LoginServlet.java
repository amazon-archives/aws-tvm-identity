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

package com.amazonaws.tvm.identity;

import static com.amazonaws.tvm.Utilities.encode;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.tvm.RootServlet;
import com.amazonaws.tvm.Utilities;

/**
 * This class is used to generate encryption key (in identity use case) and send back to user. This key is used to encrypt data in further
 * communication. A key is generated if the user supplied credentials are valid
 * 
 */
public class LoginServlet extends RootServlet {
	
	protected String processRequest( HttpServletRequest request, HttpServletResponse response ) throws Exception {
		log.info( "entering processRequest" );
		try {
			
			IdentityTokenVendingMachine identityTokenVendingMachine = new IdentityTokenVendingMachine();
			
			String username = super.getRequiredParameter( request, "username" );
			String timestamp = super.getRequiredParameter( request, "timestamp" );
			String signature = super.getRequiredParameter( request, "signature" );
			String uid = super.getRequiredParameter( request, "uid" );
			
			String endpoint = Utilities.getEndPoint( request );
			
			log.info( "username : " + encode( username ) );
			log.info( "timestamp : " + encode( timestamp ) );
			log.info( "uid : " + encode( uid ) );
			log.info( "endpoint : " + encode( endpoint ) );
			
			int responseCode = identityTokenVendingMachine.validateLoginRequest( username, uid, signature, timestamp );
			
			if ( responseCode != HttpServletResponse.SC_OK ) {
				log.severe( "Error validating login request for username : " + encode( username ) );
				super.sendErrorResponse( responseCode, response );
				return null;
			}
			
			String data = identityTokenVendingMachine.getKey( username, uid );
			
			if ( null == data ) {
				log.severe( "Error generating key for UID : " + encode( uid ) );
				super.sendErrorResponse( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response );
				return null;
			}
			
			super.sendOKResponse( response, data );
		}
		finally {
			log.info( "leaving processRequest" );
		}
		
		return null;
	}
}

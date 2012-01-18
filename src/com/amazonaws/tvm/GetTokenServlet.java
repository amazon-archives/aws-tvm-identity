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

import static com.amazonaws.tvm.Utilities.encode;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.tvm.identity.IdentityTokenVendingMachine;

/**
 * Servlet implementation class GetTokenServlet
 */
public class GetTokenServlet extends RootServlet {
	
	@Override
	protected String processRequest( HttpServletRequest request, HttpServletResponse response ) throws Exception {
		log.info( "processing request" );
		try {
			
			IdentityTokenVendingMachine identityTokenVendingMachine = new IdentityTokenVendingMachine(); 
			
			String uid = super.getRequiredParameter( request, "uid" );
			String signature = super.getRequiredParameter( request, "signature" );
			String timestamp = super.getRequiredParameter( request, "timestamp" );
			
			int responseCode = identityTokenVendingMachine.validateTokenRequest( uid, signature, timestamp );
			if ( responseCode != HttpServletResponse.SC_OK ) {
				log.severe( "Error validating token request for UID : " + encode( uid ) );
				super.sendErrorResponse( responseCode, response );
				return null;
			}
			
			String data = identityTokenVendingMachine.getToken( uid );
			
			if ( null == data ) {
				log.severe( "Error generating session credentials for UID : " + encode( uid ) );
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

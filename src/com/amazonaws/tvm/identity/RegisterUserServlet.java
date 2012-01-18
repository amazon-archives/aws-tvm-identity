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

import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.tvm.MissingParameterException;
import com.amazonaws.tvm.RootServlet;
import com.amazonaws.tvm.Utilities;

/**
 * Servlet implementation class UserRegisterServlet
 */
public class RegisterUserServlet extends RootServlet {
	
	protected String processRequest( HttpServletRequest request, HttpServletResponse response ) throws Exception {
		log.info( "entering processRequest" );
		try {
			
			IdentityTokenVendingMachine identityTokenVendingMachine = new IdentityTokenVendingMachine();
			
			String username = super.getRequiredParameter( request, "username" );
			String password = super.getRequiredParameter( request, "password" );
			String endpoint = Utilities.getEndPoint( request );
			
			log.info( "username : " + encode( username ) );
			log.info( "endpoint : " + encode( endpoint ) );
			
			int responseCode = identityTokenVendingMachine.registerUser( username, password, endpoint );
			
			if ( responseCode != HttpServletResponse.SC_OK ) {
				log.warning( "User : " + encode( username ) + " registration failed" );
				response.setStatus( responseCode );
				return super.getServletParameter( this, "error" );
			}
			
			log.info( "User : " + encode( username ) + " registered successfully" );
			return super.getServletParameter( this, "success" );
		}
		catch ( MissingParameterException exception ) {
			log.warning( "Setting Http status code " + HttpServletResponse.SC_BAD_REQUEST );
			response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
			return "/mpe.jsp";
		}
		catch ( Exception exception ) {
			log.log( Level.SEVERE, "Exception during processRequest", exception );
			request.setAttribute( "exception", exception );
			return "/error.jsp";
		}
		finally {
			log.info( "leaving processRequest" );
		}
	}
	
}

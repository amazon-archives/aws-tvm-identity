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
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_ACCEPTABLE;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_TIMEOUT;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.tvm.TemporaryCredentialManagement;
import com.amazonaws.tvm.TokenVendingMachineLogger;
import com.amazonaws.tvm.Utilities;
import com.amazonaws.tvm.custom.DeviceAuthentication;
import com.amazonaws.tvm.custom.UserAuthentication;

/**
 * This class implements functions for Identity mode. Identity mode is more useful when application developer needs to track their customer and how much resources 
 * each of them is using. This mode is also suitable to when application developer wants to charge as per usage. It allows new users to register by providing username
 *  and password combination. Registered users can then obtain encryption key after login. This key is used to encrypt tokens in future communication. Since a username 
 *  can have many devices associated with it each login request must explicitly specify the UID. The generated key is then associated to this UID.
 */
public class IdentityTokenVendingMachine {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	
	/**
	 * Verify if the token request is valid. UID is authenticated. The timestamp is checked to see it falls within the valid timestamp window. The
	 * signature is computed and matched against the given signature. Useful in Anonymous and Identity modes
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @param signature
	 *            Base64 encoded HMAC-SHA256 signature derived from key and timestamp
	 * @param timestamp
	 *            Timestamp of the request in ISO8601 format
	 * @return status code indicating if token request is valid or not
	 * @throws Exception
	 */
	public int validateTokenRequest( String uid, String signature, String timestamp ) throws Exception {
		if ( !Utilities.isTimestampValid( timestamp ) ) {
			log.warning( "Timestamp : " + encode( timestamp ) + " not valid. Setting Http status code " + SC_REQUEST_TIMEOUT );
			return SC_REQUEST_TIMEOUT;
		}
		
		log.fine( String.format( "Timestamp [ %s ] is valid", encode( timestamp ) ) );
		
		DeviceAuthentication auth = new DeviceAuthentication();
		String key = auth.getKey( uid );
		
		if ( !this.authenticateSignature( key, timestamp, signature ) ) {
			log.warning( "Client signature doesnot match with server generated signature .Setting Http status code " + SC_UNAUTHORIZED );
			return SC_UNAUTHORIZED;
		}
		
		log.fine( "Signature matched!!!" );
		return SC_OK;
	}
	
	/**
	 * Generate tokens for given UID. The tokens are encrypted using the key corresponding to UID. Encrypted tokens are then wrapped in JSON object
	 * before returning it. Useful in Anonymous and Identity modes
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @return encrypted tokens as JSON object
	 * @throws Exception
	 */
	public String getToken( String uid ) throws Exception {
		DeviceAuthentication auth = new DeviceAuthentication();
		String key = auth.getKey( uid );
		
        String username = UserAuthentication.getUsernameFromUID( auth.getUserId( uid ) );
        if ( username == null ) {
            log.severe( "Username not found for: " + username );            
            return null;
        }
                
		Credentials sessionCredentials = TemporaryCredentialManagement.getTemporaryCredentials( username );
		// if unable to create session credentials then return HTTP 500 error code
		if ( sessionCredentials == null ) {
			return null;
		}
		else {
			log.info( "Generating session tokens for UID : " + encode( uid ) );
			String data = Utilities.prepareJsonResponseForTokens( sessionCredentials, key );
			if ( null == data ) {
				log.severe( "Error generating xml response for token request" );
				return null;
			}
			return data;
		}
		
	}
	
	/**
	 * Allows users to register with Token Vending Machine (TVM). This function is useful in Identity mode
	 * 
	 * @param username
	 *            Unique alphanumeric string of length between 3 to 128 characters with special characters limited to underscore (_), period (.) and (@).
	 * @param password
	 *            String of length between 6 to 128 characters
	 * @param endpoint
	 *            DNS name of host machine
	 * @return status code indicating if the registration was successful or not
	 * @throws Exception
	 */
	public int registerUser( String username, String password, String endpoint ) throws Exception {
		if ( !Utilities.isValidUsername( username ) || !Utilities.isValidPassword( password ) ) {
			log.warning( "Setting Http status code " + SC_BAD_REQUEST );
			return SC_BAD_REQUEST;
		}
		
		UserAuthentication authenticator = new UserAuthentication();
		boolean userWasRegistered = authenticator.registerUser( username, password, endpoint );
		
		if ( userWasRegistered ) {
			log.info( "User : " + encode( username ) + " registered successfully" );
			return SC_OK;
		}
		else {
			log.warning( "User : " + encode( username ) + " registration failed" );
			return SC_NOT_ACCEPTABLE;
		}
	}
	
	/**
	 * Verify if the login request is valid. Username and UID are authenticated. The timestamp is checked to see it falls within the valid timestamp
	 * window. The signature is computed and matched against the given signature. Also its checked to see if the UID belongs to the username. This
	 * function is useful in Identity mode
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param uid
	 *            Unique device identifier
	 * @param signature
	 *            Base64 encoded HMAC-SHA256 signature derived from hash of salted-password and timestamp
	 * @param timestamp
	 *            Timestamp of the request in ISO8601 format
	 * @return status code indicating if login request is valid or not
	 * @throws Exception
	 */
	public int validateLoginRequest( String username, String uid, String signature, String timestamp ) throws Exception {
		if ( !Utilities.isTimestampValid( timestamp ) ) {
			log.warning( "Timestamp : " + encode( timestamp ) + " not valid. Setting Http status code " + SC_REQUEST_TIMEOUT );
			return SC_REQUEST_TIMEOUT;
		}
		
		log.fine( String.format( "Timestamp [ %s ] is valid", timestamp ) );
		
		UserAuthentication authenticator = new UserAuthentication();
		
		// Authenticate user signature
		String useridFromUserTable = authenticator.authenticateUserSignature( username, timestamp, signature );
		if ( null == useridFromUserTable ) {
			log.warning( "Client signature : " + encode( signature ) + " doesnot match with server generated signature .Setting Http status code " + SC_UNAUTHORIZED );
			return SC_UNAUTHORIZED;
		}
		
		log.fine( "Signature matched!!!" );
		
		// Register device
		final Map<String, String> deviceAttributes = this.regenerateKey( uid, useridFromUserTable );
		if ( null == deviceAttributes ) {
			log.severe( String.format( "Error registering device for UID : [ %s ] username : [ %s ] userid : [ %s ]", encode( uid ), encode( username ), encode( useridFromUserTable ) ) );
			log.severe( "Setting response code : " + SC_INTERNAL_SERVER_ERROR );
			return SC_INTERNAL_SERVER_ERROR;
		}
		
		log.fine( "Device found/registered successfully!!!" );
		
		// get device attribute
		String useridFromDeviceTable = deviceAttributes.get( "userid" );
		String encryptionKey = deviceAttributes.get( "key" );
		
		if ( null == useridFromDeviceTable || null == encryptionKey ) {
			log.severe( String.format( "Setting Http status code : %d", SC_INTERNAL_SERVER_ERROR ) );
			return SC_INTERNAL_SERVER_ERROR;
		}
		
		if ( !this.deviceBelongsToUser( useridFromUserTable, useridFromDeviceTable ) ) {
			log.warning( String.format( "Userid mismatch between device table and user table. Userid from user_table : [ %s ] and userid from device_table : [ %s ]. Setting Http statuscode %d",
					encode( useridFromUserTable ), encode( useridFromDeviceTable ), SC_UNAUTHORIZED ) );
			return SC_UNAUTHORIZED;
		}
		
		return SC_OK;
	}
	
	/**
	 * Generate key for device UID. The key is encrypted by hash of salted password of the user. Encrypted key is then wrapped in JSON object before
	 * returning it. This function is useful in Identity mode
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param uid
	 *            Unique device identifier
	 * @return encrypted key as JSON object
	 * @throws Exception
	 */
	public String getKey( String username, String uid ) throws Exception {
		DeviceAuthentication deviceAuthenticator = new DeviceAuthentication();
		String key = deviceAuthenticator.getKey( uid );
		
		UserAuthentication userAuthenticator = new UserAuthentication();
		String hashSaltedPassword = userAuthenticator.getHashSaltedPassword( username );
		
		log.info( "Responding with encrypted key for UID : " + encode( uid ) );
		String data = Utilities.prepareJsonResponseForKey( key, hashSaltedPassword );
		if ( null == data ) {
			log.severe( "Error generating json response for key request" );
		}
		
		return data;
	}
	
	/**
	 * This method regenerates the key each time. It lookups up device details of a registered device. 
	 * Also registers device if it is not already registered. 
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @param useridFromUserTable
	 *            Userid of the current user
	 * @return device attributes i.e. key and userid
	 * @throws UnsupportedEncodingException
	 *             if encoding format is wrong or missing
	 */
	private Map<String, String> regenerateKey( String uid, String useridFromUserTable ) throws UnsupportedEncodingException {
		DeviceAuthentication deviceAuthenticator = new DeviceAuthentication();
		Map<String, String> attributes = null;
		
		log.info( "Generating encryption key" );
		String encryptionKey = Utilities.generateRandomString();
		
		if ( deviceAuthenticator.registerDevice( uid, encryptionKey, useridFromUserTable ) ) {
			attributes = deviceAuthenticator.getDevice( uid );
		}
		
		return attributes;
	}
	
	/**
	 * Checks of the device UID belongs to the given user
	 * 
	 * @param useridFromUser
	 *            Userid of the current user
	 * @param useridFromDevice
	 *            Userid associated with the given UID
	 * @return true if device UID belongs to current user, false otherwise
	 */
	private boolean deviceBelongsToUser( String useridFromUser, String useridFromDevice ) {
		return useridFromUser.equals( useridFromDevice );
	}
	
	/**
	 * Verify if the given signature is valid.
	 * 
	 * @param key
	 *            The key used in the signature process
	 * @param timestamp
	 *            The timestamp of the request in ISO8601 format
	 * @param signature
	 *            Base64 encoded HMAC-SHA256 signature derived from key and timestamp
	 * @return true if computed signature matches with the given signature, false otherwise
	 * @throws Exception
	 */
	private boolean authenticateSignature( String key, String timestamp, String signature ) throws Exception {
		if ( null == key ) {
			log.warning( "Key not found" );
			return false;
		}
		
		String computedSignature = Utilities.sign( timestamp, key );		
		return Utilities.slowStringComparison(signature, computedSignature);
	}
	
}

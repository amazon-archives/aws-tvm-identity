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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.util.DateUtils;
import com.amazonaws.util.HttpUtils;

public class Utilities {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	private static String RAW_POLICY_OBJECT = null;
	private static SecureRandom RANDOM = new SecureRandom();
	
	public static String prepareJsonResponseForTokens( Credentials sessionCredentials, String key ) throws Exception {
		
		StringBuilder responseBody = new StringBuilder();
		responseBody.append( "{" );
		responseBody.append( "\taccessKey: \"" ).append( sessionCredentials.getAccessKeyId() ).append( "\"," );
		responseBody.append( "\tsecretKey: \"" ).append( sessionCredentials.getSecretAccessKey() ).append( "\"," );
		responseBody.append( "\tsecurityToken: \"" ).append( sessionCredentials.getSessionToken() ).append( "\"," );
		responseBody.append( "\texpirationDate: \"" ).append( sessionCredentials.getExpiration().getTime() ).append( "\"" );
		responseBody.append( "}" );
		
		// Encrypting the response
		return AESEncryption.wrap( responseBody.toString(), key );
	}
	
	public static String prepareJsonResponseForKey( String data, String key ) throws Exception {
		
		StringBuilder responseBody = new StringBuilder();
		responseBody.append( "{" );
		responseBody.append( "\tkey: \"" ).append( data ).append( "\"" );
		responseBody.append( "}" );
		
		// Encrypting the response
		return AESEncryption.wrap( responseBody.toString(), key.substring( 0, 32 ) );
	}
	
	public static String sign( String content, String key ) {
		try {
			byte[] data = content.getBytes( Constants.ENCODING_FORMAT );
			Mac mac = Mac.getInstance( Constants.SIGNATURE_METHOD );
			mac.init( new SecretKeySpec( key.getBytes( Constants.ENCODING_FORMAT ), Constants.SIGNATURE_METHOD ) );
			char[] signature = Hex.encodeHex( mac.doFinal( data ) );
			return new String( signature );
		}
		catch ( Exception exception ) {
			log.log( Level.SEVERE, "Exception during sign", exception );
		}
		return null;
	}
	
	public static String getSaltedPassword( String username, String endPointUri, String password ) {
		return sign( ( username + Configuration.APP_NAME + endPointUri.toLowerCase() ), password );
	}
	
	public static String base64( String data ) throws UnsupportedEncodingException {
		byte[] signature = Base64.encodeBase64( data.getBytes( Constants.ENCODING_FORMAT ) );
		return new String( signature, Constants.ENCODING_FORMAT );
	}
	
	public static String getEndPoint( HttpServletRequest request ) {
		if ( null == request ) {
			return null;
		}
		else {
            String endpoint = request.getServerName().toLowerCase();
			log.info( "Endpoint : " + encode( endpoint ) );
			return endpoint;
		}
	}
	
	/**
	 * Checks to see if the request has valid timestamp. If given timestamp falls in 30 mins window from current server timestamp
	 */
	public static boolean isTimestampValid( String timestamp ) {
		long timestampLong = 0L;
		final long window = 15 * 60 * 1000L;
		
		if ( null == timestamp ) {
			return false;
		}
		
		try {
			timestampLong = new DateUtils().parseIso8601Date( timestamp ).getTime();
		}
		catch ( ParseException exception ) {
			log.warning( "Error parsing timestamp sent from client : " + encode( timestamp ) );
			return false;
		}
		
		Long now = new Date().getTime();
		
		long before15Mins = new Date( now - window ).getTime();
		long after15Mins = new Date( now + window ).getTime();
		
		return ( timestampLong >= before15Mins && timestampLong <= after15Mins );
	}
	
    public static String generateRandomString() {
		byte[] randomBytes = RANDOM.generateSeed( 16 );
		String randomString = new String( Hex.encodeHex( randomBytes ) );
		return randomString;
	}
	
	public static boolean isValidUsername( String username ) {
		int length = username.length();
		if ( length < 3 || length > 128 ) {
			return false;
		}
		
		char c = 0;
		for ( int i = 0; i < length; i++ ) {
			c = username.charAt( i );
			if ( !Character.isLetterOrDigit( c ) && '_' != c && '.' != c ) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean isValidPassword( String password ) {
		int length = password.length();
		return ( length >= 6 && length <= 128 );
	}
	
	public static boolean isEmpty( String str ) {
		if ( null == str || str.trim().length() == 0 )
			return true;
		return false;
	}
	
	public static String encode( String s ) {
		if ( null == s )
			return s;
		return HttpUtils.urlEncode( s, false );
	}
	
	public static String getRawPolicyFile() {
		
		if ( RAW_POLICY_OBJECT == null ) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream( 8196 );
			InputStream in = null;
			try {
				in = Utilities.class.getResourceAsStream( "/TokenVendingMachinePolicy.json" );
				byte[] buffer = new byte[ 1024 ];
				int length = 0;
				while ( ( length = in.read( buffer ) ) != -1 ) {
					baos.write( buffer, 0, length );
				}
				
				RAW_POLICY_OBJECT = baos.toString();
			}
			catch ( Exception exception ) {
				log.log( Level.SEVERE, "Unable to load policy object.", exception );
				RAW_POLICY_OBJECT = "";
			}
			finally {
				try {
					baos.close();
					in.close();
				}
				catch ( Exception exception ) {
					log.log( Level.SEVERE, "Unable to close streams.", exception );
				}
				in = null;
				baos = null;
			}
		}
		
		return RAW_POLICY_OBJECT;
	}
	
	/**
	 * This method is low performance string comparison function. The purpose of this method is to prevent timing attack.
	 */
	public static boolean slowStringComparison(String givenSignature, String computedSignature) {
		if( null == givenSignature || null == computedSignature || givenSignature.length() != computedSignature.length()) return false;

		int n = computedSignature.length();
		boolean signaturesMatch = true;
		
		for (int i = 0; i < n; i++) {
			signaturesMatch &= (computedSignature.charAt(i) == givenSignature.charAt(i));
		}
		
		return signaturesMatch;
	}
	
}

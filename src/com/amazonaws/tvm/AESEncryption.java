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

import java.security.AlgorithmParameters;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

public class AESEncryption {
	
	public static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
	
	public static String wrap( String clearText, String key ) throws Exception {
		byte[] iv = getIv();
		
		byte[] cipherText = encrypt( clearText, key, iv );
		byte[] wrapped = new byte[ iv.length + cipherText.length ];
		System.arraycopy( iv, 0, wrapped, 0, iv.length );
		System.arraycopy( cipherText, 0, wrapped, 16, cipherText.length );
		
		return new String( Base64.encodeBase64( wrapped ) );
	}
	
	public static byte[] encrypt( String clearText, String key, byte[] iv ) throws Exception {
		Cipher cipher = Cipher.getInstance( ENCRYPTION_ALGORITHM );
		AlgorithmParameters params = AlgorithmParameters.getInstance( "AES" );
		params.init( new IvParameterSpec( iv ) );
		cipher.init( Cipher.ENCRYPT_MODE, getKey( key ), params );
		return cipher.doFinal( clearText.getBytes() );
	}
	
	private static SecretKeySpec getKey( String key ) throws Exception {
		return new SecretKeySpec( Hex.decodeHex(key.toCharArray()), "AES" );
	}
	
	private static byte[] getIv() throws Exception {
		byte[] iv = new byte[ 16 ];
		new SecureRandom().nextBytes( iv );
		
		return iv;
	}
}

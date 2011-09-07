
package com.amazonaws.admin;

import com.amazonaws.tvm.MissingParameterException;

public class CountDevices extends BaseAdmin {
	
	public static void main( String[] args ) {
		try {
			String awsAccessKeyID = getEnv( AWSAccessKeyID );
			String awsSecretKey = getEnv( AWSSecretKey );
			String deviceDomain = getEnv( "DeviceDomain" );
			
			CountDevices obj = new CountDevices( awsAccessKeyID, awsSecretKey );
			if ( null == obj.sdb ) {
				System.err.println( "Unable to connect to SimpleDB" );
				return;
			}
			
			if ( !obj.doesDomainExist( deviceDomain ) ) {
				System.err.println( "Invalid user domain : " + deviceDomain );
				return;
			}
			
			System.out.println( "The number of users = " + obj.countDevices( deviceDomain ) );
			
		}
		catch ( MissingParameterException e ) {
			System.out.println( "Usage:java CountDevices -DAWSAccessKeyID=<access_key> -DAWSSecretKey=<secret_key> -DDeviceDomain=<domain_name>" );
			System.out.println( e.getMessage() );
		}
	}
	
	public CountDevices( String awsAccessKeyID, String awsSecretKey ) {
		super( awsAccessKeyID, awsSecretKey );
	}
	
	/**
	 * Returns the list of usernames stored in the identity domain.
	 */
	public int countDevices( String deviceDomain ) {
        return super.getDomainCount( deviceDomain );
	}
	
}

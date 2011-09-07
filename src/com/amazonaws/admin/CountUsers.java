
package com.amazonaws.admin;

import com.amazonaws.tvm.MissingParameterException;

public class CountUsers extends BaseAdmin {
	
	public static void main( String[] args ) {
		try {
			String awsAccessKeyID = getEnv( AWSAccessKeyID );
			String awsSecretKey = getEnv( AWSSecretKey );
			String userDomain = getEnv( "UserDomain" );
			
			CountUsers obj = new CountUsers( awsAccessKeyID, awsSecretKey );
			if ( null == obj.sdb ) {
				System.err.println( "Unable to connect to SimpleDB" );
				return;
			}
			
			if ( !obj.doesDomainExist( userDomain ) ) {
				System.err.println( "Invalid user domain : " + userDomain );
				return;
			}
			
			System.out.println( "The number of users = " + obj.countUsers( userDomain ) );
			
		}
		catch ( MissingParameterException e ) {
			System.out.println( "Usage:java CountUsers -DAWSAccessKeyID=<access_key> -DAWSSecretKey=<secret_key> -DUserDomain=<domain_name>" );
			System.out.println( e.getMessage() );
		}
	}
	
	public CountUsers( String awsAccessKeyID, String awsSecretKey ) {
		super( awsAccessKeyID, awsSecretKey );
	}
	
	/**
	 * Returns the list of usernames stored in the identity domain.
	 */
	public int countUsers( String userDomain ) {
        return super.getDomainCount( userDomain );
	}
	
}

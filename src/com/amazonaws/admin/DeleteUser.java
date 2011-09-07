
package com.amazonaws.admin;

import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.tvm.MissingParameterException;

public class DeleteUser extends BaseAdmin {
	
	public static void main( String[] args ) {
		try {
			String awsAccessKeyID = getEnv( AWSAccessKeyID );
			String awsSecretKey = getEnv( AWSSecretKey );
			String userDomain = getEnv( "UserDomain" );
			
			if ( args.length == 0 || args[ 0 ].length() == 0 ) {
				throw new MissingParameterException( "username" );
			}
			
			String username = args[ 0 ];
			
			DeleteUser obj = new DeleteUser( awsAccessKeyID, awsSecretKey );
			if ( null == obj.sdb ) {
				System.err.println( "Unable to connect to SimpleDB" );
				return;
			}
			
			if ( !obj.doesDomainExist( userDomain ) ) {
				System.err.println( "Invalid user domain : " + userDomain );
				return;
			}
			
			obj.deleteUser( username, userDomain );
			System.out.println( "User deleted successfully" );
			
		}
		catch ( MissingParameterException e ) {
			System.out.println( "Usage:java DeleteUser -DAWSAccessKeyID=<access_key> -DAWSSecretKey=<secret_key> -DUserDomain=<domain_name> <username_to_be_deleted>" );
			System.out.println( e.getMessage() );
		}
	}
	
	public DeleteUser( String awsAccessKeyID, String awsSecretKey ) {
		super( awsAccessKeyID, awsSecretKey );
	}
	
	/**
	 * Deletes the specified username from the identity domain.
	 */
	public void deleteUser( String username, String domain ) {
		DeleteAttributesRequest dar = new DeleteAttributesRequest( domain, username );
		this.sdb.deleteAttributes( dar );
	}
}

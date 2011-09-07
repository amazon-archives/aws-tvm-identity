
package com.amazonaws.admin;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.tvm.MissingParameterException;

public class ListUsers extends BaseAdmin {
	
	public static void main( String[] args ) {
		try {
			String awsAccessKeyID = getEnv( AWSAccessKeyID );
			String awsSecretKey = getEnv( AWSSecretKey );
			String userDomain = getEnv( "UserDomain" );
			
			ListUsers obj = new ListUsers( awsAccessKeyID, awsSecretKey );
			if ( null == obj.sdb ) {
				System.err.println( "Unable to connect to SimpleDB" );
				return;
			}
			
			if ( !obj.doesDomainExist( userDomain ) ) {
				System.err.println( "Invalid user domain : " + userDomain );
				return;
			}
			
            for ( String username : obj.listUsers( userDomain ) ) {
                System.out.println( username );
            }            			
		}
		catch ( MissingParameterException e ) {
			System.out.println( "Usage:java CountUsers -DAWSAccessKeyID=<access_key> -DAWSSecretKey=<secret_key> -DUserDomain=<domain_name>" );
			System.out.println( e.getMessage() );
		}
	}
	
	public ListUsers( String awsAccessKeyID, String awsSecretKey ) {
		super( awsAccessKeyID, awsSecretKey );
	}
	
	/**
	 * Returns the list of usernames stored in the identity domain.
	 */
	public List<String> listUsers( String userDomain ) {
		List<String> users = new ArrayList<String>( 1000 );
		
		SelectResult result = null;
		do {
			SelectRequest sr = new SelectRequest( "select * from `" + userDomain + "`", Boolean.TRUE );
            sr.setNextToken( (result == null ) ? null : result.getNextToken() );
            
			result = this.sdb.select( sr );            
			
			for ( Item item : result.getItems() ) {
				users.add( item.getName() );
			}
		}
		while ( result != null && result.getNextToken() != null );
		
		return users;
	}
}

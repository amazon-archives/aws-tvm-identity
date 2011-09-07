
package com.amazonaws.admin;

import java.util.List;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.tvm.MissingParameterException;

public class DescribeUser extends BaseAdmin {
	
	public static void main( String[] args ) {
		try {
			String awsAccessKeyID = getEnv( AWSAccessKeyID );
			String awsSecretKey = getEnv( AWSSecretKey );
			String userDomain = getEnv( "UserDomain" );
			
			if ( args.length == 0 || args[ 0 ].length() == 0 ) {
				throw new MissingParameterException( "username" );
			}
			
			String username = args[ 0 ];
			
			DescribeUser obj = new DescribeUser( awsAccessKeyID, awsSecretKey );
			if ( null == obj.sdb ) {
				System.err.println( "Unable to connect to SimpleDB" );
				return;
			}
			
			if ( !obj.doesDomainExist( userDomain ) ) {
				System.err.println( "Invalid user domain : " + userDomain );
				return;
			}
			
			obj.describeUser( username, userDomain );
			
		}
		catch ( MissingParameterException e ) {
			System.out.println( "Usage:java DescribeUser -DAWSAccessKeyID=<access_key> -DAWSSecretKey=<secret_key> -DUserDomain=<domain_name> <username_to_be_described>" );
			System.out.println( e.getMessage() );
		}
	}
	
	public DescribeUser( String awsAccessKeyID, String awsSecretKey ) {
		super( awsAccessKeyID, awsSecretKey );
	}
	
	/**
	 * Returns the list of usernames stored in the identity domain.
	 */
	public void describeUser( String username, String userDomain ) {
		SelectResult result = null;
		SelectRequest sr = new SelectRequest( "select * from `" + userDomain + "`", Boolean.TRUE );
		result = this.sdb.select( sr );
		
		List<Item> list = result.getItems();
		
		for ( Item item : list ) {
			if ( username.equals( item.getName() ) ) {
				System.out.println( "username = " + username );
				List<Attribute> attribs = item.getAttributes();
				for ( Attribute attr : attribs ) {
					System.out.println( attr.getName() + " = " + attr.getValue() );
				}
				return;
			}
		}
		
		System.err.println( "No record found for username '" + username + "'" );
	}
}

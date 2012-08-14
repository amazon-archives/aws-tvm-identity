
package com.amazonaws.admin;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.BasicAWSCredentials;

import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ListDomainsRequest;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;

import com.amazonaws.tvm.MissingParameterException;

public class BaseAdmin {
	
	protected final static String AWSAccessKeyID = "AWS_ACCESS_KEY_ID";	
	protected final static String AWSSecretKey = "AWS_SECRET_KEY";
	
	protected AmazonSimpleDBClient sdb;
	
	public BaseAdmin( String awsAccessKeyID, String awsSecretKey ) {
		this.sdb = new AmazonSimpleDBClient( new BasicAWSCredentials( awsAccessKeyID, awsSecretKey ) );
		if ( null != this.sdb )
			this.sdb.setEndpoint( "sdb.amazonaws.com" );
	}
	
	protected Attribute findAttributeInList( String attributeName, List<Attribute> attributes ) {
		for ( Attribute attribute : attributes ) {
			if ( attribute.getName().equals( attributeName ) ) {
				return attribute;
			}
		}
		
		return null;
	}

	protected int getDomainCount( String domainName ) {
		SelectResult result = null;
		SelectRequest sr = new SelectRequest( "select count(*) from `" + domainName + "`", Boolean.TRUE );
		result = this.sdb.select( sr );
        
		if ( result != null && result.getItems() != null ) {
            return Integer.parseInt( this.findAttributeInList( "Count", result.getItems().get( 0 ).getAttributes() ).getValue() );
        }
        else {
            return 0;
        }    
	}

	protected boolean doesDomainExist( String domainName ) {
		try {
			List<String> domains = this.getAllDomains();
			return ( domains.contains( domainName ) );
		}
		catch ( Exception exception ) {
			return false;
		}
	}
	
	protected List<String> getAllDomains() {
		List<String> domains = new ArrayList<String>();
		String nextToken = null;
		do {
			ListDomainsRequest ldr = new ListDomainsRequest();
			ldr.setNextToken( nextToken );
			
			ListDomainsResult result = this.sdb.listDomains( ldr );
			domains.addAll( result.getDomainNames() );
			
			nextToken = result.getNextToken();
		}
		while ( nextToken != null );
		
		return domains;
	}
	
	protected static String getEnv( String name ) throws MissingParameterException {
		String value = System.getProperty( name );
		if ( null == value || value.length() == 0 ) {
			throw new MissingParameterException( name );
		}
		return value;
	}
	
}

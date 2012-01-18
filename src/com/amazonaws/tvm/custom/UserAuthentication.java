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

package com.amazonaws.tvm.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.ListDomainsRequest;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.tvm.Configuration;
import com.amazonaws.tvm.TokenVendingMachineLogger;
import com.amazonaws.tvm.Utilities;

/**
 * This class is used store and authenticate users. All users and there username/password information is stored in a SimpleDB domain.
 */
public class UserAuthentication {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	
	private final AmazonSimpleDBClient sdb;
	
	/**
	 * Constant for the Domain name used to store the identities.
	 */
	private final static String IDENTITY_DOMAIN = Configuration.USERS_DOMAIN;
	
	/** Constant for the userid attribute */
	private final static String USER_ID = "userid";
	
	/** Constant for the hash of password attribute */
	private final static String HASH_SALTED_PASSWORD = "hash_salted_password";
	
	/** Constant for the enabled attribute */
	private final static String IS_ENABLED = "enabled";
	
	/** Constant select expression used to list all the identities stored in the Domain. */
	private final static String SELECT_USERS_EXPRESSION = "select * from `" + IDENTITY_DOMAIN + "`";
	
	/**
	 * Looks up domain name and creates one if it doesnot exist
	 */
	public UserAuthentication() {
		this.sdb = new AmazonSimpleDBClient( new BasicAWSCredentials( Configuration.AWS_ACCESS_KEY_ID, Configuration.AWS_SECRET_KEY ) );
		this.sdb.setEndpoint( Configuration.SIMPLEDB_ENDPOINT );
		
		if ( !this.doesDomainExist( IDENTITY_DOMAIN ) ) {
			this.createIdentityDomain();
		}
	}
	
	/**
	 * Returns the list of usernames stored in the identity domain.
	 * 
	 * @return list of existing usernames in SimpleDB domain
	 */
	public List<String> listUsers() {
		List<String> users = new ArrayList<String>( 1000 );
		
		SelectResult result = null;
		do {
			SelectRequest sr = new SelectRequest( SELECT_USERS_EXPRESSION, Boolean.TRUE );
			result = this.sdb.select( sr );
			
			for ( Item item : result.getItems() ) {
				users.add( item.getName() );
			}
		}
		while ( result != null && result.getNextToken() != null );
		
		return users;
	}
	
	/**
	 * Attempts to register the username, password combination. Checks if username not already exist. Returns true if successful, false otherwise.
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param password
	 *            user password
	 * @param uri
	 *            endpoint URI
	 * @return true if successful, false otherwise.
	 */
	public boolean registerUser( String username, String password, String uri ) {
		try {
			if ( this.checkUsernameExists( username ) )
				return false;
			this.storeUser( username, password, uri );
			return this.authenticateUser( username, password, uri );
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during registerUser", exception );
			return false;
		}
	}
	
	/**
	 * Deletes the specified username from the identity domain.
	 */
	public void deleteUser( String username ) {
		DeleteAttributesRequest dar = new DeleteAttributesRequest( IDENTITY_DOMAIN, username );
		this.sdb.deleteAttributes( dar );
	}
	
	/**
	 * Authenticates the given username, password combination. Hash of password is matched against the hash value stored for password field
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param password
	 *            user password
	 * @param uri
	 *            endpoint URI
	 * @return true if authentication was successful, false otherwise
	 * @throws Exception
	 */
	public boolean authenticateUser( String username, String password, String uri ) throws Exception {
		if ( null == username || null == password ) {
			return false;
		}
		
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, username ).withConsistentRead( Boolean.TRUE );
		String hashedSaltedPassword = Utilities.getSaltedPassword( username, uri, password );
		
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		if ( data != null && !data.isEmpty() ) {
			Attribute passwordAttribute = this.findAttributeInList( HASH_SALTED_PASSWORD, data );
			return passwordAttribute.getValue().equals( hashedSaltedPassword );
		}
		else {
			return false;
		}
	}
	
	/**
	 * Authenticates the given username, signature combination. A signature is generated and matched against the given signature. If they match then
	 * returns true.
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param timestamp
	 *            Timestamp of the request
	 * @param signature
	 *            Signature of the request
	 * @return true if authentication was successful, false otherwise
	 */
	public String authenticateUserSignature( String username, String timestamp, String signature ) throws Exception {
		
		String hashSaltedPassword = this.getHashSaltedPassword( username );
		
		String computedSignature = Utilities.sign( timestamp, hashSaltedPassword );
		if ( Utilities.slowStringComparison(signature, computedSignature) )
			return this.getUserid( username );
		return null;
	}
	
	/**
	 * Store the username, password combination in the Identity domain. The username will represent the item name and the item will contain a
	 * attributes password and userid.
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param password
	 *            user password
	 * @param uri
	 *            endpoint URI
	 */
	protected void storeUser( String username, String password, String uri ) throws Exception {
		if ( null == username || null == password ) {
			return;
		}
		
		String hashedSaltedPassword = Utilities.getSaltedPassword( username, uri, password );
		String userId = Utilities.generateRandomString();
		
		ReplaceableAttribute userIdAttr = new ReplaceableAttribute( USER_ID, userId, Boolean.TRUE );
		ReplaceableAttribute passwordAttr = new ReplaceableAttribute( HASH_SALTED_PASSWORD, hashedSaltedPassword, Boolean.TRUE );
		ReplaceableAttribute enableAttr = new ReplaceableAttribute( IS_ENABLED, "true", Boolean.TRUE );
		
		List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>( 3 );
		attributes.add( userIdAttr );
		attributes.add( passwordAttr );
		attributes.add( enableAttr );
		
		try {
			PutAttributesRequest par = new PutAttributesRequest( IDENTITY_DOMAIN, username, attributes );
			this.sdb.putAttributes( par );
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during storeUser", exception );
		}
	}
	
	/**
	 * Find and return the attribute in the attribute list
	 * 
	 * @param attributeName
	 *            attribute to search for in the list
	 * @param attributes
	 *            list of attributes
	 * @return attribute found, null if not such attribute found
	 */
	protected Attribute findAttributeInList( String attributeName, List<Attribute> attributes ) {
		for ( Attribute attribute : attributes ) {
			if ( attribute.getName().equals( attributeName ) ) {
				return attribute;
			}
		}
		
		return null;
	}
	
	/**
	 * Used to create the Identity Domain. This function only needs to be called once.
	 */
	protected void createIdentityDomain() {
		this.sdb.createDomain( new CreateDomainRequest( IDENTITY_DOMAIN ) );
	}
	
	/**
	 * Fetch list of all the domains in SimpleDB
	 * 
	 * @return list of domain names
	 */
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
	
	/**
	 * Checks to see if given domainName exist
	 * 
	 * @param domainName
	 *            The domain name to check
	 * @return true if domainName exist, false otherwise
	 */
	protected boolean doesDomainExist( String domainName ) {
		try {
			List<String> domains = this.getAllDomains();
			return ( domains.contains( domainName ) );
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during doesDomainExist", exception );
			return false;
		}
	}
	
	/**
	 * Get hash of salted password associated with the username
	 * 
	 * @param username
	 *            Unique user identifier
	 * @return hash of salted password for the username
	 * @throws Exception
	 */
	public String getHashSaltedPassword( String username ) throws Exception {
		return this.getAttribute( username, HASH_SALTED_PASSWORD );
	}
	
	/**
	 * Get userid associated with the username
	 * 
	 * @param username
	 *            Unique user identifier
	 * @return userid for the username
	 * @throws Exception
	 */
	public String getUserid( String username ) throws Exception {
		return this.getAttribute( username, USER_ID );
	}
	
	/**
	 * Get specific attribute for the username
	 * 
	 * @param username
	 *            Unique user identifier
	 * @param attribute
	 *            The user attribute name
	 * @return Value for the attribute, null otherwise
	 * @throws Exception
	 */
	private String getAttribute( String username, String attribute ) throws Exception {
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, username ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		if ( data != null && !data.isEmpty() ) {
			Attribute keyAttribute = this.findAttributeInList( attribute, data );
			return keyAttribute.getValue();
		}
		else {
			return null;
		}
	}
	
	/**
	 * Checks to see if the username already exist in the user domain
	 * 
	 * @param username
	 *            Unique user identifier
	 * @return true if username already exist, false otherwise
	 */
	private boolean checkUsernameExists( String username ) {
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, username ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		return ( data != null && !data.isEmpty() );
	}
	
}

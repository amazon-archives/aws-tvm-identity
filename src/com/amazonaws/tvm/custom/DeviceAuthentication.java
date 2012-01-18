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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

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
import com.amazonaws.tvm.Constants;
import com.amazonaws.tvm.TokenVendingMachineLogger;

/**
 * This class is used to store and authenticate devices. All devices and their information is stored in a SimpleDB domain.
 */
public class DeviceAuthentication {
	
	protected static final Logger log = TokenVendingMachineLogger.getLogger();
	
	private final AmazonSimpleDBClient sdb;
	
	/**
	 * Constant for the Domain name used to store the identities.
	 */
	private final static String IDENTITY_DOMAIN = Configuration.DEVICE_DOMAIN;
	
	/**
	 * Constant for the key attribute.
	 */
	private final static String KEY = "key";
	
	/**
	 * Constant for the userid attribute.
	 */
	private final static String USERID = "userid";
	
	/**
	 * Constant select expression used to list all the identities stored in the Domain.
	 */
	private final static String SELECT_DEVICE_EXPRESSION = "select * from " + IDENTITY_DOMAIN;
	
	/**
	 * Looks up domain name and creates one if it doesnot exist
	 */
	public DeviceAuthentication() {
		this.sdb = new AmazonSimpleDBClient( new BasicAWSCredentials( Configuration.AWS_ACCESS_KEY_ID, Configuration.AWS_SECRET_KEY ) );
		
		if ( !this.doesDomainExist( IDENTITY_DOMAIN ) ) {
			this.createIdentityDomain();
		}
	}
	
	/**
	 * @return the list of device ID (UID) stored in the identity domain.
	 */
	public List<String> listDevices() {
		List<String> users = new ArrayList<String>( 1000 );
		
		SelectResult result = null;
		SelectRequest sr = new SelectRequest( SELECT_DEVICE_EXPRESSION, Boolean.TRUE );
		result = this.sdb.select( sr );
		
		for ( Item item : result.getItems() ) {
			users.add( item.getName() );
		}
		
		return users;
	}
	
	/**
	 * Returns device attributes for given device ID (UID)
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @return list of attributes for the given uid
	 */
	public Map<String, String> getDevice( String uid ) {
		
		Map<String, String> result = new HashMap<String, String>();
		
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, uid ).withConsistentRead( Boolean.TRUE );
		List<Attribute> list = this.sdb.getAttributes( gar ).getAttributes();
		
		if ( null == list || list.isEmpty() ) {
			return result;
		}
		
		for ( Attribute attribute : list ) {
			result.put( attribute.getName(), attribute.getValue() );
		}
		
		return result;
	}
	
	/**
	 * Attempts to register the UID, Key and userid combination. Returns true if successful, false otherwise. Useful in Identity mode.
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @param key
	 *            encryption key associated with UID
	 * @param userid
	 *            Unique user identifier
	 * @return true if device registration was successful, false otherwise
	 */
	public boolean registerDevice( String uid, String key, String userid ) {
		try {
			String existingUserId = getUserId( uid );
			if ( null != existingUserId && !existingUserId.equals( userid ) ) {
				return false;
			}
			this.storeDevice( uid, key, userid );
			return this.authenticateDevice( uid, key );
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during registerDevice", exception );
			return false;
		}
	}
	
	/**
	 * Deletes the specified UID from the identity domain.
	 * 
	 * @param uid
	 *            Unique device identifier
	 */
	public void deleteDevice( String uid ) {
		DeleteAttributesRequest dar = new DeleteAttributesRequest( IDENTITY_DOMAIN, uid );
		this.sdb.deleteAttributes( dar );
	}
	
	/**
	 * Authenticates the given UID, Key combination. If the password in the item identified by the item name 'UID' matches the Key given then true is
	 * returned, false otherwise.
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @param key
	 *            encryption key associated with UID
	 * @return true if authentication was successful, false otherwise
	 */
	public boolean authenticateDevice( String UID, String Key ) {
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, UID ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		if ( data != null && !data.isEmpty() ) {
			Attribute passwordAttribute = this.findAttributeInList( KEY, data );
			return passwordAttribute.getValue().equals( Key );
		}
		else {
			return false;
		}
	}
	
	/**
	 * Store the UID, Key, userid combination in the Identity domain. The UID will represent the item name and the item will contain attributes key
	 * and userid.
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @param key
	 *            encryption key associated with UID
	 * @param userid
	 *            Unique user identifier
	 */
	protected void storeDevice( String uid, String key, String userid ) {
		
		ReplaceableAttribute keyAttr = new ReplaceableAttribute( KEY, key, Boolean.TRUE );
		ReplaceableAttribute useridAttr = new ReplaceableAttribute( USERID, userid, Boolean.TRUE );
		
		List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>( 2 );
		attributes.add( keyAttr );
		attributes.add( useridAttr );
		
		try {
			PutAttributesRequest par = new PutAttributesRequest( IDENTITY_DOMAIN, uid, attributes );
			this.sdb.putAttributes( par );
		}
		catch ( Exception exception ) {
			log.log( Level.WARNING, "Exception during storeDevice", exception );
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
	 * Get the key associated with Device id
	 * 
	 * @param UID
	 *            Unique device identifier
	 * @return key associated with UID, null if not found
	 */
	public String getKey( String UID ) {
		if ( null == UID ) {
			return null;
		}
		
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, UID ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		if ( data != null && !data.isEmpty() ) {
			Attribute keyAttribute = this.findAttributeInList( KEY, data );
			return keyAttribute.getValue();
		}
		else {
			return null;
		}
	}
	
	/**
	 * Get the userid associated with Device id
	 * 
	 * @param UID
	 *            Unique device identifier
	 * @return userid associated with UID, null if not found
	 */
	public String getUserId( String UID ) {
		if ( null == UID ) {
			return null;
		}
		
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, UID ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		if ( data != null && !data.isEmpty() ) {
			Attribute keyAttribute = this.findAttributeInList( USERID, data );
			return keyAttribute.getValue();
		}
		else {
			return null;
		}
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
		List<String> domains = new ArrayList<String>( 1000 );
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
	 * Checks to see if the device id (UID) already exist in the device domain
	 * 
	 * @param uid
	 *            Unique device identifier
	 * @return true if the given UID already exist, false otherwise
	 */
	private boolean checkUidExists( String uid ) {
		GetAttributesRequest gar = new GetAttributesRequest( IDENTITY_DOMAIN, uid ).withConsistentRead( Boolean.TRUE );
		List<Attribute> data = this.sdb.getAttributes( gar ).getAttributes();
		return ( data != null && !data.isEmpty() );
	}
}

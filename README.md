# SCIM2 LSC Plugin

A SCIM2 plugin connector for LSC (LDAP Synchronization Connector)

## Goal
The object of this plugin is to synchronize users and groups from an identity server supporting SCIM2 protocol to another LSC compatible destination. For example it can be used to synchronize users from a WSO2 Identity Server user store to an application database.

## Configuration
There are an example of configuration in the `etc` directory. The `lsc.xml` file describes a synchronization from SCIM2 API to a database.

#### Source service

##### Connection
+ `name`: the name of the connection
+ `url`: the base URL of the SCIM source 
+ `username`: username of a user which has read permissions on the SCIM source 
+ `password`: user password

##### Service settings
+ `entity`: the entity to synchronize ('User' or 'Group') 
+ `pivot`: the pivot attribute name, default is **"id"** (OPTIONAL)
+ `domain`: The name of the user store to which filtering needs to be applied  (OPTIONAL) 
+ `pageSize`: Specifies the desired maximum number of query results (OPTIONAL) 
+ `filter`: A filter expression used to filter users (OPTIONAL) 
+ `attributes`: Attribute names of attributes that are to be included in the response (OPTIONAL) 
+ `excludedAttributes`: Attribute names of attributes that are to be excluded from the response. (OPTIONAL) 



# SCIM2 LSC Plugin

[![Build Status](https://travis-ci.org/giuseppeamato/lsc-scim-plugin.svg?branch=master)](https://travis-ci.org/giuseppeamato/lsc-scim-plugin)
[![codecov](https://codecov.io/gh/giuseppeamato/lsc-scim-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/giuseppeamato/lsc-scim-plugin)

A SCIM2 plugin connector for LSC (LDAP Synchronization Connector)

## Goal
The object of this plugin is to synchronize users and groups between an identity server supporting SCIM2 protocol and another LSC compatible source/destination. For example it can be used to synchronize users from a WSO2 Identity Server user store to an application database.

**Note**:
<em>The destination service is still experimental</em>, although mostly working, because of some bugs in my SCIM provider reference implementation (WSO2 IdS). 
I'll test this plugin with other SCIM compliant identity servers in the near future.

## Installation

Download the latest distibution asset or build it with maven and upload the jar into LSC lib 
folder.
Run LSC with flag **-DLSC.PLUGINS.PACKAGEPATH=it.pz8.lsc.plugins.connectors.scim.generated**

**Build note**:
The test phase of build process is docker container based, test units connects via https 
to the services exposed by them, so you must include the SSL certificate into your Java 
truststore (cacerts file).
Latest release tests are based on WSO2 IdS v5.10.0, so the certificate is the following:

```xml 
-----BEGIN CERTIFICATE-----
MIIDqTCCApGgAwIBAgIEXbABozANBgkqhkiG9w0BAQsFADBkMQswCQYDVQQGEwJV
UzELMAkGA1UECAwCQ0ExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxDTALBgNVBAoM
BFdTTzIxDTALBgNVBAsMBFdTTzIxEjAQBgNVBAMMCWxvY2FsaG9zdDAeFw0xOTEw
MjMwNzMwNDNaFw0yMjAxMjUwNzMwNDNaMGQxCzAJBgNVBAYTAlVTMQswCQYDVQQI
DAJDQTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzENMAsGA1UECgwEV1NPMjENMAsG
A1UECwwEV1NPMjESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAxeqoZYbQ/Sr8DOFQ+/qbEbCp6Vzb5hzH7oa3hf2FZxRK
F0H6b8COMzz8+0mvEdYVvb/31jMEL2CIQhkQRol1IruD6nBOmkjuXJSBficklMaJ
ZORhuCrB4roHxzoG19aWmscA0gnfBKo2oGXSjJmnZxIh+2X6syHCfyMZZ00LzDyr
goXWQXyFvCA2ax54s7sKiHOM3P4A9W4QUwmoEi4HQmPgJjIM4eGVPh0GtIANN+BO
Q1KkUI7OzteHCTLu3VjxM0sw8QRayZdhniPF+U9n3fa1mO4KLBsW4mDLjg8R/JuA
GTX/SEEGj0B5HWQAP6myxKFz2xwDaCGvT+rdvkktOwIDAQABo2MwYTAUBgNVHREE
DTALgglsb2NhbGhvc3QwHQYDVR0OBBYEFEDpLB4PDgzsdxD2FV3rVnOr/A0DMB0G
A1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjALBgNVHQ8EBAMCBPAwDQYJKoZI
hvcNAQELBQADggEBAE8H/axAgXjt93HGCYGumULW2lKkgqEvXryP2QkRpbyQSsTY
cL7ZLSVB7MVVHtIsHh8f1C4Xq6Qu8NUrqu5ZLC1pUByaqR2ZIzcj/OWLGYRjSTHS
VmVIq9QqBq1j7r6f3BWqaOIiknmTzEuqIVlOTY0gO+SHdS62vr2FCz4yOrBEulGA
vomsU8sqg4PhFnkhxI4M912Ly+2RgN9L7AkhzK+EzXY1/QtlI/VysNfS6zrHasKz
6CrKKCGqQnBnSvSTyF9OR5KFHnkAwE995IZrcSQicMxsLhTMUHDLQ/gRyy7V/ZpD
MfAWR+5OeQiNAp/bG4fjJoTdoqkul51+2bHHVrU=
-----END CERTIFICATE-----
```


## Configuration

##### Connection
+ `name`: the name of the connection
+ `url`: the base URL of the SCIM source 
+ `username`: username of a user which has appropriate permissions on the SCIM2 Provider 
+ `password`: user password

##### Service settings
+ `entity`: the entity to synchronize ('**Users**' or '**Groups**')
+ `sourcePivot`: the pivot attribute name on the source side, default is `pivot` attribute value. (used only by SCIM Destination Service). This configuration parameter is deprecated, next release will relies on **pivotTransformation** feature of LSC 2.2. (OPTIONAL)
+ `pivot`: the pivot attribute name, default is **"id"** (OPTIONAL)
+ `domain`: The name of the user store to which filtering needs to be applied  (OPTIONAL) 
+ `pageSize`: Specifies the desired maximum number of query results (OPTIONAL) 
+ `filter`: A filter expression used to filter users (OPTIONAL) 
+ `attributes`: Attribute names of attributes that are to be included in the response (OPTIONAL) 
+ `excludedAttributes`: Attribute names of attributes that are to be excluded from the response. (OPTIONAL) 
+ `schema`: Define aliases for schema extension URIs. (OPTIONAL)
+ `writableAttributes`: list of attributes to manage trough this connector (used only by SCIM Destination Service). 

## Examples
In the `etc` directory there are two configuration examples:

the directory `src-service` contains a configuration example of synchronization from SCIM2 to database. 

The directory `dst-service`, contains a configuration example of synchronization from LDAP to SCIM2

##### schema configuration
The connector flattens the nested structure of the SCIM response into a key-value map (with keys as path with dot–notation form) and viceversa, 
so, when attributes with schema extension URI containing '.' are envolved, for example `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User`, 
the dot char cause problems; therefore is important that the alias must not contains the '.' char.
The services makes the proper transformations transparently. 

E.g.:
 
```xml 
<scim:schema>
  <scim:namespace>
    <scim:alias>ENTERPRISE_USER</scim:alias>
    <scim:uri>urn:ietf:params:scim:schemas:extension:enterprise:2.0:User</scim:uri>
  </scim:namespace>
</scim:schema>
```

##### Source Pivot

During the sync phase, the destination service has to check the source dataset to find the ID of the item, if the pivot attribute name on the source doesn't have the same name of the destination counterpart,
you have to set that name in the sourcePivot configuration parameter.
Future versions of this plugin will relies on **pivotTransformation** feature of LSC 2.2.

E.g.:

```xml 
<scim:entity>Users</scim:entity>
<scim:sourcePivot>uid</scim:sourcePivot>
<scim:pivot>userName</scim:pivot>
```

##### Attribute names

+ `nested attribute`: the attribute name must have dot-notation form, e.g. **name.givenName**
+ `multivalued attribute` 
    + `simple`: the attribute name must end with `[]`, e.g. **phoneNumbers[]**
    + `with path`: attribute with type discriminator must have the selector path into the square brackets, e.g. **emails[type eq work]**
+ `extension schema attributes`: the attribute name contains the alias of the schema URI, e.g. **ENTERPRISE_USER.department**

##### Groups and membership
Although a service can obtain role membership through `groups` or `roles` SCIM attribute of **User** entity, 
updating role membership is possible only through `members` attribute of **Group** entity 
  
Examples of user role attribute definition in users sync task (**source service**):

```xml 
<dataset>
  <name>roles</name>
  <forceValues>
    <string>
      <![CDATA[rjs:
      var memberOf = '';
      srcBean.getAttributesNames().forEach(function(entry) {
        if (entry.startsWith("groups[display eq ")) {
          memberOf = memberOf + entry.substring("groups[display eq ".length, entry.indexOf("]"))+",";
        }
      });
      memberOf.slice(0, -1)
      ]]>          
    </string>
  </forceValues>
</dataset>
``` 

or simply: 

```xml 
<dataset>
  <name>roles</name>
  <forceValues>
    <string>srcBean.getDatasetValuesById("roles[type eq default]")</string>
  </forceValues>
</dataset>
``` 

Example of user membership attribute definition in group sync task (**destination service**):

```xml
<dataset>
  <name>members[]</name>
  <forceValues>
    <string>
      <![CDATA[rjs:
      var membersSrcDn = srcBean.getDatasetValuesById("member");
      var membersDstDn = [];
      for  (var i=0; i<membersSrcDn.size(); i++) {
        var memberSrcDn = membersSrcDn.get(i);
        var uid = memberSrcDn.substring(4,memberSrcDn.indexOf(","));
        obj = "{\"display\": \""+uid+"\"}";
        membersDstDn.push(obj);
      }
      (membersDstDn.length==0)?[""]:membersDstDn
      ]]>          
    </string>
  </forceValues>
</dataset>
``` 
 

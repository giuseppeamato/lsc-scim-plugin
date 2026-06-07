<p align="center">
    <img alt="LSC SCIM2" src="assets/lscscim-logo.png" width="600" />
</p>

# SCIM2 LSC Plugin

[![Build Status](https://github.com/giuseppeamato/lsc-scim-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/giuseppeamato/lsc-scim-plugin/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/giuseppeamato/lsc-scim-plugin/graph/badge.svg?token=S80UDC1165)](https://codecov.io/gh/giuseppeamato/lsc-scim-plugin)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=giuseppeamato_lsc-scim-plugin&metric=alert_status)](https://sonarcloud.io/dashboard?id=giuseppeamato_lsc-scim-plugin)

A SCIM2 plugin connector for [LSC (LDAP Synchronization Connector)](https://github.com/lsc-project/lsc)

## Goal
The purpose of this plugin is to synchronize users and groups between an identity provider that supports the SCIM2 protocol and another LSC-compatible system, such as an LDAP server or a database.

## Installation

Download the latest distibution asset or build it with maven and upload the jar into LSC lib folder.
Run LSC with flag **-DLSC.PLUGINS.PACKAGEPATH=it.pz8.lsc.plugins.connectors.scim.generated**

## Compatibility

This component has been tested against the following Identity Providers:

| IdP        | Version |
|-----------|--------|
| WSO2 Identity Server   | 5.11   |
| WSO2 Identity Server     | 7.2 |
| Keycloak (with SCIM for Keycloak Plugin) | 26.6 |
| WSO2 Asgardeo |  |
| Auth0     | |

> **Note:** the level of SCIM feature support is not the same across all IdPs. Some
> providers implement only a subset of the protocol or map SCIM attributes onto an
> underlying store with different semantics. See [Provider-specific notes](#provider-specific-notes).

## Configuration

##### Connection
+ `name`: the name of the connection
+ `url`: the base URL of the SCIM source 
+ `username`: username of a user which has appropriate permissions on the SCIM2 Provider 
+ `password`: user password

Two authentication strategies are supported. **Basic authentication** is used when `username`/`password`
are set. **OAuth2 (client credentials)** is used when an `oauth2ConnectionSettings` block is present;
in that case leave `username`/`password` empty and configure:

+ `tokenURL`: the OAuth2 token endpoint
+ `clientId`: the OAuth2 client identifier
+ `clientSecret`: the OAuth2 client secret
+ `scope`: requested scope (OPTIONAL)
+ `token`: a pre-obtained bearer token to use directly instead of the client-credentials flow (OPTIONAL)

```xml
<pluginConnection>
    <name>scim-conn</name>
    <url>http://localhost:8100/realms/myrealm/scim/v2</url>
    <username></username>
    <password></password>
    <scim:oauth2ConnectionSettings>
        <scim:tokenURL>http://localhost:8100/realms/myrealm/protocol/openid-connect/token</scim:tokenURL>
        <scim:clientId>scim-client</scim:clientId>
        <scim:clientSecret>${KEYCLOAK_CLIENT_SECRET}</scim:clientSecret>
    </scim:oauth2ConnectionSettings>
</pluginConnection>
```

##### Service settings
+ `entity`: the entity to synchronize ('**Users**' or '**Groups**')
+ `pivot`: the pivot attribute name, default is **"id"** (OPTIONAL)
+ `sourcePivot`: the pivot attribute name on the source side, default is the `pivot` attribute value. (used only by SCIM Destination Service).
+ `sourceUUID`: the source attribute holding the stable unique identifier of the object, stored in the mapping cache and used to resolve group membership across tasks (used only by SCIM Destination Service, OPTIONAL).
+ `cacheConnection`: references a `databaseConnection` used as the UUID mapping cache (see [UUID mapping cache](#uuid-mapping-cache)). Attributes: `reference` (the connection name) and `writeEnabled` (`true` to populate the cache during this task, `false` to read only). (OPTIONAL)
+ `domain`: The name of the user store to which filtering needs to be applied  (OPTIONAL) 
+ `pageSize`: Specifies the desired maximum number of query results (OPTIONAL) 
+ `filter`: A filter expression used to filter users (OPTIONAL) 
+ `attributes`: Attribute names of attributes that are to be included in the response (OPTIONAL) 
+ `excludedAttributes`: Attribute names of attributes that are to be excluded from the response. (OPTIONAL) 
+ `schema`: Define aliases for schema extension URIs. (OPTIONAL)
+ `flatMultivalueStrategy`: how PATCH updates are built for **flat** multivalued attributes (those ending with `[]`). `ELEMENT_DIFF` (default) computes the minimal per-element add/remove operations; `WHOLESALE_REPLACE` replaces the whole array in a single `replace` operation. Use `WHOLESALE_REPLACE` for providers that reject `add`/filtered `remove` on scalar arrays (see [Provider-specific notes](#provider-specific-notes)). (OPTIONAL)
+ `writableAttributes`: list of attributes to manage trough this connector (used only by SCIM Destination Service). 

## Examples
The `etc` directory contains ready-to-adapt configuration examples, organized in two folders:

+ `src-service`: synchronization **from SCIM2 to a database** (SCIM as source).
+ `dst-service`: synchronization **from LDAP to SCIM2** (SCIM as destination).

Each folder provides a variant per tested provider — `lsc-wso2-ids.xml`, `lsc-keycloak.xml`,
`lsc-wso2-asgardeo.xml` and `lsc-auth0.xml` — showing the provider-specific settings discussed in
[Provider-specific notes](#provider-specific-notes).

##### schema configuration
The connector flattens the nested structure of the SCIM response into a key-value map (with keys as path with dot–notation form) and viceversa, 
so, when attributes with schema extension URI containing '.' are envolved, for example `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User`, 
the dot char cause problems; therefore is important that the alias must not contains the '.' char.
The services makes the proper transformations transparently. 

E.g.:
 
```xml 
<scim:schema>
  <scim:namespace>
    <scim:alias>ENTERPRISE_USER_SCHEMA</scim:alias>
    <scim:uri>urn:ietf:params:scim:schemas:extension:enterprise:2.0:User</scim:uri>
  </scim:namespace>
</scim:schema>
```

##### Attribute names 

+ `nested attribute`: the attribute name must have dot-notation form, e.g. **name.givenName**
+ `multivalued attribute` 
    + `simple`: the attribute name must end with `[]`, the result is an array of primitive elements, e.g. **phoneNumbers[]**
    + `with path`: attribute with type discriminator must have the selector path into the square brackets, e.g. **emails[type eq work]** 
+ `extension schema attributes`: the attribute name contains the alias of the schema URI, e.g. **ENTERPRISE_USER_SCHEMA.department**

NB: Don't use these naming rules on "filter", "attributes" and "excludedAttributes" configuration parameters.

##### Source Pivot

During the sync phase, the destination service has to check the source dataset to find the ID of the item, if the pivot attribute name on the source doesn't have the same name of the destination counterpart,
you have to set that name in the sourcePivot configuration parameter.

E.g.:

```xml 
<scim:entity>Users</scim:entity>
<scim:sourcePivot>uid</scim:sourcePivot>
<scim:pivot>userName</scim:pivot>
```

##### Password strategy on WSO2 IDS
In the provided example, the plugin is configured to generate a temporary random password and enforce its change upon the user's first login. With WSO2 IDS this is achieved by setting the claim **urn:scim:wso2:schema.askPassword**

```xml 
<dataset>
	<name>password</name>
	<policy>FORCE</policy>
	<createValues>
		<string>
			<![CDATA["Temp!" + java.util.UUID.randomUUID().toString().substring(0,8);]]>
		</string>						
	</createValues>
</dataset>
<dataset>
	<name>IDENTITY_USER_SCHEMA.askPassword</name>
	<policy>FORCE</policy>
	<createValues>
		<string>"true"</string>          
	</createValues>
</dataset>
```




##### UUID mapping cache

When synchronizing groups, the `members` attribute of a SCIM **Group** references users by their
SCIM `id`, which is assigned by the destination provider and is not known on the source side. To
resolve membership the plugin keeps a mapping cache (H2 + HikariCP) that associates, per entity, the
source UUID, the source pivot and the SCIM `id` assigned by the provider.

The cache is wired through the `cacheConnection` element pointing to a `databaseConnection`:

```xml
<databaseConnection>
    <name>scim-cache-conn</name>
    <url>jdbc:h2:file:./data/scim2mapping</url>
    <username>sa</username>
    <password>admin</password>
    <driver>org.h2.Driver</driver>
</databaseConnection>
...
<scim:sourceUUID>entryDN</scim:sourceUUID>
<scim:cacheConnection reference="scim-cache-conn" writeEnabled="true" />
```

Set `writeEnabled="true"` on the task that owns the entity (typically the Users task) so it populates
the cache, and `writeEnabled="false"` on tasks that only read it (typically the Groups task). The
membership scripts in the examples resolve entries via the `ScimUtils` helper
(`getCachedDataByUUID` / `getCachedDataByPivot`).

> **Concurrency:** a file-based H2 cache (`jdbc:h2:file:...`) is shared across sequential runs. If you
> intend to run tasks in parallel, give each run a distinct cache file (or use an in-memory cache)
> to avoid contention.

##### Multivalued attributes: provider limitations

The SCIM standard leaves room for interpretation, and real providers diverge in how they handle
multivalued attributes. The most common pitfalls:

+ **`primary` cannot be inferred.** The plugin does not automatically derive which element is the
  primary one; set it explicitly with a selector such as `emails[primary eq true]`.
+ **Duplicate `type` values.** Some IdPs reject multiple elements that share the same `type`
  (e.g. WSO2 IS does not allow more than one email with `type="work"`), because they map the
  multivalued attribute onto an underlying single-valued LDAP claim.
+ **`add` on scalar arrays.** In WSO2 Identity Server 7.2 the `add` operation cannot be used to
  append new values to a simple multivalued attribute (an array of primitives). This is allowed by
  RFC 7644, which does not require servers to support `add` on scalar arrays without a filter or
  index. For these providers set `flatMultivalueStrategy` to `WHOLESALE_REPLACE`, so the whole array
  is rewritten with a single `replace` operation instead of incremental `add`/`remove`.

##### Groups and membership

A service can *read* role membership through the `groups` or `roles` attribute of the **User** entity,
but membership can only be *updated* through the `members` attribute of the **Group** entity. Because
`members` references users by their provider-assigned SCIM `id`, group synchronization relies on the
[UUID mapping cache](#uuid-mapping-cache) to translate source identifiers into SCIM ids.
  
Examples of user role attribute definition in users sync task (**source service**):

```xml 
<dataset>
  <name>roles</name>
  <forceValues>
    <string>
      <![CDATA[
      var memberOf = '';
      srcBean.getAttributesNames().forEach(function(entry) {
        if (entry.startsWith("groups[display eq ")) {
          memberOf = memberOf + JSON.parse(entry.substring("groups[display eq ".length, entry.indexOf("]")))+",";
        }
      });
      memberOf.slice(0, -1)
      ]]>          
    </string>
  </forceValues>
</dataset>
``` 

Example of user membership attribute definition in group sync task (**destination service**):

```xml
<dataset>
  <name>members[]</name>
  <forceValues>
    <string>
      <![CDATA[
      var membersSrcDn = srcBean.getDatasetValuesById("member");
      var scimUtils = Java.type("it.pz8.lsc.plugins.connectors.scim.utils.ScimUtils");
      var membersDstDn = [];
      for  (var i=0; i<membersSrcDn.size(); i++) {
        var memberSrcDn = membersSrcDn.get(i);
        var cachedData = scimUtils.getCachedDataByUUID(memberSrcDn, "Users");
        var uid = memberSrcDn.substring(4,memberSrcDn.indexOf(","));
        obj = JSON.stringify({display: uid, value: cachedData.getScimId()});
        membersDstDn.push(obj);
      }
      (membersDstDn.length==0)?[""]:membersDstDn
      ]]>          
    </string>
  </forceValues>
</dataset>
``` 

## Provider-specific notes

SCIM feature support varies between providers; the table below summarizes the quirks worth knowing
when configuring a sync task.

| Provider | Notes |
|---|---|
| **WSO2 Identity Server** | Often persists multivalued attributes onto single-valued underlying LDAP claims, so multiple elements with the same `type` may be rejected. `add` on scalar arrays is not supported in 7.2 — use `flatMultivalueStrategy=WHOLESALE_REPLACE`. A temporary random password with forced change at first login can be requested via the `urn:scim:wso2:schema.askPassword` claim. |
| **Keycloak** (SCIM for Keycloak plugin) | Group membership PATCH on `group.members` may return `501 Not Implemented`; synchronize membership from the Group entity using the UUID mapping cache as shown in the examples. |
| **WSO2 Asgardeo** | Rejects `add` and unfiltered `remove` on flat extension-URN paths; prefer selector-filtered paths and `WHOLESALE_REPLACE` for scalar arrays. |
| **Auth0** | Subset of SCIM operations supported; validate write paths against your tenant configuration. |
 

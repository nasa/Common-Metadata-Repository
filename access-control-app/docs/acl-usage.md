## Using ACLS in the CMR

### Background

Access Control Lists (ACLs) are the mechanism by which users are granted access to perform different operations in the CMR. CMR ACLs follow the same design as ECHO ACLs which in turn are derived from the generic ACL design pattern used in many other systems. At a high level, an ACL is a mapping of actors (subjects) to resources (object) to operations (predicate). For instance, a CMR ACL might specify that all Registered users have READ access to ASTER data or all users in a provider operations group have permissions to ingest data for a particular provider.

The full ACL JSON schema is documented [here](acl-schema.html).  Below is an overview of the concepts which make up an ACL.

#### Subjects

The subject specifies the **who** of an ACL, or what actors are being granted access by the ACL. CMR provides two mechanisms to define the subject:

- **group_id**: The concept id of a CMR access-control group
- **user_type**: A built in meta grouping of users:
  - **guest**: matches all users who have not authenticated with the system
  - **registered**: matches all users who have authenticated with the system

#### Objects

The object specifies the resource on which access is being controlled by an ACL. There are 4 classes of objects:

- **System Identities**: Global CMR resources which are not associated with a specific provider. Generally, access to these resources is only granted to CMR operations staff (though there are some exceptions - e.g. partial user information access granted to the MERIS form tool user).
- **Provider Identities**: Resources which belong to a provider. Permissions on provider resources are limited to the specified provider and do not transfer to another. Examples include access to ingest new data or view/modify order and service options for a provider's holdings.
- **Single Instance Identities**: A single discrete resource as opposed to a class of resources. Currently, this is used only for specifying a specific group, for instance to grant members of one group the ability to manage another group.  This 'Group Management' Single Instance ACL grants members of the managing group the ability to update (e.g. add/remove users) or delete the managed group.
- **Catalog Item Identities**: Collection and granule resources. ACLs on these resources is used to determine what results a user sees in search results.

System and provider resources are specified as a single target string (e.g. `INGEST_MANAGEMENT_ACL`), and in the case of provider resources, a provider ID. The list of available targets can be found in the [access control API docs](api.html) under 'Grantable Permissions'. Single instance resources are specified by a target string (currently only `GROUP_MANAGEMENT` is supported) and a target ID (i.e. group concept-id). Catalog Item resources have a more complex specification which is described below.

#### Catalog Item Identities

Unlike the other types of resource identities, catalog item identities contain a filter of catalog items rather than a simple target string. Catalog item identities contain:

- **name**: A descriptive name for the resources being selected, e.g. `All Granules` or `ASTER Data`
- **provider_id**: The provider with which the identity is associated
- **collection_applicable**: Flag indicating whether the filter matches collections
- **granule_applicable**: Flag indicating whether the filter matches granules
- **collection_identifier**: A filter defining the collections matched by this ACL.  This filter consists of a combination of Collection Entry Titles, Collection Concept Ids, a restriction flag(AKA Access Value) range, and a temporal range (see the [schema](acl-schema.html#-collectionidentifiertype-object-))
- **granule_identifier**: A filter defining the granules matched by this ACL.  This filter consists of a combination of restriction flag (AKA Access Value) range, and a temporal range and can be combined with a collection_identifier (see the [schema](acl-schema.html#-granuleidentifiertype-object-))

It should be noted that while temporal Catalog item filters are supported by the API, they are not currently used operationally.  In addition, a restriction flag (or Access Value) filter  may specify a include_undefined_value flag.  If set to false, only items which have an access value within the specified range will be matched.  If set to true, item with no value set, as well as those with a value in the specified range will be matched. include_undefined_value defaults to 'false'

#### Predicates

The predicate specifies which operations are permitted on a resource. The available predicates are `create`, `read`, `update`, `delete`, and `order`. The first 4 operations correspond to the expected CRUD operations on an object while `order` is a predicate specific to catalog item resources which allows users to order the specified resources, rather than simply view the metadata. Each ACL resource has a subset of these predicates which are valid for that resource (see the [access control API docs](api.html) under 'Grantable Permissions'). The meaning of each predicate in relation to a given resource can usually be deduced, but ultimately, the CMR and ECHO code which performs access verification on each operation implicitly defines the meaning of each.  An ACL's meaning ultimately depends only on the code which is performing checks against the ACL, so where there is any confusion about what an ACL does, ask CMR development.

### Built in ACLs

There are a set of ACLs which are expected to exist in any CMR environment and which are set up as part of the initialization of a clean system in dev or ci environments. A client can expect these ACLs to exist in any environment and should not have to create them.

#### Minimal ACLS for an empty system

The ACLS below are preloaded before the CMR can start up. The JSON representation below comes from the CMR access-control endpoint with ACL summaries requested ( `http://localhost:3011/acls?include-full-acl=false`).  These ACLs can be populated in a fresh environment using `java -cp cmr-legacy-services-0.1.0-SNAPSHOT-standalone.jar cmr.db bootstrap-db-and-migrate`

```
{
  "hits": 3,
  "took": 6,
  "items": [
    {
      "revision_id": 1,
      "concept_id": "ACL1200000001-CMR",
      "identity_type": "Group",
      "name": "Group - AG1200000000-CMR",
      "location": "http://localhost:3011/acls/ACL1200000001-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000003-CMR",
      "identity_type": "System",
      "name": "System - ANY_ACL",
      "location": "http://localhost:3011/acls/ACL1200000003-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000002-CMR",
      "identity_type": "System",
      "name": "System - GROUP",
      "location": "http://localhost:3011/acls/ACL1200000002-CMR"
    }
  ]
}
```

#### ACLS bootstrapped in the system to allow normal legacy-services/CMR operation

The ACLs below are loaded into a clean CMR system once it is started. These ACLs are required for normal CMR operations. The JSON representation below comes from the CMR access control endpoint with full ACL records requested (http://localhost:3011/acls?include-full-acl=true) (note that the full records for the summaries above are listed as the first three ACLs here.)  These ACLs can be populated using `java -cp cmr-legacy-services-0.1.0-SNAPSHOT-standalone.jar cmr.db bootstrap-running-kernel`

```
{
  "hits": 24,
  "took": 28,
  "items": [
    {
      "revision_id": 2,
      "concept_id": "ACL1200000001-CMR",
      "identity_type": "Group",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "441AED4651485230E055000000000001",
        "single_instance_identity": {
          "target": "GROUP_MANAGEMENT",
          "target_id": "AG1200000000-CMR"
        }
      },
      "name": "Group - AG1200000000-CMR",
      "location": "http://localhost:3011/acls/ACL1200000001-CMR"
    },
    {
      "revision_id": 3,
      "concept_id": "ACL1200000003-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "create",
              "delete",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "441AED46514E5230E055000000000001",
        "system_identity": {
          "target": "ANY_ACL"
        }
      },
      "name": "System - ANY_ACL",
      "location": "http://localhost:3011/acls/ACL1200000003-CMR"
    },
    {
      "revision_id": 3,
      "concept_id": "ACL1200000002-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "create",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "441AED46514B5230E055000000000001",
        "system_identity": {
          "target": "GROUP"
        }
      },
      "name": "System - GROUP",
      "location": "http://localhost:3011/acls/ACL1200000002-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000021-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "B7E5F03E-2E65-23AB-F7E0-66DE63E741FE",
        "system_identity": {
          "target": "ARCHIVE_RECORD"
        }
      },
      "name": "System - ARCHIVE_RECORD",
      "location": "http://localhost:3011/acls/ACL1200000021-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000012-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "66130CB6-E64A-A3CD-AFCA-DCB08902CC4B",
        "system_identity": {
          "target": "ERROR_MESSAGE"
        }
      },
      "name": "System - ERROR_MESSAGE",
      "location": "http://localhost:3011/acls/ACL1200000012-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000011-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "AB929485-EAB0-5C6A-9670-ACB0AFAB460C",
        "system_identity": {
          "target": "EVENT_NOTIFICATION"
        }
      },
      "name": "System - EVENT_NOTIFICATION",
      "location": "http://localhost:3011/acls/ACL1200000011-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000005-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "4302E0A4-A037-2A26-F1B3-59149BB66B9D",
        "system_identity": {
          "target": "EXTENDED_SERVICE"
        }
      },
      "name": "System - EXTENDED_SERVICE",
      "location": "http://localhost:3011/acls/ACL1200000005-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000013-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "create"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "9521D0F0-875F-16A0-0248-8C0D9463128F",
        "system_identity": {
          "target": "EXTENDED_SERVICE_ACTIVATION"
        }
      },
      "name": "System - EXTENDED_SERVICE_ACTIVATION",
      "location": "http://localhost:3011/acls/ACL1200000013-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000024-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "E7256B8F-D380-D85E-8BA9-BC124B0230A7",
        "system_identity": {
          "target": "INGEST_MANAGEMENT_ACL"
        }
      },
      "name": "System - INGEST_MANAGEMENT_ACL",
      "location": "http://localhost:3011/acls/ACL1200000024-CMR"
    },
    {
      "revision_id": 2,
      "concept_id": "ACL1200000022-CMR",
      "identity_type": "System",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "663C1CCF-CB62-BECF-5986-824AD8FBB74D",
        "system_identity": {
          "target": "METRIC_DATA_POINT_SAMPLE"
        }
      },
      "name": "System - METRIC_DATA_POINT_SAMPLE",
      "location": "http://localhost:3011/acls/ACL1200000022-CMR"
    }
  ]
}
```

### Additional Common ACLs

These ACLs are commonly added for normal operations, and may need to be added by a client such as MMT.

#### Provider ACLs

When a provider is created by a client, it is expected that certain provider object ACLs will also be created. Most of this is currently manual via PUMP or a script, but could be automated by MMT. Note that the ACLs below grant permissions to the System Administrator group. In general, these should also be granted to the Provider Administrator group. These ACLs can be retrieved using e.g. http://localhost:3011/acls?include-full-acl=true&identity_type=catalog_item&provider=CUKE_PROV1.

```
{
  "hits": 10,
  "took": 4,
  "items": [
    {
      "revision_id": 1,
      "concept_id": "ACL1200000045-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "D9A3AF53-CDBD-6338-84E3-01657E1410CC",
        "provider_identity": {
          "target": "DATASET_INFORMATION",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - DATASET_INFORMATION",
      "location": "http://localhost:3011/acls/ACL1200000045-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000051-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "create",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "A9C1FB0D-3A8E-3031-3359-23F6C0641AE8",
        "provider_identity": {
          "target": "DATA_QUALITY_SUMMARY_ASSIGNMENT",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - DATA_QUALITY_SUMMARY_ASSIGNMENT",
      "location": "http://localhost:3011/acls/ACL1200000051-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000049-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "create",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "A58CD41F-1D03-D1D6-A89E-D77E24ECB070",
        "provider_identity": {
          "target": "DATA_QUALITY_SUMMARY_DEFINITION",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - DATA_QUALITY_SUMMARY_DEFINITION",
      "location": "http://localhost:3011/acls/ACL1200000049-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000052-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "create",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "BB852244-EDFC-2821-05EB-A71C69FC125E",
        "provider_identity": {
          "target": "EXTENDED_SERVICE",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - EXTENDED_SERVICE",
      "location": "http://localhost:3011/acls/ACL1200000052-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000047-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "E3B64C6E-1D79-2E8A-10CE-2D41093FAB78",
        "provider_identity": {
          "target": "INGEST_MANAGEMENT_ACL",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - INGEST_MANAGEMENT_ACL",
      "location": "http://localhost:3011/acls/ACL1200000047-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000054-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "create",
              "delete",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "DCF981ED-39C5-F334-9928-6C02407F85CF",
        "provider_identity": {
          "target": "OPTION_ASSIGNMENT",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - OPTION_ASSIGNMENT",
      "location": "http://localhost:3011/acls/ACL1200000054-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000048-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "create",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "06781F79-BB3D-9443-BE7B-108C923D5697",
        "provider_identity": {
          "target": "OPTION_DEFINITION",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - OPTION_DEFINITION",
      "location": "http://localhost:3011/acls/ACL1200000048-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000053-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "create",
              "delete"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "201C706A-07A8-3CA0-30DA-3C85D2CFD515",
        "provider_identity": {
          "target": "PROVIDER_CALENDAR_EVENT",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - PROVIDER_CALENDAR_EVENT",
      "location": "http://localhost:3011/acls/ACL1200000053-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000050-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "E51E1A3E-3B0D-CB5B-06AC-E4BD33D9266A",
        "provider_identity": {
          "target": "PROVIDER_CONTEXT",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - PROVIDER_CONTEXT",
      "location": "http://localhost:3011/acls/ACL1200000050-CMR"
    },
    {
      "revision_id": 1,
      "concept_id": "ACL1200000046-CMR",
      "identity_type": "Provider",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "delete",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          }
        ],
        "legacy_guid": "79FC6E75-83F5-7DB5-72B5-DD03A483E548",
        "provider_identity": {
          "target": "PROVIDER_POLICIES",
          "provider_id": "CUKE_PROV1"
        }
      },
      "name": "Provider - CUKE_PROV1 - PROVIDER_POLICIES",
      "location": "http://localhost:3011/acls/ACL1200000046-CMR"
    }
  ]
}
```

#### Single Instance ACLs

When a group is created, it will generally be assigned one or more 'management groups' which have the ability to update or delete the group.  Management access is managed via Single Instance ACLs as shown below.

```
      "revision_id": 17,
      "concept_id": "ACL1200213959-CMR",
      "identity_type": "Group",
      "acl": {
        "group_permissions": [
          {
            "permissions": [],
            "group_id": "AG1200211475-CMR"
          },
          {
            "permissions": [
              "delete",
              "update"
            ],
            "group_id": "AG1200211473-CMR"
          },
          {
            "permissions": [
              "delete",
              "update"
            ],
            "group_id": "AG1200211474-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211483-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211477-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211479-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211480-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200213192-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211482-CMR"
          },
          {
            "permissions": [],
            "group_id": "AG1200211476-CMR"
          }
        ],
        "legacy_guid": "566C22AE-44C7-A060-560A-C054F8D3F134",
        "single_instance_identity": {
          "target": "GROUP_MANAGEMENT",
          "target_id": "AG1200211475-CMR"
        }
      },
      "name": "Group - AG1200211475-CMR",
      "location": "https://cmr.sit.earthdata.nasa.gov:443/access-control/acls/ACL1200213959-CMR"
    }
```

#### Catalog Item ACLs

Every provider needs to have Catalog Item ACLs to manage access to its holdings. The ACLs below grant view/order access to all collections and granules.  These ACLS must not be removed and must always be granted to the System Administrator group. The Catalog item identity portion of the ACL must also not be modified. Changes to these ACLs (other than to the group_permissions) may cause the CMR/legacy-services applications to no longer have access to all holdings which will result in unexpected behavior.

NOTE that the schema allows catalog item ACLs to grant Create, Read, Update, Delete, and Order permissions.  Only Read and Order are actually meaningful. Read permission allows users to see the affected catalog items in search results, and Order allows the user to order the items.  Ingest and delete permissions are managed by a separate `INGEST_MANAGEMENT_ACL` Provider ACL target.  Granting Create, Update, or Delete permissions on catalog item ACLs will have no effect.

https://cmr.sit.earthdata.nasa.gov/access-control/acls?include-full-acl=true&identity_type=catalog_item&provider=DEMO_PROV
```
...
    {
      "revision_id": 5,
      "concept_id": "ACL1200215231-CMR",
      "identity_type": "Catalog Item",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "order",
              "read"
            ],
            "group_id": "AG1200211474-CMR"
          },
          {
            "permissions": [
              "order",
              "read"
            ],
            "group_id": "AG1200212873-DEMO_PROV"
          },
          {
            "permissions": [
              "order",
              "read"
            ],
            "user_type": "registered"
          },
          {
            "permissions": [
              "read",
              "order"
            ],
            "user_type": "guest"
          }
        ],
        "legacy_guid": "4A8F942E-01D8-4CEC-AC5D-A1A5B9112614",
        "catalog_item_identity": {
          "name": "All Collections",
          "provider_id": "DEMO_PROV",
          "collection_applicable": true,
          "granule_applicable": false
        }
      },
      "name": "All Collections",
      "location": "https://cmr.sit.earthdata.nasa.gov:443/access-control/acls/ACL1200215231-CMR"
    },
    {
      "revision_id": 5,
      "concept_id": "ACL1200215232-CMR",
      "identity_type": "Catalog Item",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "order",
              "read"
            ],
            "group_id": "AG1200212873-DEMO_PROV"
          },
          {
            "permissions": [
              "read",
              "order"
            ],
            "group_id": "AG1200211474-CMR"
          },
          {
            "permissions": [
              "read",
              "order"
            ],
            "user_type": "registered"
          },
          {
            "permissions": [
              "read",
              "order"
            ],
            "user_type": "guest"
          }
        ],
        "legacy_guid": "BE0C8766-CA6E-8AE6-12F5-7A2657760EEC",
        "catalog_item_identity": {
          "name": "All Granules",
          "provider_id": "DEMO_PROV",
          "collection_applicable": false,
          "granule_applicable": true
        }
      },
      "name": "All Granules",
      "location": "https://cmr.sit.earthdata.nasa.gov:443/access-control/acls/ACL1200215232-CMR"
    }
...
```

While operational environments generally provide separate 'All Collections' and 'All Granules' ACLs, these can be combined for convenience in dev and ci environments as shown below.

```
{
  "hits": 1,
  "took": 5,
  "items": [
    {
      "revision_id": 3,
      "concept_id": "ACL1200000057-CMR",
      "identity_type": "Catalog Item",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "update",
              "order",
              "read"
            ],
            "group_id": "AG1200000000-CMR"
          },
          {
            "permissions": [
              "order",
              "read"
            ],
            "user_type": "guest"
          },
          {
            "permissions": [
              "order",
              "read"
            ],
            "user_type": "registered"
          }
        ],
        "legacy_guid": "32F9D75C-E5D7-F55C-17B7-36E3DDA8D4C2",
        "catalog_item_identity": {
          "name": "AllCollsAndGrans",
          "provider_id": "CUKE_PROV1",
          "collection_applicable": true,
          "granule_applicable": true
        }
      },
      "name": "AllCollsAndGrans",
      "location": "http://localhost:3011/acls/ACL1200000057-CMR"
    }
  ]
}
```

##### Restriction Flag and Entry Title Filtering

Below is a sample ACL which permits guest access to granules within a specific collection which have a specified Restriction flag.

```
    {
      "revision_id": 4,
      "concept_id": "ACL1200215510-CMR",
      "identity_type": "Catalog Item",
      "acl": {
        "group_permissions": [
          {
            "permissions": [
              "read",
              "order"
            ],
            "user_type": "guest"
          }
        ],
        "legacy_guid": "7118D8B5-0978-592F-FA00-FC905F085FDC",
        "catalog_item_identity": {
          "name": "FreeAsterAccessForAuthorizedUsers_Gran",
          "provider_id": "DEMO_PROV",
          "collection_applicable": false,
          "granule_applicable": true,
          "collection_identifier": {
            "entry_titles": [
              "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"
            ]
          },
          "granule_identifier": {
            "access_value": {
              "min_value": 225,
              "max_value": 225,
              "include_undefined_value": false
            }
          }
        }
      },
      "name": "FreeAsterAccessForAuthorizedUsers_Gran",
      "location": "https://cmr.sit.earthdata.nasa.gov:443/access-control/acls/ACL1200215510-CMR"
    }
```

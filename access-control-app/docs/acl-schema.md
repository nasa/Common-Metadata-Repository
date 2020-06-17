## ACL Schema Definitions

The schema defines the following properties:

## `legacy_guid` (IdentifierType)

## `group_permissions` (array)

The object is an array with all elements of the type `GroupPermissionsType`.

## `system_identity` (SystemIdentityType)

## `provider_identity` (ProviderIdentityType)

## `single_instance_identity` (SingleInstanceIdentityType)

## `catalog_item_identity` (CatalogItemIdentityType)

---

## Sub Schemas

The schema defines the following additional types:

### `SingleInstanceIdentityType` (object)

Properties of the `SingleInstanceIdentityType` object:

#### `target_id` (IdentifierType, required)

#### `target` (, enum, required)

This element must be one of the following enum values:

* `GROUP_MANAGEMENT`

### `GroupPermissionsType` (object)

Properties of the `GroupPermissionsType` object:

#### `permissions` (array)

#### `group_id` (string)

#### `user_type`

This element must be one of the following enum values:

* `registered`
* `guest`

### `IdentifierType` (string)

### `GranuleIdentifierType` (object)

Properties of the `GranuleIdentifierType` object:

#### `access_value` (AccessValueType)

#### `temporal` (TemporalIdentifierType)

### `SystemIdentityType` (object)

Properties of the `SystemIdentityType` object:

#### `target` (, enum, required)

This element must be one of the following enum values:

* `SYSTEM_AUDIT_REPORT`
* `METRIC_DATA_POINT_SAMPLE`
* `SYSTEM_INITIALIZER`
* `ARCHIVE_RECORD`
* `ERROR_MESSAGE`
* `TOKEN`
* `TOKEN_REVOCATION`
* `EXTENDED_SERVICE_ACTIVATION`
* `ORDER_AND_ORDER_ITEMS`
* `PROVIDER`
* `TAG_GROUP`
* `TAXONOMY`
* `TAXONOMY_ENTRY`
* `USER_CONTEXT`
* `USER`
* `GROUP`
* `ANY_ACL`
* `EVENT_NOTIFICATION`
* `EXTENDED_SERVICE`
* `SYSTEM_OPTION_DEFINITION`
* `SYSTEM_OPTION_DEFINITION_DEPRECATION`
* `INGEST_MANAGEMENT_ACL`
* `SYSTEM_CALENDAR_EVENT`
* `DASHBOARD_ADMIN`
* `DASHBOARD_ARC_CURATOR`
* `DASHBOARD_MDQ_CURATOR`

### `TemporalIdentifierType` (object)

Properties of the `TemporalIdentifierType` object:

#### `start_date` (string, required)

#### `stop_date` (string, required)

#### `mask` (, enum, required)

This element must be one of the following enum values:

* `intersect`
* `contains`
* `disjoint`

### `CatalogItemIdentityType` (object)

Properties of the `CatalogItemIdentityType` object:

#### `name` (IdentifierType, required)

#### `provider_id` (IdentifierType, required)

#### `collection_applicable` (boolean)

#### `granule_applicable` (boolean)

#### `collection_identifier` (CollectionIdentifierType)

#### `granule_identifier` (GranuleIdentifierType)

### `CollectionIdentifierType` (object)

Properties of the `CollectionIdentifierType` object:

#### `entry_titles` (array)

The object is an array with all elements of the type `string`.

#### `concept-ids` (array)

The object is an array with all elements of the type `string`.

#### `access_value` (AccessValueType)

#### `temporal` (TemporalIdentifierType)

### `AccessValueType` (object)

Properties of the `AccessValueType` object:

#### `min_value` (number)

#### `max_value` (number)

#### `include_undefined_value` (boolean)

### `ProviderIdentityType` (object)

Properties of the `ProviderIdentityType` object:

#### `provider_id` (IdentifierType, required)

#### `target` (, enum, required)

This element must be one of the following enum values:

* `AUDIT_REPORT`
* `OPTION_ASSIGNMENT`
* `OPTION_DEFINITION`
* `OPTION_DEFINITION_DEPRECATION`
* `DATASET_INFORMATION`
* `PROVIDER_HOLDINGS`
* `EXTENDED_SERVICE`
* `PROVIDER_ORDER`
* `PROVIDER_ORDER_RESUBMISSION`
* `PROVIDER_ORDER_ACCEPTANCE`
* `PROVIDER_ORDER_REJECTION`
* `PROVIDER_ORDER_CLOSURE`
* `PROVIDER_ORDER_TRACKING_ID`
* `PROVIDER_INFORMATION`
* `PROVIDER_CONTEXT`
* `AUTHENTICATOR_DEFINITION`
* `PROVIDER_POLICIES`
* `USER`
* `GROUP`
* `PROVIDER_OBJECT_ACL`
* `CATALOG_ITEM_ACL`
* `INGEST_MANAGEMENT_ACL`
* `DATA_QUALITY_SUMMARY_DEFINITION`
* `DATA_QUALITY_SUMMARY_ASSIGNMENT`
* `PROVIDER_CALENDAR_EVENT`
* `DASHBOARD_DAAC_CURATOR`
* `NON_NASA_DRAFT_USER`
* `NON_NASA_DRAFT_APPROVER`
* `SUBSCRIPTION_MANAGEMENT`

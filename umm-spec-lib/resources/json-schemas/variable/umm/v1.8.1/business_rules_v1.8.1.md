# Changes for UMM-Var 1.8.1

(This is accompanying ancillary documentation that is useful for CMR and MMT to handle the latest implementation of schema changes.  These include any noteworthy ingest/field migration rules, how the MMT forms should handle the new/additional fields, how the CMR preview should handle the new/additional fields, etc.).

## CMR
* Migrating UMM-Var records up to 1.8.1
         * MetadataSpecification needs to migrated up.
* Migrate down to 1.8 from 1.8.1
	 * If the variable Type = COORDINATE migrate down to OTHER.
	 * If the variable Subtype = LATITUDE migrate down to OTHER.
	 * If the variable Subtype = LONGITUDE migrate down to OTHER.
	 * If the variable Subtype = TIME migrate down to OTHER.
         * MetadataSpecification needs to migrated down.

## MMT
* Add the enumerations so that users can use them.
	* New VariableType enumeration is COORDINATE 
        * New VariableSubtype enumerations are: LATITUDE, LONGITUDE, TIME.

* Preview
    * Add the enumerations.

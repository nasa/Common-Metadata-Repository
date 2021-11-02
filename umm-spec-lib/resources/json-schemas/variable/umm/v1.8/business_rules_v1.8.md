# Changes for UMM-Var 1.8

(This is accompanying ancillary documentation that is useful for CMR and MMT to handle the latest implementation of schema changes.  These include any noteworthy ingest/field migration rules, how the MMT forms should handle the new/additional fields, how the CMR preview should handle the new/additional fields, etc.).

## CMR
* Migrating UMM-Var records up to 1.8
	 * add the MetadataSpecification for version 1.8
* Migrate down to 1.7 from 1.8
	 * drop MetadataSpecification
	 * drop RelatedURLs.

## MMT
* Follow UMM-S and UMM-T as to the form layout of RelatedURLs. RelatedURLs should be the last form for UMM-Var.
* Format and MimeType are new fields for RelatedURLs and they will be added to UMM-S and UMM-T RelatedURL section as well in subsequent UMM-S and UMM-T versions. The placement of these should be the same.
* MetadataSpecification should not be on a form, but hidden and filled out for the user.
* Preview
    * Add RelatedURLs.

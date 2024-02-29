As a consequence of the changes in 1.18.0, applications will need to make the
following changes:

UMM:
  1. UMM-C schema added "Type" and "DescriptionOfOtherType" to the AssociatedDoiType.
  2. UMM-C schema modified SpatialCoverageTypeEnum. Added "EARTH/GLOBAL" and "LUNAR" to the enum.
  3. UMM-C schema added "Data Maturity"
  4. UMM-C schema added PreviousVersion to the DoiType
  5. UMM-C schema added OtherIdentifiers
  6. UMM-C schema added FileNamingConvention
  7. UMM-C schema added TemporalResolution to TemporalExtent.

CMR:
  - CMR will need to be changed to use the new version schema.
  Migrating:
  1. AssociatedDoiType
    -  Up: No Change.
    -  Down: Remove "Type" and "DescriptionOfOtherType" from the AssociatedDOIs.

  2. SpatialCoverageTypeEnum
    - Up: No Change.
    - Down: If SpatialExtentType/SpatialCoverageType is "EARTH/GLOBAL" or "LUNAR", remove the SpatialCoverageType.

  3. Data Maturity
    - Up: No Change.
    - Down: Remove "Data Maturity"

  4. DOI/PreviousVersion
    - Up: No Change. (Note: CMR needs to validate DOI value in the PreviousVersion when ingest because it needs to match certain pattern like in doi-format-warning-validation)
    - Down: Remove PreviousVersions from DOI.

  5. OtherIdentifiers
    - Up: No Change.
    - Down: Remove OtherIdentifiers.

  6. FileNamingConvention
    - Up: No Change.
    - Down: Remove FileNamingConvention.

  7. TemporalResolution
    - Up: No Change.
    - Down: Remove TemporalExtent/TemporalResolution.

MMT:
  - Support all the new schema changes

EDSC:
  - no change

Stakeholders:
  - Stakeholders should be notified of the changes to the schema.


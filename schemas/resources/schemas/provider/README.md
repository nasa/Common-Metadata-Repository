# Provider Schema

An engenering schema to document metadata fields for a Provider. This is not an official UMM format, nor is it currently a Generic. However the name `UMM-P` and `:provider` are reserved for future use by this format if needed.

## Overview
Durring the effort to remove Legacy Services from CMR, the provider tables from both systems needed to be merged. The Legacy Services Provider included a few fields related to descrirption of holding and Contact information. These fields looked very similar to the UMM objects for Organization, ContactGroups, and ContactPersons as it is intended to make Provider data look as similar as posible to other UMM documents.

## Files

* README.md - this file
* example.json - a valid instance of the provider schema
* schema.json - the definition

## Use by CMR

* CMR will use ProviderId as the ShortName, both are saved in the SQL table as a column
* Consortiums will be extracted and set saved in the SQL table as a column
* fields `small` and `cmr-only` are read and stripped out before validation
* Admins can be found by `.ContactPersons[].Roles["PROVIDER MANAGMENT"]`. UserName is asssumed to be the EDL login name.

## Similarities to Legacy Services

* DescriptionOfHoldings maps directly
* OrganizationName maps to LongName
* DiscoverURLs maps to Organizations->URLValue where ProviderId and ShortName match
* Contacts mapped to ContactPersons with Roles set to `PROVIDER MANAGMENT`

## Diferences Other Systems

* **NO** GUID!
* No RestOnly
* ShortName is assumed to be the same as Provider ID as historicly these have always been the same. In the future, if software needs to specify a different value, then find the first Organization marked with the `PUBLISHER` role and use that ShortName
* There is no official `small` or `cmr-only` field as these are assumed to be false and to be used very rarely in the future. 
* Contact[Persons|Groups]->ContactInformation->HoursOfService is the same as ServiceHours in UMM. These formats are expected to be changed to match Provider.
* ContactPersons has `additionalProperties=false`, UMM is expected to change to match Provider
* ContactPerson contains a `writeOnly` UserName for initializing admins and may not be returned by CMR.
* ContactPerson->Roles accepts `PROVIDER MANAGMENT` for marking Admins to be associated with the provider
* ContactMechanisms accepts `type` set to `Slack` because it's 2023 and not 2003.

## Future

This format is not expected to be promoted as a UMM file as it's usage is to ensure communcitation between the Provider Managment Tool (a command line tool for managing providers) and CMR are correct. These are expected to be the only two software components that need to use the schema. Downstream clients reading providers will need to be aware of MetadataSpecification value changes and be defensive in using CMR provider output. 

New fields are not expected to be added to the SQL table, instead future fields will be added to the schema.

## License

Copyright © 2023 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.

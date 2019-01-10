# Introduction

## CMR

The Common Metadata Repository (CMR) is a high-performance, high-quality,
continuously evolving metadata system that catalogs Earth Science data and
associated service metadata records. These metadata records are registered,
modified, discovered, and accessed through programmatic interfaces leveraging
standard protocols and APIs.

## CMR Service-Bridge

CMR Service-Bridge is an integration service offered by the CMR that allows
client applications to use CMR metadata to integrate features from other
services - as if they were interacting with just the CMR. As new service
integrations are added to CMR Service-Bridge, they will be listed here and
APIs for the new operations will be included in the documentation.

Currently, the following integrations are supported:

* OPeNDAP URL Service - converts CMR or EDSC metadata queries into OPeNDAP
  queries that will actually allow clients to download subsetted data
  directly from OPeNDAP servers.
* Sizing Estimation Service - converts CMR or EDSC queries into size estimates
  for the GIS data that they point to.

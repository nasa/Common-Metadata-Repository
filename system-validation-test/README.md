# CMR::Validation

This is the repository for the Common Metadata Repository Client Validation Gem. This gem is intended to be a suite of tests to provide validation of a CMR instance for common queries and functions of the CMR. The tests within use only the existing outwardly facing CMR APIs

## Prerequisites

* Ruby

## Installation

And then execute:

    $ bundle install

## Usage

To run all tests invoke cucumber with the following

    $ cucumber CMR_ROOT=https://cmr.my-instance.com CMR_TOKEN=$MY_CMR_EDL_TOKEN


To perform a limited test use the `@quick` tag as shown below

    $ cucumber CMR_ROOT=https://cmr.my-instance.com --tags @quick


When running the suite against a newly instantiated instance of CMR, use the `@quick` tag as other tests may make assumptions about data being present.

### Tags

Cucumber supports tagging of tests to allow for targeted runs of certain areas of functionality. Some basic broad categories included are the following

* @quick - No internal data required
* @search - Related to search operations
* @ingest - Related to ingest operations
* @acls - Related to access-control operations

## Test Construction Philosophy

Tests should be constructed such that only externally facing APIs are used. No access to dev-system or side-api is invoked, nor direct access to the database or Elasticsearch cluster.

Tests should follow the Given-When-Then syntax.

Tests that create data should clean up after themselves and not leave any artifacts.

Use tags judiciously. Only tests that can function without data in the CMR instance should use the `@quick` tag. Do not create tags that are overly specific.

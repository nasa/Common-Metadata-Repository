# Common Metadata Repository
Visit the CMR at [https://cmr.earthdata.nasa.gov/search/](https://cmr.earthdata.nasa.gov/search/)


## About
The Common Metadata Repository (CMR) is an earth science metadata repository for [NASA](http://nasa.gov) [EOSDIS](https://earthdata.nasa.gov) data. The CMR Search API provides access to this metadata.

## User-facing Components
- Search
  - Allows the user to search by collections, granules, and concepts with a myriad of different query types
  - API Docs: https://cmr.earthdata.nasa.gov/search/site/search_api_docs.html

- Ingest
  - Insert or modify existing collections in the CMR so they could be revealed by search
  - API Docs: https://cmr.earthdata.nasa.gov/ingest/site/ingest_api_docs.html

- Ingest
  - Control a user or group's privileges to access or modify collections
  - API Docs: https://cmr.earthdata.nasa.gov/access-control/site/access_control_api_docs.html

## Development Environment
- We use several different editors based on preference, but the most popular setup is the following.
  - Mac OSX
  - Atom: https://atom.io/
  - Proto-Repl: https://atom.io/packages/proto-repl
    - Installed and configured according to this guide: https://gist.github.com/jasongilman/d1f70507bed021b48625

## Prerequisites
- Java 1.7.0_25 or higher
- Leiningen (http://leiningen.org) 2.5.1 or above.
  - We've had success with Homebrew and with the install script on the Leiningen website.
- RVM (http://rvm.io/)
- Vagrant (http://www.vagrantup.com/)

## Building and running the CMR from source
The simplest way to do this is to build what is known as the `uberjar`. Navigate to the project root in your terminal and run `lein uberjar`. Leiningen should build the project and show a path to your executable file
  - https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md#uberjar

## Further Reading
- CMR Client Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide
- CMR Data Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide
- CMR Client Developer Forum: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Developer+Forum

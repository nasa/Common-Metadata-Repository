# cmr-dev-system

Dev System combines together the separate microservices of the CMR into a single application to make it simpler to develop.

## Usage

  - Clone this project.
  - Clone all the dependent projects.
    - See the project.clj for the list.
  - Run `lein install` in each of the dependent projects
    - TODO eventually we can script this
  - Run `lein create-checkouts`
  - Make a config directory
    - Copy the elasticsearch_config.json file from the indexer here.
    - TODO we should have a better way to handle this. The path is local so that's why it must be in dev-system
      - I'll give someone a gold star if the fix this
  - Open a repl in sublime from this project.

## Create a Jar for ECHO

  - Increment version in project.clj
  - Update version in build-and-run.sh to match.
  - Update all projects
  - Sign on to the vpn
  - run support/build-cmr-for-echo.sh
    - This will build uberjar and push it to cmr-wl-app1 in your home directory
  - Notify echo dev of new version.


## License

Copyright © 2014 NASA


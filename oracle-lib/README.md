# cmr-oracle-lib

Contains utilities for connecting to and manipulating data in Oracle.

## Instructions for Obtaining Oracle Jars

Oracle Jars are not installed on public maven servers so they must be manually
obtained and installed.

### Configuring a Private Repository

If you have the jars located on a internal maven repository you can configure
the location of that through the environment variable `CMR_ORACLE_JAR_REPO`.
This will be used dynamically as the repository location in the leiningen
project.

### Manually installing the Oracle JDBC Jars

Download oracle Jars from Oracle's website. You can usually google for each of the jar files with a search term such as "ojdbc8.jar 19c download" to find the location of each jar file. In each case, be sure you get the version matching the versions specified in the `project.clj`. You need to download the following:

  * `ojdbc8.jar` - Orcale JDBC Driver
  * `ucp.jar` - Universal Connection Pool (not bundled with the JDBC driver)
  * `ons.jar` - Oracle Notification Services (should be on the same page as
    the JDBC driver)

To install the oracle jars for CMR:
1. `cd oracle-lib`
2. `mkdir support`
3. Put the Jars in `oracle-lib/support`
4. In the project root directory, run `cmr install oracle-libs` which will
   install the oracle jars in your local maven repository.

## License

Copyright © 2014-2021 NASA

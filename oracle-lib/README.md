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

1. Download oracle Jars from Oracle's website. You can usually google for
   each of the jar files with a search term such as "oracle ucp jar" to find
   the location of each jar file. You need to download the following:

  * `ojdbc6.jar` - Orcale JDBC Driver
  * `ucp.jar` - Universal Connection Pool (not bundled with the JDBC driver)
  * `ons.jar` - Oracle Notification Services (should be on the same page as
    the JDBC driver)

  In each case, be sure you get the version matching the versions specified
  in the `project.clj`.
2. Put the Jars in `oracle-lib/support`
3. Finally, perform the actual installation: `cmr install oracle-libs`.

## License

Copyright © 2014-2018 NASA

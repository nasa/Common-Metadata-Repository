# cmr-oracle-lib

Contains utilities for connecting to and manipulating data in Oracle.

## Instructions for Obtaining Oracle Jars

Oracle Jars are not installed on public maven servers so they must be manually obtained and installed.

### Configuring a Private Repository

If you have the jars located on a internal maven repository you can configure the location of that through the environment variable `CMR_ORACLE_JAR_REPO`. This will be used dynamically as the repository location in the leiningen project.

### Manually installing the Oracle JDBC Jars

1. Download oracle Jars from Oracle's website. You can usually google for each for the jar files such as "oracle ucp jar" to find the location of each jar file. You need to download `ojdbc6.jar`, `ucp.jar`, and `ons.jar`. Make sure to get the version matching the versions specified in `project.clj`.
2. Put the Jars in `oracle-lib/support`
3. Run `install_oracle_jars.sh` in `oracle-lib/support` which will install the oracle jars in your local maven repository.



## License

Copyright © 2014 NASA

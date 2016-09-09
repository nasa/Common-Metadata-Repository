# cmr-oracle-lib

Contains utilities for connecting to and manipulating data in Oracle.

## Instructions for Upgrading Oracle Jars

Oracle Jars are not installed on public maven servers so they must be hosted on a different location.

1. Download oracle Jars from Oracle's website. You can usually google for each for the jar files such as "oracle ucp jar" to find the location of each jar file.
2. SCP them to devrepo1.dev.echo.nasa.gov
3. chusr to echo_opr
4. Run the install commands to install them into the maven repo there:

Replace `<version>` with the version from Oracle

```
mvn install:install-file -Dfile=ojdbc6.jar -DartifactId=ojdbc6 -Dversion=<version>  -DgroupId=com.oracle -Dpackaging=jar -DlocalRepositoryPath=/data/dist/projects/echo/mavenrepo -DcreateChecksum=true
mvn install:install-file -Dfile=ons.jar -DartifactId=ons -Dversion=<version>  -DgroupId=com.oracle -Dpackaging=jar -DlocalRepositoryPath=/data/dist/projects/echo/mavenrepo -DcreateChecksum=true
mvn install:install-file -Dfile=ucp.jar -DartifactId=ucp -Dversion=<version>  -DgroupId=com.oracle -Dpackaging=jar -DlocalRepositoryPath=/data/dist/projects/echo/mavenrepo -DcreateChecksum=true
```

5. Update the version in the project.clj and run `lein deps`.

## License

Copyright © 2014 NASA

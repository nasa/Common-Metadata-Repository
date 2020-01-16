# Token Service Sample JAVA Client

This is a sample JAVA client to demonstrate getting token and validating token with Launchpad Token Service. The user should have acquired the Launchpad PKI certificate, set up CMR Ingest workflow and followed the other necessary steps to authenticate with Launchpad Token Service. Please follow [CMR Launchpad Authentication User's Guide](https://wiki.earthdata.nasa.gov/display/CMR/Launchpad+Authentication+User%27s+Guide) to complete the necessary setup.

The following configurations (in source code) need to be updated to the user's own settings to test against Token Service.

LAUNCHPAD_TOKEN_SERVICE_URL_ROOT: Launchpad Token Service root url, default to the Sandbox url
CACERTS_PATH: full path of cacerts which has the PKI certificate for connecting to token service
KEYSTORE_PASSWD: keystore password user used to update the cacerts


## Compile
```
javac -cp ./lib/json-simple-1.1.1.jar cmr/client/SslClient.java
```

## get token
```
java -cp ./lib/*:. cmr.client.SslClient get-token
```

## validate token
```
java -cp ./lib/*:. cmr.client.SslClient validate-token <token>
```

# cmr-transmit-lib

The Transmit Library is responsible for defining the common transmit libraries that invoke services within the CMR projects.

## Usage

Transmit lib is invoked by cmr microservices which will then trigger an API request found and defined in the <service>/routes/api for a given service

conn (config/context->app-connection context <your-context>)
            url (format "%s/%s" (conn/root-url conn) <your-url-extension>)
            params (merge
                    (config/conn-params conn)
                    {:headers {:accept-charset "utf-8"}
                     :throw-exceptions true})
            start (System/currentTimeMillis)
            response (client/<your-request-type> url params)

## License

Copyright © 2023 NASA

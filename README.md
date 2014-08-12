# cmr-mock-echo-app

FIXME

## Running

To start a web server for the application, run:

    lein run

## Examples of using ECHO

### Login

curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json"  https://testbed.echo.nasa.gov/echo-rest/tokens -d '
{"token": {"username":"guest",
  "password":"blah",
  "client_id":"dev test",
  "user_ip_address":"127.0.0.1"}}'

HTTP/1.1 201 Created
Location: https://testbed.echo.nasa.gov/echo-rest/tokens/B3832BEE-5830-1973-1FEB-CBF7F4FCDB1F?clientId=unknown
{"token":{"client_id":"dev test","id":"XXXXX","user_ip_address":"127.0.0.1","username":"guest"}}


## Equivalent examples using mock echo

### Login

curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json"  http://localhost:3000/tokens -d '
{"token": {"username":"guest",
  "password":"blah",
  "client_id":"dev test",
  "user_ip_address":"127.0.0.1"}}'


HTTP/1.1 201 Created
Location: http://localhost:3000/tokens/ABC-1
Content-Type: application/json; charset=utf-8
Content-Length: 114
Server: Jetty(7.6.8.v20121106)

{"token":{"id":"ABC-1","username":"guest","password":"blah","client_id":"dev test","user_ip_address":"127.0.0.1"}}



## License

Copyright © 2014 NASA

# cmr-mock-echo-app

This mocks out the ECHO REST API. It's purpose is to make it easier to integration test the CMR system without having to run a full instance of ECHO. It won't mock it perfectly or completely. It will only implement the minimum necessary to enable integratino testing.

## Running

To start a web server for the application, run:

    lein run

## Examples of using ECHO

### Login

curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json"  https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens -d '
{"token": {"username":"guest",
  "password":"blah",
  "client_id":"dev test",
  "user_ip_address":"127.0.0.1"}}'

HTTP/1.1 201 Created
Location: https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens/XXXXXXXX?clientId=unknown
{"token":{"client_id":"dev test","id":"XXXXX","user_ip_address":"127.0.0.1","username":"guest"}}

### Get token info

#### Guest

curl -i -H "Accept: application/json" https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens/XXXXXXXX/token_info
HTTP/1.1 200 OK
Content-Type: application/json;charset=utf-8

{"token_info":{"client_id":"dev test","created":"2014-08-11T23:44:42Z","expires":"2014-09-10T23:44:42Z","guest":true,"token":"XXXXXXXX","user_guid":"blah","user_name":"guest"}}

#### User
curl -i -H "Accept: application/json" -H "Echo-Token: XXXXXXXX" https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens/XXXXXXXXXX/token_info
HTTP/1.1 200 OK
{"token_info":{"client_id":"dev test","created":"2014-08-12T10:47:26Z","expires":"2014-09-11T10:47:26Z","guest":false,"token":"XXXXXXXXXX","user_guid":"XXXXXXXXX","user_name":"jagilman"}}


### Get Current SIDS

#### Guest

curl -i -H "Accept: application/json" https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens/XXXXXXXX/current_sids
HTTP/1.1 200 OK
[{"sid":{"user_authorization_type_sid":{"user_authorization_type":"GUEST"}}}]

#### User

curl -i -H "Accept: application/json" -H "Echo-Token: XXXXXX" https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/tokens/XXXXXXXXX/current_sids
HTTP/1.1 200 OK
[{"sid":{"user_authorization_type_sid":{"user_authorization_type":"REGISTERED"}}},{"sid":{"group_sid":{"group_guid":"XXXXXX"}}},{"sid":{"group_sid":{"group_guid":"XXXXXXXXX"}}}]


## Equivalent examples using mock echo

### Login

#### Guest

curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json"  http://localhost:3000/tokens -d '
{"token": {"username":"guest",
  "password":"blah",
  "client_id":"dev test",
  "user_ip_address":"127.0.0.1"}}'


HTTP/1.1 201 Created
{"token":{"id":"ABC-1","username":"guest","password":"blah","client_id":"dev test","user_ip_address":"127.0.0.1"}}

#### User

curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json"  http://localhost:3000/tokens -d '
{"token": {"username":"jason",
  "password":"blah",
  "client_id":"dev test",
  "user_ip_address":"127.0.0.1",
  "group_guids":["guid1", "guid2"]}}'

HTTP/1.1 201 Created
{"token":{"id":"ABC-2","username":"jason","password":"blah","client_id":"dev test","user_ip_address":"127.0.0.1","group_guids":["guid1","guid2"]}}

### Get token info

Note: Currently missing user_guid and dates

curl -i -H "Accept: application/json" -H "Echo-Token: foo" http://localhost:3000/tokens/ABC-1/token_info

HTTP/1.1 200 OK
Date: Tue, 12 Aug 2014 00:25:08 GMT
Content-Type: application/json; charset=utf-8
Content-Length: 122
Server: Jetty(7.6.8.v20121106)

{"token_info":{"user_name":"guest","client_id":"dev test","guest":true,"token":"ABC-1","user_guid":"unsupported-in-mock-echo"}}

### Gateway Timeout Response

Using a token with value `gateway-timeout` will cause mock-echo to return a 504 Gateway Timeout response to be returned.

``` shell
curl -i -H"Accept: application/xml" http://localhost:3003/collections?token=gateway-timeout

HTTP/1.1 504 Gateway Timeout
Date: Tue, 16 Aug 2022 14:56:41 GMT
Content-Type: application/xml
Content-Length: 134
Server: Jetty(9.4.39.v20210325)

<?xml version="1.0" encoding="UTF-8"?><errors><error>A gateway timeout occurred, please try your request again later.</error></errors>
```

### Get Current Sids

#### Guest

curl -i -H "Accept: application/json" http://localhost:3000/tokens/ABC-1/current_sids
HTTP/1.1 200 OK
[{"sid":{"user_authorization_type_sid":{"user_authorization_type":"GUEST"}}}]


#### User

curl -i -H "Accept: application/json" http://localhost:3000/tokens/ABC-2/current_sids
HTTP/1.1 200 OK
[{"sid":{"user_authorization_type_sid":{"user_authorization_type":"REGISTERED"}}},{"sid":{"group_sid":{"group_guid":"guid1"}}},{"sid":{"group_sid":{"group_guid":"guid2"}}}]

### Logout

curl -i -XDELETE http://localhost:3000/tokens/ABC-2

### Reset

Clears all data

curl -i -XPOST http://localhost:3000/reset



## License

Copyright © 2022 NASA

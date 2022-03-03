# TEA Configuration Generator API

## General
Calls can be made with curl as such:

    curl -is -H 'Authorization: Bearer abcd1234' http://localhost:3000/dev/configuration/tea/provider/POCLOUD

## Capabilities
To get a programatic output of the supported urls with descriptions, call the capabilities end point which is availible as either the root of the service or named:

* Request
	* GET /configuration/tea/
	* GET /configuration/tea/capabilities
	* Headers: none
* Response
	* Content Returns a JSON dictionary containing a list of urls
	* Headers: `cmr-took` - number of seconds of execution
	* Status Codes: 200 - success

Example response:

	{
	  "urls": [
	  {
	   "name": "Root",
	   "path": "/configuration/tea",
	   "description": "Alias for capabilities"
	  },
	  {
	   "name": "Capabilities",
	   "path": "/configuration/tea/capabilities",
	   "description": "Show which endpoints exist on this service"
	  },
	  {
	   "name": "Status",
	   "path": "/configuration/tea/status",
	   "description": "Returns service status"
	  },
	  {
	   "name": "Generate",
	   "path": "/configuration/tea/provider/<provider>",
	   "description": "Generate a TEA config for provider"
	  }
	 ]
	}

URL dictionary contains:

* name : human readable name
* description : human readable description of the parameter
* path : URL fragment to be appended to the host name and end point

## Generate Configuration
Generate a TEA configuration file based on the supplied CMR provider id and [Lanchpad][lpad]/[EDL][edl] user token.

* Request
	* GET /configuration/tea/provider<provider-id>
	* Headers: `Authorization`: [CMR token]
* Response
	* Content: Tea configuration file on success, 400 on error
	* Headers: `cmr-took` - number of seconds of execution
	* status codes:
		* 200 - success
		* 400 - Token is required
		* 404 - No S3 prefixes returned

## Status
Return a simple response indicating that the service is running

* Request
	* GET /configuration/tea/status
	* Headers: none
* Response 
	* Content: `I'm a teapot` 
	* Headers: `cmr-took` - number of seconds of execution
	* Status Codes: 418 - active

## License
Copyright © 2022-2022 United States Government as represented by the Administrator
of the National Aeronautics and Space Administration. All Rights Reserved.

[lpad]: https://launchpad.nasa.gov/ "NASA LaunchPad"
[edl]: https://urs.earthdata.nasa.gov/ "EarthData Login"
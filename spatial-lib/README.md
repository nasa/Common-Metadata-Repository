# cmr-spatial-lib

A spatial library for the CMR. A collection of functions for managing and calculating geometrys.

## Library

(more should be said here)

## Command line Interface

A command line interface is available for getting direct access to some of the functions inside the library. This is used to ensure that external processing tools can use the exact same algorithm as is being used in CMR. Currently only one function is exposed:

**find-lr**: "Finds the 'largest' interior rectangle (LR) of the polygon. This is not the provably largest interior rectangle for a polygon. It uses a simpler algorithm that works well for simple 4 point rings and less well for more points. It should always find a LR for any ring of arbitrary polygon."

Usage example:

	cd spatial-lib
	lein compile uberjar
	java -Dclojure.warn.reflection=false
		-jar target/cmr-spatial-lib-0.1.0-SNAPSHOT-standalone.jar
		lr-json "POLYGON ((-124.409202 32.531669, -114.119061 32.531669, -114.119061 41.99954, -124.409202 41.99954, -124.409202 32.531669))"
	java -Dclojure.warn.reflection=false
		-jar cmr-spatial-lib-0.1.0-SNAPSHOT-standalone.jar cmr.spatial.runner
		lr-json "POLYGON((-124.409202 32.531669, -114.119061 32.531669, -114.119061 41.99954, -124.409202 41.99954, -124.409202 32.531669))"

| Command | Input           | Output                                         | Description
| ------- | --------------- | ---------------------------------------------- | -----------
| lr-json | POLYGON((...))) | {"west":x1 ,"east":x2 ,"south":y1 ,"north":y1} | JSON bounding box of the Largest *internal* Rectangle
| lr-wkt  | POLYGON((...))) | POLYGON((x1 y1, x2 y2, x3 y3, x4 y4, x1 y1))   | Well Known Text of the Largest *internal* Rectangle

## License

Copyright © 2021-2024 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

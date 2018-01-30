# orbits-lib

This is a Clojure wrapper of a Ruby implementation of the Backtrack Orbit Search Algorithm (BOSA) originally written in Java and maintained by [NSIDC](http://nsidc.org/). References to "the Java code" or "the NSIDC spheres code" in the refer to the original library which is available [here](http://geospatialmethods.org/spheres/).  Along with the original code, geospatialmethods.org also has a [description of the algorithm] (http://geospatialmethods.org/bosa/).

## Overview

Orbiting satellites have sensors that trace a circular path around the Earth.  It is difficult to express this ground track as a 2D polygon with lat/lon coordinates.  Most of the orbits we deal with have a long, narrow, sinusoidal shape that would require hundreds of points to describe.  Further, their minimum bounding rectangle covers nearly the whole Earth and some orbits have ground tracks that cross themselves within a single granule of data.

Instead of describing the orbit as a complex 2D polygon, we want to answer a different question: *Where would the orbit need to cross the equator in order to see a given point on the Earth?*

Instead of using a complicated 2D polygon query, we could instead perform a simple range query: "Find all data granules whose orbit crosses the equator between longitudes `a` and `b`."

See the `orbit.rb` Ruby code for more information.
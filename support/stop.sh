#!/bin/sh

#Shuts down the running dev system
# True is appended to ignore an error response from curl. Calling stop synchronously shutsdown the jvm so no response could be sent.

curl -i -XPOST http://localhost:2999/stop; true
# redis-utils-lib

A Clojure library containing utilities for dealing with Redis.

## Contents

This library contains functions to enable an embedded redis server for dev use
as well as provide an implementation of the CmrCache protocol for use with Redis.
The implementation will not evict keys unless ttl is set.

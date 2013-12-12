#!/bin/sh

# The echo commands add newlines

curl -XPUT localhost:9210/string_match_test/fooers/1 -d '{"value" : "foo"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/2 -d '{"value" : "bar"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/3 -d '{"value" : "foo"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/4 -d '{"value" : "bar"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/5 -d '{"value" : "umm"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/6 -d '{"value" : "ohno"}'
echo
curl -XPUT localhost:9210/string_match_test/fooers/7 -d '{"value" : ["never", "a", "dull", "foo"]}'
echo

# Make all indexed items available for searching
curl -XPOST 'http://localhost:9210/_refresh'
echo


curl -XGET 'http://localhost:9210/string_match_test/fooers/_search?pretty=true' -d '{
    "query": {
        "filtered" : {
            "query" : {
                "match_all" : { }
            },
            "filter" : {
                "script" : {
                    "script" : "string_match",
                    "params" : {
                        "field" : "value",
                        "search-string" : "foo"
                    },
                    "lang": "native"
                }
            }
        }
    }
}' | sed 's/\\n/\
/g' | sed 's/\\"/"/g'
echo
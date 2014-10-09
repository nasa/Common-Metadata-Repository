#!/bin/sh

if [ -z "$1" ]
  then
    echo "Must supply the tag name to use. ie. sprint10"
    exit 1
fi

git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags


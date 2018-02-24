#!/bin/bash

git clone git@github.com:nasa-cmr/cmr.git cmr-nasa
git clone cmr-nasa cmr-preped
cd cmr-preped
git rm -r .gitig* CH* *.md LIC* project.clj resources .mailmap
git commit -a -m "Removed files in preparation for repo split tasks."

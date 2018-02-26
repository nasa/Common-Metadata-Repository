# Splitting the CMR Repo into Multiple Repos

Steps:

1.  Get the scripts:
	```
	$ git clone git@github.com:cmr-exchange/repo-splitter.git
	```
1. Create a directory to work in and set up the base repos:
	```
	$ mkdir cmr-experimental
	$ cd cmr-experimental
	```
1. Split the repos:
	```
	$ ../repo-splitter/split-repos.sh
	```

At this point, the scripts will begin to populate `cmr-experimental` with repos
split out from the CMR monorepo. Note that due to careful history preservation,
this process will take several hours on a fast machine. Once done, there will
be a `cmr` directory in `cmr-experimental`: this is the new umrealla project
containing all the split out repos -- the CMR, the REPL, and the tests all work
exactly as the did before the split.

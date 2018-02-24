# Splitting the CMR Repo into Multiple Repos

Steps:

```
$ git clone git@github.com:cmr-exchange/repo-splitter.git
$ mkdir cmr-experimental
$ cd cmr-experimental
$ ../repo-splitter/split-repos.sh
```

At this point, the scripts will begin to populate `cmr-experimental` with repos
split out from the CMR monorepo. Note that due to careful history preservation,
this process will take several hours on a fast machine.

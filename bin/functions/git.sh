#!/bin/sh
IFS='' read -r -d '' GIT_HELP <<'EOH'

Usage: cmr git [SUBCOMMANDS]

Defined subcommands:

    branches [ARGS]   - Show the local branches in order of recently updated.
    help              - Show this message.
    log-files [ARGS]  - Show the files that were changed in previous commits.
    log-graph [ARGS]  - Show branch graphs with log output; uses short output
                        format.
    log-latest [ARGS] - Show the most recent 30 commits; uses short output
                        format.
    log-short [ARGS]  - Show commit log in short output format.
    tag TAGNAME       - Create and push a new tag to the CMR repo.

EOH

function git_log_short {
    git log \
      --pretty=format:"%C(yellow)%h %Creset%ad%Cred%d %Cgreen%s%Creset %C(bold blue)<%an>%Creset" \
      --decorate \
      --date=short \
      $@
}

function git_log_graph {
    git log \
        --pretty=format:"%C(yellow)%h %Creset%ad%Cred%d %Cgreen%s%Creset %C(bold blue)<%an>%Creset" \
        --decorate \
        --date=relative \
        --branches \
        --graph \
        $@
}

function git_log_latest {
    git log \
        --pretty=format:"%C(yellow)%h %Creset%ad%Cred%d %Cgreen%s%Creset %C(bold blue)<%an>%Creset" \
        --decorate \
        --date=short \
        -30 \
        $@
}

function git_log_files {
    git log \
    --pretty=format:"%C(yellow)%h%Cred%d %Cgreen%s%Creset %C(bold blue)<%an>%Creset" \
    --decorate \
    --numstat \
    $@
}

function git_branches {
    git branch --sort=committerdate
}

function git_tag {
    if [ -z "$1" ]
      then
        echo "Must supply the tag name to use. Example: 'cmr git tag sprint10'"
        exit 1
    fi

    git tag -a "$1" -m "Tagging at the end of the sprint"
    git push --tags
}

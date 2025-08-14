# file: cmr.fish
# cmr (sub)command-completion for fish shell
# copy this file to ~/.config/fish/completions

set -l apps access-control bootstrap dev-system indexer ingest metadata-db search virtual-product
set -l libs acl-lib common-app-lib common-lib elastic-utils-lib es-spatial-plugin message-queue-lib oracle-lib orbits-lib redis-lib spatial-lib transmit-lib umm-lib umm-spec-lib vdd-spatial-viz
set -l apps_libs $apps $libs

# Level 1
set -l top_commands build clean git install setup show start status stop test
set -l top_dashed -h -v --help --version

# Level 2
set -l setup_level local db dev help profile
set -l build_level docker help uberdocker uberjar uberjars
set -l clean_level all es-data help $apps_libs
set -l install_level all docs help jars 'jars,docs' local orbits-gems oracle-libs
set -l show_level help log log-tail log-test log-tests port-process sqs-queues
set -l start_level all docker help local repl uberdocker uberjar $apps
set -l status_level docker help sqs-sns uberdocker uberjar
set -l stop_level all docker help local uberdocker uberjar $apps
set -l test_level all cicd dep-tree dep-trees help lint versions
set -l git_level branches help log-latest log-short log-graph tag

# Level 3
set -l setup_db_level create-users do-migrations help
set -l star_docker_level all help $apps
set -l star_local_level spatial_plugin sqs-sns help
set -l uberdocker_level separate together

# Main completion function
complete -c cmr -f

# Level 1 completions
complete -c cmr -n __fish_use_subcommand -a "$top_commands"
complete -c cmr -n __fish_use_subcommand -a "$top_dashed"

# Level 2 completions
complete -c cmr -n '__fish_seen_subcommand_from build' -a "$build_level"
complete -c cmr -n '__fish_seen_subcommand_from clean' -a "$clean_level"
complete -c cmr -n '__fish_seen_subcommand_from git' -a "$git_level"
complete -c cmr -n '__fish_seen_subcommand_from help' -a "$top_commands"
complete -c cmr -n '__fish_seen_subcommand_from install' -a "$install_level"
complete -c cmr -n '__fish_seen_subcommand_from setup' -a "$setup_level"
complete -c cmr -n '__fish_seen_subcommand_from show' -a "$show_level"
complete -c cmr -n '__fish_seen_subcommand_from start' -a "$start_level"
complete -c cmr -n '__fish_seen_subcommand_from status' -a "$status_level"
complete -c cmr -n '__fish_seen_subcommand_from stop' -a "$stop_level"
complete -c cmr -n '__fish_seen_subcommand_from test' -a "$test_level"

# Level 3 completions
complete -c cmr -n '__fish_seen_subcommand_from setup; and __fish_seen_subcommand_from db' -a "$setup_db_level"
complete -c cmr -n '__fish_seen_subcommand_from test; and __fish_seen_subcommand_from dep-tree' -a "$apps_libs"
complete -c cmr -n '__fish_seen_subcommand_from start; and __fish_seen_subcommand_from docker' -a "$star_docker_level"
complete -c cmr -n '__fish_seen_subcommand_from stop; and __fish_seen_subcommand_from docker' -a "$star_docker_level"
complete -c cmr -n '__fish_seen_subcommand_from status; and __fish_seen_subcommand_from docker' -a "$star_docker_level"
complete -c cmr -n '__fish_seen_subcommand_from test; and __fish_seen_subcommand_from lint' -a "$apps_libs"
complete -c cmr -n '__fish_seen_subcommand_from start; and __fish_seen_subcommand_from local' -a "$star_local_level"
complete -c cmr -n '__fish_seen_subcommand_from stop; and __fish_seen_subcommand_from local' -a "$star_local_level"
complete -c cmr -n '__fish_seen_subcommand_from show; and __fish_seen_subcommand_from log' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from show; and __fish_seen_subcommand_from log-tail' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from show; and __fish_seen_subcommand_from log-test' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from start; and __fish_seen_subcommand_from uberdocker' -a "$uberdocker_level"
complete -c cmr -n '__fish_seen_subcommand_from stop; and __fish_seen_subcommand_from uberdocker' -a "$uberdocker_level"
complete -c cmr -n '__fish_seen_subcommand_from status; and __fish_seen_subcommand_from uberdocker' -a "$uberdocker_level"
complete -c cmr -n '__fish_seen_subcommand_from start; and __fish_seen_subcommand_from uberjar' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from stop; and __fish_seen_subcommand_from uberjar' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from build; and __fish_seen_subcommand_from uberjar' -a "$apps"
complete -c cmr -n '__fish_seen_subcommand_from test; and __fish_seen_subcommand_from versions' -a "$apps_libs"

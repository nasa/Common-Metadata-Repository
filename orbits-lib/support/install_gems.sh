#!/bin/bash

# This script installs the ruby gems needed by orbits-lib.

jruby_version=$1
gem_install_path=$2

# unset env vars that might cause warning messages
unset GEM_HOME
unset GEM_PATH

# currently we only have one gem to install
rspec_version=2.12.0
echo "Installing rspec $rspec_version"
java -cp $HOME/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem install -i $gem_install_path rspec -v $rspec_version

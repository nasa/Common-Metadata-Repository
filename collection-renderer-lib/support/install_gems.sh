#!/bin/bash

# This script clones cmr_metadata_preview project and install the cmr_metadata_preview gem and
# all its dependencies in gems directory under collection-renderer-lib.

jruby_version=$1
cmr_metadata_preview_repo=$2
commit=$3
gemDir=cmr_metadata_preview-0.0.1
gemName="${gemDir}.gem"
tmpGemDir=tmp_gem_dir

remove_dir ()
{
  if [ ! -z "$1" ]
    then
      rm -rf $1
  fi
}

# clean up temp gem directory first
remove_dir $tmpGemDir
# unset env vars that might cause warning messages
unset GEM_HOME
unset GEM_PATH

# clone cmr_metadata_preview from repo and install the gems
git clone $cmr_metadata_preview_repo $tmpGemDir
currDir=$PWD
cd $tmpGemDir
if [ ! -z "$commit" ]
  then
    git checkout -b tmp $commit
    echo "Building metadata preview gem off commit id: $commit"
  else
    echo "Building metadata preview gem off HEAD"
fi

# build cmr_metadata_preview gem
java -cp $HOME/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem build *.gemspec
echo "Installing gems..."
java -cp $HOME/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem install $gemName -i ../gems

# cleanup temp gem directory
cd $currDir
remove_dir $tmpGemDir

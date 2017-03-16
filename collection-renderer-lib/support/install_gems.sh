#!/bin/bash

jruby_version=9.1.8.0
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
git clone $1 $tmpGemDir
currDir=$PWD
cd $tmpGemDir
java -cp $HOME/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem build *.gemspec
echo "Installing gems..."
java -cp $HOME/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem install $gemName -i ../gems

# cleanup temp gem directory
cd $currDir
remove_dir $tmpGemDir

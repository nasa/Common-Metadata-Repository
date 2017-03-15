#!/bin/bash

jruby_version=9.1.8.0
gemDir=cmr_metadata_preview-0.0.1
gemName="${gemDir}.gem"
tmpGemDir=tmp_gem_dir
resourceDir=resources/cmr_metadata_preview

remove_dir ()
{
  if [ ! -z "$1" ]
    then
      rm -rf $1
  fi
}

# clean up cmr_metadata_preview resource and temp gem directories first
remove_dir $resourceDir
remove_dir $tmpGemDir

# clone cmr_metadata_preview from repo and install the gems
git clone $1 $tmpGemDir
currDir=$PWD
cd $tmpGemDir
java -cp /Users/yliu10/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem build *.gemspec
echo "Installing gems..."
java -cp /Users/yliu10/.m2/repository/org/jruby/jruby-complete/$jruby_version/jruby-complete-$jruby_version.jar org.jruby.Main -S gem install $gemName -i ../gems

# cleanup temp gem directory
cd $currDir
remove_dir $tmpGemDir

# copy the code needed into cmr_metadata_preview resource directory
cp -r gems/gems/$gemDir/app $resourceDir

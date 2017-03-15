#!/bin/bash

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
currentDir=$PWD
cd $tmpGemDir
java -cp /Users/yliu10/.m2/repository/org/jruby/jruby-complete/1.7.4/jruby-complete-1.7.4.jar org.jruby.Main -S gem build *.gemspec
echo "Installing gems..."
java -cp /Users/yliu10/.m2/repository/org/jruby/jruby-complete/1.7.4/jruby-complete-1.7.4.jar org.jruby.Main -S gem install -f $gemName -i ../gems

# cleanup temp gem directory
cd $currentDir
remove_dir $tmpGemDir

# copy the code needed into cmr_metadata_preview resource directory
cp -r gems/gems/$gemDir/app $resourceDir

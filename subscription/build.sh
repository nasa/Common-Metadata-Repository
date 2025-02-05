# This script is used by the bamboo build project.

zip -r deployment_package.zip src Dockerfile -x "src/__pycache__/*"

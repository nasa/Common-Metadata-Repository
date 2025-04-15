cd ..
docker run -v ~/.m2:/root/.m2 -v .:/cmr -w /cmr cmr-local lein install-no-clean!
cd ..

docker build -t cmr-builder -f dev-system/Dockerfile.dev .

docker run --mount type=bind,source=${pwd}/,destination=/cmr cmr-builder ./bin cmr build all
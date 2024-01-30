FROM clojure:temurin-17-lein-bullseye@sha256:b6714f29ab9697cf29f2a5049a04b73085ec70ec0a3c6a2b5d7f8cd61270b794

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

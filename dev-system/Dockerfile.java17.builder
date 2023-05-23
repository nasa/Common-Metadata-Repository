FROM clojure:temurin-17-lein

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:17

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      clojure \
      curl \
      leiningen \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

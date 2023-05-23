FROM eclipse-temurin:17

RUN apt update \
 && DEBIAN_FRONTEND=noninteractive \
    apt install --assume-yes \
      clojure \
      curl \
      leiningen \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

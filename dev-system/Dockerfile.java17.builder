FROM clojure:temurin-17-lein-trixie

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3 \
      curl \
      netbase \
      unzip \
      zip \
      ca-certificates \
 && update-ca-certificates -f \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /root/.lein
COPY profiles.bamboo.clj /root/.lein/profiles.clj

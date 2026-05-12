FROM clojure:temurin-17-lein-bullseye

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

RUN mkdir -p \$HOME/.lein \
  && cd \$HOME/.lein \
  && curl --silent --location --remote-name \
  https://maven.earthdata.nasa.gov/repository/cmr/profiles.clj


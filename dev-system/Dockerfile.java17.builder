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

# Downgrade Leiningen to 2.10.0 to fix compatibility issues with lein-modules 0.3.11
RUN curl -L https://raw.githubusercontent.com/technomancy/leiningen/2.10.0/bin/lein -o /usr/local/bin/lein \
 && chmod a+x /usr/local/bin/lein \
 && lein version

FROM clojure:temurin-17-lein-trixie

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3 \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /root/.lein
COPY profiles.bamboo.clj /root/.lein/profiles.clj

# Pin Leiningen; versions before 2.11.0 spend minutes in the pedantic dependency check
RUN curl -L https://raw.githubusercontent.com/technomancy/leiningen/2.12.0/bin/lein -o /usr/local/bin/lein \
 && chmod a+x /usr/local/bin/lein \
 && lein version

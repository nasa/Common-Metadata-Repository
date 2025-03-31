FROM clojure:temurin-17-lein-bullseye

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3 \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

 COPY profiles.clj /cmr/profiles.clj

 RUN ./bin/cmr build all

 COPY ./target/cmr-0.1.0-SNAPSHOT.jar /cmr/cmr-0.1.0-SNAPSHOT.jar

 CMD ["java", "-jar", "-Xmx1g", "cmr-"]
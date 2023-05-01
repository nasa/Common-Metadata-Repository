FROM clojure:openjdk-17-lein

RUN apt update && apt install -y \
curl \
netbase \
unzip \
zip \
&& rm -rf /var/lib/apt/lists/*

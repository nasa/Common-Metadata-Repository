FROM node:14
# Run together to help with Docker caching
## keep package list alphabetized for easy curation
RUN apt-get update && \
    apt install -y \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /build
# Check .dockerignore for list of ignored files

COPY package*.json /build/
RUN npm install -g serverless --package-lock false
RUN npm install
COPY . /build

ADD "https://www.random.org/cgi-bin/randbyte?nbytes=10&format=h" skipcache
RUN node -v

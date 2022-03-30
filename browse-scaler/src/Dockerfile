FROM node:14

# Create and define the node_modules's cache directory.
RUN mkdir /usr/src/cache
WORKDIR /usr/src/cache

# Install the application's dependencies into the node_modules's cache directory.
COPY ./package.json ./
COPY ./package-lock.json ./
RUN npm install --production

RUN mkdir /usr/src/app
WORKDIR /usr/src/app

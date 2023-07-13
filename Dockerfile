FROM clojure:temurin-17-lein-bullseye

RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y \
  curl \
  git \
  netbase \
  unzip \
  zip \
  && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/nasa/Common-Metadata-Repository.git /cmr --depth=1

WORKDIR /cmr

RUN echo "export PATH=\$PATH:`pwd`/bin" >> ~/.profile
RUN echo "source `pwd`/resources/shell/cmr-bash-autocomplete" >> ~/.profile

RUN bash -c "source ~/.profile && cmr setup profile"

RUN bash -c "source ~/.profile && cmr setup dev"

RUN echo "CMR_DIR=/cmr" >> ~/.profile

RUN bash -c "source ~/.profile && cmr build uberjar common-app-lib"

RUN bash -c "source ~/.profile && cmr build uberjar common-lib"

RUN bash -c "source ~/.profile && cmr build uberjar metadata-db-app"

RUN bash -c "source ~/.profile && cmr build uberjar search-app"

RUN bash -c "source ~/.profile && cmr build uberjar spatial-lib"

RUN bash -c "source ~/.profile && cmr build uberjar umm-lib"

RUN bash -c "source ~/.profile && lein modules :dirs metadata-db-app utest"

RUN bash -c "source ~/.profile && lein modules :dirs search-app utest"

RUN bash -c "source ~/.profile && lein modules :dirs umm-lib utest"

# RUN bash -c "source ~/.profile && lein modules :dirs search ptest"
# RUN bash -c "source ~/.profile && lein modules :dirs umm-lib ptest"



# RUN bash -c "source ~/.profile && cmr build uberjars"

# RUN bash -c "source ~/.profile && cmr build uberjar access-control-app"


# COPY ./es-spatial-plugin /cmr/es-spatial-plugin

# CMD cmr start repl


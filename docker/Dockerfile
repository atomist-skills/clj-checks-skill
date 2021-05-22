FROM clojure:openjdk-11-lein-2.9.3@sha256:9edfff75280da4e5875381a502ffe2aec0be87b54d3ad0da0fe16a2d0512b9c3

LABEL maintainer="Jim Clark <jim@atomist.com>"

RUN apt-get update && apt-get install -y \
    build-essential=12.6 \
    curl=7.64.0-4+deb10u2 \
    dumb-init=1.2.2-1.1 \
    gnupg=2.2.12-1+deb10u1

RUN curl -sL https://deb.nodesource.com/setup_12.x  | bash - \
    && apt-get -y install nodejs

RUN curl -O https://download.clojure.org/install/linux-install-1.10.1.462.sh \
    && chmod +x linux-install-1.10.1.462.sh \
    && ./linux-install-1.10.1.462.sh

WORKDIR /usr/src/app  

COPY package*.json /usr/src/app/
RUN npm ci \
    && npm cache clean --force \
    && rm -rf ~/.cache

COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN "`npm bin`/shadow-cljs" release release --verbose \
    && if ! [ -f index.js ]; then exit 101; fi; \
    rm -rf .cpcache .shadow-cljs ~/.m2

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]

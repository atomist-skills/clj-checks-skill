FROM clojure:openjdk-11-lein-2.9.3@sha256:416e12c0e28470b9a406ebe8ab21347d142b4f3b53539ae90c559cc392184aed

LABEL maintainer="Jim Clark <jim@atomist.com>"

RUN apt-get update && apt-get install -y \
        curl \
        gnupg \
        build-essential \
        dumb-init

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

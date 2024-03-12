#!/usr/bin/env bash

# sdk install java 21.0.2-graalce

export GRAALVM_HOME=$HOME/.sdkman/candidates/java/21.0.2-graalce
export PATH=$GRAALVM_HOME/bin:$PATH

rm -v ./finddep || true
clojure -X:depstar && \
native-image com.github.ivarref.finddep -cp target/uber.jar \
--enable-url-protocols=http,https \
--initialize-at-build-time \
-H:IncludeResources=clojure/tools/deps/deps.edn \
-H:IncludeResources=clojure/tools/deps/license-abbrev.edn \
-H:ReflectionConfigurationFiles=reflect-config.json \
-H:Name=finddep \
--no-fallback && \
./finddep
./finddep > out.txt 2>&1

head -n10 out.txt

#clojure -M:build

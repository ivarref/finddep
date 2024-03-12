#!/usr/bin/env bash

# sdk install java 21.0.2-graalce

export GRAALVM_HOME=$HOME/.sdkman/candidates/java/21.0.2-graalce
export PATH=$GRAALVM_HOME/bin:$PATH

rm -v ./finddep || true
clojure -X:depstar && \
native-image com.github.ivarref.finddep -cp target/uber.jar \
--enable-url-protocols=http,https \
--initialize-at-build-time \
--initialize-at-run-time=org.apache.http.impl.auth.NTLMEngineImpl \
-H:IncludeResources=clojure/tools/deps/deps.edn \
-H:IncludeResources=clojure/tools/deps/license-abbrev.edn \
-H:IncludeResources=org/apache/maven/model/pom-4.0.0.xml \
-H:IncludeResources=deps/pom.properties \
-H:IncludeResources=deps/pom.xml \
-H:IncludeResources=jetty/jetty-util/pom.properties \
-H:IncludeResources=jetty/jetty-util/pom.xml \
-H:IncludeResources=org/eclipse/jetty/version/build.properties \
-H:ReflectionConfigurationFiles=reflect-config.json \
-H:Name=finddep \
--no-fallback && \
./finddep
rm -v ./out.txt || true
./finddep > out.txt 2>&1

head -n10 out.txt

rm -v ./fixed.txt || true

bb ./fix.clj

if [[ -f "./fixed.txt" ]]; then
    echo "re-launching ... :-)";
    ./build.sh
fi

#clojure -M:build

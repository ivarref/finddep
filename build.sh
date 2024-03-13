#!/usr/bin/env bash

# sdk install java 21.0.2-graalce

if [ -z ${GRAALVM_HOME+x} ]; then
  export GRAALVM_HOME=$HOME/.sdkman/candidates/java/21.0.2-graalce
  export PATH=$GRAALVM_HOME/bin:$PATH
fi

rm -v ./finddep || true
clojure -X:depstar && \
native-image com.github.ivarref.finddep -cp target/uber.jar \
--enable-url-protocols=http,https \
--initialize-at-build-time \
--initialize-at-run-time=org.apache.http.impl.auth.NTLMEngineImpl \
-H:IncludeResources=clojure/tools/deps/deps.edn \
-H:IncludeResources=clojure/tools/deps/license-abbrev.edn \
-H:IncludeResources=org/apache/maven/model/pom-4.0.0.xml \
-H:ReflectionConfigurationFiles=reflect-config.json \
-H:Name=finddep \
--no-fallback && \
./finddep
#rm -v ./out.txt || true
#./finddep > out.txt 2>&1
#
#head -n10 out.txt
#
#rm -v ./fixed.txt || true
#
#bb ./fix.clj
#
#if [[ -f "./fixed.txt" ]]; then
#    echo "re-launching ... :-)";
#    ./build.sh
#fi

#clojure -M:build

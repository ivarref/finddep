#!/usr/bin/env bash

DIR=$(pwd)
clojure -Ttools install com.github.ivarref/finddep '{:local/root "'$DIR'"}' :as finddep2
set -x
clojure -Tfinddep2 $@

#!/usr/bin/env bash

python3 -c '
import sys
for fil in sys.argv[1:]:
  with open(fil) as fd:
    for lin in fd.readlines():
      lin = lin.strip()
      if "^:focus" in lin:
        sys.exit(0)
sys.exit(1)
' $(find test -name '*.clj')
EXIT_CODE="$?"

if [[ "$EXIT_CODE" == "0" ]]; then
  echo 'Running focused tests ...'
#  docker ps
#  rm -rf /tmp/lima
#  find /tmp/lima/
  clojure -X:test :includes '[:focus]'
else
  echo 'Running all tests ...'
  clojure -X:test
fi

# finddep

Ever wondered where some dependency comes from?
Tired of manually "parsing" the output of `clojure -Stree`?
If so then `finddep` is for you.

## Installation

Paste this into your shell:

```bash
bash -c '
set -euo pipefail

clojure -Ttools install com.github.ivarref/finddep \
"{:git/tag \"0.1.116\" :git/sha \"63b35219bc91226abfa1fe6df4ea88f97cfdcbe3\"}" \
:as finddep

cat > "$HOME/.local/bin/finddep" <<EOF
#!/usr/bin/env bash

if [[ "\$1" == "--help" ]] || [[ "\$1" == "-h" ]] || [[ "\$#" -eq 0 ]]; then
  printf "\e[0;1m\e[0;4m%s\e[0m" "Usage:"
  printf "\e[0;1m%s\e[0m" " finddep "
  printf "%s" "NEEDLE OPTIONS"
  printf "\n\n"
  printf "Search default alias\n"
  printf "\$ finddep asm\n\n"
  printf "Search a specific alias\n"
  printf "\$ finddep asm :aliases [:build]\n\n"
  printf "Search all aliases\n"
  printf "\$ finddep asm :aliases all\n\n"
  printf "Example output\n\n"
  printf "org.clojure/tools.deps {:mvn/version "0.26.1553"}\n"
  printf "  com.cognitect.aws/api {:mvn/version "0.8.762"}\n"
  printf "    org.clojure/core.async {:mvn/version "1.8.741"}\n"
  printf "      org.clojure/tools.analyzer.jvm {:mvn/version "1.3.2"}\n"
  printf "        org.ow2.asm/asm {:mvn/version "9.2"}\n"
  if [[ "\$#" -eq 0 ]]; then
    exit 1
  fi
else
  clojure -Tfinddep find :name "\$@"
fi
EOF
chmod +x "$HOME/.local/bin/finddep"'
```

## Basic usage

```
$ finddep --help
Usage: finddep NEEDLE OPTIONS

Search default alias:
finddep asm

Search a specific alias:
finddep asm :aliases [:build]

Search all aliases:
finddep asm :aliases all

Example output

org.clojure/tools.deps {:mvn/version 0.26.1553}
  com.cognitect.aws/api {:mvn/version 0.8.762}
    org.clojure/core.async {:mvn/version 1.8.741}
      org.clojure/tools.analyzer.jvm {:mvn/version 1.3.2}
        org.ow2.asm/asm {:mvn/version 9.2}
```

## Including children in the results

```
$ finddep tools.analyzer.jvm :include-children true

org.clojure/tools.deps {:mvn/version "0.19.1417"}
  com.cognitect.aws/api {:mvn/version "0.8.686"}
    org.clojure/core.async {:mvn/version "1.6.673"}
      org.clojure/tools.analyzer.jvm {:mvn/version "1.2.2"}
      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        org.clojure/core.memoize {:mvn/version "1.0.253"}
          org.clojure/core.cache {:mvn/version "1.0.225"}
            org.clojure/data.priority-map {:mvn/version "1.1.0"}
        org.clojure/tools.analyzer {:mvn/version "1.1.0"}
        org.clojure/tools.reader {:mvn/version "1.3.6"}
        org.ow2.asm/asm {:mvn/version "9.2"}
```

## Usage with fzf

Go to your deps-based project and invoke the tool:

```bash
clojure -Tfinddep fzf
```

Start typing to see the dependency tree for a given dependency.

Output:

```
org.clojure/tools.deps {:mvn/version "0.19.1417"}
  com.cognitect.aws/api {:mvn/version "0.8.686"}
    org.clojure/core.async {:mvn/version "1.6.673"}
      org.clojure/tools.analyzer.jvm {:mvn/version "1.2.2"}
        org.ow2.asm/asm {:mvn/version "9.2"}
```

Right, it so that's why it was included...

### TODOs

Fix tool alias expansion for fzf mode.

### Making a new release

```bash
./release.py
# or "./release.py --dry" if you want to see the changes about to be made
```


## License

Copyright © 2023 — 2026 Ivar Refsdal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

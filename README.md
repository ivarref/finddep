# finddep

Ever wondered where some dependency comes from?
Tired of manually "parsing" the output of `clojure -Stree`?
If so then `finddep` is for you.

## Installation

```
clojure -Ttools install com.github.ivarref/finddep \
'{:git/tag "0.1.81" :git/sha "7abb7017298058021b901c2e55a358998a8944a0"}' \
:as finddep
```

### Optional installation

If you are only going to use the `:name` parameter search (see next section), you can
also do the following:

```bash
bash -c 'cat > "$HOME/.local/bin/finddep" <<EOF
#!/usr/bin/env bash

if [[ "\$1" == "--help" ]] || [[ "\$1" == "-h" ]] || [[ "\$#" -eq 0 ]]; then
  printf "\e[0;1m\e[0;4m%s\e[0m" "Usage:"
  printf "\e[0;1m%s\e[0m" " finddep "
  printf "%s" "NEEDLE"
  printf "\n\n"
  printf "Search in the default alias\n"
  printf "\$ finddep asm\n\n"
  printf "org.clojure/tools.deps {:mvn/version "0.26.1553"}\n"
  printf "  com.cognitect.aws/api {:mvn/version "0.8.762"}\n"
  printf "    org.clojure/core.async {:mvn/version "1.8.741"}\n"
  printf "      org.clojure/tools.analyzer.jvm {:mvn/version "1.3.2"}\n"
  printf "        org.ow2.asm/asm {:mvn/version "9.2"}\n\n"
  printf "Search in a specific alias\n"
  printf "\$ finddep plexus-utils :aliases [:build]\n(output elided)\n\n"
  printf "Search in all aliases\n"
  printf "\$ finddep plexus-utils :aliases all\n(output elided)\n"
  if [[ "\$#" -eq 0 ]]; then
    exit 1
  fi
else
  clojure -Tfinddep find :name "\$@"
fi
EOF
chmod +x "$HOME/.local/bin/finddep"'
```

## Usage with `:name` parameter search

Go to your deps-based project and invoke the tool.

For example in this project if you are wondering why `org.ow2.asm/asm` is included, you can
do the following:

```bash
clojure -Tfinddep find :name asm

org.clojure/tools.deps {:mvn/version "0.19.1417"}
  com.cognitect.aws/api {:mvn/version "0.8.686"}
    org.clojure/core.async {:mvn/version "1.6.673"}
      org.clojure/tools.analyzer.jvm {:mvn/version "1.2.2"}
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



## Usage with aliases

```bash
clojure -Tfinddep find :name java.classpath :aliases '[:test]'

io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "48c3c67f98362ba1e20526db4eeb6996209c050a"}
  org.clojure/tools.namespace {:mvn/version "1.1.0"}
    org.clojure/java.classpath {:mvn/version "1.0.0"}
```

### Use all aliases

```bash
clojure -Tfinddep find :name java.classpath :aliases ':all'

io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "48c3c67f98362ba1e20526db4eeb6996209c050a"}
  org.clojure/tools.namespace {:mvn/version "1.1.0"}
    org.clojure/java.classpath {:mvn/version "1.0.0"}
```

## Usage with include-children

```bash
clojure -Tfinddep find :name tools.analyzer.jvm :include-children true

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

### Making a new release

```bash
./release.py
# or "./release.py --dry" if you want to see the changes about to be made
```


## License

Copyright © 2023 — 2026 Ivar Refsdal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

# finddep

Ever wondered where some dependency comes from?
Tired of manually "parsing" the output of `clojure -Stree`?
If so then `finddep` is for you. 

## Installation

```
clojure -Ttools install com.github.ivarref/finddep '{:git/sha "f6d89893eeac33b1b8998de5fef086eb58bdc4a3"}' :as finddep
```

### Optional installation
Optionally add an alias to your shell's init file:
```bash
alias finddep='clojure -Tfinddep fzf'
```

## Example usage

Go to your deps-based project and invoke the tool:

```bash
clojure -Tfinddep fzf
```

Start typing to see the dependency tree for a given dependency. 

For example in this project if you are wondering why `org.ow2.asm/asm` is included, you can
type that in. And you will see this output:

Output:
```
org.clojure/tools.deps {:mvn/version "0.19.1417"}
  com.cognitect.aws/api {:mvn/version "0.8.686"}
    org.clojure/core.async {:mvn/version "1.6.673"}
      org.clojure/tools.analyzer.jvm {:mvn/version "1.2.2"}
        org.ow2.asm/asm {:mvn/version "9.2"}
```

Right, it so that's why it was included...

## Usage with parameter search

```bash
clojure -Tfinddep find name asm
# OR: clojure -Tfinddep find :name asm

org.clojure/tools.deps {:mvn/version "0.19.1417"}
  com.cognitect.aws/api {:mvn/version "0.8.686"}
    org.clojure/core.async {:mvn/version "1.6.673"}
      org.clojure/tools.analyzer.jvm {:mvn/version "1.2.2"}
        org.ow2.asm/asm {:mvn/version "9.2"}
```

## License

Copyright © 2023 — 2024 Ivar Refsdal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

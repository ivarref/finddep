# finddep

Ever wondered where some dependency comes from?
Tired of manually "parsing" the output of `clojure -Stree`?
If so then `finddep` is for you. 

## Installation

```
clojure -Ttools install com.github.ivarref/finddep '{:git/tag "0.1.1" :git/sha "a8a0fa7"}' :as finddep
```

### Optional installation
Optionally add an alias to your shell's init file:
```bash
alias finddep='clojure -Tfinddep find :name'
```

## Example usage

Go to your deps-based project and invoke the tool.

Here [build.edn](https://github.com/liquidz/build.edn) is used as an example.
Let us see what introduced `slf4j-api` on the classpath:

```bash
clojure -Tfinddep find :name slf4j-api
```

Output:
```
slipset/deps-deploy {:mvn/version "0.2.1"}
  org.slf4j/slf4j-nop {:mvn/version "2.0.7"}
    org.slf4j/slf4j-api {:mvn/version "2.0.7"}

# Aha, deps-deploy included slf4j-api via slf4j-nop.
```

## License

Copyright © 2023 Ivar Refsdal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

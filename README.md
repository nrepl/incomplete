[![Clojars Project](https://img.shields.io/clojars/v/org.nrepl/incomplete.svg)](https://clojars.org/org.nrepl/incomplete)
[![cljdoc badge](https://cljdoc.org/badge/org.nrepl/incomplete)](https://cljdoc.org/d/org.nrepl/incomplete/CURRENT)
[![downloads badge](https://versions.deps.co/org.nrepl/incomplete/downloads.svg)](https://clojars.org/org.nrepl/incomplete)

# incomplete

A simple Clojure library providing code completion.
The library was extracted from nREPL's codebase and
aims to replace [clojure-complete](https://github.com/ninjudd/clojure-complete).

`incomplete`'s name refers to its basic nature and modest goals.
It doesn't aim to compete with the gold standard for completion [compliment](https://github.com/alexander-yakushev/compliment).

## Rationale

`clojure-complete` has several long-standing bugs and hasn't seen much love in recent years.
Still, the project is extremely popular due to its simplicity and the fact that it's
bundled with tools like Leiningen and REPLy.

`incomplete` started its life inside nREPL, as the provider of nREPL's built-in code
completion, but I decided it might be a useful standalone library as well.
It sits somewhere between `clojure-complete` and `compliment` in the sense that it has
more features (and less bugs) than the former, and it's much simpler and less capable than the
latter.

Here's a list of the `incomplete`'s advantages over `clojure-complete`:

* better completion of Java instance and static members
* keyword completion in Clojure
* candidate metadata (useful for tool authors)
* cleaner codebase (subjective, of course)

The long term goal for the project is to replace `clojure-complete` in REPLy and Leiningen.

## Usage

You need only one function from incomplete's API - `completions`.

``` clojure
(require 'incomplete.core)

;; var completion
(completions "map")
({:candidate "map", :type :function}
 {:candidate "map-entry?", :type :function}
 {:candidate "map-indexed", :type :function}
 {:candidate "map?", :type :function}
 {:candidate "mapcat", :type :function}
 {:candidate "mapv", :type :function})

;; ns completion
(completions "incomplete.co")
({:candidate "incomplete.core", :type :namespace}
 {:candidate "incomplete.core-test", :type :namespace})

;; keyword completion
(completions ":v")
({:candidate ":val", :type :keyword}
 {:candidate ":valf", :type :keyword}
 {:candidate ":valid", :type :keyword}
 {:candidate ":validator", :type :keyword}
 {:candidate ":value", :type :keyword}
 {:candidate ":var", :type :keyword}
 {:candidate ":var-form", :type :keyword}
 {:candidate ":var-name", :type :keyword}
 {:candidate ":var-params", :type :keyword}
 {:candidate ":var-query", :type :keyword}
 {:candidate ":varargs", :type :keyword}
 {:candidate ":vector", :type :keyword}
 {:candidate ":vector-long", :type :keyword}
 {:candidate ":verbose", :type :keyword}
 {:candidate ":verbose?", :type :keyword}
 {:candidate ":version-string", :type :keyword}
 {:candidate ":versions", :type :keyword}
 {:candidate ":via", :type :keyword}
 {:candidate ":volatile", :type :keyword}
 {:candidate ":volatile-mutable", :type :keyword})

;; static method completion
(completions "Integer/re")
({:candidate "Integer/remainderUnsigned", :type :static-method}
 {:candidate "Integer/reverse", :type :static-method}
 {:candidate "Integer/reverseBytes", :type :static-method})

;; instance method completion
(completions ".to")
({:candidate ".toBinaryString", :type :method}
 {:candidate ".toChars", :type :method}
 {:candidate ".toCodePoint", :type :method}
 {:candidate ".toDegrees", :type :method}
 {:candidate ".toHexString", :type :method}
 {:candidate ".toIntExact", :type :method}
 {:candidate ".toLowerCase", :type :method}
 {:candidate ".toOctalString", :type :method}
 {:candidate ".toRadians", :type :method}
 {:candidate ".toString", :type :method}
 {:candidate ".toTitleCase", :type :method}
 {:candidate ".toUnsignedInt", :type :method}
 {:candidate ".toUnsignedLong", :type :method}
 {:candidate ".toUnsignedString", :type :method}
 {:candidate ".toUpperCase", :type :method})
```

By default the function operates on the current ns (`*ns*`), but you
can also specify an explicit namespace.

``` clojure
(completions "ma" 'clojure.core)
```

## License

Copyright © 2021 Bozhidar Batsov

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

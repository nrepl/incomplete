(ns incomplete.core-test
  "Unit tests for incomplete's public API."
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [incomplete.core :as completion :refer [completions]]))

(def t-var "var" nil)
(defn t-fn "fn" [x] x)
(defmacro t-macro "macro" [y] y)

(defn- candidates
  "Return only the candidate names without any additional
  metadata for them."
  ([prefix]
   (candidates prefix *ns*))
  ([prefix ns]
   (map :candidate (completions prefix ns))))

(defn- distinct-candidates?
  "Return true if every candidate occurs in the list of
   candidates only once."
  ([prefix]
   (distinct-candidates? prefix *ns*))
  ([prefix ns]
   (apply distinct? (candidates prefix ns))))

(deftest completions-test
  (testing "var completion"
    (is (= '("alength" "alias" "all-ns" "alter" "alter-meta!" "alter-var-root")
           (candidates "al" 'clojure.core)))

    (is (= '("jio/make-input-stream" "jio/make-output-stream" "jio/make-parents" "jio/make-reader" "jio/make-writer")
           (candidates "jio/make" 'clojure.core)))

    (is (= '("clojure.core/alter" "clojure.core/alter-meta!" "clojure.core/alter-var-root")
           (candidates "clojure.core/alt" 'clojure.core)))

    (is (= () (candidates "fake-ns-here/")))

    (is (= () (candidates "/"))))

  (testing "namespace completion"
    (is (= '("incomplete.core" "incomplete.core-test")
           (candidates "incomplete.co")))

    (is (set/subset?
         #{"clojure.core" "clojure.core.ArrayChunk" "clojure.core.ArrayManager" "clojure.core.IVecImpl" "clojure.core.Vec" "clojure.core.VecNode" "clojure.core.VecSeq" "clojure.core.protocols" "clojure.core.protocols.InternalReduce"}
         (set (candidates "clojure.co")))))

  (testing "Java instance methods completion"
    (is (= '(".toUpperCase")
           (candidates ".toUpper")))

    (is (distinct-candidates? ".toString")))

  (testing "static members completion"
    (is (= '("System/out")
           (candidates "System/o")))

    (is (= '("java.lang.System/out")
           (candidates "java.lang.System/out")))

    (is (some #{"String/valueOf"} (candidates "String/")))
    (is (distinct-candidates? "String/v"))

    (is (not (some #{"String/indexOf" ".indexOf"} (candidates "String/")))))

  (testing "candidate types"
    (is (some #{{:candidate "t-var"
                 :type :var}}
              (completions "t-var" 'incomplete.core-test)))
    (is (some #{{:candidate "t-var"
                 :type :var
                 :doc "var"}}
              (completions "t-var" 'incomplete.core-test {:extra-metadata #{:arglists :doc}})))
    (is (some #{{:candidate "t-fn"
                 :type :function}}
              (completions "t-fn" 'incomplete.core-test)))
    (is (some #{{:candidate "t-fn"
                 :type :function
                 :arglists "([x])"
                 :doc "fn"}}
              (completions "t-fn" 'incomplete.core-test {:extra-metadata #{:arglists :doc}})))
    (is (some #{{:candidate "t-macro"
                 :type :macro}}
              (completions "t-macro" 'incomplete.core-test)))
    (is (some #{{:candidate "t-macro"
                 :type :macro
                 :arglists "([y])"
                 :doc "macro"}}
              (completions "t-macro" 'incomplete.core-test {:extra-metadata #{:arglists :doc}})))
    (is (some #{{:candidate "unquote" :type :var}}
              (completions "unquote" 'clojure.core)))
    (is (some #{{:candidate "if" :ns "clojure.core" :type :special-form}}
              (completions "if" 'clojure.core)))
    (is (some #{{:candidate "UnsatisfiedLinkError" :type :class}}
              (completions "Unsatisfied" 'clojure.core)))
    ;; ns with :doc meta
    (is (some #{{:candidate "clojure.core"
                 :type :namespace}}
              (completions "clojure.core" 'clojure.core)))
    (is (some #{{:candidate "clojure.core"
                 :type :namespace
                 :doc "Fundamental library of the Clojure language"}}
              (completions "clojure.core" 'clojure.core {:extra-metadata #{:doc}})))
    ;; ns with docstring argument
    (is (some #{{:candidate "incomplete.core-test"
                 :type :namespace}}
              (completions "incomplete.core-test" 'clojure.core)))
    (is (some #{{:candidate "incomplete.core-test"
                 :type :namespace
                 :doc "Unit tests for incomplete's public API."}}
              (completions "incomplete.core-test" 'clojure.core {:extra-metadata #{:doc}})))
    (is (some #{{:candidate "Integer/parseInt" :type :static-method}}
              (completions "Integer/parseInt" 'clojure.core)))
    (is (some #{{:candidate "File/separator", :type :static-method}}
              (completions "File/" 'incomplete.core)))
    (is (some #{{:candidate ".toString" :type :method}}
              (completions ".toString" 'clojure.core)))))

(deftest keyword-completions-test
  (testing "colon prefix"
    (is (set/subset? #{":doc" ":refer" ":refer-clojure"}
                     (set (candidates ":" *ns*)))))

  (testing "unqualified keywords"
    (do #{:t-key-foo :t-key-bar :t-key-baz :t-key/quux}
        (is (set/subset? #{":t-key-foo" ":t-key-bar" ":t-key-baz" ":t-key/quux"}
                         (set (candidates ":t-key" *ns*))))))

  (testing "auto-resolved unqualified keywords"
    (do #{::foo ::bar ::baz}
        (is (set/subset? #{":incomplete.core-test/bar" ":incomplete.core-test/baz"}
                         (set (candidates ":incomplete.core-test/ba" *ns*))))
        (is (set/subset? #{"::bar" "::baz"}
                         (set (candidates "::ba" 'incomplete.core-test))))))

  (testing "auto-resolved qualified keywords"
    (do #{:nrepl.core/aliased-one :nrepl.core/aliased-two}
        (require '[nrepl.core :as core])
        (is (set/subset? #{"::core/aliased-one" "::core/aliased-two"}
                         (set (candidates "::core/ali" *ns*))))))

  (testing "namespace aliases"
    (is (set/subset? #{"::set"}
                     (set (candidates "::s" 'incomplete.core-test)))))

  (testing "namespace aliases without namespace"
    (is (empty? (candidates "::/" *ns*)))))

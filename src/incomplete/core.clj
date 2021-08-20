(ns incomplete.core
  "Code completion functionality extracted from nREPL.

  The functionality here is experimental and
  the API is subject to changes."
  {:author "Bozhidar Batsov"}
  (:require [clojure.main]
            [clojure.java.io :as io])
  (:import [java.util.jar JarFile]
           [java.io File]
           [java.lang.reflect Field Member]
           [java.util.jar JarEntry]
           [java.util.concurrent ConcurrentHashMap]))

;; Code adapted from Compliment (https://github.com/alexander-yakushev/compliment)

(defn annotate-keyword
  [kw]
  {:candidate kw :type :keyword})

(defn all-keywords
  []
  (let [^Field field (.getDeclaredField clojure.lang.Keyword "table")]
    (.setAccessible field true)
    (.keySet ^ConcurrentHashMap (.get field nil))))

(defn- resolve-namespace
  [sym ns]
  (get (ns-aliases ns) sym (find-ns sym)))

(defn qualified-auto-resolved-keywords
  "Given a namespace alias, a prefix, and a namespace, return completion
  candidates for qualified, auto-resolved keywords (e.g. ::foo/bar)."
  [ns-alias prefix ns]
  (let [ns-alias-name (str (resolve-namespace (symbol ns-alias) ns))]
    (sequence
     (comp
      (filter #(= (namespace %) ns-alias-name))
      (filter #(.startsWith (name %) prefix))
      (map #(str "::" ns-alias "/" (name %)))
      (map annotate-keyword))
     (all-keywords))))

(defn unqualified-auto-resolved-keywords
  "Given a prefix and a namespace, return completion candidates for
  keywords that belong to the given namespace."
  [prefix ns]
  (sequence
   (comp
    (filter #(= (namespace %) (str ns)))
    (filter #(.startsWith (name %) (subs prefix 2)))
    (map #(str "::" (name %)))
    (map annotate-keyword))
   (all-keywords)))

(defn keyword-namespace-aliases
  "Given a prefix and a namespace, return completion candidates for namespace
  aliases as auto-resolved keywords."
  [prefix ns]
  (sequence
   (comp
    (map (comp name first))
    (filter (fn [^String alias-name] (.startsWith alias-name (subs prefix 2))))
    (map #(str "::" (name %)))
    (map annotate-keyword))
   (ns-aliases ns)))

(defn single-colon-keywords
  "Given a prefix, return completion candidates for keywords that are either
  unqualified or qualified with a synthetic namespace."
  [prefix]
  (sequence
   (comp
    (filter #(.startsWith (str %) (subs prefix 1)))
    (map #(str ":" %))
    (map annotate-keyword))
   (all-keywords)))

(defn keyword-candidates
  [^String prefix ns]
  (assert (string? prefix))
  (let [double-colon? (.startsWith prefix "::")
        single-colon? (.startsWith prefix ":")
        slash-pos (.indexOf prefix "/")]
    (cond
      (and double-colon? (pos? slash-pos))
      (let [ns-alias (subs prefix 2 slash-pos)
            prefix (subs prefix (inc slash-pos))]
        (qualified-auto-resolved-keywords ns-alias prefix ns))

      double-colon?
      (into
       (unqualified-auto-resolved-keywords prefix ns)
       (keyword-namespace-aliases prefix ns))

      single-colon?
      (single-colon-keywords prefix))))

;; Code adapted from clojure-complete (https://github.com/ninjudd/clojure-complete)
;; The results follow compliment's format, as it's richer and gives more useful
;; data to clients.

;;; Utility functions
(defn namespaces
  "Returns a list of potential namespace completions for a given namespace"
  [ns]
  (concat (map ns-name (all-ns)) (keys (ns-aliases ns))))

(defn ns-public-vars
  "Returns a list of potential public var name completions for a given namespace"
  [ns]
  (vals (ns-publics ns)))

(defn ns-vars
  "Returns a list of all potential var name completions for a given namespace"
  [ns]
  (filter var? (vals (ns-map ns))))

(defn ns-classes
  "Returns a list of potential class name completions for a given namespace"
  [ns]
  (keys (ns-imports ns)))

(def special-forms
  '[def if do let quote var fn loop recur throw try monitor-enter monitor-exit dot new set!])

(defn- static? [#^java.lang.reflect.Member member]
  (java.lang.reflect.Modifier/isStatic (.getModifiers member)))

(defn ns-java-methods
  "Returns a list of Java method names for a given namespace."
  [ns]
  (distinct ; some methods might exist in multiple classes
   (for [class (vals (ns-imports ns)) method (.getMethods ^Class class) :when (static? method)]
     (str "." (.getName ^Member method)))))

(defn static-members
  "Returns a list of potential static members for a given class"
  [^Class class]
  (->> (concat (.getMethods class) (.getDeclaredFields class))
       (filter static?)
       (map #(.getName ^Member %))
       (distinct)))

(defn path-files [^String path]
  (cond (.endsWith path "/*")
        (for [^File jar (.listFiles (File. path)) :when (.endsWith ^String (.getName jar) ".jar")
              file (path-files (.getPath jar))]
          file)

        (.endsWith path ".jar")
        (try (for [^JarEntry entry (enumeration-seq (.entries (JarFile. path)))]
               (.getName entry))
             (catch Exception _e))

        :else
        (for [^File file (file-seq (File. path))]
          (.replace ^String (.getPath file) path ""))))

(def classfiles
  (for [prop (filter #(System/getProperty %1) ["sun.boot.class.path" "java.ext.dirs" "java.class.path"])
        path (.split (System/getProperty prop) File/pathSeparator)
        ^String file (path-files path) :when (and (.endsWith file ".class") (not (.contains file "__")))]
    file))

(defn- classname [^String file]
  (.. file (replace ".class" "") (replace File/separator ".")))

(def top-level-classes
  (doall
   (for [file classfiles :when (re-find #"^[^\$]+\.class" file)]
     (classname file))))

(def nested-classes
  (doall
   (for [file classfiles :when (re-find #"^[^\$]+(\$[^\d]\w*)+\.class" file)]
     (classname file))))

(defn resolve-class [ns sym]
  (try (let [val (ns-resolve ns sym)]
         (when (class? val) val))
       (catch Exception e
         (when (not= ClassNotFoundException
                     (class (clojure.main/repl-exception e)))
           (throw e)))))

;;; Candidates

(def ^:private safe-var-metadata
  "A list of var metadata attributes are safe to return to the clients.
  We need to guard ourselves against EDN data that's not encodeable/decodable
  with bencode. We also optimize the response payloads by not returning
  redundant metadata."
  [:ns :name :doc :file :arglists :forms :macro :special-form
   :protocol :line :column :added :deprecated :resource])

(defn- handle-file-meta
  "Convert :file metadata to string.
  Typically `value` would be a string, a File or an URL."
  [value]
  (when value
    (str (if (string? value)
           ;; try to convert relative file paths like "clojure/core.clj"
           ;; to absolute file paths
           (or (io/resource value) value)
           ;; If :file is a File or URL object we just return it as is
           ;; and covert it to string
           value))))

(defn- sanitize-meta
  "Sanitize a Clojure metadata map such that it can be bencoded."
  [m]
  (-> m
      (select-keys safe-var-metadata)
      (update :ns str)
      (update :name str)
      (update :protocol str)
      (update :file handle-file-meta)
      (cond-> (:macro m) (update :macro str))
      (cond-> (:special-form m) (update :special-form str))
      (assoc :arglists-str (str (:arglists m)))
      (cond-> (:arglists m) (update :arglists str))))

(defn annotate-var [var {:keys [extra-metadata]}]
  (let [{macro :macro arglists :arglists var-name :name doc :doc} (-> var meta sanitize-meta)
        type (cond macro :macro
                   arglists :function
                   :else :var)]
    (cond-> {:candidate (name var-name) :type type}
      (and (contains? extra-metadata :doc) doc) (assoc :doc doc)
      (and (contains? extra-metadata :arglists) arglists) (assoc :arglists arglists))))

(defn annotate-class
  [cname]
  {:candidate cname :type :class})

(def special-form-candidates
  (map #(hash-map :candidate (name %) :type :special-form :ns "clojure.core") special-forms))

(defn ns-candidates
  [ns {:keys [extra-metadata]}]
  ;; Calling meta on sym that names a namespace only returns doc if the ns form
  ;; uses the docstring arg, but not if it uses the ^{:doc "..."} meta form.
  ;;
  ;; find-ns returns the namespace the sym names. Calling meta on it returns
  ;; the docstring, no matter which way it's defined.
  (map #(let [doc (some-> % find-ns meta :doc)]
          (cond-> {:candidate (name %)
                   :type :namespace}
            (and (contains? extra-metadata :doc) doc) (assoc :doc doc)))
       (namespaces ns)))

(defn ns-var-candidates
  [ns options]
  (map #(annotate-var % options) (ns-vars ns)))

(defn ns-public-var-candidates
  [ns options]
  (map #(annotate-var % options) (ns-public-vars ns)))

(defn ns-class-candidates
  [ns]
  (map #(hash-map :candidate (name %) :type :class) (ns-classes ns)))

(defn ns-java-method-candidates
  [ns]
  (for [method (ns-java-methods ns)]
    {:candidate method :type :method}))

(defn static-member-candidates
  [class]
  (for [name (static-members class)]
    {:candidate name :type :static-method}))

(defn scoped-candidates
  [^String prefix ns options]
  (when-let [prefix-scope (first (.split prefix "/"))]
    (let [scope (symbol prefix-scope)]
      (map #(update % :candidate (fn [c] (str scope "/" c)))
           (if-let [class (resolve-class ns scope)]
             (static-member-candidates class)
             (when-let [ns (or (find-ns scope) (scope (ns-aliases ns)))]
               (ns-public-var-candidates ns options)))))))

(defn class-candidates
  [^String prefix _ns]
  (map annotate-class
       (if (.contains prefix "$")
         nested-classes
         top-level-classes)))

(defn generic-candidates
  [ns options]
  (concat special-form-candidates
          (ns-candidates ns options)
          (ns-var-candidates ns options)
          (ns-class-candidates ns)))

(defn completion-candidates
  [^String prefix ns options]
  (cond
    (.startsWith prefix ":") (keyword-candidates prefix ns)
    (.startsWith prefix ".") (ns-java-method-candidates ns)
    (.contains prefix "/")  (scoped-candidates prefix ns options)
    (.contains prefix ".")  (concat (ns-candidates ns options) (class-candidates prefix ns))
    :else                   (generic-candidates ns options)))

(defn completions
  "Return a sequence of matching completion candidates given a prefix string and an optional current namespace.
  You can also provide an additional `options` map to tweak the candidate list to your needs.
  E.g. you can pass {:extra-metadata #{:arglists :doc}} to request additional metadata for the candidates."
  ([prefix]
   (completions prefix *ns*))
  ([prefix ns]
   (completions prefix ns nil))
  ([^String prefix ns options]
   (let [candidates (completion-candidates prefix ns options)]
     (sort-by :candidate (filter #(.startsWith ^String (:candidate %) prefix) candidates)))))

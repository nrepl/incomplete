(defproject org.nrepl/incomplete "0.1.0"
  :description "A simple code completion library."
  :url "https://github.com/nrepl/incomplete"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  ;; we actually target Clojure 1.7, but some dev tools no longer support it
  ;; TODO: Add several profiles for various Clojure versions here.
  :dependencies [[org.clojure/clojure "1.8.0"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :repl-options {:init-ns incomplete.core})

(defproject alibi "0.1.0-SNAPSHOT"
  :description "A clojure-based time tracker for developers"
  :url "https://infi.nl"
  :license {:name "Mozilla Public License Version 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.7.1"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.4.0"]
                 [compojure "1.4.0"]
                 [selmer "0.9.5"]
                 [clj-time "0.11.0"]
                 [bouncer "1.0.0"]
                 [org.clojure/java.jdbc "0.7.0-alpha2"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.2"]
                 [buddy/buddy-auth "1.1.0"]
                 [ring/ring-codec "1.0.0"]
                 [crypto-random "1.2.0"]
                 [clj-http "2.1.0"]
                 [buddy/buddy-sign "1.1.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.xerial/sqlite-jdbc "3.16.1"]]
  :repl-options {:init-ns user}
  :plugins [[lein-ring "0.9.7"] 
            [cider/cider-nrepl "0.10.0"]
            [lein-pprint "1.1.1"]
            [lein-figwheel "0.5.8"]
            [lein-cljsbuild "1.1.4" :exclusions [[org.clojure/clojure]]]]
  :main alibi.core

  :ring {
         :handler alibi.core/app
         ;:stacktraces? false
         ;:nrepl {:start? true}
         }

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/frontend/cljs"]

                :figwheel {}

                :compiler {:main alibi.core
                           :asset-path "/dist/cljs/out"
                           :output-to "resources/public/dist/cljs/alibi.js"
                           :output-dir "resources/public/dist/cljs/out"
                           :source-map-timestamp true
                           :language-in :ecmascript5
                           :preloads [devtools.preload]}}
               {:id "single-file"
                :source-paths ["src/frontend/cljs"]
                :compiler {:output-to "resources/public/dist/cljs/alibi.js"
                           :main alibi.core
                           :output-dir "resource/public/dist/cljs/out-single-file"
                           :optimizations :simple
                           :language-in :ecmascript5
                           :pretty-print true}}
               {:id "min"
                :source-paths ["src/frontend/cljs"]
                :compiler {:output-to "resources/public/dist/cljs/alibi.js"
                           :main alibi.core
                           :optimizations :advanced
                           :externs ["externs/js-joda.js"
                                     "externs/jquery-3.1.js"
                                     "externs/datepicker.js"
                                     "externs/selectize.js"]
                           ;:pseudo-names true
                           :verbose true
                           :language-in :ecmascript5
                           :pretty-print false}}]}

  :figwheel {:server-port 5076
             :css-dirs ["resources/public/res/css"]
             :server-logfile "logs/figwheel.log"}
  :profiles {:dev [{:dependencies [[ring/ring-mock "0.3.0"]
                                   [binaryage/devtools "0.8.2"]
                                   [figwheel-sidecar "0.5.8"]
                                   [com.cemerick/piggieback "0.2.1"]
                                   [org.clojure/clojurescript "1.9.229"]
                                   [org.clojure/core.async "0.2.391"
                                    :exclusions [org.clojure/tools.reader]]
                                   [cljsjs/react "15.4.2-0"]
                                   [cljsjs/react-dom "15.4.2-0"]
                                   [sablono "0.7.7"]]
                    :source-paths ["src" "dev" "src/frontend/cljs"]
                    :repl-options {:init (set! *print-length* 50)
                                   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                    :resource-paths ["config/dev"]}
                   :custom-persistence]
             :test [{:resource-paths ["config/test"]}
                    :custom-persistence]
             :local [{:resource-paths ["config/local"]}
                     :custom-persistence]
             :demo [{:resource-paths ["config/demo"]}
                     :custom-persistence]
             :prod [{:resource-paths ["config/prod"]}
                    :custom-persistence]
             :custom-persistence {}})

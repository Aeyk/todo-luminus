(defproject luminus-full-stack "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]   
                [keybind "2.2.0"]              
                 [io.netty/netty-transport-native-epoll "4.1.53.Final"]
                 [cheshire "5.10.0"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.fasterxml.jackson.core/jackson-core "2.11.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.11.3"]
                 [com.google.javascript/closure-compiler-unshaded "v20200504" :scope "provided"]
                 [org.clojure/tools.logging "1.1.0"]

                 [conman "0.9.0"]
                 [cprop "0.1.17"]
                 [expound "0.8.6"]
                 [funcool/struct "1.4.0"]
                 [luminus-http-kit "0.1.9"]
                 [luminus-migrations "0.6.9"]
                 [luminus-transit "0.1.2"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.5"]
                 [metosin/jsonista "0.2.7"]
                 [metosin/muuntaja "0.6.7"]
                 [metosin/reitit "0.5.9"]
                 [metosin/ring-http-response "0.9.1"]
                 [mount "0.1.16"]
                 [nrepl "0.8.2"]
                 [binaryage/devtools "0.9.7"]
                 [cider/cider-nrepl "0.21.1"]
                 [http-kit "2.5.0"]
                 [com.taoensso/sente "1.16.0"]   
                 [com.taoensso/encore "3.9.1"]
                 [rum/rum "0.12.3"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773" :scope "provided"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/google-closure-library "0.0-20191016-6ae1f72f" :scope "provided"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]

                 [org.postgresql/postgresql "42.2.18"]
                 [com.impossibl.pgjdbc-ng/pgjdbc-ng "0.8.6"]
                 [org.webjars.npm/bulma "0.9.1"]
                 [org.webjars.npm/material-icons "0.3.1"]
                 [org.webjars/webjars-locator "0.40"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.12.31"]
                 [org.clojure/tools.reader "1.3.3"]
                 [thheller/shadow-cljs "2.11.5" :scope "provided"]]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot luminus-full-stack.core

  :plugins [[lein-shadow "0.2.0"]] 
  :clean-targets ^{:protect false}
  [:target-path "target/cljsbuild"]
  :shadow-cljs
  {:nrepl {:port 7002}
   :builds
   {:app
    {:target :browser
     :output-dir "target/cljsbuild/public/js"
     :asset-path "/js"
     ;; https://stackoverflow.com/questions/57885828/netty-cannot-access-class-jdk-internal-misc-unsafe
     :jvm-opts ["-Xmx2g" "--add-opens java.base/jdk.internal.misc=ALL-UNNAMED" "-Dio.netty.tryReflectionSetAccessible=true" "--illegal-access=warn"] 
     :modules {:app {:entries [luminus-full-stack.client]}}
     :devtools {:watch-dir "resources/public"}}
    :test
    {:target :node-test
     :output-to "target/test/test.js"
     :autorun true}}}
  
  :npm-deps []
  :npm-dev-deps [[xmlhttprequest "1.8.0"]
                 [react "16.14.0"]                 
                 [react-dom "16.14.0"]
                 ]

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks ["compile" ["shadow" "release" "app"]]
             
             :aot :all
             :uberjar-name "luminus-full-stack.jar"
             :source-paths ["env/prod/clj"  "env/prod/cljs" ]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn"
                             "-Xmx1g"]
                  :dependencies [[binaryage/devtools "1.0.2"]
                                 [cider/piggieback "0.5.1"]
                                 [pjstadig/humane-test-output "0.10.0"]
                                 [prone "2020-01-17"]
                                 [ring/ring-devel "1.8.2"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "0.3.5"]] 
                  
                  
                  :source-paths ["env/dev/clj"  "env/dev/cljs" "test/cljs" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"] 
                  
                  
                  }
   :profiles/dev {}
   :profiles/test {}}
   :repositories 
  [["java.net" "https://download.java.net/maven/2"]
   ["sonatype" {:url "https://oss.sonatype.org/content/repositories/releases"
                ;; If a repository contains releases only setting
                ;; :snapshots to false will speed up dependencies.
                :snapshots false
                ;; You can also set the policies for how to handle
                ;; :checksum failures to :fail, :warn, or :ignore.
                :checksum :fail
                ;; How often should this repository be checked for
                ;; snapshot updates? (:daily, :always, or :never)
                :update :always
                ;; You can also apply them to releases only:
                :releases {:checksum :fail :update :always}}]])

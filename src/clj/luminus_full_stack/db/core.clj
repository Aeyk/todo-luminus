(ns luminus-full-stack.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [next.jdbc :as jdbc]
    [next.jdbc.date-time]
    [next.jdbc.prepare]
    [next.jdbc.result-set]
    [clojure.tools.logging :as log]
    [conman.core :as conman]
    [luminus-full-stack.config :refer [env]]
    [mount.core :refer [defstate] :as mount])
  (:import
   [com.impossibl.postgres.api.jdbc PGNotificationListener]
   [com.impossibl.postgres.jdbc PGDriver]))

(def listener
  (reify PGNotificationListener
    (^void notification 
     [this ^int processId ^String channelName ^String payload]
     (log/log :info payload))))

(defn add-listener! []
  (doto (jdbc/get-connection 
          (env :database-url))
    (.addNotificationListener listener))
  (jdbc/execute!
    (env :database-url)
    ["LISTEN events;"]))

(defn remove-listener! []
  (.removeNotificationListener (jdbc/get-connection (env :database-url))
    listener))

(defstate ^:dynamic *db*
  :start (if-let [jdbc-url (env :database-url)]
           (conman/connect! {:jdbc-url jdbc-url})
           (add-listener!)
           (do
             (log/warn "database connection URL was not found, please set :database-url in your config, e.g: dev-config.edn")
             *db*))
  :stop (do 
          (remove-listener!
          (conman/disconnect! *db*))))

(conman/bind-connection *db* "sql/queries.sql")

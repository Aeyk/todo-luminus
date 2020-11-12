(ns luminus-full-stack.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [conman.core :as conman]
    [luminus-full-stack.config :refer [env]]
    [mount.core :refer [defstate] :as mount])
  (:import
   [com.impossibl.postgres.api.jdbc PGNotificationListener]))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defstate notifications-connection
  :start (jdbc/get-connection {:connection-uri (env :database-url)})
  :stop (.close notifications-connection))

(defn add-listener [conn id listener-fn]
  (let [listener (proxy [PGNotificationListener] []
                   (notification [chan-id channel message]
                     (listener-fn chan-id channel message)))]
    (.addNotificationListener conn listener)
    (jdbc/execute!
      {:connection notifications-connection}
      ["LISTEN ?" (name id)])
    listener))

(defn remove-listener [conn listener]
  (.removeNotificationListener conn listener))

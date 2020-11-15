(ns luminus-full-stack.db.events
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [luminus-full-stack.config :refer [env]]
    [mount.core :refer [defstate] :as mount])
  (:import
   [com.impossibl.postgres.api.jdbc PGNotificationListener]))

(defstate notifications-connection
  :start 
  (jdbc/get-connection {:connection-uri (env :database-url)})
  :stop (.close notifications-connection))

(defn add-listener [conn id listener-fn]
  (let [listener (proxy [PGNotificationListener] []
                   (notification [chan-id channel message]
                     (listener-fn chan-id channel message)))]
    (.addNotificationListener conn listener)
    (jdbc/execute!
      {:connection-uri (env :database-url)}
      [(str "LISTEN " (name id))])
    listener))

(defn remove-listener [conn listener]
  (.removeNotificationListener conn listener))

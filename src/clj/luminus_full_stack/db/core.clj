(ns luminus-full-stack.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [conman.core :as conman]
    [luminus-full-stack.db.events :refer [notifications-connection
                                          add-listener
                                          remove-listener]]
    [luminus-full-stack.config :refer [env]]
    [mount.core :refer [defstate] :as mount])
  (:import
   [com.impossibl.postgres.api.jdbc PGNotificationListener]))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(ns luminus-full-stack.routes.websockets  
  (:require [luminus-full-stack.db.core :as db]
            [reitit.ring :as ring]
            [reitit.core :as route]
            [org.httpkit.server :as server
             :refer [send! with-channel on-close on-receive]]            
            [taoensso.sente :as sente]            
            [taoensso.sente.server-adapters.http-kit 
             :refer (get-sch-adapter)]
            [clojure.core.async :as async  
             :refer (<! <!! >! >!! put! chan go go-loop)]
            [clojure.tools.logging :as log]))

(defonce channels (atom #{}))

(defn persist-event! [_ event]
  (db/event! {:event event}))

(defn connect! [channel]
 (log/log :info "channel open")
 (swap! channels conj channel))

(defn disconnect! [channel status]
 (log/log :info (str "channel closed:" status))
 (swap! channels #(remove #{channel} %)))

(defn notify-clients [msg]
 (doseq [channel @channels]
     (send! channel msg)))

(defn websocket-handler [request]
  (with-channel request channel
    (connect! channel)
    (on-close channel (partial disconnect! channel))
    (on-receive channel #(notify-clients %))))

(ns luminus-full-stack.routes.websockets    
  (:require [luminus-full-stack.config :refer [env]]
            [luminus-full-stack.db.core :as db]            
            [luminus-full-stack.db.events :as events]            
            [reitit.ring :as ring]
            [reitit.core :as route]
            [org.httpkit.server :as server
             :refer [as-channel send! with-channel on-close on-receive]]            
            [taoensso.sente :as sente]            
            [taoensso.sente.server-adapters.http-kit 
             :refer (get-sch-adapter)]
            [clojure.core.async :as async  
             :refer (<! <!! >! >!! put! chan go go-loop)]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate] :as mount]
            [clojure.tools.reader :as edn]))

(let [packer :edn
      ;; (sente-transit/get-transit-packer) ; needs Transit dep
      chsk-server
      (sente/make-channel-socket-server!
       (get-sch-adapter) {:user-id-fn (fn [ring-req] (:client-id ring-req))
                          :packer packer})
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      chsk-server]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(add-watch connected-uids :connected-uids
  (fn [_ _ old new]
    (when (not= old new)
      (log/log :info (str "Connected uids change: %s" new)))))

(defstate channels :start (atom #{}))

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
  (as-channel request #_channel
    {:on-open connect!
    :on-close disconnect!
    :on-receive persist-event!}))


;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg) ; Handle event-msgs on a single thread
  ;; (future (-event-msg-handler ev-msg)) ; Handle event-msgs on a thread pool
  )

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (log/log :info (str "Unhandled event: %s" event))
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-server event}))))


(defmethod -event-msg-handler
  :chsk/uidport-open
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn ring-req client-id]}]
  (let [session (:session ring-req)
        uid     (:uid session)]
    (log/log :info (str "Opened connection: " event "\n"))
    (when ?reply-fn
      (?reply-fn {:event event}))))

(defmethod -event-msg-handler
  :chsk/uidport-close
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn ring-req client-id]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (log/log :info (str "Closed connection: " event "\n"))
    (when ?reply-fn
      (?reply-fn {:event event}))))


;; (defmethod -event-msg-handler :example/test-rapid-push
;;   [ev-msg] (test-fast-server>user-pushes))

(defmethod -event-msg-handler
  :comment-list/add-comment
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (log/log :info (str ":comment-list/add-comment: " event))
    (if (not (nil? event)) 
      (db/event! {:event event})
      (log/log :info
        (drop 0 event)))
    (when ?reply-fn
      (?reply-fn {:comment-list/add-comment event}))))

(defn send-to-all [message]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid message)))

(defmethod -event-msg-handler
   :comment-list/get-comments
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn ring-req client-id]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    ((juxt      
       #_#(log/log :info %)
       #(chsk-send! 
          client-id [:comment-list/comments [(edn/read-string %)]]))
      (str 
        #_(comment ":client-id: " client-id " \n:comment-list/get-comments: ") 
        (db/get-events)))    
    (when ?reply-fn
      (?reply-fn  #_
        (apply str (map :event (take-last 5 (db/get-events)))) #_(db/get-events)
        {:comment-list/comments
         (str (db/get-events))}))))

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-fn @router_] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-server-chsk-router!
      ch-chsk event-msg-handler)))


(defstate ^:dynamic *ws*
  :start 
  (do (log/log :info "Starting Websockets on backend.")
      (start-router!))
  :stop 
  (do
    (log/log :info "Stopping Websockets on backend.")
    (stop-router!)))

(defstate ^{:on-reload :noop} event-listener
  :start (events/add-listener
           events/notifications-connection
           :events
           (fn [_ _ message]
             (doseq [channel @channels]
               (server/send! channel message))))
  :stop (events/remove-listener
          events/notifications-connection
          event-listener))

(ns luminus-full-stack.routes.websockets    
  (:require [luminus-full-stack.config :refer [env]]
            [luminus-full-stack.db.core :as db]            
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
            [mount.core :refer [defstate] :as mount]))

(let [packer :edn
      ;; (sente-transit/get-transit-packer) ; needs Transit dep
      chsk-server
      (sente/make-channel-socket-server!
       (get-sch-adapter) {:packer packer})
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      chsk-server]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

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

(add-watch connected-uids :connected-uids
  (fn [_ _ old new]
    (when (not= old new)
      (log/log :info (str "Connected uids change: %s" new)))))

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

;; (defmethod -event-msg-handler :example/test-rapid-push
;;   [ev-msg] (test-fast-server>user-pushes))

(defmethod -event-msg-handler :comment-list/add-comment
  [{:as ev-msg :keys [?reply-fn ?data send-fn]}]
  (let [content (:content ?data)]
    (log/log :info (str ":comment-list/add-comment : " content))
  (if (not (nil? content)) (db/event! {:event content}))))


(defmethod -event-msg-handler :comment-list/get-comments
  [{:as ev-msg :keys [?reply-fn ?data send-fn]}]
  (let [content (:content ?data)]
    (log/log :info (str ":comment-list/get-comments : " (db/get-events)))
  (if (not (nil? content)) (db/event! {:event content}))))


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
  :start (db/add-listener
           db/notifications-connection
           :events
           (fn [_ _ message]
             (doseq [channel @channels]
               (server/send! channel message))))
  :stop (db/remove-listener
          db/notifications-connection
          event-listener))

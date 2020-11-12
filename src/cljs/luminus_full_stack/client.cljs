(ns luminus-full-stack.client
  (:require 
   [rum.core :as rum]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.sente  :as sente :refer (cb-success?)]   
   [taoensso.timbre :as log]))

(def handlers {:on-message (fn [e] (prn (.-data e)))
               :on-open    #(prn "Opening a new connection")
               :on-close   #(prn "Closing a connection")})

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

(if ?csrf-token
  (println "CSRF token detected in HTML, great!")
  (println "CSRF token NOT detected in HTML, default Sente config will reject requests"))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk" ; Note the same path as before
       ?csrf-token
       {:type :auto; e/o #{:auto :ajax :ws}
        :packer :edn})]

  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(enable-console-print!)

(defn ->output! [fmt & args]
  (let [msg (apply encore/format fmt args)]
    (js/console.log)
       msg))

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (->output! "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (->output! "Channel socket successfully established!: %s" new-state-map)
      (->output! "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake: %s" ?data)))

(defmethod -event-msg-handler :comment-list/comments
  [{:as ev-msg :keys [event ?reply-fn ?data send-fn]}]
  (let [content (:content ?data)
        events (map :event ?data)]
    (log/log :info (str ":comment-list/comments : " event events))))


(def comments (atom ["I can be a comment" "I can too"]))
(def potential-comment (atom ""))

(def state (atom "John"))

(rum/defc em-tag [text]
  [:pre
   (str "<em>"text"</em>")])

(rum/defc my-form < 
  {:did-mount 
   (fn [e] 
     (chsk-send! [:comment-list/get-comments] 1005 
       (fn [callback-reply args]
         (js/console.log callback-reply args)
         (callback-reply args)))
     )}
  []
  [:form 
   [:input]    
   [:button     
    {:on-click 
     (fn[e] 
       (js/e.preventDefault)
       (#(log/log :info %) 
         (str 
           "form input: "
           (.-value (.querySelector js/document "form input"))))       
       (chsk-send! [:comment-list/add-comment 
                    {:content 
                     (.-value 
                       (.querySelector 
                         js/document "form input"))}] 1003 
         (fn [callback-reply]           
           (callback-reply "Hello"))))}
    "Submit"]
   [:button 
    {:on-click 
     (fn [e]
       (js/e.preventDefault)
       (chsk-send! [:comment-list/get-comments] 1002))}
    "Update"]])

(def events (atom []))

(rum/defc my-list < {}
  
  []
  [:ul
   (for [event @events]
     [:li event])])

(defn app []
  
  [(em-tag "Hello")
   (my-form)
   (my-list)])

(defn start []
  (rum/mount 
    (app)
    (js/document.getElementById "app")))

(defn ^:export init! []
  (start))

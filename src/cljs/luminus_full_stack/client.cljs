(ns luminus-full-stack.client
  (:require 
   [cljs.reader :as edn]
   [keybind.core :as key]
   [rum.core :as rum]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.sente  :as sente :refer (cb-success?)]   
   [taoensso.timbre :as log]))

(def events (atom []))

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

(if ?csrf-token
  (println "CSRF token detected in HTML, great!")
  (println "CSRF token NOT detected in HTML, default Sente config will reject requests"))

(enable-console-print!)

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

(defn get-messages []
  (chsk-send! 
    [:comment-list/get-comments] 5002
    (fn [s]
      (let [new-event
            (map 
              (comp :content second cljs.reader/read-string :event) 
              (reverse 
                (take-last 10 
                  (cljs.reader/read-string (nth (.-arr ^Object s) 1)))))]        
        (reset! events 
          new-event)))))

(defn submit-message [msg]
  (chsk-send! 
    [:comment-list/add-comment {:content msg}] 1002))

(def handlers 
  {:on-message 
   (fn [e] ((comp 
              js/console.log  #(log/log :info %) prn) (.-data e)))
   :on-open    #(prn "Opening a new connection\n")
   :on-close   #(prn "Closing a connection")})

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
    (log/log :info (str ":comment-list/comments : " content event events))))

(add-watch events :events
  (fn [_ _ old new]
    (js/console.log (clj->js old new))))

(defn ws-mixin [key]
  {:will-mount
   (fn [state]
     (let [*data (atom nil)
           comp  (:rum/react-component state)]
       (chsk-send! 
         [:comment-list/get-comments] 5002
         (fn [data]
           (let [new-event                 
                 (reverse 
                   (take-last 10 
                     (cljs.reader/read-string (nth (.-arr ^Object data) 1))))]        
             (reset! *data data)
             (rum/request-render comp))))))})

(rum/defc em-tag [text]
  [:pre
   (str "<em>"text"</em>")])

(rum/defc my-form <
  (ws-mixin ::events)
  {:after-render    
   (fn [state]
     (key/bind! "enter" ::enter
       #(let [data (.-value 
                    (.querySelector 
                      js/document "form input"))]
          (do 
            (submit-message data)
            (get-messages)
            (set! (.-value 
                    (.querySelector 
                      js/document "form input")) "")))))}
  []
  [:form {:on-submit (fn [e] (.preventDefault e))}
   [:input]])

(rum/defc my-list <  
  rum/reactive    
  [*events]    
  []
  [:ul
   (for [event  
         (rum/react events)]
     [:li 
      {:on-click 
       (fn [e] 
         (if (= "disabled" (-> e .-target .-parentNode .-className))
           (set! (-> e .-target .-parentNode .-className) "")
           (set! (-> e .-target .-parentNode .-className) "disabled"))
         (js/console.log (-> e .-target .-parentNode)))} [:p event]])])

(rum/defc app <
  rum/reactive
  [*events]
  {:after-render
   (fn [state]
     (get-messages))}
  []
  [:div.container
   (em-tag "Hello World")
   (my-form)
   (my-list)])

(defn start []
  (rum/mount 
    (app)
    (js/document.getElementById "app")))

(defn ^:export init! []
  (start))

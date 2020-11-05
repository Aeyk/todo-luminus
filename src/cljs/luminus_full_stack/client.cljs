(ns luminus-full-stack.client
  (:require [rum.core :as rum]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(def handlers {:on-message (fn [e] (prn (.-data e)))
               :on-open    #(prn "Opening a new connection")
               :on-close   #(prn "Closing a connection")})

(def socket-uri "ws://localhost:3000/ws")

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/ws" ; Note the same path as before
       ?csrf-token
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]

  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )
(enable-console-print!)

(rum/defc hello [text]
  [:pre.app 
   (str "<em>"text"</em>")])

(rum/defc numbered-list [n]
  (for [x (range 1 (inc n))]
    [:div (str x)]))

(defn start []
  (rum/mount 
    [(hello "Hello")]
    (js/document.getElementById "app")))

(defn ^:export init! []
  (start))

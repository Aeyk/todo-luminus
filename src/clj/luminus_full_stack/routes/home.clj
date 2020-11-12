(ns luminus-full-stack.routes.home
  (:require
   [rum.core :as rum]
   [luminus-full-stack.layout :as layout]
   [luminus-full-stack.db.core :as db]
   [luminus-full-stack.routes.websockets :as ws 
    :refer [chsk-send! 
            ring-ajax-get-or-ws-handshake ring-ajax-post
            ]]
   [clojure.java.io :as io]
   [luminus-full-stack.middleware :as middleware]
   [taoensso.sente.server-adapters.http-kit 
    :refer (get-sch-adapter)]
   [taoensso.sente :as sente]            
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn tt-page [request]
  [:div#sente-csrf-token {:data-csrf-token (:anti-forgery-token request)}
     [:form 
      [:input
       {:id "comment"
        :type "text"
        :value (rum/react request)}]]])

(defn landing-page [request]
    [:h1 "Sente reference example"]
    (let [csrf-token
          ;; (force anti-forgery/*anti-forgery-token*)
          (:anti-forgery-token request) ]

      [:div#sente-csrf-token {:data-csrf-token csrf-token}])
    [:p "An Ajax/WebSocket" [:strong " (random choice!)"] " has been configured for this example"]
    [:hr]
    [:p [:strong "Step 1: "] " try hitting the buttons:"]
    [:p
     [:button#btn1 {:type "button"} "chsk-send! (w/o reply)"]
     [:button#btn2 {:type "button"} "chsk-send! (with reply)"]]
    [:p
     [:button#btn3 {:type "button"} "Test rapid server>user async pushes"]]
    [:p
     [:button#btn5 {:type "button"} "Disconnect"]
     [:button#btn6 {:type "button"} "Reconnect"]]
    ;;
    [:p [:strong "Step 2: "] " observe std-out (for server output) and below (for client output):"]
    [:textarea#output {:style "width: 100%; height: 200px;"}]
    ;;
    [:hr]
    [:h2 "Step 3: try login with a user-id"]
    [:p  "The server can use this id to send events to *you* specifically."]
    [:p
     [:input#input-login {:type :text :placeholder "User-id"}]
     [:button#btn-login {:type "button"} "Secure login!"]]
    ;;
    [:hr]
    [:h2 "Step 4: want to re-randomize Ajax/WebSocket connection type?"]
    [:p "Hit your browser's reload/refresh button"]
    [:script {:src "main.js"}] ; Include our cljs target
    )



(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/tt" {:get landing-page }]
   ["/chsk" {:get ring-ajax-get-or-ws-handshake
             :post ring-ajax-post}]
   ["/ws " ws/websocket-handler]
   ["/docs" 
    {:get 
     (fn [_]
       (-> 
         (response/ok (-> "docs/docs.md" io/resource slurp))
         (response/header "Content-Type" "text/plain; charset=utf-8")))}]
   ["/init" 
    {:get  
     (fn [request]
       (let [uid  (get-in request [:session :uid])]
         {:status 200
          :headers {"Content-Type" "text/javascript; charset=utf-8"}
          :session (assoc (:session request) :uid (:client-id request))}))}]])


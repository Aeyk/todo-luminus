(ns luminus-full-stack.routes.home
  (:require
   [luminus-full-stack.layout :as layout]
   [luminus-full-stack.db.core :as db]
   [luminus-full-stack.routes.websockets :as ws]
   [clojure.java.io :as io]
   [luminus-full-stack.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/ws" ws/websocket-handler]
   ["/docs" 
    {:get 
     (fn [_]
       (-> 
         (response/ok (-> "docs/docs.md" io/resource slurp))
         (response/header "Content-Type" "text/plain; charset=utf-8")))}]])


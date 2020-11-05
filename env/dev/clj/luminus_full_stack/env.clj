(ns luminus-full-stack.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [luminus-full-stack.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[luminus-full-stack started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[luminus-full-stack has shut down successfully]=-"))
   :middleware wrap-dev})

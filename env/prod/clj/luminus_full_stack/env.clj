(ns luminus-full-stack.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[luminus-full-stack started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[luminus-full-stack has shut down successfully]=-"))
   :middleware identity})

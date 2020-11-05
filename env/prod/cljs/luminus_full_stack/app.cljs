(ns luminus-full-stack.app
  (:require [luminus-full-stack.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)

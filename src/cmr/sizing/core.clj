(ns cmr.sizing.core
  (:require
    [taoensso.timbre :as log]))

(defn estimate-size
  [component user-token data]
  (log/trace "Parameter data:" data)
  {:errors ["sizing estimate not-implemented"]})

(ns cmr.opendap.sizing.core
  (:require
    [taoensso.timbre :as log]))

(defn estimate-size
  [component api-version user-token data]
  (log/trace "Parameter data:" data)
  {:errors ["sizing estimate not-implemented"]})

(ns cmr.sizing.core
  (:require
    [cmr.exchange.common.results.core :as results]
    [cmr.exchange.common.results.errors :as errors]
    [cmr.exchange.common.results.warnings :as warnings]
    [taoensso.timbre :as log]))

(defn estimate-size
  [component user-token
   {:keys [format] :as raw-params}]
  (log/trace "raw-params:" raw-params)
  (log/debug "Got format:" format)
  (case format
    :binary)
  {:errors ["sizing estimate not-implemented"]})

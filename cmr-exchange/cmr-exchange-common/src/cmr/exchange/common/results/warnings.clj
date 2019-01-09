(ns cmr.exchange.common.results.warnings
  (:require
   [clojusc.results.warnings :as warnings]))

(def get-warnings #'warnings/get-warnings)
(def collect #'warnings/collect)

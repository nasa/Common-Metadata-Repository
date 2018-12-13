(ns cmr.nlp.config
  (:require
   [cmr.exchange.common.config :as config]))

(def config-file "config/cmr-nlp/config.edn")

(def data #(config/data config-file))

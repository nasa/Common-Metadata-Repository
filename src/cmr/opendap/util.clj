(ns cmr.opendap.util
  (:require
   [cmr.exchange.common.util :as util]))

;; XXX Once the ns's that call this functions have been updated to reference
;; `cmr.exchange.common.util` directly, we can delete this (and this whole
;; file).
(def bool util/bool?)
(def bool? util/bool?)
(def remove-empty util/remove-empty)
(def deep-merge util/deep-merge)
(def now util/now)
(def timed util/timed)
(def most-frequent util/most-frequent)
(def promise? util/promise?)

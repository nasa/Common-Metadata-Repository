(ns cmr.nlp.query
  (:require
   [clojure.string :as string]
   [cmr.nlp.core :as nlp]
   [cmr.nlp.time.human :as human-time]
   [cmr.nlp.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn even-dates
  [dates]
  (if (even? (count dates))
    dates
    (let [parser (human-time/create)]
      (conj dates (human-time/date parser "now")))))

(defn dates->strs
  [dates]
  (map util/date->cmr-date-string dates))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->cmr-temporal
  [sentence]
  (let [dates (nlp/extract-dates sentence)
        dates-pairs (->> dates
                         even-dates
                         dates->strs
                         (partition 2)
                         (map #(string/join "," %))
                         (interleave (repeat "temporal[]"))
                         (partition 2))]
    (util/encode-tuples dates-pairs)))

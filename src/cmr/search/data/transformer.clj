(ns cmr.search.data.transformer
  "Provdes functions for retrieving formatted concepts."
  (:require [cmr.transmit.transformer :as t]))

(defn get-formatted-concepts
  "Get concepts given by the concept-id, revision-id tuples in the
  given format."
  [context concept-tuples format]
  (let [concepts "A"]))
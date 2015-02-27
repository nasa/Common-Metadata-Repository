(ns cmr.ingest.services.helper
  "This contains the helper functions for the ingest application."
  (:require [cmr.transmit.metadata-db :as mdb]))

(defn find-visible-collections
  "Returns the non-deleted latest revision collections that matches the given params in metadata db."
  [context params]
  (let [coll-concepts (mdb/find-collections context (assoc params :latest true))]
    ;; Find the latest version of the concepts that aren't deleted. There should be only one
    (remove :deleted coll-concepts)))
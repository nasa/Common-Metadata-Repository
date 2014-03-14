(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.log :refer (debug info warn error)]))

(defn save-concept
  "Store a concept record and return the revision"
  [system concept]
  (let [{:keys [db]} system]
    (data/save-concept db concept)))
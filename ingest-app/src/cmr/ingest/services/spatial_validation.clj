(ns cmr.ingest.services.spatial-validation
  "Provides functions to validate the spatial attributes of a collection during its update."
  (:require [cmr.common.util :as util]
            [camel-snake-kebab.core :as csk]))

(defn- extract-granule-spatial-representation
  "Returns the granule spatial representation of the collection or a default of :no-spatial."
  [coll]
  (get-in coll [:spatial-coverage :granule-spatial-representation] :no-spatial))

(defn spatial-param-change-searches
  "Validates that if a collection changes its spatial representation for granules then it can
  contain no granules. Any existing granules would be configured for the previous granule spatial
  representation and would be indexed based on that. We can't allow this change if there are any
  granules. Returns a search that will see if the collection contains any granules if the gsr
  changes."
  [concept-id concept prev-concept]
  (let [prev-gsr (extract-granule-spatial-representation prev-concept)
        new-gsr (extract-granule-spatial-representation concept)]
    (when-not (= prev-gsr new-gsr)
      [{:params {:collection-concept-id concept-id}
        :error-msg (format (str "Collection changing from %s granule spatial representation to %s"
                                " is not allowed when the collection has granules.")
                           (csk/->SCREAMING_SNAKE_CASE_STRING prev-gsr)
                           (csk/->SCREAMING_SNAKE_CASE_STRING new-gsr))}])))
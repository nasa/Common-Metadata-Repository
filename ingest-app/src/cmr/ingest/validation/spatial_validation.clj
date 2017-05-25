(ns cmr.ingest.validation.spatial-validation
  "Provides functions to validate the spatial attributes of a collection during its update."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common.util :as util]))

(defn- extract-granule-spatial-representation
  "Returns the granule spatial representation of the collection or a default of :no-spatial."
  [coll]
  (or (get-in coll [:SpatialExtent :GranuleSpatialRepresentation]) :no-spatial))

(defn spatial-param-change-searches
  "Validates that if a collection changes its spatial representation for granules then it can
  contain no granules. Any existing granules would be configured for the previous granule spatial
  representation and would be indexed based on that. We can't allow this change if there are any
  granules. Returns a search that will see if the collection contains any granules if the gsr
  changes."
  [context concept-id concept prev-concept]
  (let [prev-gsr (extract-granule-spatial-representation prev-concept)
        new-gsr (extract-granule-spatial-representation concept)]
    (when-not (= prev-gsr new-gsr)
      [{:params {:collection-concept-id concept-id}
        :error-msg (format (str "Collection changing from %s granule spatial representation to %s"
                                " is not allowed when the collection has granules.")
                           prev-gsr new-gsr)}])))

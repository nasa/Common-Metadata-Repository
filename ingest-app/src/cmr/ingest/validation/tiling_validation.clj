(ns cmr.ingest.validation.tiling-validation
  "Provides functions to validate the tiling identification system names during collection update"
  (:require
   [clojure.set :as s]
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]))

(defn deleted-tiling-searches
  "Returns granule searches for deleted tiling identification system names.
  We should not delete tiling identification system names in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [previous-tiles
         (map :TilingIdentificationSystemName (:TilingIdentificationSystems prev-concept))
        current-tiles
         (map :TilingIdentificationSystemName (:TilingIdentificationSystems concept))
        tile-alias-map
         (get (humanizer-alias-cache/get-humanizer-alias-map context) "tiling_system_name")
        tile-aliases
         (mapcat #(get tile-alias-map %) (map str/upper-case current-tiles))
        ;; Only the deleted ones that are not part of the tile-aliases need to be validated.
        deleted-tile-names
         (s/difference
           (set (map util/safe-lowercase previous-tiles))
           (set (map util/safe-lowercase (concat current-tiles tile-aliases))))]
    (for [name deleted-tile-names]
      {:params {"two-d-coordinate-system[]" name
                :collection-concept-id concept-id}
       :error-msg (format
                    (str "Collection TilingIdentificationSystemName [%s] is referenced by existing"
                         " granules, cannot be removed.") name)})))

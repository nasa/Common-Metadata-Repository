(ns cmr.ingest.validation.instrument-validation
  "Provides functions to validate the instruments during collection update"
  (:require
   [clojure.set :as s]
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]))

(defn- get-parent-instruments-from-concept
  "Returns all the parent instrument shortnames from a collection concept.
   Note: Currently we don't support hierarchical search on the parent instrument yet.
   All the parent instruments will be combined into one big list because it doesn't matter
   which platform it's under, it's all treated the same way in the search.
   This needs to be revisited when CMR-3834 is fixed, which supports hierarchical search"
  [concept]
  (for [platform (:Platforms concept)
        instrument (:Instruments platform)]
    (:ShortName instrument)))

(defn- get-child-instruments-from-concept
  "Returns all the child instrument shortnames from a collection concept.
   Note: Currently we don't support hierarchical search on the child instrument yet.
   All the child instruments will be combined into one big list because it doesn't matter
   which platform it's under, it's all treated the same way in the search.
   This needs to be revisited when CMR-3834 is fixed, which supports hierarchical search."
  [concept]
  (for [platform (:Platforms concept)
        instrument (:Instruments platform)
        child-instrument (:ComposedOf instrument)]
    (:ShortName child-instrument)))

(defn deleted-parent-instrument-searches
  "Returns granule searches for deleted parent instruments. We should not delete instruments in a
   collection that are still referenced by existing granules. This function builds the search
   parameters to identify such invalid deletions."
  [context concept-id concept prev-concept]
  (let [ins-alias-map (get (humanizer-alias-cache/get-humanizer-alias-map context) "instrument")
        current-parent-ins (get-parent-instruments-from-concept concept)
        previous-parent-ins (get-parent-instruments-from-concept prev-concept)
        ins-aliases (mapcat #(get ins-alias-map %) (map str/upper-case current-parent-ins))
        ;; Only the deleted ones that are not part of the ins-aliases need to be validated.
        deleted-parent-instrument-names (s/difference
                                          (set (map util/safe-lowercase previous-parent-ins))
                                          (set (map util/safe-lowercase (concat current-parent-ins ins-aliases))))]
    (for [name deleted-parent-instrument-names]
      {:params {"instrument[]" name
                :collection-concept-id concept-id
                "options[instrument][exclude_collection]" "true"}
       :error-msg (format (str "Collection Instrument [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))

(defn deleted-child-instrument-searches
  "Returns granule searches for deleted child instruments. We should not delete instruments in a
   collection that are still referenced by existing granules. This function builds the search
   parameters to identify such invalid deletions."
  [context concept-id concept prev-concept]
  (let [ins-alias-map (get (humanizer-alias-cache/get-humanizer-alias-map context) "instrument")
        current-child-ins (get-child-instruments-from-concept concept)
        previous-child-ins (get-child-instruments-from-concept prev-concept)
        ins-aliases (mapcat #(get ins-alias-map %) (map str/upper-case current-child-ins))
        ;; Only the deleted ones that are not part of the ins-aliases need to be validated.
        deleted-child-instrument-names (s/difference
                                         (set (map util/safe-lowercase previous-child-ins))
                                         (set (map util/safe-lowercase (concat current-child-ins ins-aliases))))]
    (for [name deleted-child-instrument-names]
      {:params {"sensor[]" name
                :collection-concept-id concept-id
                "options[sensor][exclude_collection]" "true"}
       :error-msg (format (str "Collection Child Instrument [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))

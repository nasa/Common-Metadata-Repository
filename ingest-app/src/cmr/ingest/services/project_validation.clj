(ns cmr.ingest.services.project-validation
  "Provides functions to validate the projects during collection update"
  (:require [cmr.common.util :as util]))

(defn deleted-project-searches
  "Returns granule searches for deleted projects. We should not delete projects in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [concept-id concept prev-concept]
  (let [project-names (set (map :short-name (:projects concept)))
        prev-project-names (set (map :short-name (:projects prev-concept)))
        deleted-project-names (clojure.set/difference prev-project-names project-names)]
    (map #(hash-map :params {"project[]" %}
                    :error-msg (format (str "Collection project [%s] is referenced by existing"
                                            " granules, cannot be removed.") %))
         deleted-project-names)))

(ns cmr.ingest.services.project-validation
  "Provides functions to validate the projects during collection update"
  (:require [clojure.set :as s]))

(defn deleted-project-searches
  "Returns granule searches for deleted projects. We should not delete projects in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [concept-id concept prev-concept]
  (let [deleted-project-names (s/difference
                                (set (map :short-name (:projects prev-concept)))
                                (set (map :short-name (:projects concept))))]
    (for [name deleted-project-names]
      {:params {"project[]" name
                :collection-concept-id concept-id}
       :error-msg (format (str "Collection Project [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))
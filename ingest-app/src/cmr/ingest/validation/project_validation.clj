(ns cmr.ingest.validation.project-validation
  "Provides functions to validate the projects during collection update"
  (:require
   [clojure.set :as s]))

(defn deleted-project-searches
  "Returns granule searches for deleted projects. We should not delete projects in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [deleted-project-names (s/difference
                                (set (map :ShortName (:Projects prev-concept)))
                                (set (map :ShortName (:Projects concept))))]
    (for [name deleted-project-names]
      {:params {"project[]" name
                :collection-concept-id concept-id}
       :error-msg (format (str "Collection Project [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))

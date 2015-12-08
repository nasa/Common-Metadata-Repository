(ns cmr.ingest.validation.collection-unique-ids-validation
  "Provides functions to validate the collection unique identifiers during collection update")

(defn unique-ids-searches
  "Returns granule searches for the collection. We should not update collection unique identifiers
  (entry-title or the short-name and version-id pair) if there are granules for this collection.
  This function builds the search parameters for the granule search."
  [concept-id concept prev-concept]
  (let [{{:keys [short-name version-id]} :product
         entry-title :entry-title} concept
        prev-entry-title (:entry-title prev-concept)
        prev-short-name (get-in prev-concept [:product :short-name])
        prev-version-id (get-in prev-concept [:product :version-id])]
    (if-not (= entry-title prev-entry-title)
      [{:params {:collection-concept-id concept-id}
        :error-msg (format (str "Collection with entry-title [%s] is referenced by existing"
                                " granules, cannot be renamed.") prev-entry-title)}]
      (if-not (and (= short-name prev-short-name)
                   (= version-id prev-version-id))
        [{:params {:collection-concept-id concept-id}
          :error-msg (format (str "Collection with short-name [%s] & version-id [%s] is referenced by"
                                  " existing granules, cannot be renamed.")
                             prev-short-name prev-version-id)}]))))
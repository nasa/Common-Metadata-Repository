(ns cmr.ingest.validation.collection-unique-ids-validation
  "Provides functions to validate the collection unique identifiers during collection update")

(defn entry-title-searches
  "Returns granule searches if entry-title is updated; nil if collection entry-title is not updated.
  We should not update collection entry-title if there are granules for this collection.
  This function builds the granule search parameters for identifying if there are granules for this
  collection and the error message when validation fails."
  [concept-id concept prev-concept]
  (let [{:keys [entry-title]} concept
        prev-entry-title (:entry-title prev-concept)]
    (when-not (= entry-title prev-entry-title)
      [{:params {:collection-concept-id concept-id}
        :error-msg (format (str "Collection with entry-title [%s] is referenced by existing"
                                " granules, cannot be renamed.") prev-entry-title)}])))

(defn short-name-version-id-searches
  "Returns granule searches if short-name and version-id pair is updated; nil if collection
  short-name and version-id is not updated. We should not update collection short-name or version-id
  if there are granules for this collection. This function builds the granule search parameters for
  identifying if there are granules for this collection and the error message when validation fails."
  [concept-id concept prev-concept]
  (let [{{:keys [short-name version-id]} :product} concept
        prev-short-name (get-in prev-concept [:product :short-name])
        prev-version-id (get-in prev-concept [:product :version-id])]
    (when-not (and (= short-name prev-short-name)
                   (= version-id prev-version-id))
      [{:params {:collection-concept-id concept-id}
        :error-msg (format (str "Collection with short-name [%s] and version-id [%s] is referenced by"
                                " existing granules, cannot be renamed.")
                           prev-short-name prev-version-id)}])))
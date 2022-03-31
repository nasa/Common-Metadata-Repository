(ns cmr.ingest.validation.project-validation
  "Provides functions to validate the projects during collection update"
  (:require
   [clojure.set :as s]
   [cmr.common.util :as util]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]))

(comment
  (println concept-id)
  (deleted-project-searches context concept-id concept prev-concept)
  platform-alias-map (get (humanizer-alias-cache/get-humanizer-alias-map context) "platform")
        current-platforms (map :ShortName (:Platforms concept))
        previous-platforms (map :ShortName (:Platforms prev-concept))
        platform-aliases (mapcat #(get platform-alias-map %) (map str/upper-case current-platforms))
        ;; Only the deleted ones that are not part of the platform-aliases need to be validated.
        deleted-platform-names (s/difference
                                (set (map util/safe-lowercase previous-platforms))
                                (set (map util/safe-lowercase (concat current-platforms platform-aliases)))))

(defn deleted-project-searches
  "Returns granule searches for deleted projects. We should not delete projects in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [project-alias-map (get (humanizer-alias-cache/get-humanizer-alias-map context) "project")
        current-projects (map :ShortName (:Projects concept))
        previous-projects (map :ShortName (:Projects prev-concept))
        project-aliases (mapcat #(get project-alias-map %) current-projects)
        deleted-project-names (s/difference
                                (set (map util/safe-lowercase previous-projects))
                                (set (map util/safe-lowercase (concat current-projects project-aliases))))]
    (for [name deleted-project-names]
      {:params {"project[]" name
                :collection-concept-id concept-id}
       :error-msg (format (str "Collection Project [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))

(ns cmr.indexer.data.concepts.organization
  "Contains functions to extract organizaiton fields"
  (require [cmr.common-app.services.kms-fetcher :as kf]
           [clojure.string :as str]))

(defn extract-archive-centers
  "Extract the archive organization/archive-centers from collection"
  [collection]
  (let [orgs (:organizations collection)]
    (for [org orgs
          :when (= :archive-center (:type org))]
      (:org-name org))))

(def default-archive-center-values
  "Default values to use for any archive-center fields which are nil."
  (zipmap [:level-0 :level-1 :level-2 :level-3 :long-name :data-center-url]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn archive-center-short-name->elastic-doc
  "Converts an archive-center short-name into an elastic document with the full nested hierarchy
  for that short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy,
  we use a dummy value to indicate the field was not present."
  [gcmd-keywords-map short-name]
  (let [full-archive-center
        (merge default-archive-center-values
               (kf/get-full-hierarchy-for-short-name gcmd-keywords-map :providers short-name))
        {:keys [level-0 level-1 level-2 level-3 long-name data-center-url uuid]} full-archive-center]
    {:level-0 level-0
     :level-0.lowercase (str/lower-case level-0)
     :level-1 level-1
     :level-1.lowercase (str/lower-case level-1)
     :level-2 level-2
     :level-2.lowercase (str/lower-case level-2)
     :level-3 level-3
     :level-3.lowercase (str/lower-case level-3)
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name.lowercase (str/lower-case long-name)
     :data-center-url data-center-url
     :data-center-url.lowercase (str/lower-case data-center-url)
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))


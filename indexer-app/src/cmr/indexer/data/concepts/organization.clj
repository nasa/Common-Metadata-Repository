(ns cmr.indexer.data.concepts.organization
  "Contains functions to extract organization fields. There are four types of organizations.
  Archive centers, distribution centers, processing centers, and originating centers. The term
  data centers is a general term for any of the four types."
  (require [cmr.common-app.services.kms-fetcher :as kf]
           [clojure.string :as str]))

(def default-data-center-values
  "Default values to use for any data-center fields which are nil."
  (zipmap [:level-0 :level-1 :level-2 :level-3 :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn extract-data-center-names
  "Extract the unique organization names from a collection. Optionally takes an organization-type
  field to limit the results to that type."
  ([collection]
   (distinct (map :org-name (:organizations collection))))
  ([collection organization-type]
   (distinct (for [org (:organizations collection)
                   :when (= organization-type (:type org))]
               (:org-name org)))))

(defn data-center-short-name->elastic-doc
  "Converts a data-center short-name into an elastic document with the full nested hierarchy
  for that short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy,
  we use a dummy value to indicate the field was not present."
  [gcmd-keywords-map short-name]
  (let [full-data-center
        (merge default-data-center-values
               (kf/get-full-hierarchy-for-short-name gcmd-keywords-map :providers short-name))
        {:keys [level-0 level-1 level-2 level-3 short-name long-name url uuid]
                ;; Use the short-name from KMS if present, otherwise use the metadata short-name
                :or {short-name short-name}} full-data-center]
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
     :url url
     :url.lowercase (when url (str/lower-case url))
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))


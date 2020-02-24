(ns cmr.indexer.data.concepts.collection.data-center
  "Contains functions to extract data center fields. There are four types of data centers:
  ARCHIVER, DISTRIBUTOR, PROCESSOR and ORIGINATOR."
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.umm-spec.util :as spec-util]))

(def default-data-center-values
  "Default values to use for any data-center fields which are nil."
  (zipmap [:level-0 :level-1 :level-2 :level-3 :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn data-center-contacts
  "Returns the data center contacts with ContactInformation added if it doesn't have contact info"
  [data-center]
  (let [contacts (concat (:ContactPersons data-center) (:ContactGroups data-center))]
    (map (fn [contact]
           (if (:ContactInformation contact)
             contact
             (assoc contact :ContactInformation (:ContactInformation data-center))))
         contacts)))

(defn- ordered-data-centers
  "Returns the given data centers in the order of archive centers first, then distribution centers,
  then the rest."
  [centers]
  (let [center-weight (fn [center]
                        (let [{roles :Roles} center]
                          (cond
                            (.contains roles "ARCHIVER") 1
                            (.contains roles "DISTRIBUTOR") 2
                            :else 3)))]
    (sort-by center-weight centers)))

(defn- sanitized-data-centers
  "Returns the data centers by removing the default data centers"
  [data-centers]
  (when (not= [spec-util/not-provided-data-center] data-centers)
    data-centers))

(defn extract-data-center-names
  "Extract the unique data center names from the given collection. The data center names are
  ordered by archive-center names first, then distribution-center names, then the rest.
  Optionally takes an data center role field to limit the results to that type."
  ([collection]
   (distinct (map :ShortName
                  (ordered-data-centers (sanitized-data-centers (:DataCenters collection))))))
  ([collection data-center-role]
   (distinct (for [center (sanitized-data-centers (:DataCenters collection))
                   :when (.contains (:Roles center) data-center-role)]
               (:ShortName center)))))

(defn extract-archive-center-names
  "Extract the unique archive center names from the given collection."
  [collection]
  (extract-data-center-names collection "ARCHIVER"))

(defn data-center-short-name->elastic-doc
  "Converts a data-center short-name into an elastic document with the full nested hierarchy
  for that short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy,
  we use a dummy value to indicate the field was not present."
  [kms-index short-name]
  (let [full-data-center
        (merge default-data-center-values
               (kms-lookup/lookup-by-short-name kms-index :providers short-name))
        {:keys [level-0 level-1 level-2 level-3 short-name long-name url uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-data-center]
    {:level-0 level-0
     :level-0-lowercase (str/lower-case level-0)
     :level-1 level-1
     :level-1-lowercase (str/lower-case level-1)
     :level-2 level-2
     :level-2-lowercase (str/lower-case level-2)
     :level-3 level-3
     :level-3-lowercase (str/lower-case level-3)
     :short-name short-name
     :short-name-lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (str/lower-case long-name)
     :url url
     :url-lowercase (when url (str/lower-case url))
     :uuid uuid
     :uuid-lowercase (when uuid (str/lower-case uuid))}))

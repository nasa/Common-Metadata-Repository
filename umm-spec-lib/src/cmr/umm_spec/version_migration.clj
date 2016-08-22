(ns cmr.umm-spec.version-migration
  "Contains functions for migrating between versions of UMM schema."
  (:require [clojure.set :as set]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.umm-spec.versioning :refer [versions current-version]]
            [cmr.umm-spec.location-keywords :as lk]
            [cmr.umm-spec.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(defn- version-steps
  "Returns a sequence of version steps between begin and end, inclusive."
  [begin end]
  (->> (condp #(%1 %2) (compare begin end)
         neg?  (sort versions)
         zero? nil
         pos?  (reverse (sort versions)))
       (partition 2 1 nil)
       (drop-while #(not= (first %) begin))
       (take-while #(not= (first %) end))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Migrating Between Versions

;; Private Migration Functions

(defn- dispatch-migrate
  [_context _collection concept-type source-version dest-version]
  [concept-type source-version dest-version])

(defmulti ^:private migrate-umm-version
  "Returns the given data structure of the indicated concept type and UMM version updated to conform to the
  target UMM schema version."
  #'dispatch-migrate)

(defmethod migrate-umm-version :default
  [context c & _]
  ;; Do nothing by default. This lets us skip over "holes" in the version sequence, where the UMM
  ;; version may be updated but a particular concept type's schema may not be affected.
  c)

;; Collection Migrations

(def not-provided-organization
  "Place holder to use when an organization is not provided."
  {:Role "RESOURCEPROVIDER"
   :Party {:OrganizationName {:ShortName u/not-provided}}})

(defmethod migrate-umm-version [:collection "1.0" "1.1"]
  [context c & _]
  (-> c
      (update-in [:TilingIdentificationSystem] #(when % [%]))
      (set/rename-keys {:TilingIdentificationSystem :TilingIdentificationSystems})))

(defmethod migrate-umm-version [:collection "1.1" "1.0"]
  [context c & _]
  (-> c
      (update-in [:TilingIdentificationSystems] first)
      (set/rename-keys {:TilingIdentificationSystems :TilingIdentificationSystem})))

(defmethod migrate-umm-version [:collection "1.1" "1.2"]
  [context c & _]
  ;; Change SpatialKeywords to LocationKeywords
  (-> c
      (assoc :LocationKeywords (lk/translate-spatial-keywords context (:SpatialKeywords c)))))

(defmethod migrate-umm-version [:collection "1.2" "1.1"]
  [context c & _]
  ;;Assume that IsoTopicCategories will not deviate from the 1.1 list of allowed values.
  (-> c
      (assoc :SpatialKeywords
             (or (seq (lk/location-keywords->spatial-keywords (:LocationKeywords c)))
                 (:SpatialKeywords c)
                 ;; Spatial keywords are required
                 [u/not-provided]))
      (assoc :LocationKeywords nil)))

(defmethod migrate-umm-version [:collection "1.2" "1.3"]
  [context c & _]
  (-> c
      (update-in [:PaleoTemporalCoverage] #(when % [%]))
      (set/rename-keys {:PaleoTemporalCoverage :PaleoTemporalCoverages})))

(defmethod migrate-umm-version [:collection "1.3" "1.2"]
  [context c & _]
  (-> c
      (update-in [:PaleoTemporalCoverages] first)
      (set/rename-keys {:PaleoTemporalCoverages :PaleoTemporalCoverage})))

(defn map-data-center-role
  [role]
  (if (contains? #{"ARCHIVER" "DISTRIBUTOR" "PROCESSOR" "ORIGINATOR"} role)
    role
    "ARCHIVER"))

(defn party->contact-information
  [party]
  (let [contact-information
        {:ServiceHours (:ServiceHours party)
         :ContactInstruction (:ContactInstructions party)
         :RelatedUrls (:RelatedUrls party)
         :Addresses (:Addresses party)
         :ContactMechanisms (:Contacts party)}]
    (when (seq (util/remove-nil-keys contact-information))
      [contact-information])))

(defn person->contact-persons
  [person]
  (when (seq person)
   (if (empty? (:Roles person))
     [(assoc person :Roles [u/not-provided-contact-person-role])]
     [person])))

(defn organization->data-center
  [organization]
  (let [party (:Party organization)]
    {:Roles [(map-data-center-role (:Role organization))]
     :ShortName (:ShortName (:OrganizationName party))
     :LongName (:LongName (:OrganizationName party))
     :ContactInformation (party->contact-information party)
     :ContactPersons (person->contact-persons (:Person party))}))

(defn organizations->data-centers
  [organizations]
  (if (seq organizations)
    (mapv organization->data-center organizations)
    [u/not-provided-data-center]))

(defn personnel->contact-person
  [personnel]
  (let [party (:Party personnel)
        person (:Person party)]
    {:Roles [(:Role personnel)]
     :FirstName (:FirstName person)
     :MiddleName (:MiddleName person)
     :LastName (:LastName person)
     :Uuid (:Uuid person)
     :ContactInformation (party->contact-information party)}))

(defn personnel->contact-persons
  [personnel]
  (mapv personnel->contact-person personnel))

(defmethod migrate-umm-version [:collection "1.3" "1.4"]
  [context c & _]
  (-> c
      (assoc :DataCenters (organizations->data-centers (:Organizations c)))
      (assoc :ContactPersons (personnel->contact-persons (:Personnel c)))
      (dissoc :Organizations :Personnel)))

(defmethod migrate-umm-version [:collection "1.4" "1.3"]
  [context c & _]
  (-> c
      ;; This is a lossy migration. DataCenters, ContactGroups and ContactPersons fields in the original UMM JSON are dropped.
      (dissoc :DataCenters :ContactGroups :ContactPersons)
      (assoc :Organizations [not-provided-organization])))

(defn- update-attribute-description
  "If description is nil, set to default of 'Not provided'"
  [attribute]
  (if (nil? (:Description attribute))
     (assoc attribute :Description u/not-provided)
     attribute))

(defmethod migrate-umm-version [:collection "1.4" "1.5"]
  [context c & _]
  (-> c
    ;; If an Additional Attribute has no description, set the description
    ;; to the default "Not provided"
    (update-in [:AdditionalAttributes] #(mapv update-attribute-description %))))

(defmethod migrate-umm-version [:collection "1.5" "1.4"]
  [context c & _]
  ;; Don't need to migrate Additional Attribute description back since 'Not provided' is valid
  c)

(defn- first-contact-info
  "Update the array of contact infos to be a single instance using the first contact info in the list"
  [c]
  (update-in c [:ContactInformation] first))

(defn- update-data-center-contact-info
  "Update data center contact infos to be a single instance - this includes the contact info on the
   data center, the contact info inside each contact person and each contact group"
  [data-center]
  (-> data-center
      first-contact-info
      (update-in [:ContactPersons] #(mapv first-contact-info %))
      (update-in [:ContactGroups] #(mapv first-contact-info %))))

(defmethod migrate-umm-version [:collection "1.5" "1.6"]
  [context c & _]
  (-> c
    (update-in [:DataCenters] #(mapv update-data-center-contact-info %))
    (update-in [:ContactPersons] #(mapv first-contact-info %))
    (update-in [:ContactGroups] #(mapv first-contact-info %))))

(defn- contact-info-to-array
  "Update the contact info field to be an array. If contact info is nil, leave it as nil."
  [c]
  (if (some? (:ContactInformation c))
   (assoc c :ContactInformation [(:ContactInformation c)])
   c))

(defn- update-data-center-contact-info-to-array
  "Update data center contact infos to be arrays - this includes the contact info on the
   data center, the contact info inside each contact person and each contact group"
  [data-center]
  (-> data-center
      (contact-info-to-array)
      (update-in [:ContactPersons] #(mapv contact-info-to-array %))
      (update-in [:ContactGroups] #(mapv contact-info-to-array %))))

(defmethod migrate-umm-version [:collection "1.6" "1.5"]
  [context c & _]
  (-> c
      (update-in [:DataCenters] #(mapv update-data-center-contact-info-to-array %))
      (update-in [:ContactPersons] #(mapv contact-info-to-array %))
      (update-in [:ContactGroups] #(mapv contact-info-to-array %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public Migration Interface

(defn migrate-umm
  [context concept-type source-version dest-version data]
  (if (= source-version dest-version)
    data
    ;; Migrating across versions is just reducing over the discrete steps between each version.
    (reduce (fn [data [v1 v2]]
              (migrate-umm-version context data concept-type v1 v2))
            data
            (version-steps source-version dest-version))))

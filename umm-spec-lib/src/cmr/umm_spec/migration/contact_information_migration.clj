(ns cmr.umm-spec.migration.contact-information-migration
  "Contains helper functions for migrating between different versions of UMM contact information"
  (:require [cmr.umm-spec.util :as u]))

(defn first-contact-info
  "Update the array of contact infos to be a single instance using the first contact info in the list"
  [c]
  (update c :ContactInformation first))

(defn contact-info-to-array
  "Update the contact info field to be an array. If contact info is nil, leave it as nil."
  [c]
  (if (some? (:ContactInformation c))
   (assoc c :ContactInformation [(:ContactInformation c)])
   c))

(defn update-data-center-contact-info-to-array
  "Update data center contact infos to be arrays - this includes the contact info on the
   data center, the contact info inside each contact person and each contact group"
  [data-center]
  (-> data-center
      (contact-info-to-array)
      (update :ContactPersons #(mapv contact-info-to-array %))
      (update :ContactGroups #(mapv contact-info-to-array %))))

(defn update-data-center-contact-info
  "Update data center contact infos to be a single instance - this includes the contact info on the
   data center, the contact info inside each contact person and each contact group"
  [data-center]
  (-> data-center
      first-contact-info
      (update :ContactPersons #(mapv first-contact-info %))
      (update :ContactGroups #(mapv first-contact-info %))))

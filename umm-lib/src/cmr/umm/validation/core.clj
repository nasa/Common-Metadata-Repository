(ns cmr.umm.validation.core
  (:require [bouncer.core :as b]
            [bouncer.validators :as v]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

(def spatial-coverage-validations
  "Defines spatial coverage validations for collections."
  {
   ;; temporary made up example
   ;;:granule-spatial-representation [v/required]
   })

(def collection-validations
  "Defines validations for collections"
  {

   :product-specific-attributes vu/unique-by-name-validator

   ;; Example of how you would
   ;:spatial-coverage spatial-coverage-validations

   ;; Temporary example of how you would use multiple validations
   ;;:access-value [v/required v/number]

   })

(def granule-validations
  "Defines validations for granules"
  {
   ;; TODO this is a temporary validation. There must be at least one validation or else bouncer fails.
   :access-value v/number
   })

(def umm-validations
  "A list of validations by type"
  {UmmCollection collection-validations
   UmmGranule granule-validations})

(defn validate
  "Validates the umm record returning a list of error messages appropriate for the given metadata
  format and concept type. Returns an empty sequence if it is valid."
  [metadata-format umm]
  (vu/perform-validation metadata-format umm (umm-validations (type umm))))


(comment

  (validate :echo10
            :collection
            (c/map->UmmCollection
              {:access-value "f"
               :product-specific-attributes [{:name "foo"}
                                             {:name "foo"}
                                             {:name "bar"}]}))


  (validate :dif :collection (c/map->UmmCollection {}))

  )
(ns cmr.umm.granule
  "Defines the UMM Granule record. TODO - add granule info to UMM Overview document.")

(defrecord CollectionRef
  [
   ;; maps to  Granule/Collection/DataSetId in echo granule schema
   entry-title

   ;; maps to Granule/Collection/ShortName
   short-name

   ;;  maps to Granule/Collection/VersionId
   version-id
   ])

(defrecord UmmGranule
  [
   ;; maps to Granule/GranuleUR in echo granule schema
   granule-ur

   ;; granule parent
   collection-ref
   ])

(defn collection-ref
  ([entry-title]
   (map->CollectionRef {:entry-title entry-title}))
  ([short-name version-id]
   (map->CollectionRef {:short-name short-name :version-id version-id})))
(ns cmr.umm.granule
  "Defines the UMM Granule record. TODO - add granule info to UMM Overview document.")

(defrecord CollectionRef
  [
   ;; maps to  Granule/Collection/DataSetId in echo granule schema
   entry-id

   ;; maps to Granule/Collection/ShortName
   short-name

   ;;  maps to Granule/Collection/VersionId
   version-id
  ])

(defrecord UmmEchoGranule
  [
   ;; maps to Granule/GranuleUR in echo granule schema
   granule-ur

   ;; granule parent
   collection-ref
   ])

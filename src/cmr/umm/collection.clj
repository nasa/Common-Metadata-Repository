(ns cmr.umm.collection
  "Defines the UMM Collection record. See the UMM Overview document for more information on the breakdown.")

(defrecord Product
  [
   short-name
   long-name
   version-id
  ])

(defrecord UmmCollection
  [
   ;; A combination of shortname and version id
   ;; TODO how do we combine them? I've emailed Katie for clarification.
   entry-id

   ;; The dataset-id in ECHO10
   entry-title

   ;; Refers to a Product
   product
   ])


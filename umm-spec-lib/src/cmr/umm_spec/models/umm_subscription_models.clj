;; WARNING: This file was generated from umm-sub-json-schema.json. Do not manually modify.
(ns cmr.umm-spec.models.umm-subscription-models
   "Defines UMM-Sub clojure records."
 (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord UMM-Sub
  [
   ;; The name of a subscription.
   Name

   ;;  The type of a subscription.
   Type

   ;; The userid of the subscriber.
   SubscriberId

   ;; The email address of the subscriber.
   EmailAddress

   ;; The collection concept id of the granules subscribed.
   CollectionConceptId

   ;; The search query for the granules that matches the subscription.
   Query

   ;; Requires the user to add in schema information into every service record. It includes the
   ;; schema's name, version, and URL location. The information is controlled through enumerations
   ;; at the end of this schema.
   MetadataSpecification
  ])
(record-pretty-printer/enable-record-pretty-printing UMM-Sub)

;; This object requires any metadata record that is validated by this schema to provide information
;; about the schema.
(defrecord MetadataSpecificationType
  [
   ;; This element represents the URL where the schema lives. The schema can be downloaded.
   URL

   ;; This element represents the name of the schema.
   Name

   ;; This element represents the version of the schema.
   Version
  ])
(record-pretty-printer/enable-record-pretty-printing MetadataSpecificationType)

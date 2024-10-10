(ns cmr.umm-spec.test.migration.version.subscription
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.umm-spec.migration.version.core :as vm]))

;; All migrations in this file will migrate variables in this way
(def migrate-subscription (partial vm/migrate-umm {} :subscription))

(def granule-subscription-concept-1_0
  {:Name "subscription-1"
   :SubscriberId "subscriber-id-1"
   :EmailAddress "email-address@cmr.gov"
   :CollectionConceptId "C1200000005-PROV1"
   :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"})

(def granule-subscription-concept-1_1
  {:Name "subscription-1"
   :Type "granule"
   :SubscriberId "subscriber-id-1"
   :EmailAddress "email-address@cmr.gov"
   :CollectionConceptId "C1200000005-PROV1"
   :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
   :MetadataSpecification
   {:URL "https://cdn.earthdata.nasa.gov/umm/subscription/v1.1"
    :Name "UMM-Sub"
    :Version "1.1"}})

(def granule-subscription-concept-1_1_1
  {:Name "subscription-1"
   :Type "granule"
   :SubscriberId "subscriber-id-1"
   :EmailAddress "email-address@cmr.gov"
   :CollectionConceptId "C1200000005-PROV1"
   :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
   :EndPoint "arn:aws:sqs:us-east-1:1234455667:TestSNSQueue"
   :Mode ["New" "Update"]
   :Method "ingest"
   :MetadataSpecification
   {:URL "https://cdn.earthdata.nasa.gov/umm/subscription/v1.1.1"
    :Name "UMM-Sub"
    :Version "1.1.1"}})

(deftest migrate-1_0->1_1
  (is (= granule-subscription-concept-1_1
         (migrate-subscription "1.0" "1.1" granule-subscription-concept-1_0))))

(deftest migrate-1_1->1_0
  (is (thrown? clojure.lang.ExceptionInfo (migrate-subscription "1.1" "1.0" granule-subscription-concept-1_1))))

(deftest migrate-1_1->1_1_1
  (is (= (dissoc granule-subscription-concept-1_1_1 :Mode :EndPoint :Method)
         (migrate-subscription "1.1" "1.1.1" granule-subscription-concept-1_1))))

(deftest migrate-1_1_1->1_1
  (is (= granule-subscription-concept-1_1
         (migrate-subscription "1.1.1" "1.1" granule-subscription-concept-1_1_1))))

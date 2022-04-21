(ns cmr.umm-spec.test.migration.version.subscription
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

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

(deftest migrate-1_0->1_1
  (is (= granule-subscription-concept-1_1
         (migrate-subscription "1.0" "1.1" granule-subscription-concept-1_0))))

(deftest migrate-1_1->1_0
  (is (thrown? clojure.lang.ExceptionInfo (migrate-subscription "1.1" "1.0" granule-subscription-concept-1_1))))
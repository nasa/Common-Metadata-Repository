(ns cmr.umm-spec.test.migration.version.variable
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

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:variable ["1.0" "1.1"]}}
    (is (= [] (#'vm/version-steps :variable "1.1" "1.1")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :variable "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :variable "1.1" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-var-generator)
            dest-version (gen/elements (v/versions :variable))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :variable dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (nil?
       (:Services
        (vm/migrate-umm {} :variable "1.0" "1.1"
                        {:ServiceTypes ["OPeNDAP"] :Visualizable false :Subsettable false})))))

(deftest migrate-1_1-down-to-1_0
  (is (nil?
       (:Services
        (vm/migrate-umm {} :variable "1.1" "1.0"
                        {})))))

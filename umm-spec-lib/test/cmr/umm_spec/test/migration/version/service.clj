(ns cmr.umm-spec.test.migration.version.service
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

(def service-concept-1-0
  {:RelatedURL {:URLContentType "CollectionURL"
                :Description "OPeNDAP Service"
                :Type "GET SERVICE"
                :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"},
   :Coverage {:Type "SPATIAL_POINT"}
   :AccessConstraints ["TEST"]})

(def service-concept-1-1
  {:Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType "SPATIAL_POINT"}}
   :AccessConstraints "TEST"
   :RelatedURLs [{:URLContentType "CollectionURL"
                  :Description "OPeNDAP Service"
                  :Type "GET SERVICE"
                  :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}]})

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:service ["1.0" "1.1"]}}
    (is (= [] (#'vm/version-steps :service "1.1" "1.1")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :service "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :service "1.1" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-var-generator)
            dest-version (gen/elements (v/versions :service))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :service dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (= service-concept-1-1
         (vm/migrate-umm {} :service "1.0" "1.1"
           {:Coverage {:Type "SPATIAL_POINT"}
            :AccessConstraints ["TEST"]
            :RelatedURL {:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                         :Type "GET SERVICE"
                         :URLContentType "CollectionURL"}}))))

(deftest migrate-1_1-down-to-1_0
  (is (= service-concept-1-0
         (vm/migrate-umm {} :service "1.1" "1.0"
           {:RelatedURLs [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                           :Type "GET SERVICE"
                           :URLContentType "CollectionURL"}]
            :AccessConstraints "TEST"
            :Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType
                                               "SPATIAL_POINT"}
                       :CoverageTemporalExtent {:CoverageTemporalExtentTypeType
                                                "TIME_STAMP"}}}))))

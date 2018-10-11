(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.umm-spec.test.umm-g.generators :as generators]
   [cmr.umm-spec.test.umm-g.sanitizer :as sanitizer]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-lib-g]
   [cmr.umm.umm-spatial :as umm-s]))

(defn- umm->expected-parsed
  "Modifies the UMM record for testing UMM-G. As the fields are added to UMM-G support for
  parsing and generating in cmr.umm-spec.umm-g.granule, the fields should be taken off the
  excluded list below."
  [gran]
  (-> gran
      (dissoc :orbit-calculated-spatial-domains)
      umm-lib-g/map->UmmGranule))

(defspec generate-granule-is-valid-umm-g-test 100
  (for-all [granule generators/umm-g-granules]
    (let [granule (sanitizer/sanitize-granule granule)
          metadata (core/generate-metadata {} granule :umm-json)]
      (empty? (core/validate-metadata :granule :umm-json metadata)))))

(defspec generate-and-parse-umm-g-granule-test 100
  (for-all [granule generators/umm-g-granules]
    (let [granule (sanitizer/sanitize-granule granule)
          umm-g-metadata (core/generate-metadata {} granule :umm-json)
          parsed (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          ;; change the geometries to a set for comparison
          parsed (update-in parsed [:spatial-coverage :geometries] set)
          expected-parsed (umm->expected-parsed granule)
          ;; change the geometries to a set for comparison
          expected-parsed (update-in expected-parsed [:spatial-coverage :geometries] set)]
      (= parsed expected-parsed))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.4/GranuleExample.json"))))

(def expected-granule
  (umm-lib-g/map->UmmGranule
   {:granule-ur "Unique_Granule_UR"
    :data-provider-timestamps (umm-lib-g/map->DataProviderTimestamps
                               {:insert-time (dtp/parse-datetime "2018-08-19T01:00:00Z")
                                :update-time (dtp/parse-datetime "2018-09-19T02:00:00Z")
                                :delete-time (dtp/parse-datetime "2030-08-19T03:00:00Z")})
    :collection-ref (umm-lib-g/map->CollectionRef
                     {:entry-title nil
                      :short-name "CollectionShortName"
                      :version-id "Version"})
    :access-value 42
    :data-granule (umm-lib-g/map->DataGranule
                   {:day-night "UNSPECIFIED"
                    :producer-gran-id "SMAP_L3_SM_P_20150407_R13080_001.h5"
                    :production-date-time (dtp/parse-datetime "2018-07-19T12:01:01.000Z")
                    :size 23})
    :temporal (umm-lib-g/map->GranuleTemporal
               {:range-date-time (umm-c/map->RangeDateTime
                                  {:beginning-date-time (dtp/parse-datetime "2018-07-17T00:00:00.000Z")
                                   :ending-date-time (dtp/parse-datetime "2018-07-17T23:59:59.999Z")})})
    :platform-refs [(umm-lib-g/map->PlatformRef
                     {:short-name "Aqua"
                      :instrument-refs
                      [(umm-lib-g/map->InstrumentRef
                        {:short-name "AMSR-E"
                         :characteristic-refs [(umm-lib-g/map->CharacteristicRef
                                                {:name "InstrumentCaracteristicName1",
                                                 :value "150"})
                                               (umm-lib-g/map->CharacteristicRef
                                                {:name "InstrumentCaracteristicName2",
                                                 :value "22F"})]
                         :sensor-refs [(umm-lib-g/map->SensorRef
                                        {:short-name "AMSR-E_ChildInstrument",
                                         :characteristic-refs
                                         [(umm-lib-g/map->CharacteristicRef
                                           {:name "ChildInstrumentCharacteristicName3",
                                            :value "250"})]})]
                         :operation-modes ["Mode1" "Mode2"]})]})]
    :cloud-cover 60
    :project-refs ["Campaign1" "Campaign2" "Campaign3"]
    :two-d-coordinate-system (umm-lib-g/map->TwoDCoordinateSystem
                              {:name "MODIS Tile EASE"
                               :start-coordinate-1 -100
                               :end-coordinate-1 -50
                               :start-coordinate-2 50
                               :end-coordinate-2 100})
    :product-specific-attributes [(umm-lib-g/map->ProductSpecificAttributeRef
                                    {:name "AdditionalAttribute1 Name1"
                                     :values
                                     ["AdditionalAttribute1 Value3"
                                      "AdditionalAttribute1 Value4"]})
                                  (umm-lib-g/map->ProductSpecificAttributeRef
                                    {:name "EVI1KM16DAYQCLASSPERCENTAGE"
                                     :values
                                     ["EVI1KM16DAYQCLASSPERCENTAGE Value5"
                                      "EVI1KM16DAYQCLASSPERCENTAGE Value6"]})
                                  (umm-lib-g/map->ProductSpecificAttributeRef
                                    {:name "QAFRACTIONGOODQUALITY"
                                     :values
                                     ["QAFRACTIONGOODQUALITY Value7"
                                      "QAFRACTIONGOODQUALITY Value8"]})
                                  (umm-lib-g/map->ProductSpecificAttributeRef
                                    {:name "QAFRACTIONNOTPRODUCEDCLOUD"
                                     :values
                                     ["QAFRACTIONNOTPRODUCEDCLOUD Value9"
                                      "QAFRACTIONNOTPRODUCEDCLOUD Value10"]})]
    :spatial-coverage (umm-lib-g/map->SpatialCoverage
                       {:geometries
                        [(p/point -77 88) (p/point 10 10)
                         (mbr/mbr -180 85.04450225830078 180 -85.04450225830078)
                         (poly/polygon
                          [(umm-s/ring
                            [(p/point -10 -10) (p/point 10 -10) (p/point 10 10) (p/point -10 10) (p/point -10 -10)])
                           (umm-s/ring
                            [(p/point -5 -5) (p/point -1 -5) (p/point -1 -1) (p/point -5 -1) (p/point -5 -5)])
                           (umm-s/ring
                            [(p/point 0 0) (p/point 5 0) (p/point 5 5) (p/point 0 5) (p/point 0 0)])])
                         (l/line-string [(p/point -100 -70) (p/point -88 -66)])]})
    :measured-parameters [(umm-lib-g/map->MeasuredParameter
                           {:parameter-name "Parameter Name"
                            :qa-stats (umm-lib-g/map->QAStats
                                       {:qa-percent-missing-data 10
                                        :qa-percent-out-of-bounds-data 20
                                        :qa-percent-interpolated-data 30
                                        :qa-percent-cloud-cover 40})
                            :qa-flags (umm-lib-g/map->QAFlags
                                       {:automatic-quality-flag "Passed"
                                        :automatic-quality-flag-explanation "Automatic Quality Flag Explanation"
                                        :operational-quality-flag "Passed"
                                        :operational-quality-flag-explanation "Operational Quality Flag Explanation"
                                        :science-quality-flag "Passed"
                                        :science-quality-flag-explanation "Science Quality Flag Explanation"})})]
    :related-urls [(umm-c/map->RelatedURL
                    {:type "GET DATA"
                     :url "https://daac.ornl.gov/daacdata/islscp_ii/vegetation/erbe_albedo_monthly_xdeg/data/erbe_albedo_1deg_1986.zip"
                     :description "This link provides direct download access to the granule."
                     :mime-type "application/zip"
                     :title "This link provides direct download access to the granule."
                     :size 395.673})
                   (umm-c/map->RelatedURL
                    {:type "VIEW RELATED INFORMATION"
                     :sub-type "USER'S GUIDE"
                     :url "https://daac.ornl.gov/ISLSCP_II/guides/erbe_albedo_monthly_xdeg.html"
                     :description "ORNL DAAC Data Set Documentation"
                     :mime-type "text/html"
                     :title "ORNL DAAC Data Set Documentation"})
                   (umm-c/map->RelatedURL
                    {:type "GET RELATED VISUALIZATION"
                     :url "https://webmap.ornl.gov/sdat/pimg/957_1.png"
                     :description "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :mime-type "image/png"
                     :title "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :size 10})]}))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))

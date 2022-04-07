(ns cmr.umm-spec.test.umm-g.expected-util
  "Namespace containing functions for determining expected umm-lib granule records
   used in testing."
  (:require
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.util :as util]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.umm.umm-spatial :as umm-s]
   [cmr.umm-spec.umm-g.measured-parameters :as measured-parameters]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-lib-g]))

(defn- expected-qa-flags
  "Converts generated qa-flags to what is expected by using the sanitized flag values."
  [qa-flags]
  (when-not (empty? (util/remove-nil-keys qa-flags))
    (let [{:keys [automatic-quality-flag
                  automatic-quality-flag-explanation
                  operational-quality-flag
                  operational-quality-flag-explanation
                  science-quality-flag
                  science-quality-flag-explanation]}
          qa-flags]
      (umm-lib-g/map->QAFlags
       {:automatic-quality-flag (measured-parameters/sanitize-quality-flag
                                 :automatic-quality-flag
                                 automatic-quality-flag)
        :automatic-quality-flag-explanation automatic-quality-flag-explanation
        :operational-quality-flag (measured-parameters/sanitize-quality-flag
                                   :operational-quality-flag
                                   operational-quality-flag)
        :operational-quality-flag-explanation operational-quality-flag-explanation
        :science-quality-flag (measured-parameters/sanitize-quality-flag
                               :science-quality-flag
                               science-quality-flag)
        :science-quality-flag-explanation science-quality-flag-explanation}))))

(defn- expected-measured-parameter
  "Updates qa-flags for given measured paramater map."
  [measured-parameter]
  (umm-lib-g/map->MeasuredParameter
   (update measured-parameter :qa-flags expected-qa-flags)))

(defn umm->expected-parsed
  "Modifies the UMM record for testing UMM-G. As the fields are added to UMM-G support for
  parsing and generating in cmr.umm-spec.umm-g.granule, the fields should be taken off the
  excluded list below."
  [gran]
  (-> gran
      (util/update-in-each [:measured-parameters] expected-measured-parameter)
      (update-in [:spatial-coverage :geometries] set)
      ;; Need to remove the possible duplicate entries in crid-ids and feature-ids
      ;; because Identifiers in UMM-G v1.4 can't contain any duplicates.
      (as-> updated-umm (if (get-in updated-umm [:data-granule :crid-ids])
                          (update-in updated-umm [:data-granule :crid-ids] distinct)
                          updated-umm))
      (as-> updated-umm (if (get-in updated-umm [:data-granule :feature-ids])
                          (update-in updated-umm [:data-granule :feature-ids] distinct)
                          updated-umm))
      (as-> updated-umm (if (:data-granule updated-umm)
                          (update-in updated-umm [:data-granule :day-night] #(if % % "UNSPECIFIED"))
                          updated-umm))
      (as-> updated-umm (if (:project-refs updated-umm)
                          (update updated-umm :project-refs #(conj % umm-spec-util/not-provided))
                          updated-umm))
      umm-lib-g/map->UmmGranule))

(def expected-sample-granule
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
    :access-value 42.0
    :data-granule (umm-lib-g/map->DataGranule
                   {:day-night "UNSPECIFIED"
                    :producer-gran-id "SMAP_L3_SM_P_20150407_R13080_001.h5"
                    :crid-ids ["CRIDValue"]
                    :feature-ids ["FeatureIdValue1" "FeatureIdValue2"]
                    :production-date-time (dtp/parse-datetime "2018-07-19T12:01:01.000Z")
                    :size-in-bytes 23552
                    :checksum (umm-lib-g/map->Checksum
                                {:value "E51569BF48DD0FD0640C6503A46D4753"
                                 :algorithm "MD5"})
                    :size-unit "MB"
                    :size 0.023
                    :format "ZIP"
                    :files [(umm-lib-g/map->File
                             {:name "GranuleFileName1"
                              :size 10
                              :size-unit "KB"
                              :format "NETCDF-4"
                              :mime-type "application/x-netcdf"
                              :format-type "Native"
                              :checksum (umm-lib-g/map->Checksum
                                         {:value "E51569BF48DD0FD0640C6503A46D4754"
                                          :algorithm "MD5"})})
                            (umm-lib-g/map->File
                             {:name "GranuleFileName2"
                              :size 1
                              :size-unit "KB"
                              :format "ASCII"
                              :mime-type "text/plain"
                              :format-type "NA"})]})
    :pge-version-class (umm-lib-g/map->PGEVersionClass
                        {:pge-name "A PGE Name"
                         :pge-version "6.0.27"})
    :temporal (umm-lib-g/map->GranuleTemporal
               {:range-date-time (umm-c/map->RangeDateTime
                                  {:beginning-date-time (dtp/parse-datetime "2018-07-17T00:00:00.000Z")
                                   :ending-date-time (dtp/parse-datetime "2018-07-17T23:59:59.999Z")})})
    :orbit-calculated-spatial-domains [(umm-lib-g/map->OrbitCalculatedSpatialDomain
                                        {:orbital-model-name "OrbitalModelName"
                                         :start-orbit-number 99263
                                         :stop-orbit-number 99263
                                         :equator-crossing-longitude 88.92
                                         :equator-crossing-date-time (dtp/parse-datetime "2018-08-16T16:22:21.000Z")})]
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
    :cloud-cover 60.0
    :project-refs ["Project1" "Project2" "Campaign1" "Campaign2" "Campaign3"]
    :two-d-coordinate-system (umm-lib-g/map->TwoDCoordinateSystem
                              {:name "MODIS Tile EASE"
                               :start-coordinate-1 -100.0
                               :end-coordinate-1 -50.0
                               :start-coordinate-2 50.0
                               :end-coordinate-2 100.0})
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
                                       {:qa-percent-missing-data 10.0
                                        :qa-percent-out-of-bounds-data 20.0
                                        :qa-percent-interpolated-data 30.0
                                        :qa-percent-cloud-cover 40.0})
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
                     :format "ZIP"
                     :mime-type "application/zip"
                     :title "This link provides direct download access to the granule."
                     :size 395.673})
                   (umm-c/map->RelatedURL
                    {:type "VIEW RELATED INFORMATION"
                     :sub-type "USER'S GUIDE"
                     :url "https://daac.ornl.gov/ISLSCP_II/guides/erbe_albedo_monthly_xdeg.html"
                     :description "ORNL DAAC Data Set Documentation"
                     :format "HTML"
                     :mime-type "text/html"
                     :title "ORNL DAAC Data Set Documentation"})
                   (umm-c/map->RelatedURL
                    {:type "GET RELATED VISUALIZATION"
                     :url "https://webmap.ornl.gov/sdat/pimg/957_1.png"
                     :description "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :format "PNG"
                     :mime-type "image/png"
                     :title "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :size 10})
                   (umm-c/map->RelatedURL
                    {:type "GET DATA VIA DIRECT ACCESS"
                     :url "s3://aws.com/asdf/asdf/dataproduct.nc"
                     :description "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :format "NETCDF-4"
                     :mime-type "application/x-netcdf"
                     :title "ISLSCP II EARTH RADIATION BUDGET EXPERIMENT (ERBE) MONTHLY ALBEDO, 1986-1990"
                     :size 1000})
                   (umm-c/map->RelatedURL
                    {:type "EXTENDED METADATA"
                     :sub-type "DMR++"
                     :url "s3://aws.com/bucket-name/dataproduct.data"
                     :description "DMR++ Description"
                     :format "NETCDF-4"
                     :mime-type "application/x-netcdf"
                     :title "DMR++ Description"
                     :size 1000})]}))

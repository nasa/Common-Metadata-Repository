(ns cmr.umm-spec.test.umm-json
  (:require
   [clj-time.core :as time]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext :refer [checking]]
   [cmr.common.util :as util]
   [cmr.umm-spec.json-schema :as json-schema]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.umm-spec.versioning :as umm-spec-versioning]))

(def minimal-example-umm-c-record
  "This is the minimum valid UMM-C."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URL "http://google.com"
                                                 :URLContentType "DistributionURL"
                                                 :Type "GET DATA"})]
     :DataCenters [umm-spec-util/not-provided-data-center]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

     :ShortName "short"
     :Version "V1"
     :EntryTitle "The entry title V5"
     :CollectionProgress "COMPLETE"
     :DataDates [(umm-cmn/map->DateType {:Date (time/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2012)]})]
     :DOI (umm-cmn/map->DoiType {:DOI "10.5678/TestDOI"})
     :MetadataSpecification (umm-c/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})}))

(def contact-group-example-umm-c-record
  "This is the minimum valid UMM-C with contact groups."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URL "http://google.com"
                                                 :URLContentType "DistributionURL"
                                                 :Type "GET DATA"})]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

     :ShortName "short"
     :Version "V1"
     :EntryTitle "The entry title V5"
     :CollectionProgress "COMPLETE"
     :DataDates [(umm-cmn/map->DateType {:Date (time/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2012)]})]
     :DOI (umm-cmn/map->DoiType {:DOI "10.5678/TestDOI"})
     :DataCenters [umm-spec-util/not-provided-data-center]
     :ContactGroups [(umm-cmn/map->ContactGroupType {:Roles ["Investigator"]
                                                     :GroupName "ABC"})]
     :MetadataSpecification (umm-c/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})}))

(defn- remove-get-service-and-get-data-nils
  "Removes nil values in GetService and GetData added by json->umm to RelatedUrls,
  this is needed to prevent failures caused by json->umm parser. It will add in :GetService
  and :GetData that shouldn't be there because it is calling the constructor for RelatedUrlType defrecord.
  So we remove them because they should only be present when URLContentType = DistributionURL and
  Type = GET DATA or GET SERVICE."
  [umm]
  (let [remove-nils (fn [ru]
                      (let [get-service (:GetService ru)
                            get-data (:GetData ru)
                            url-content-type (:URLContentType ru)
                            type (:Type ru)]
                        (cond
                          (and (nil? get-data) (nil? get-service))
                          (dissoc ru :GetService :GetData)
                          (and (= "DistributionURL" url-content-type) (= "GET SERVICE" type) (nil? get-service))
                          (dissoc ru :GetData)
                          (and (= "DistributionURL" url-content-type) (= "GET DATA" type) (nil? get-data))
                          (dissoc ru :GetService)
                          :else ru)))]
    (-> umm
        (util/update-in-all [:ContactGroups :ContactInformation :RelatedUrls] remove-nils)
        (util/update-in-all [:ContactPersons :ContactInformation :RelatedUrls] remove-nils)
        (util/update-in-all [:DataCenters :ContactGroups :ContactInformation :RelatedUrls] remove-nils)
        (util/update-in-all [:DataCenters :ContactInformation :RelatedUrls] remove-nils)
        (util/update-in-all [:DataCenters :ContactPersons :ContactInformation :RelatedUrls] remove-nils)
        (util/update-in-all [:RelatedUrls] remove-nils))))

(defn- remove-related-urls
  "Removes all of the RelatedUrls which are all nil under ContactGroups and
   ContactPersons/ContactInformation."
  [umm]
  (-> umm
      (util/update-in-all [:ContactGroups :ContactInformation] #(dissoc % :RelatedUrls))
      (util/update-in-all [:ContactPersons :ContactInformation] #(dissoc % :RelatedUrls))))

;; This only tests a minimum example record for now. We need to test with larger more complicated
;; records. We will do this as part of CMR-1929

(deftest generate-and-parse-umm-c-json
  (testing "minimal umm-c record"
    (let [json (umm-json/umm->json minimal-example-umm-c-record)
          _ (is (empty? (json-schema/validate-umm-json json :collection)))
          parsed (umm-json/json->umm {} :collection json)]
      (is (= minimal-example-umm-c-record parsed)))))

(deftest generate-and-parse-contact-group-umm-c-json
  (testing "contact group umm-c record. We add this test mostly for future when we add allOf
           support in umm-spec-lib to verify that validation is done correctly."
    (let [json (umm-json/umm->json contact-group-example-umm-c-record)
          _ (is (empty? (json-schema/validate-umm-json json :collection)))
          parsed (umm-json/json->umm {} :collection json)]
      (is (= contact-group-example-umm-c-record parsed)))))

(deftest all-umm-c-records
  (checking "all umm-c records" 100
    [umm-c-record (gen/no-shrink umm-gen/umm-c-generator)]
    (let [json (umm-json/umm->json umm-c-record)
          _ (is (empty? (json-schema/validate-umm-json json :collection)))
          parsed (umm-json/json->umm {} :collection json)
          parsed (remove-get-service-and-get-data-nils parsed)
          parsed (update-in parsed [:UseConstraints] util/remove-nil-keys)
          umm-c-record (update-in parsed [:UseConstraints] util/remove-nil-keys)]
      (is (= umm-c-record parsed)))))

(deftest all-umm-s-records
  (checking "all umm-s records" 100
    [umm-s-record (gen/no-shrink umm-gen/umm-s-generator)]
    ;; For some reason :RelatedUrls nil is being generated eventhough it is not
    ;; part of the schema. Removing it.
    (let [umm-s-record (remove-related-urls umm-s-record)
          json (umm-json/umm->json umm-s-record)
          _ (is (empty? (json-schema/validate-umm-json json :service)))
          parsed (umm-json/json->umm {} :service json)]
      (is (= umm-s-record parsed)))))

(deftest all-umm-t-records
  (checking "all umm-t records" 100
    [umm-t-record (gen/no-shrink umm-gen/umm-t-generator)]
    (let [json (umm-json/umm->json umm-t-record)
          _ (is (empty? (json-schema/validate-umm-json json :tool)))
          parsed (umm-json/json->umm {} :tool json)]
      (is (= umm-t-record parsed)))))

(deftest all-umm-sub-records
  (checking "all umm-sub records" 100
    [umm-sub-record (gen/no-shrink umm-gen/umm-sub-generator)]
    (let [json (umm-json/umm->json umm-sub-record)
          _ (is (empty? (json-schema/validate-umm-json json :subscription)))
          parsed (umm-json/json->umm {} :subscription json)]
      (is (= umm-sub-record parsed)))))

(deftest all-umm-var-records
  (checking "all umm-var records" 100
    [umm-var-record (gen/no-shrink umm-gen/umm-var-generator)]
    (let [json (umm-json/umm->json umm-var-record)
          _ (is (empty? (json-schema/validate-umm-json json :variable)))
          parsed (umm-json/json->umm {} :variable json)]
      (is (= umm-var-record parsed)))))

(deftest validate-json-with-extra-fields
  (let [json (umm-json/umm->json (assoc minimal-example-umm-c-record :foo "extra"))]
    (is (= ["#: extraneous key [foo] is not permitted"]
           (json-schema/validate-umm-json json :collection)))))

(deftest json-schema-coercion
  (is (= (json-schema/parse-umm-c
          {:EntryTitle "an entry title"
           :Abstract "A very abstract collection"
           :DataLanguage "eng"
           :TemporalExtents [{:TemporalRangeType "temp range"
                              :PrecisionOfSeconds "3"
                              :EndsAtPresentFlag "false"
                              :RangeDateTimes [{:BeginningDateTime "2000-01-01T00:00:00.000Z"
                                                :EndingDateTime "2001-01-01T00:00:00.000Z"}
                                               {:BeginningDateTime "2002-01-01T00:00:00.000Z"
                                                :EndingDateTime "2003-01-01T00:00:00.000Z"}]}]})
         (umm-c/map->UMM-C
          {:EntryTitle "an entry title"
           :Abstract "A very abstract collection"
           :DataLanguage "eng"
           :TemporalExtents [(umm-cmn/map->TemporalExtentType
                              {:TemporalRangeType "temp range"
                               :PrecisionOfSeconds 3
                               :EndsAtPresentFlag false
                               :RangeDateTimes [(umm-cmn/map->RangeDateTimeType
                                                 {:BeginningDateTime (time/date-time 2000)
                                                  :EndingDateTime (time/date-time 2001)})
                                                (umm-cmn/map->RangeDateTimeType
                                                 {:BeginningDateTime (time/date-time 2002)
                                                  :EndingDateTime (time/date-time 2003)})]})]}))))

(deftest json-schema-parsing-errors

  (testing "UMM-C with parsing errors"
    (let [umm-c (json-schema/parse-umm-c
                 {:SpatialExtent {:OrbitParameters {:NumberOfOrbits "foo"
                                                    :SwathWidth "123"}}
                                 :TemporalExtents [{:SingleDateTimes ["nonsense"
                                                                      "2000-01-01T00:00:00.000Z"]}]})
          orbit-params (-> umm-c :SpatialExtent :OrbitParameters)]
      (is (= 123.0 (:SwathWidth orbit-params)))
      (is (= {:NumberOfOrbits "Could not parse number value: foo"} (:_errors orbit-params)))
      (is (= [nil (time/date-time 2000 1 1)]
             (-> umm-c :TemporalExtents first :SingleDateTimes)))
      (is (= {:SingleDateTimes ["Could not parse date-time value: nonsense" nil]}
             (-> umm-c :TemporalExtents first :_errors)))))

  (testing "UMM-C with no parsing errors"
    (let [umm-c (json-schema/parse-umm-c
                 {:SpatialExtent {:OrbitParameters {:NumberOfOrbits "30"
                                                    :SwathWidth "123"}}
                                 :TemporalExtents [{:SingleDateTimes ["2000-01-01T00:00:00.000Z"
                                                                      "2005-01-01T00:00:00.000Z"]}]})
          orbit-params (-> umm-c :SpatialExtent :OrbitParameters)]
      (is (= 123.0 (:SwathWidth orbit-params)))
      (is (= nil (:_errors orbit-params)))
      (is (= [(time/date-time 2000) (time/date-time 2005)]
             (-> umm-c :TemporalExtents first :SingleDateTimes)))
      (is (= nil
             (-> umm-c :TemporalExtents first :_errors))))))

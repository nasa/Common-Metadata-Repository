(ns cmr.system-int-test.ingest.collection-misc-validation-test
  "CMR Ingest miscellaneous validation integration tests"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as data-core]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (data-umm-c/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (data-core/ingest provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-conflict
  [coll-attributes errors]
  (let [collection (assoc (data-umm-c/collection coll-attributes) :native-id (:native-id coll-attributes))
        response (data-core/ingest "PROV1" collection {:allow-failure? true})]
    (is (= {:status 409
            :errors errors}
           (select-keys response [:status :errors])))))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest spatial-with-no-representation
  ;; ISO19115 allows you to ingest metadata with no spatial coordinate reference but have spatial
  ;; points. We should reject it because UMM requires a spatial coordinate reference.
  (testing "A collection with spatial data but no representation should fail ingest validation"
    (let [bad-metadata (slurp (io/resource
                                "iso-samples/iso-spatial-data-missing-coordinate-system.iso19115"))
          {:keys [status warnings]}
          (ingest/ingest-concept (ingest/concept :collection "PROV1" "foo" :iso19115 bad-metadata))]

      (is (= 201 status))
      (is (str/includes? warnings
           "[:SpatialExtent] Granule Spatial Representation must be supplied.")))))

(deftest duplicate-entry-title-test
  (testing "same entry-title and native-id across providers is valid"
    (assert-valid
      {:EntryTitle "ET-1" :Version "V1" :concept-id "C1-PROV1" :native-id "Native1"})
    (assert-valid
      {:EntryTitle "ET-1" :Version "V2" :concept-id "C1-PROV2" :native-id "Native1" :provider-id "PROV2"}))

  (testing "entry-title must be unique for a provider"
    (assert-conflict
      {:EntryTitle "ET-1" :Version "V3" :concept-id "C2-PROV1" :native-id "Native2"}
      ["The Entry Title [ET-1] must be unique. The following concepts with the same entry title were found: [C1-PROV1]."])))

(deftest nil-version-test
  (testing "Collections with nil versions are rejected"
    (let [concept (data-umm-c/collection-concept {:Version nil} :iso19115)
          response (ingest/ingest-concept concept)]
      (is (= 201 
             (:status response))))))

(deftest field-exceeding-maxlength-warnings
  (testing "Multiple warnings returned for the fields exceeding maxlength allowed"
    (let [collection (data-umm-c/collection-missing-properties-dif10
                       {:Platforms [(data-umm-cmn/platform {:ShortName (apply str (repeat 81 "x"))})]
                        :Purpose (apply str (repeat 12000 "y"))
                        :ProcessingLevel {:Id "1"}
                        :CollectionProgress "COMPLETE"})
          ingest-response (data-core/ingest "PROV1" collection {:format :dif10})
          validation-response (ingest/validate-concept (data-umm-c/collection-concept collection :dif10))]
      (is (some? (re-find #"#/Platforms/0/ShortName: expected maxLength: 80, actual: 81" (:warnings ingest-response))))
      (is (some? (re-find #"#/Platforms/0/ShortName: expected maxLength: 80, actual: 81" (:warnings validation-response))))
      (is (some? (re-find #"#/Purpose: expected maxLength: 10000, actual: 12000" (:warnings ingest-response))))
      (is (some? (re-find #"#/Purpose: expected maxLength: 10000, actual: 12000" (:warnings validation-response)))))))

(deftest error-messages-are-friendly
  (testing "Error messages don't contain regexes"
    ;; Include fields that will fail the regex pattern and ensure that the regexes
    ;; don't show up in the returned errors
    (let [platform-collection (data-umm-c/collection
                               {:Platforms [(data-umm-cmn/platform {:ShortName ""
                                                                    :LongName ""})]})
          data-center-collection (data-umm-c/collection
                                  {:DataCenters [(data-umm-cmn/data-center {:ShortName "asdf"
                                                                            :Roles ["ARCHIVER"]})]})
          science-keyword-collection (data-umm-c/collection
                                      {:ScienceKeywords
                                       [(data-umm-cmn/science-keyword
                                         {:VariableLevel1 ""
                                          :VariableLevel2 ""
                                          :VariableLevel3 ""
                                          :Category ""
                                          :Term ""
                                          :DetailedVariable ""
                                          :Topic ""})]})]
      (are3 [collection]
       (let [ingest-response (data-core/ingest-umm-spec-collection "PROV1" collection {:format :umm-json :allow-failure? true})
             validation-response (ingest/validate-concept (data-umm-c/collection-concept collection :umm-json))]
         (is (nil? (first (map #(re-find #"(ECMA|regex)" %) (:errors ingest-response)))))
         (is (nil? (first (map #(re-find #"(ECMA|regex)" %) (:errors validation-response)))))
         (is (some? (map #(re-find #"is an invalid format" %) (:errors ingest-response))))
         (is (some? (map #(re-find #"is an invalid format" %) (:errors validation-response)))))
       "Invalid platforms"
       platform-collection

       "Invalid Data Centers"
       data-center-collection

       "Invalid Science Keywords"
       science-keyword-collection)
      (testing "Explicitly test error messages - ShorName and LongName should fail the regex"
        (let [ingest-response (data-core/ingest-umm-spec-collection "PROV1" platform-collection {:format :umm-json :allow-failure? true})
              validation-response (ingest/validate-concept (data-umm-c/collection-concept platform-collection :umm-json))]
          (is (= (:errors ingest-response)
                 ["#/Platforms/0/LongName: expected minLength: 1, actual: 0"
                  "#/Platforms/0/LongName: string [] does not match pattern [\\w\\-&'()\\[\\]/.\"#$%\\^@!*+=,][\\w\\-&'()\\[\\]/.\"#$%\\^@!*+=, ]{0,1023}"
                  "#/Platforms/0/ShortName: expected minLength: 1, actual: 0"
                  "#/Platforms/0/ShortName: string [] does not match pattern [\\w\\-&'()\\[\\]/.\"#$%\\^@!*+=,][\\w\\-&'()\\[\\]/.\"#$%\\^@!*+=, ]{1,79}"]))
          (is (= (:status ingest-response) 400)))))))

(deftest multiple-warnings
 (testing "Schema and UMM-C validation warnings"
  (let [collection (data-umm-c/collection
                     {:DataCenters nil
                      :RelatedUrls [{:URL "htp://www.x.com"
                                     :URLContentType "DistributionURL"
                                     :Type "GET DATA"}]})
        ingest-response (data-core/ingest "PROV1" collection)
        validation-response (ingest/validate-concept (data-umm-c/collection-concept collection))]
    (is (some? (re-find #"#: required key \[DataCenters\] not found"  (:warnings ingest-response))))
    (is (some? (re-find #"#: required key \[DataCenters\] not found" (:warnings validation-response))))
    (is (some? (re-find #"\[:RelatedUrls 0 :URL\] \[htp://www.x.com\] is not a valid URL" (:warnings ingest-response))))
    (is (some? (re-find #"\[:RelatedUrls 0 :URL\] \[htp://www.x.com\] is not a valid URL" (:warnings validation-response))))
    (is (some? (re-find #"\[:RelatedUrls 0\] RelatedUrl does not have a description." (:warnings ingest-response))))
    (is (some? (re-find #"\[:RelatedUrls 0\] RelatedUrl does not have a description." (:warnings validation-response)))))))

(comment
  (ingest/delete-provider "PROV1")
  ;; Attempt to create race conditions by ingesting the same concept-id simultaneously. We expect
  ;; some requests to succeed while others return a 409.
  ;; If the race condition is reproduced you will see a message like:
  ;; 409 returned, Errors: [Conflict with existing concept-id [C1-PROV1] and revision-id [23]]
  (do
    (cmr.system-int-test.utils.dev-system-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})

    (doseq [_ (range 150)]
      (future (do (let [response (ingest/ingest-concept
                                   (data-umm-c/collection-concept
                                    {:concept-id "C1-PROV1"
                                     :native-id "Same Native ID"}))]
                    (when (= 409 (:status response))
                      (println "409 returned, Errors:" (:errors response)))))))))

(ns cmr.system-int-test.ingest.collection-misc-validation-test
  "CMR Ingest miscellaneous validation integration tests"
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (data-umm-c/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest-umm-spec-collection provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-conflict
  [coll-attributes errors]
  (let [collection (assoc (data-umm-c/collection coll-attributes) :native-id (:native-id coll-attributes))
        response (d/ingest-umm-spec-collection "PROV1" collection {:allow-failure? true})]
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
          {:keys [status errors]}
          (ingest/ingest-concept (ingest/concept :collection "PROV1" "foo" :iso19115 bad-metadata))]

      (is (= 422 status))
      (is (= [{:errors ["Spatial coordinate reference type must be supplied."]
               :path ["SpatialCoverage"]}]
             errors)))))

(deftest duplicate-entry-title-test
  (testing "same entry-title and native-id across providers is valid"
    (assert-valid
      {:EntryTitle "ET-1" :concept-id "C1-PROV1" :native-id "Native1"})
    (assert-valid
      {:EntryTitle "ET-1" :concept-id "C1-PROV2" :native-id "Native1" :provider-id "PROV2"}))

  (testing "entry-title must be unique for a provider"
    (assert-conflict
      {:EntryTitle "ET-1" :concept-id "C2-PROV1" :native-id "Native2" :ShortName "S2"}
      ["The Entry Title [ET-1] must be unique. The following concepts with the same entry title were found: [C1-PROV1]."])))

(deftest nil-version-test
  (testing "Collections with nil versions are rejected"
    (let [concept (data-umm-c/collection-concept {:Version nil} :iso19115)
          response (ingest/ingest-concept concept)]
      (is (= {:status 422
              :errors ["Version is required."]}
             response)))))

(deftest field-exceeding-maxlength-warnings
  (testing "Multiple warnings returned for the fields exceeding maxlength allowed"
    (let [collection (data-umm-c/collection
                       {:Platforms [(data-umm-c/platform {:ShortName (apply str (repeat 81 "x"))})]
                        :Purpose (apply str (repeat 12000 "y"))
                        :CollectionProgress "COMPLETE"})
          ingest-response (d/ingest-umm-spec-collection "PROV1" collection {:format :dif10})
          validation-response (ingest/validate-concept (data-umm-c/collection-concept collection :dif10))]
      (is (some? (re-find #"/Platforms/0/ShortName string.*is too long \(length: 81, maximum allowed: 80\)" (:warnings ingest-response))))
      (is (some? (re-find #"/Platforms/0/ShortName string.*is too long \(length: 81, maximum allowed: 80\)" (:warnings validation-response))))
      (is (some? (re-find #"/Purpose string.*is too long \(length: 12000, maximum allowed: 10000\)" (:warnings ingest-response))))
      (is (some? (re-find #"/Purpose string.*is too long \(length: 12000, maximum allowed: 10000\)" (:warnings validation-response)))))))

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
                                   (data-umm-c/collection-concept {:concept-id "C1-PROV1"
                                                           :native-id "Same Native ID"}))]
                    (when (= 409 (:status response))
                      (println "409 returned, Errors:" (:errors response)))))))))

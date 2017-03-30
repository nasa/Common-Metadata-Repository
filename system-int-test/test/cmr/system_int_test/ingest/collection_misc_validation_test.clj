(ns cmr.system-int-test.ingest.collection-misc-validation-test
  "CMR Ingest miscellaneous validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.common-app.test.side-api :as side]
    [cmr.ingest.config :as icfg]
    [clojure.java.io :as io]))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-conflict
  [coll-attributes errors]
  (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
        response (d/ingest "PROV1" collection {:allow-failure? true})]
    (is (= {:status 409
            :errors errors}
           (select-keys response [:status :errors])))))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest spatial-with-no-representation
  ;; ISO19115 allows you to ingest metadata with no spatial coordinate reference but have spatial
  ;; points. We should reject it because UMM requires a spatial coordinate reference.
  (testing "A collection with spatial data but no representation should fail ingest validation"
    (side/eval-form `(icfg/set-return-umm-spec-validation-errors! true))
    (let [bad-metadata (slurp (io/resource
                                "iso-samples/iso-spatial-data-missing-coordinate-system.iso19115"))
          {:keys [status errors]}
          (ingest/ingest-concept (ingest/concept :collection "PROV1" "foo" :iso19115 bad-metadata))]

      (is (= 422 status))
      (is (= [{:errors ["Granule Spatial Representation must be supplied."]
               :path ["SpatialExtent"]}]
             errors)))
    (side/eval-form `(icfg/set-return-umm-spec-validation-errors! false))))

(deftest duplicate-entry-title-test
  (testing "same entry-title and native-id across providers is valid"
    (assert-valid
      {:entry-title "ET-1" :concept-id "C1-PROV1" :native-id "Native1"})
    (assert-valid
      {:entry-title "ET-1" :concept-id "C1-PROV2" :native-id "Native1" :provider-id "PROV2"}))

  (testing "entry-title must be unique for a provider"
    (assert-conflict
      {:entry-title "ET-1" :concept-id "C2-PROV1" :native-id "Native2"}
      ["The Entry Title [ET-1] must be unique. The following concepts with the same entry title were found: [C1-PROV1]."])))

(deftest nil-version-test
  (testing "Collections with nil versions are rejected"
    (let [concept (dc/collection-concept {:version-id nil} :iso19115)
          response (ingest/ingest-concept concept)]
      (is (= {:status 422
              :errors ["Version is required."]}
             response)))))

(deftest field-exceeding-maxlength-warnings
  (testing "Multiple warnings returned for the fields exceeding maxlength allowed"
    (let [collection (dc/collection-dif10
                       {:platforms [(dc/platform {:short-name (apply str (repeat 81 "x"))})]
                        :purpose (apply str (repeat 12000 "y"))
                        :product (dc/product {:processing-level-id "1"})
                        :collection-progress :complete})
          ingest-response (d/ingest "PROV1" collection {:format :dif10})
          validation-response (ingest/validate-concept (dc/collection-concept collection :dif10))]
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
                                   (dc/collection-concept {:concept-id "C1-PROV1"
                                                           :native-id "Same Native ID"}))]
                    (when (= 409 (:status response))
                      (println "409 returned, Errors:" (:errors response)))))))))

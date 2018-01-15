(ns cmr.system-int-test.search.facets.granule-v2-facets-search-test
  "This tests retrieving v2 facets when searching for granules."
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.search.query-validation :as cqv]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- search-and-return-v2-facets
  "Returns only the facets from a v2 granule facets search request."
  [search-params]
  (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
    (get-in (search/find-concepts-json :granule query-params) [:results :facets])))

(deftest granule-facet-tests
  (testing (str "Granule facets are returned when requesting V2 facets in JSON format for a single"
                " collection concept ID.")
    (let [facets (search-and-return-v2-facets {:collection-concept-id "C1-PROV1"})]
      (is (= "Browse Granules" (:title facets))))))

(defn- setup-collections-and-granules
  "Ingests the collections and granules needed for the single collection validation test."
  []
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:ShortName "SN1"
                                               :Version "V1"
                                               :EntryTitle "ET1"
                                               :concept-id "C1-PROV1"}))
        gran1 (d/item->concept
               (dg/granule-with-umm-spec-collection
                coll1 (:concept-id coll1) {:concept-id "G1-PROV1"}))
        coll2 (d/ingest-umm-spec-collection
               "PROV2" (data-umm-c/collection {:ShortName "SN2"
                                               :Version "V1"
                                               :EntryTitle "ET2"
                                               :concept-id "C1-PROV2"}))
        coll3 (d/ingest-umm-spec-collection
               "PROV2" (data-umm-c/collection {:ShortName "SN2"
                                               :Version "V2"
                                               :EntryTitle "ET3"
                                               :concept-id "C2-PROV2"}))
        gran2 (d/item->concept
               (dg/granule-with-umm-spec-collection
                coll2 (:concept-id coll2) {:concept-id "G1-PROV2"}))

        gran3 (d/item->concept
               (dg/granule-with-umm-spec-collection
                coll3 (:concept-id coll3) {:concept-id "G2-PROV2"}))]
    (ingest/ingest-concept gran1)
    (ingest/ingest-concept gran2)
    (ingest/ingest-concept gran3)
    (index/wait-until-indexed)))

(deftest single-collection-validation-tests
  (setup-collections-and-granules)
  (testing "Allowed single collection queries"
    (util/are3
      [query-params]
      (let [response (search/find-concepts-json :granule (merge query-params
                                                                {:include-facets "v2"}))]
        (is (= 200 (:status response)))
        (is (= "Browse Granules" (get-in response [:results :facets :title]))))

      "Entry title" {:entry-title "ET1"}
      "Provider with single collection" {:provider "PROV1"}
      "Collection concept ID" {:collection-concept-id "C1-PROV1"}
      "ECHO Collection ID" {:echo-collection-id "C1-PROV1"}
      "Short name" {:short-name "SN1"}
      "Short name and version combination" {:short-name "SN2" :version "V1"}))

  (testing "Rejected multi-collection queries"
    (util/are3
      [query-params num-matched]
      (let [response (search/find-concepts-json :granule (merge query-params
                                                                {:include-facets "v2"}))]
        (is (= 400 (:status response)))
        (is (= [(str "Granule V2 facets are limited to a single collection, but query matched "
                     num-matched " collections.")]
               (:errors response))))

      "Multiple matched entry titles" {:entry-title ["ET1" "ET2"]} 2
      "Provider with multiple collections" {:provider "PROV2"} 2
      "Collection concept IDs" {:collection-concept-id ["C1-PROV1" "C1-PROV2" "C2-PROV2"]} 3
      "ECHO Collection IDs" {:echo-collection-id ["C1-PROV2" "C2-PROV2"]} 2
      "Short name with two matching collection versions" {:short-name "SN2"} 2
      "All granules query" {} "an undetermined number of")))

(deftest granule-facets-parameter-validation-tests
  (testing "Only the json format supports V2 granule facets."
    (let [xml-error-string (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>"
                                "V2 facets are only supported in the JSON format.</error></errors>")
          json-error-string "{\"errors\":[\"V2 facets are only supported in the JSON format.\"]}"]
      (doseq [fmt (cqv/supported-result-formats :granule)
              ;; timeline is not supported on the granule search route and json is valid, so we
              ;; do not test those.
              :when (not (#{:timeline :json} fmt))
              :let [expected-body (if (= :csv fmt) json-error-string xml-error-string)]]
        (testing (str "format" fmt)
          (let [response (search/find-concepts-in-format fmt :granule {:include-facets "v2"}
                                                         {:url-extension (name fmt)
                                                          :throw-exceptions false})]
            (is (= {:status 400
                    :body expected-body}
                   (select-keys response [:status :body]))))))))
  (testing "The value for include_facets must be v2 case insensitive."
    (testing "Case insensitive"
      (let [facets (get-in (search/find-concepts-json :granule {:include-facets "V2"
                                                                :collection-concept-id "C1-PROV1"})
                           [:results :facets])]
        (is (= "Browse Granules" (:title facets)))))
    (testing "Invalid values"
      (util/are3
        [param-value]
        (is (= {:status 400
                :errors [(str "Granule parameter include_facets only supports the value v2, but "
                              "was [" param-value "]")]}
               (search/find-concepts-json :granule {:include-facets param-value})))
        "foo" "foo"
        "V22" "V22"
        "true" true
        "false" false))))

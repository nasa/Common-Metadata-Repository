(ns cmr.system-int-test.search.facets.granule-v2-facets-search-test
  "This tests retrieving v2 facets when searching for granules."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.common-app.services.search.query-validation :as cqv]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]))

(defn- search-and-return-v2-facets
  "Returns only the facets from a v2 granule facets search request."
  [search-params]
  (index/wait-until-indexed)
  (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
    (get-in (search/find-concepts-json :granule query-params) [:results :facets])))

(deftest granule-facet-tests
  (testing "Granule facets are returned when requesting V2 facets in JSON format."
    (let [facets (search-and-return-v2-facets {:collection-concept-id "C1-PROV1"})]
      (is (= "Browse Granules" (:title facets)))))

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

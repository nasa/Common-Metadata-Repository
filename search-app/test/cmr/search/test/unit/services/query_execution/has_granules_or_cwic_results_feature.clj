(ns cmr.search.test.unit.services.query-execution.has-granules-or-cwic-results-feature 
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.common.util :refer [are3]]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as hgocrf]))

 (deftest create-granules-or-cwic-map-test
   (let [context nil]
      (are3 [expected get-collection-granule-counts-fn get-cwic-collections-fn]
            (is (= expected (hgocrf/create-has-granules-or-cwic-map
                             context
                             get-collection-granule-counts-fn
                             get-cwic-collections-fn)))

            "No collection granules counts and no cwic collections"
            {}
            (fn [_context] {})
            (fn [_context] {})

            "with collection granule count and no cwic collections"
            {"C1234-PROV1" true "C1235-PROV2" true}
            (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
            (fn [_context] {})

            "with collection granule count and with cwic collections"
            {"C1234-PROV1" true "C1235-PROV2" true "C1236-PROV1" true "C1237-PROV2" true}
            (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
            (fn [_context] {"C1236-PROV1" 10 "C1237-PROV2" 20})

            "no collection granule count and with cwic collections"
            {"C1236-PROV1" true "C1237-PROV2" true}
            (fn [_context] {})
            (fn [_context] {"C1236-PROV1" 10 "C1237-PROV2" 20})

            "with collection granule count and with cwic collections with same values"
            {"C1234-PROV1" true "C1235-PROV2" true}
            (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
            (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20}))))

(deftest create-granules-or-opensearch-map-test
  (let [context nil]
    (are3 [expected get-collection-granule-counts-fn get-opensearch-collections-fn]
          (is (= expected (hgocrf/create-has-granules-or-opensearch-map
                           context
                           get-collection-granule-counts-fn
                           get-opensearch-collections-fn)))

          "No collection granules counts and no opensearch collections"
          {}
          (fn [_context] {})
          (fn [_context] {})

          "with collection granule count and no opensearch collections"
          {"C1234-PROV1" true "C1235-PROV2" true}
          (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
          (fn [_context] {})

          "with collection granule count and with opensearch collections"
          {"C1234-PROV1" true "C1235-PROV2" true "C1236-PROV1" true "C1237-PROV2" true}
          (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
          (fn [_context] {"C1236-PROV1" 10 "C1237-PROV2" 20})

          "no collection granule count and with opensearch collections"
          {"C1236-PROV1" true "C1237-PROV2" true}
          (fn [_context] {})
          (fn [_context] {"C1236-PROV1" 10 "C1237-PROV2" 20})

          "with collection granule count and with opensearch collections with same values"
          {"C1234-PROV1" true "C1235-PROV2" true}
          (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20})
          (fn [_context] {"C1234-PROV1" 10 "C1235-PROV2" 20}))))

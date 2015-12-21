(ns cmr.system-int-test.search.granule-counts-search-test
  "This tests the granule counts search feature which allows retrieving counts of granules per collection."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.granule-counts :as gran-counts]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.codec :as codec]
            [cmr.spatial.point :as p]
            [cmr.common.util :as util]
            [cmr.spatial.mbr :as m]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn temporal-range
  "Creates attributes for collection or granule defining a temporal range between start and stop
  which should be single digit integers."
  [start stop]
  (let [n-to-date-str #(str "200" % "-01-01T00:00:00Z")]
    {:beginning-date-time (n-to-date-str start)
     :ending-date-time (n-to-date-str stop)}))

(defn temporal-search-range
  "Creates a temporal search range between start and stop which should be single digit integers"
  [start stop]
  (let [{:keys [beginning-date-time ending-date-time]} (temporal-range start stop)]
    (str beginning-date-time "," ending-date-time)))

(defn make-coll
  ([n shape temporal-attribs]
   (make-coll n shape temporal-attribs {}))
  ([n shape temporal-attribs other-attribs]
   (let [spatial-attribs (when shape
                           {:spatial-coverage
                            (dc/spatial {:gsr :geodetic
                                         :sr :geodetic
                                         :geometries [shape]})})
         coll-attribs (merge {:entry-title (str "coll" n)}
                             spatial-attribs
                             temporal-attribs
                             other-attribs)]
     (d/ingest "PROV1" (dc/collection coll-attribs)))))

(defn make-gran
  [coll shape temporal-attribs]
  (let [spatial-attribs (when shape {:spatial-coverage (dg/spatial shape)})
        gran-attribs (merge {} spatial-attribs temporal-attribs)]
    (d/ingest "PROV1" (dg/granule coll gran-attribs))))

(deftest granule-related-collection-query-results-features-test
  (let [;; Create collections
        ;; whole world, no temporal, and science keywords
        coll1 (make-coll 1 m/whole-world nil {:science-keywords
                                              [(dc/science-keyword {:category "Tornado"
                                                                    :topic "Wind"
                                                                    :term "Speed"})]})
        ;; western hemisphere
        coll2 (make-coll 2 (m/mbr -180 90 0 -90) (temporal-range 1 3))
        ;; eastern hemisphere
        coll3 (make-coll 3 (m/mbr 0 90 180 -90) (temporal-range 2 4))
        ;; northern hemisphere
        coll4 (make-coll 4 (m/mbr -180 90 180 0) (temporal-range 3 5))
        ;; southern hemisphere
        coll5 (make-coll 5 (m/mbr -180 0 180 -90) (temporal-range 4 6))
        ;; no spatial
        coll6 (make-coll 6 nil (temporal-range 1 6))
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6]]

    ;; -- Make granules --
    ;; Coll1 granules
    (make-gran coll1 (p/point 0 0) nil)
    (make-gran coll1 (p/point 0 90) nil)
    (make-gran coll1 (p/point 0 -90) nil)
    (make-gran coll1 (p/point -135 0) nil)
    (make-gran coll1 (p/point 135 0) nil)

    ;; Coll2 granules
    ;; There are none (should always be 0)

    ;; Coll3 granules
    (make-gran coll3 (p/point 0 90) (temporal-range 2 2))
    (make-gran coll3 (p/point 135 0) (temporal-range 3 3))
    (make-gran coll3 (p/point 0 -90) (temporal-range 4 4))

    ;; Coll4 granules
    (make-gran coll4 (p/point 0 90) (temporal-range 3 3))
    (make-gran coll4 (p/point 135 45) (temporal-range 4 4))
    (make-gran coll4 (p/point -135 45) (temporal-range 5 5))

    ;; Coll5 granules
    (make-gran coll5 (p/point 0 -90) (temporal-range 4 4))
    (make-gran coll5 (p/point 135 -45) (temporal-range 5 5))
    (make-gran coll5 (p/point -135 -45) (temporal-range 6 6))

    ;; Coll6 granules
    (make-gran coll6 nil (temporal-range 1 1))
    (make-gran coll6 nil (temporal-range 3 3))
    (make-gran coll6 nil (temporal-range 6 6))
    (index/wait-until-indexed)

    (testing "granule counts"
      (testing "invalid include-granule-counts"
        (is (= {:errors ["Parameter include_granule_counts must take value of true, false, or unset, but was [foo]"] :status 400}
               (search/find-refs :collection {:include-granule-counts "foo"})))
        (is (= {:errors ["Parameter [include_granule_counts] was not recognized."] :status 400}
               (search/find-refs :granule {:include-granule-counts true}))))

      (testing "granule counts for all collections"
        (let [refs (search/find-refs :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} refs))))

      ;; CMR-712
      (testing "granule counts with science keywords query"
        (let [refs (search/find-refs :collection {:include-granule-counts true
                                                  :science-keywords {:0 {:category "Tornado"}}})]
          (is (gran-counts/granule-counts-match? :xml {coll1 5} refs))))

      (testing "granule counts for spatial queries"
        (are [wnes expected-counts]
             (let [refs (search/find-refs :collection {:include-granule-counts true
                                                       :bounding-box (codec/url-encode (apply m/mbr wnes))})]
               (gran-counts/granule-counts-match? :xml expected-counts refs))

             ;; Whole world
             [-180 90 180 -90] {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3}
             ;; north west quadrant
             [-180 90 0 0] {coll1 3 coll2 0 coll3 1 coll4 2 coll5 0}
             ;; south east quadrant
             [0 0 180 -90] {coll1 3 coll2 0 coll3 2 coll4 0 coll5 2}
             ;; Smaller area around one granule in coll4
             [130 47 137 44] {coll1 0 coll3 0 coll4 1}))

      (testing "granule counts for temporal queries"
        (are [start stop expected-counts]
             (let [refs (search/find-refs :collection {:include-granule-counts true
                                                       :temporal (temporal-search-range start stop)})]
               (gran-counts/granule-counts-match? :xml expected-counts refs))
             1 6 {coll2 0 coll3 3 coll4 3 coll5 3 coll6 3}
             2 3 {coll2 0 coll3 2 coll4 1 coll6 1}))

      (testing "granule counts for both spatial and temporal queries"
        (let [refs (search/find-refs :collection {:include-granule-counts true
                                                  :temporal (temporal-search-range 2 3)
                                                  :bounding-box (codec/url-encode (m/mbr -180 90 0 0))})]
          (is (gran-counts/granule-counts-match? :xml {coll2 0 coll3 1 coll4 1} refs))))

      (testing "direct transformer query"
        (let [items (search/find-metadata :collection :echo10 {:include-granule-counts true
                                                               :concept-id (map :concept-id all-colls)})]
          (is (gran-counts/granule-counts-match? :echo10
                                                 {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} items))))

      (testing "AQL with include_granule_counts"
        (let [refs (search/find-refs-with-aql :collection [] {}
                                              {:query-params {:include_granule_counts true}})]
          (is (gran-counts/granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} refs))))

      (testing "ATOM XML results with granule counts"
        (let [results (search/find-concepts-atom :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :atom {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} results))))

      (testing "JSON results with granule counts"
        (let [results (search/find-concepts-json :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :atom {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} results)))))

    (testing "has granules"
      (testing "invalid include-has-granules"
        (is (= {:errors ["Parameter include_has_granules must take value of true, false, or unset, but was [foo]"] :status 400}
               (search/find-refs :collection {:include-has-granules "foo"})))
        (is (= {:errors ["Parameter [include_has_granules] was not recognized."] :status 400}
               (search/find-refs :granule {:include-has-granules true}))))

      (testing "in results"
        (are [result-format results]
             (let [expected-has-granules (util/map-keys :concept-id
                                                        {coll1 true coll2 false coll3 true coll4 true
                                                         coll5 true coll6 true})
                   actual-has-granules (gran-counts/results->actual-has-granules result-format results)
                   has-granules-match? (= expected-has-granules actual-has-granules)]
               (when-not has-granules-match?
                 (println "Expected:" (pr-str expected-has-granules))
                 (println "Actual:" (pr-str actual-has-granules)))
               has-granules-match?)
             :xml (search/find-refs :collection {:include-has-granules true})
             :echo10 (search/find-metadata :collection :echo10 {:include-has-granules true})
             :atom (search/find-concepts-atom :collection {:include-has-granules true})
             :atom (search/find-concepts-json :collection {:include-has-granules true}))))))

(deftest collection-has-granules-caching-test
  (let [;; Create collections
        ;; whole world, no temporal, and science keywords
        coll1 (make-coll 1 m/whole-world nil)
        ;; just like coll1
        coll2 (make-coll 2 m/whole-world nil)]

    ;; Ingest some granules for coll1 first, and leave coll2 for later
    ;; Coll1 granules
    (make-gran coll1 (p/point 0 0) nil)
    (make-gran coll1 (p/point 0 90) nil)

    (index/wait-until-indexed)

    (testing "granule counts"
      (testing "granule counts for all collections"
        (let [refs (search/find-refs :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :xml {coll1 2 coll2 0} refs)))))

    (testing "has_granules"
      (are [result-format results]
          (let [expected-has-granules (util/map-keys :concept-id {coll1 true coll2 false})
                actual-has-granules (gran-counts/results->actual-has-granules result-format results)]
            (= expected-has-granules actual-has-granules))
        :xml (search/find-refs :collection {:include-has-granules true})
        :echo10 (search/find-metadata :collection :echo10 {:include-has-granules true})
        :atom (search/find-concepts-atom :collection {:include-has-granules true})
        :atom (search/find-concepts-json :collection {:include-has-granules true})))

    (testing "after ingesting more granules"
      (make-gran coll2 (p/point 0 0) nil)
      (make-gran coll2 (p/point 0 90) nil)

      (index/wait-until-indexed)

      (testing "granule counts"
        (testing "granule counts for all collections"
          (let [refs (search/find-refs :collection {:include-granule-counts true})]
            (is (gran-counts/granule-counts-match? :xml {coll1 2 coll2 2} refs)))))

      (testing "has_granules"
        (testing "without include-granule-counts"
          (are [result-format results]
              (let [expected-has-granules (util/map-keys :concept-id {coll1 true coll2 true})
                    actual-has-granules (gran-counts/results->actual-has-granules result-format results)]
                (not= expected-has-granules actual-has-granules))
            :xml (search/find-refs :collection {:include-has-granules true})
            :echo10 (search/find-metadata :collection :echo10 {:include-has-granules true})
            :atom (search/find-concepts-atom :collection {:include-has-granules true})
            :atom (search/find-concepts-json :collection {:include-has-granules true})))

        (testing "with include-granule-counts"
          (are [result-format results]
              (let [expected-has-granules (util/map-keys :concept-id {coll1 true coll2 true})
                    actual-has-granules (gran-counts/results->actual-has-granules result-format results)]
                (= expected-has-granules actual-has-granules))
            :xml (search/find-refs :collection {:include-has-granules true :include-granule-counts true})
            :echo10 (search/find-metadata :collection :echo10 {:include-has-granules true :include-granule-counts true})
            :atom (search/find-concepts-atom :collection {:include-has-granules true :include-granule-counts true})
            :atom (search/find-concepts-json :collection {:include-has-granules true :include-granule-counts true})))))))

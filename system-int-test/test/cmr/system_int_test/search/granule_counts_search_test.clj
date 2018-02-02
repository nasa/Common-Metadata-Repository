(ns cmr.system-int-test.search.granule-counts-search-test
  "This tests the granule counts search feature which allows retrieving counts of granules per collection."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.spatial.codec :as codec]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.granule-counts :as gran-counts]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       tags/grant-all-tag-fixture]))
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
  "Creates a granule using a shape for the spatial metadata."
  [coll shape temporal-attribs]
  (let [spatial-attribs (when shape {:spatial-coverage (dg/spatial shape)})
        gran-attribs (merge {} spatial-attribs temporal-attribs)]
    (d/ingest "PROV1" (dg/granule coll gran-attribs))))

(defn- make-orbit-gran
  "Creates a granule which has spatial metadata defined by orbital parameters."
  ([coll ur asc-crossing start-lat start-dir end-lat end-dir]
   (make-orbit-gran coll ur asc-crossing start-lat start-dir end-lat end-dir {}))
  ([coll ur asc-crossing start-lat start-dir end-lat end-dir other-attribs]
   (let [orbit (dg/orbit asc-crossing start-lat start-dir end-lat end-dir)]
     (d/ingest "PROV1"
               (dg/granule coll (merge {:granule-ur ur
                                        :spatial-coverage (apply dg/spatial orbit nil)}
                                       other-attribs))))))

(deftest granule-related-collection-query-results-features-test
  (let [no-match-temporal {:beginning-date-time "1990-01-01T00:00:00"
                           :ending-date-time "1991-01-01T00:00:00"}
        ;; Create collections
        ;; whole world, temporal not match search range, and science keywords
        coll1 (make-coll 1 m/whole-world no-match-temporal
                         {:science-keywords
                          [(dc/science-keyword {:category "Tornado"
                                                :topic "Wind"
                                                :term "Speed"})]})
        ;; western hemisphere
        coll2 (make-coll 2 (m/mbr -180 90 0 -90) (temporal-range 1 3))
        ;; eastern hemisphere
        coll3 (make-coll 3 (m/mbr 0 90 180 -90) (temporal-range 2 5))
        ;; northern hemisphere
        coll4 (make-coll 4 (m/mbr -180 90 180 0) (temporal-range 3 5))
        ;; southern hemisphere
        coll5 (make-coll 5 (m/mbr -180 0 180 -90) (temporal-range 4 6))
        ;; no spatial
        coll6 (make-coll 6 nil (temporal-range 1 6))

        orbit-parameters {:swath-width 2.0
                          :period 96.7
                          :inclination-angle 94.0
                          :number-of-orbits 0.25
                          :start-circular-latitude 50.0}

        orbit-coll (d/ingest "PROV1"
                             (dc/collection
                              (merge
                               {:entry-id "orbit-coll"
                                :entry-title "orbit-coll"
                                :spatial-coverage (dc/spatial {:sr :geodetic
                                                               :geometries [m/whole-world]
                                                               :gsr :orbit
                                                               :orbit orbit-parameters})}
                               no-match-temporal)))

        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 orbit-coll]]

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

    ;; Orbit granules for orbit collection
    (make-orbit-gran orbit-coll "orbit-gran" 70.80471 50.0 :asc  50.0 :desc)

    (index/wait-until-indexed)
    ;; Refresh the aggregate cache so that it includes all the granules that were added.
    (index/full-refresh-collection-granule-aggregate-cache)
    ;; Reindex all the collections to get the latest information.
    (ingest/reindex-all-collections)
    (index/wait-until-indexed)

    (testing "Sorting by has_granules"
      (is (d/refs-match-order? [coll1 coll3 coll4 coll5 coll6 orbit-coll coll2]
                               (search/find-refs :collection {:sort-key ["has_granules" "revision-date"]})))
      (is (d/refs-match-order? [coll2 coll1 coll3 coll4 coll5 coll6 orbit-coll]
                               (search/find-refs :collection {:sort-key ["-has_granules" "revision-date"]}))))

    (testing "granule counts"
      (testing "invalid include-granule-counts"
        (is (= {:errors ["Parameter include_granule_counts must take value of true, false, or unset, but was [foo]"] :status 400}
               (search/find-refs :collection {:include-granule-counts "foo"})))
        (is (= {:errors ["Parameter [include_granule_counts] was not recognized."] :status 400}
               (search/find-refs :granule {:include-granule-counts true}))))

      (testing "granule counts for all collections"
        (let [refs (search/find-refs :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3
                                                       coll6 3 orbit-coll 1} refs))))

      (testing "granule counts for native collections"
        (let [granules (search/find-metadata :collection :native {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :echo10 {coll1 5 coll2 0 coll3 3 coll4 3
                                                          coll5 3 coll6 3 orbit-coll 1} granules))))
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
          [-180 90 180 -90] {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 orbit-coll 1}
          ;; north west quadrant
          [-180 90 0 0] {coll1 3 coll2 0 coll3 1 coll4 2 coll5 0 orbit-coll 1}
          ;; south east quadrant
          [0 0 180 -90] {coll1 3 coll2 0 coll3 2 coll4 0 coll5 2 orbit-coll 0}
          ;; Smaller area around one granule in coll4
          [130 47 137 44] {coll1 0 coll3 0 coll4 1 orbit-coll 0}))

      (testing "granule counts for temporal queries"
        (are [start stop expected-counts]
          (let [refs (search/find-refs :collection {:include-granule-counts true
                                                    :temporal (temporal-search-range start stop)})]
            (gran-counts/granule-counts-match? :xml expected-counts refs))
          1 6 {coll2 0 coll3 3 coll4 3 coll5 3 coll6 3}

          ;; coll3 is returned because it covers the time range but it has no granules that cover it.
          5 6 {coll3 0 coll4 1 coll5 2 coll6 1}

          2 3 {coll2 0 coll3 2 coll4 1 coll6 1}))

      (testing "granule counts for temporal queries with limit_to_granules"
        (are [start stop expected-counts]
          (let [refs (search/find-refs :collection {:include-granule-counts true
                                                    "options[temporal][limit_to_granules]" true
                                                    :temporal (temporal-search-range start stop)})]
            (gran-counts/granule-counts-match? :xml expected-counts refs))
          1 6 {coll2 0 coll3 3 coll4 3 coll5 3 coll6 3}

          ;; coll3 not returned here because it has no granules that cover the time range
          5 6 {coll4 1 coll5 2 coll6 1}

          2 3 {coll2 0 coll3 2 coll4 1 coll6 1}))

      (testing "granule counts for both spatial and temporal queries"
        (let [refs (search/find-refs :collection {:include-granule-counts true
                                                  :temporal (temporal-search-range 2 3)
                                                  :bounding-box (codec/url-encode (m/mbr -180 90 0 0))})]
          (is (gran-counts/granule-counts-match? :xml {coll2 0 coll3 1 coll4 1} refs))))

      (testing "direct transformer query"
        (let [items (search/find-metadata :collection :echo10
                                          {:include-granule-counts true
                                           :concept-id (map :concept-id all-colls)})]
          (is (gran-counts/granule-counts-match? :echo10
                                                 {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3
                                                  orbit-coll 1}
                                                 items))))

      (testing "AQL with include_granule_counts"
        (let [refs (search/find-refs-with-aql :collection [] {}
                                              {:query-params {:include_granule_counts true}})]
          (is (gran-counts/granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3
                                                       coll6 3 orbit-coll 1}
                                                 refs))))

      (testing "ATOM XML results with granule counts"
        (let [results (search/find-concepts-atom :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :atom {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3
                                                        coll6 3 orbit-coll 1}
                                                 results))))

      (testing "JSON results with granule counts"
        (let [results (search/find-concepts-json :collection {:include-granule-counts true})]
          (is (gran-counts/granule-counts-match? :atom {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3
                                                        coll6 3 orbit-coll 1}
                                                 results)))))

    (testing "include_has_granules parameter"
      (testing "invalid include-has-granules"
        (is (= {:errors ["Parameter include_has_granules must take value of true, false, or unset, but was [foo]"] :status 400}
               (search/find-refs :collection {:include-has-granules "foo"})))
        (is (= {:errors ["Parameter [include_has_granules] was not recognized."] :status 400}
               (search/find-refs :granule {:include-has-granules true}))))

      (testing "in results"
        (are [result-format results]
          (let [expected-has-granules (util/map-keys :concept-id
                                                     {coll1 true coll2 false coll3 true coll4 true
                                                      coll5 true coll6 true orbit-coll true})
                actual-has-granules (gran-counts/results->actual-has-granules result-format results)
                has-granules-match? (= expected-has-granules actual-has-granules)]
            (when-not has-granules-match?
              (println "Expected:" (pr-str expected-has-granules))
              (println "Actual:" (pr-str actual-has-granules)))
            has-granules-match?)
          :xml (search/find-refs :collection {:include-has-granules true})
          :echo10 (search/find-metadata :collection :echo10 {:include-has-granules true})
          :atom (search/find-concepts-atom :collection {:include-has-granules true})
          :atom (search/find-concepts-json :collection {:include-has-granules true}))))

    (testing "has_granules parameter"

      (let [results (search/find-refs :collection {"has_granules" "true"})]
        (is (= (set (map :concept-id [coll1 coll3 coll4 coll5 coll6 orbit-coll]))
               (set (map :id (:refs results))))))

      (let [results (search/find-refs :collection {"has_granules" "false"})]
        (is (= [(:concept-id coll2)]
               (map :id (:refs results)))))

      (testing "direct transformer query"
        (let [results (search/find-metadata :collection :echo10 {"has_granules" "true"
                                                                 :concept-id (map :concept-id all-colls)})]
          (is (= (set (map :concept-id [coll1 coll3 coll4 coll5 coll6 orbit-coll]))
                 (set (map :concept-id (:items results))))))))))

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

      (testing "include_has_granules"
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

(deftest search-collections-has-granules-or-cwic-test
  (let [coll1 (make-coll 1 m/whole-world nil)
        coll2 (make-coll 2 m/whole-world nil)
        coll3 (make-coll 3 m/whole-world nil)
        coll4 (make-coll 4 m/whole-world nil)
        coll5 (make-coll 5 m/whole-world nil)
        coll6 (make-coll 6 m/whole-world nil)

        _ (index/wait-until-indexed)
        user1-token (e/login (s/context) "user1")
        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "NON-CWIC"})
               [coll1 coll5])
        tag2 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "org.ceos.wgiss.cwic.granules.prod"})
               [coll2 coll6])]
    (index/wait-until-indexed)

    ;; coll1
    ;; non-cwic tagged with granule
    (make-gran coll1 (p/point 0 0) nil)

    ;; coll2
    ;; cwic tagged with granule
    (make-gran coll2 (p/point 0 0) nil)

    ;; coll 3
    ;; no tag with granule
    (make-gran coll3 (p/point 0 0) nil)

    ;; coll 4
    ;; no tag without granule

    ;; coll 5
    ;; non-cwic tagged no granule

    ;; coll 6
    ;; cwic tagged no granule

    (index/wait-until-indexed)
    (testing "Search with has-granules-or-cwic feature"
      (d/refs-match? [coll1 coll3 coll6 coll2]
                     (search/find-refs :collection
                                       {:has_granules_or_cwic true}
                                       {:snake-kebab? false})))

    (testing "Search with has-granules-or-cwic feature"
      (d/refs-match? [coll4 coll5 coll6]
                     (search/find-refs :collection
                                       {:has_granules_or_cwic false}
                                       {:snake-kebab? false})))))

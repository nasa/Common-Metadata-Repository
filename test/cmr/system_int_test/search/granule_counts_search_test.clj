(ns cmr.system-int-test.search.granule-counts-search-test
  "This tests the granule counts search feature which allows retrieving counts of granules per collection."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.codec :as codec]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(comment

  (ingest/reset)
  (ingest/create-provider "provguid1" "PROV1")


  )


;; TODO add tests incorporating ACLs for granules

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

(defmulti granule-counts-match?
  "Takes a map of collections to counts and actual results and checks that the references
  were found and that the granule counts are correct."
  (fn [result-format expected-counts result]
    result-format))

(defmethod granule-counts-match? :xml
  [result-format expected-counts refs-result]
  (let [count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:entry-title coll) granule-count]))
        actual-count-map (into {} (for [{:keys [name granule-count]} (:refs refs-result)]
                                    [name granule-count]))
        refs-match? (d/refs-match? (keys expected-counts) refs-result)
        counts-match? (= count-map actual-count-map)]
    (when-not refs-match?
      (println "Expected:" (pr-str (map :entry-title (keys expected-counts))))
      (println "Actual:" (pr-str (map :name (:refs refs-result)))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and refs-match? counts-match?)))

(defmethod granule-counts-match? :echo10
  [result-format expected-counts items]
  (let [count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:concept-id coll) granule-count]))
        actual-count-map (into {} (for [{:keys [concept-id granule-count]} items]
                                    [concept-id granule-count]))
        results-match? (d/metadata-results-match? :echo10 (keys expected-counts) items)
        counts-match? (= count-map actual-count-map)]
    (when-not results-match?
      (println "Expected:" (pr-str (map :concept-id (keys expected-counts))))
      (println "Actual:" (pr-str (map :concept-id items))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and results-match? counts-match?)))

(defmethod granule-counts-match? :atom
  [result-format expected-counts atom-results]
  (let [entries (get-in atom-results [:results :entries])
        count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:entry-title coll) granule-count]))
        actual-count-map (into {} (for [{:keys [dataset-id granule-count]} entries]
                                    [dataset-id granule-count]))
        results-match? (da/atom-collection-results-match?
                         (keys expected-counts) atom-results)
        counts-match? (= count-map actual-count-map)]
    (when-not results-match?
      (println "Expected:" (pr-str (map :entry-title (keys expected-counts))))
      (println "Actual:" (pr-str (map :dataset-id entries))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and results-match? counts-match?)))

(deftest find-granule-counts-per-collection-test
  (let [make-coll (fn [n shape temporal-attribs]
                    (let [spatial-attribs (when shape
                                            {:spatial-coverage
                                             (dc/spatial :geodetic :geodetic shape)})
                          coll-attribs (merge {:entry-title (str "coll" n)}
                                              spatial-attribs
                                              temporal-attribs)]
                      (d/ingest "PROV1" (dc/collection coll-attribs))))
        make-gran (fn [coll shape temporal-attribs]
                    (let [spatial-attribs (when shape {:spatial-coverage (dg/spatial shape)})
                          gran-attribs (merge {} spatial-attribs temporal-attribs)]
                      (d/ingest "PROV1" (dg/granule coll gran-attribs))))

        ;; Create collections
        ;; whole world, no temporal
        coll1 (make-coll 1 m/whole-world nil)
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
    (index/refresh-elastic-index)

    (testing "invalid include-granule-counts"
      (is (= {:errors ["Parameter include_granule_counts must take value of true, false, or unset, but was foo"] :status 422}
             (search/find-refs :collection {:include-granule-counts "foo"})))
      (is (= {:errors ["Parameter [include_granule_counts] was not recognized."] :status 422}
             (search/find-refs :granule {:include-granule-counts true}))))

    (testing "granule counts for all collections"
      (let [refs (search/find-refs :collection {:include-granule-counts true})]
        (is (granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} refs))))

    (testing "granule counts for spatial queries"
      (are [wnes expected-counts]
           (let [refs (search/find-refs :collection {:include-granule-counts true
                                                     :bounding-box (codec/url-encode (apply m/mbr wnes))})]
             (granule-counts-match? :xml expected-counts refs))

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
             (granule-counts-match? :xml expected-counts refs))
           1 6 {coll2 0 coll3 3 coll4 3 coll5 3 coll6 3}
           2 3 {coll2 0 coll3 2 coll4 1 coll6 1}))

    (testing "granule counts for both spatial and temporal queries"
      (let [refs (search/find-refs :collection {:include-granule-counts true
                                                :temporal (temporal-search-range 2 3)
                                                :bounding-box (codec/url-encode (m/mbr -180 90 0 0))})]
        (is (granule-counts-match? :xml {coll2 0 coll3 1 coll4 1} refs))))

    (testing "direct transformer query"
      (let [items (search/find-metadata :collection :echo10 {:include-granule-counts true
                                                             :concept-id (map :concept-id all-colls)})]
        (is (granule-counts-match? :echo10
              {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} items))))

    (testing "AQL with include_granule_counts"
      (let [refs (search/find-refs-with-aql :collection [] {}
                                            {:query-params {:include_granule_counts true}})]
        (is (granule-counts-match? :xml {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} refs))))

    (testing "ATOM XML results with granule counts"
      (let [results (search/find-concepts-atom :collection {:include-granule-counts true})]
        (is (granule-counts-match? :atom {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} results))))

    ;; TODO add ATOM JSON tests


    ))













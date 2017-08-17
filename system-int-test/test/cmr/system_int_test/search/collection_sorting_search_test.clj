(ns cmr.system-int-test.search.collection-sorting-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.common-app.services.search.messages :as msg]
    [cmr.search.data.query-to-elastic :as query-to-elastic]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm.collection.entry-id :as eid]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn make-coll
  "Helper for creating and ingesting a collection"
  [provider entry-title begin end]
  (d/ingest provider
            (dc/collection {:entry-title entry-title
                            :short-name entry-title
                            :version-id "1"
                            :beginning-date-time (d/make-datetime begin)
                            :ending-date-time (d/make-datetime end)})))
(defn delete-coll
  "Deletes the collection and returns a deleted version of it for sorting comparison."
  [coll]
  (let [{:keys [revision-id]} (ingest/delete-concept (d/item->concept coll))]
    (assoc coll
           :revision-id revision-id
           :deleted true)))

(deftest invalid-sort-key-test
  (is (= {:status 400
          :errors [(msg/invalid-sort-key "foo_bar" :collection)]}
         (search/find-refs :collection {:sort-key "foo_bar"})))
  (is (= {:status 400
          :errors [(msg/invalid-sort-key "foo_bar" :collection)]}
         (search/find-refs-with-aql :collection [] {}
                                    {:query-params {:sort-key "foo_bar"}}))))

(defn- sort-order-correct?
  ([items sort-key]
   (sort-order-correct? items sort-key false))
  ([items sort-key all-revisions?]
   (if all-revisions?
     (d/refs-match-order?
       items
       (search/find-refs :collection {:page-size 20 :sort-key sort-key :all-revisions true}))
     (and
       (d/refs-match-order?
         items
         (search/find-refs :collection {:page-size 20 :sort-key sort-key}))
       (d/refs-match-order?
         items
         (search/find-refs-with-aql :collection [] {}
                                    {:query-params {:page-size 20 :sort-key sort-key}}))))))

(defn- get-field-value
  "Get the value for a given field in the umm-record"
  [coll field]
  (if (= :entry-id field)
    (eid/umm->entry-id coll)
    (field coll)))

(defn- compare-field
  "Compares collections by the given field for sorting. descending? indicates if
  the sort is descending or ascending. When the given field matches for two
  revisions the sort order is by short name ascending, version id descending, concept-id ascending,
  and revision-id descending. All revisions does not sort by short name and version id. Field values
  must implement Comparable. Strings are converted to lower case for the comparison."
  [field descending? all-revisions? c1 c2]
  (let [value1 (get-field-value c1 field)
        value2 (get-field-value c2 field)
        [value1 value2] (if (= field :entry-title)
                          [(str/trim value1) (str/trim value2)]
                          [value1 value2])
        short-name #(str/lower-case (get-in % [:product :short-name]))
        version #(str/lower-case (get-in % [:product :version-id]))
        s1 (short-name c1)
        s2 (short-name c2)
        v1 (version c1)
        v2 (version c2)]

    (if (not= value1 value2)
      (let [processed-value1 (if (string? value1) (str/lower-case value1) value1)
            processed-value2 (if (string? value2) (str/lower-case value2) value2)]
        (if descending?
          (compare processed-value2 processed-value1)
          (compare processed-value1 processed-value2)))

      (if all-revisions?
        ;; All revisions search
        ;; Concept Id ascending
        (if (not= (:concept-id c1) (:concept-id c2))
          (compare (:concept-id c1) (:concept-id c2))

          ;; Revision descending
          (compare (:revision-id c2) (:revision-id c1)))

        ;; Latest revisions search
        ;; Short name ascending
        (if (not= s1 s2)
          (compare s1 s2)

          ;; Version descending
          (if (not= v1 v2)
            (compare v2 v1)

            ;; Concept Id ascending
            (if (not= (:concept-id c1) (:concept-id c2))
              (compare (:concept-id c1) (:concept-id c2))

              ;; Revision descending
              (compare (:revision-id c2) (:revision-id c1)))))))))


(defn- sort-revisions-by-field
  "Sort revisions using the given field with sub-sorting by concept-id ascending, revision-id
  descending. The field values must implement Comparable and strings are converted to lower case."
  ([field descending? colls]
   (sort-revisions-by-field field descending? colls false))
  ([field descending? colls all-revisions?]
   (sort-by identity (partial compare-field field descending? all-revisions?) colls)))

(defn- make-coll-with-sn
  "Makes a minimal collection with a shortname and ingests it."
  [shortname]
  (d/ingest "PROV1"
            (dc/collection {:short-name shortname})))

(deftest shortname-sorting-test
  (let [c1 (make-coll-with-sn "Bob2")
        c2 (make-coll-with-sn "Aaron")
        c3 (make-coll-with-sn "Zebra")
        c4 (make-coll-with-sn "Schwartz")
        c5 (make-coll-with-sn "bob1")
        correct-sort [c2 c5 c1 c4 c3]]
    (index/wait-until-indexed)
    (is (sort-order-correct? correct-sort "short-name"))
    (is (sort-order-correct? (reverse correct-sort) "-short-name"))))

(deftest sorting-test
  (let [c1-1 (make-coll "PROV1" "et99" 10 20)
        c1-2 (make-coll "PROV1" "et99" 10 20)
        c2 (make-coll "PROV1" "et90" 14 24)
        ;; Whitespace included in fields to ensure that sorting ignores whitespace at the beginning.
        c3 (make-coll "PROV1" "   et80" 19 30)
        c4 (make-coll "PROV1" "\tet70" 24 35)

        c5 (make-coll "PROV2" "\net98" 9 19)
        c6 (make-coll "PROV2" "et91" 15 25)
        c7 (make-coll "PROV2" "et79" 20 29)
        c8 (make-coll "PROV2" "ET94" 25 36)

        c9-1 (make-coll "PROV1" "et95" nil nil)
        c9-2 (make-coll "PROV1" "et95" nil nil)
        c9-3 (make-coll "PROV1" "et95" nil nil)

        c10 (make-coll "PROV2" "et85" nil nil)
        c11 (make-coll "PROV1" "et96" 12 nil)
        c12-1 (make-coll "PROV1" "et97" nil nil)
        c12-2 (delete-coll c12-1)

        all-colls [c1-2 c2 c3 c4 c5 c6 c7 c8 c9-3 c10 c11]
        all-revisions [c1-1 c1-2 c2 c3 c4 c5 c6 c7 c8 c9-1 c9-2 c9-3 c10 c11 c12-1 c12-2]]
    (index/wait-until-indexed)

    (testing "all revisions sorting"
      (testing "various sort keys"
        (are [sort-key items]
          (sort-order-correct? items sort-key true)
          "entry-title" (sort-revisions-by-field :entry-title false all-revisions true)
          "+entry-title" (sort-revisions-by-field :entry-title false all-revisions true)
          "-entry-title" (sort-revisions-by-field :entry-title true all-revisions true)
          ;; alias for entry_title
          "dataset_id" (sort-revisions-by-field :entry-title false all-revisions true)
          "-dataset_id" (sort-revisions-by-field :entry-title true all-revisions true)
          ;; Revision date is not returned (and therefore not available for
          ;; sort-revisions-by-field, so we rely on the fact that revision date defaults to
          ;; the current time, so ordering by revision date it the same as ordering by
          ;; insertion order.
          "revision_date" all-revisions
          "-revision_date" (reverse all-revisions)
          "entry_id" (sort-revisions-by-field :entry-id false all-revisions true)
          "-entry_id" (sort-revisions-by-field :entry-id true all-revisions true)
          "start_date" [c9-3 c9-2 c9-1 c10 c12-1 c5 c1-2 c1-1 c11 c2 c6 c3 c7 c4 c8 c12-2]
          "-start_date" [c8 c4 c7 c3 c6 c2 c11 c1-2 c1-1 c5 c9-3 c9-2 c9-1 c10 c12-1 c12-2]
          "end_date" [c5 c1-2 c1-1 c2 c6 c7 c3 c4 c8 c9-3 c9-2 c9-1 c10 c11 c12-2 c12-1]
          "-end_date" [c8 c4 c3 c7 c6 c2 c1-2 c1-1 c5 c9-3 c9-2 c9-1 c10 c11 c12-2 c12-1])))

    (testing "latest revisions sorting"
      (testing "various sort keys"
        (are [sort-key items]
          (sort-order-correct? items sort-key false)
          "entry-title" (sort-revisions-by-field :entry-title false all-colls)
          "+entry-title" (sort-revisions-by-field :entry-title false all-colls)
          "-entry-title" (sort-revisions-by-field :entry-title true all-colls)
          ;; alias for entry_title
          "dataset_id" (sort-revisions-by-field :entry-title false all-colls)
          "-dataset_id" (sort-revisions-by-field :entry-title true all-colls)
          ;; Revision date is not returned (and therefore not available for
          ;; sort-revisions-by-field, so we rely on the fact that revision date defaults to
          ;; the current time, so ordering by revision date it the same as ordering by
          ;; insertion order.
          "revision_date" all-colls
          "-revision_date" (reverse all-colls)
          "entry_id" (sort-revisions-by-field :entry-id false all-colls)
          "-entry_id" (sort-revisions-by-field :entry-id true all-colls)
          "start_date" [c10 c9-3 c5 c1-2 c11 c2 c6 c3 c7 c4 c8]
          "-start_date" [c8 c4 c7 c3 c6 c2 c11 c1-2 c5 c10 c9-3]
          "end_date" [c5 c1-2 c2 c6 c7 c3 c4 c8 c10 c9-3 c11]
          "-end_date" [c8 c4 c3 c7 c6 c2 c1-2 c5 c10 c9-3 c11])))))

(deftest default-sorting-test
  (let [c1 (make-coll "PROV1" "et99" 10 20)
        c2 (make-coll "PROV2" "et99" 14 24)
        c3 (make-coll "PROV2" "et80" 19 30)
        c4 (make-coll "PROV1" "et80" 24 35)
        all-colls [c1 c2 c3 c4]]
    (index/wait-until-indexed)
    (let [sorted-colls (sort-by (juxt (comp str/lower-case :entry-title)
                                      (comp str/lower-case :provider-id)) all-colls)]
      (is (d/refs-match-order?
            sorted-colls
            (search/find-refs :collection {:page-size 20})))
      (is (d/refs-match-order?
            sorted-colls
            (search/find-refs-with-aql :collection [] {}
                                       {:query-params {:page-size 20}}))))))

;; This tests that the default sorting for parameters that are scored is by the score found.
(deftest default-sorting-for-scored-parameters-is-by-score-test
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-bin-keyword-scores! false))
  (let [platform (dc/platform {:short-name "wood"
                               :instruments [(dc/instrument {:short-name "wood"
                                                             :sensors [(dc/sensor {:short-name "wood"})]})]})
        science-keyword (dc/science-keyword {:category "wood"
                                             :topic "wood"
                                             :term "wood"})
        projects (dc/projects "wood")
        two-d (dc/two-d "wood")

        ;; Common attributes for all collections
        common-attribs {:projects projects
                        :platforms [platform]
                        :two-d-coordinate-systems [two-d]
                        :science-keywords [science-keyword]
                        :processing-level-id "wood"
                        :organizations [(dc/org :archive-center "wood")]}

        ;; 1 will have the least relevance
        coll1 (d/ingest "PROV1" (dc/collection
                                 (merge common-attribs
                                        {:entry-title "coll1"})))
        ;; 2 has the most by having both spatial and temporal keywords that match the search term
        coll2 (d/ingest "PROV1" (dc/collection
                                 (merge common-attribs
                                        {:entry-title "coll2"
                                         :spatial-keywords ["wood"]
                                         :temporal-keywords ["wood"]})))
        ;; 3 has more than 1 but less than 2. It has spatial keywords that match the search term but
        ;; no temporal keywords
        coll3 (d/ingest "PROV1" (dc/collection
                                 (merge common-attribs
                                        {:entry-title "coll3"
                                         :spatial-keywords ["wood"]})))]
    (index/wait-until-indexed)

    (testing "Keyword searching is by score"
      (is (d/refs-match-order? [coll2 coll3 coll1] (search/find-refs :collection {:keyword "wood"})))
      (testing "Adding in other parameters still sorts by score"
        (is (d/refs-match-order?
             [coll2 coll3 coll1]
             (search/find-refs :collection {:keyword "wood"
                                            :platform "wood"})))))

    (testing "Everything else should default to entry title"
      (are [params]
        (d/refs-match-order? [coll1 coll2 coll3] (search/find-refs :collection params))
        {:platform "wood"}
        {:instrument "wood"}
        {:sensor "wood"}
        {:science-keywords {"0" {:any "wood"}}}
        {:project "wood"}
        {:two-d-coordinate-system-name "wood"}
        {:processing-level-id "wood"}
        {:data-center "wood"}
        {:archive-center "wood"})))
  (dev-sys-util/eval-in-dev-sys `(query-to-elastic/set-sort-bin-keyword-scores! true)))

(deftest multiple-sort-key-test
  (let [c1 (make-coll "PROV1" "et10" 10 nil)
        c2 (make-coll "PROV1" "et20" 10 nil)
        c3 (make-coll "PROV1" "et30" 10 nil)
        c4 (make-coll "PROV1" "et40" 10 nil)

        c5 (make-coll "PROV2" "et10" 20 nil)
        c6 (make-coll "PROV2" "et20" 20 nil)
        c7 (make-coll "PROV2" "et30" 20 nil)
        c8 (make-coll "PROV2" "et40" 20 nil)]
    (index/wait-until-indexed)

    (are [sort-key items]
         (sort-order-correct? items sort-key)
         ["entry_title" "start_date"] [c1 c5 c2 c6 c3 c7 c4 c8]
         ["entry_title" "-start_date"] [c5 c1 c6 c2 c7 c3 c8 c4]
         ["start_date" "entry_title"] [c1 c2 c3 c4 c5 c6 c7 c8]
         ["start_date" "-entry_title"] [c4 c3 c2 c1 c8 c7 c6 c5]
         ["-start_date" "entry_title"] [c5 c6 c7 c8 c1 c2 c3 c4]

         ;; Tests provider sorting for collections
         ["provider" "-entry_title"] [c4 c3 c2 c1 c8 c7 c6 c5]
         ["-provider" "-entry_title"] [c8 c7 c6 c5 c4 c3 c2 c1])))

(deftest collection-platform-sorting-test
  (let [make-collection (fn [& platforms]
                          (d/ingest "PROV1"
                                    (dc/collection
                                      {:platforms (map #(dc/platform {:short-name %}) platforms)})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "platform" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-platform" [c2 c5 c1 c4 c3])))

(deftest collection-instrument-sorting-test
  (let [make-collection (fn [& instruments]
                          (d/ingest
                            "PROV1"
                            (dc/collection
                              {:platforms [(dc/platform
                                             {:instruments (map #(dc/instrument {:short-name %})
                                                                instruments)})]})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "instrument" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-instrument" [c2 c5 c1 c4 c3])))

(deftest collection-sensor-sorting-test
  (let [make-collection (fn [& sensors]
                          (d/ingest
                            "PROV1"
                            (dc/collection
                              {:platforms [(dc/platform
                                             {:instruments
                                              [(dc/instrument
                                                 {:short-name (d/unique-str "instrument")
                                                  :sensors (map #(dc/sensor {:short-name %}) sensors)})]})]})))
        c1 (make-collection "c10" "c41")
        c2 (make-collection "c20" "c51")
        c3 (make-collection "c30")
        c4 (make-collection "c40")
        c5 (make-collection "c50")]
    (index/wait-until-indexed)
    (are [sort-key items]
         (sort-order-correct? items sort-key)

         ;; Descending sorts by the min value of a multi value fields
         "sensor" [c1 c2 c3 c4 c5]
         ;; Descending sorts by the max value of a multi value fields
         "-sensor" [c2 c5 c1 c4 c3])))

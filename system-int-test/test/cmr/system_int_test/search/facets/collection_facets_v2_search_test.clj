(ns cmr.system-int-test.search.facets.collection-facets-v2-search-test
  "This tests retrieving v2 facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.search.facets.facets-util :as fu]
            [cmr.system-int-test.search.facets.facet-responses :as fr]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.search.services.query-execution.facets.facets-v2-results-feature :as frf2]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :refer [are3]]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sk1 (dc/science-keyword {:category "Earth science"
                              :topic "Topic1"
                              :term "Term1"
                              :variable-level-1 "Level1-1"
                              :variable-level-2 "Level1-2"
                              :variable-level-3 "Level1-3"
                              :detailed-variable "Detail1"}))

(def sk2 (dc/science-keyword {:category "EARTH SCIENCE"
                              :topic "Popular"
                              :term "Extreme"
                              :variable-level-1 "Level2-1"
                              :variable-level-2 "Level2-2"
                              :variable-level-3 "Level2-3"
                              :detailed-variable "UNIVERSAL"}))

(def sk3 (dc/science-keyword {:category "EARTH SCIENCE"
                              :topic "Popular"
                              :term "UNIVERSAL"}))

(def sk4 (dc/science-keyword {:category "EARTH SCIENCE"
                              :topic "Popular"
                              :term "Alpha"}))

(def sk5 (dc/science-keyword {:category "EARTH SCIENCE"
                              :topic "Popular"
                              :term "Beta"}))

(def sk6 (dc/science-keyword {:category "EARTH SCIENCE"
                              :topic "Popular"
                              :term "Omega"}))

(defn- search-and-return-v2-facets
  "Returns the facets returned by a search requesting v2 facets."
  ([]
   (search-and-return-v2-facets {}))
  ([search-params]
   (index/wait-until-indexed)
   (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
     (get-in (search/find-concepts-json :collection query-params) [:results :facets]))))

(deftest all-facets-v2-test
  (fu/make-coll 1 "PROV1"
                (fu/science-keywords sk1 sk2)
                (fu/projects "proj1" "PROJ2")
                (fu/platforms fu/FROM_KMS 2 2 1)
                (fu/processing-level-id "PL1")
                {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
  (fu/make-coll 2 "PROV1"
                (fu/science-keywords sk1 sk3)
                (fu/projects "proj1" "PROJ2")
                (fu/platforms fu/FROM_KMS 2 2 1)
                (fu/processing-level-id "PL1")
                {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
  (is (= fr/expected-v2-facets-apply-links (search-and-return-v2-facets)))
  (testing "All fields applied for all facets"
    (let [search-params {:science-keywords-h {:0 {:category "Earth Science"
                                                  :topic "Topic1"
                                                  :term "Term1"
                                                  :variable-level-1 "Level1-1"
                                                  :variable-level-2 "Level1-2"
                                                  :variable-level-3 "Level1-3"}}
                         :project-h ["proj1"]
                         :platform-h ["DIADEM-1D"]
                         :instrument-h ["ATM"]
                         :processing-level-id-h ["PL1"]
                         :data-center-h "DOI/USGS/CMG/WHSC"}]
      (is (= fr/expected-v2-facets-remove-links (search-and-return-v2-facets search-params)))
      (testing "Some group fields not applied"
        (let [response (search-and-return-v2-facets
                        (dissoc search-params :platform-h :project-h :data-center-h))]
          (is (not (fu/applied? response :platform-h)))
          (is (not (fu/applied? response :project-h)))
          (is (not (fu/applied? response :data-center-h)))
          (is (fu/applied? response :science-keywords-h))
          (is (fu/applied? response :instrument-h))
          (is (fu/applied? response :processing-level-id-h)))))))

(def science-keywords-all-applied
  "Facet response with just the title, applied, and children fields. Used to verify that when
  searching for the deepest nested field (a value of Level1-3 for variable-level-3) all of the
  fields above variable-level-3 have applied set to true."
  {:title "Browse Collections",
   :children
   [{:title "Keywords" :applied true
     :children
     [{:title "Topic1" :applied true
       :children
       [{:title "Term1" :applied true
         :children
         [{:title "Level1-1" :applied true
           :children
           [{:title "Level1-2" :applied true
             :children
             [{:title "Level1-3" :applied true}]}]}]}]}]}]})

(defn- verify-nested-facets-ordered-alphabetically
  "Recursively verify that all of the values at each level in a collection of nested facets are in
  alphabetical order."
  [facets]
  (is (fu/in-alphabetical-order? (map :title facets)))
  (mapv #(when (:children %) (verify-nested-facets-ordered-alphabetically (:children %))) facets))

(deftest facet-v2-sorting
  ;; 55 platforms all with the same count (2) and default priority
  (fu/make-coll 1 "PROV1" (fu/platforms "default" 55)
                          (fu/science-keywords sk1 sk2 sk3 sk4 sk5 sk6))
  (fu/make-coll 2 "PROV1" (fu/platforms "default" 55) (fu/science-keywords sk1 sk2))

  ;; 1 platform with a count of 1 but high priority, so it should appear
  (fu/make-coll 3 "PROV1" {:platforms [(dc/platform {:short-name "Terra"})]}
                (fu/science-keywords sk2 sk5))
  ;; 55 platforms with a count of 1, none of which should appear
  (fu/make-coll 4 "PROV1" (fu/platforms "low" 55) (fu/science-keywords sk2 sk5))

  (testing "Platform sorting behavior"
    (let [response (search-and-return-v2-facets)]
      (testing "high priority items appear regardless of count"
        (is (fu/facet-included? response :platform-h "Terra")))

      (testing "same-priority items appear based on highest count"
        (is (not-any? #(= "low" (subs % 0 3)) (fu/facet-values response :platform-h))))

      (testing "items are sorted alphabetically"
        (let [index (partial fu/facet-index response :platform-h)]
          (is (< (index "default-p2") (index "default-p10")))
          (is (< (index "default-p0") (index "Terra"))))))

    (testing "Science keywords are sorted alphabetically"
      (let [response (search-and-return-v2-facets
                      {:science-keywords-h {:0 {:topic "Popular"}}})
            science-keywords (-> (:children response) first :children)]
        (verify-nested-facets-ordered-alphabetically science-keywords)))))

(deftest remove-facets-without-collections
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk1) (fu/platforms "ASTER" 1))
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk1) (fu/platforms "MODIS" 1))
  (testing (str "When searching against faceted fields which do not match any matching collections,"
                " a link should be provided so that the user can remove the term from their search"
                " for all fields except for science keywords category.")
    (let [search-params {:science-keywords-h {:0 {:category "Earth Science"
                                                  :topic "Topic1"
                                                  :term "Term1"
                                                  :variable-level-1 "Level1-1"
                                                  :variable-level-2 "Level1-2"
                                                  :variable-level-3 "Level1-3"}}
                         :project-h ["proj1"]
                         :platform-h ["ASTER-p0"]
                         :instrument-h ["ATM"]
                         :processing-level-id-h ["PL1"]
                         :data-center-h "DOI/USGS/CMG/WHSC"
                         :keyword "MODIS"}
          response (search-and-return-v2-facets search-params)]
      (is (= fr/expected-facets-with-no-matching-collections response))))
  (testing "Facets with multiple facets applied, some with matching collections, some without"
    (is (= fr/expected-facets-modis-and-aster-no-results-found
           (search-and-return-v2-facets {:platform-h ["moDIS-p0", "ASTER-p0"]
                                         :keyword "MODIS"})))))

(deftest appropriate-hierarchical-depth
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk1 sk2))
  (testing "Default to one level without any search parameters"
    (is (= 1 (fu/get-lowest-hierarchical-depth (search-and-return-v2-facets {})))))
  (are [sk-param expected-depth]
    (= expected-depth (fu/get-lowest-hierarchical-depth
                       (search-and-return-v2-facets {:science-keywords-h {:0 sk-param}})))

    {:category "Earth Science"} 1
    {:topic "Topic1"} 2
    {:topic "Topic1" :category "Earth Science"} 2
    {:term "Term1" :category "Earth Science" :topic "Topic1"} 3
    {:variable-level-1 "Level1-1" :term "Term1" :category "Earth Science" :topic "Topic1"} 4
    {:variable-level-2 "Level1-2" :term "Term1" :category "Earth Science" :topic "Topic1"
     :variable-level-1 "Level1-1"} 5
    {:variable-level-3 "Level1-3" :variable-level-2 "Level1-2" :term "Term1"
     :category "Earth Science" :topic "Topic1" :variable-level-1 "Level1-1"} 5))

(def empty-v2-facets
  "The facets returned when there are no matching facets for the search."
  {:title "Browse Collections"
   :type "group"
   :has_children false})

(deftest empty-v2-facets-test
  (is (= empty-v2-facets (search-and-return-v2-facets))))

(deftest some-facets-missing-test
  (fu/make-coll 1 "PROV1"
                (fu/science-keywords sk3 sk2)
                (fu/processing-level-id "PL1"))
  (is (= fr/partial-v2-facets (search-and-return-v2-facets))))

(deftest only-earth-science-category-test
  (let [non-earth-science-keyword (dc/science-keyword {:category "Cat1"
                                                       :topic "OtherTopic"
                                                       :term "OtherTerm"})]
    (fu/make-coll 1 "PROV1" (fu/science-keywords non-earth-science-keyword))
    (testing "No facets included because there are no collections under the Earth Science category"
      (is (= empty-v2-facets (search-and-return-v2-facets))))
    (testing "Non Earth Science category facets are not included in v2 facets"
      (fu/make-coll 1 "PROV1" (fu/science-keywords sk1 non-earth-science-keyword))
      (let [expected-facets {:title "Browse Collections",
                             :children
                             [{:title "Keywords",
                               :children
                               [{:title "Topic1"}]}]}]
        (is (= expected-facets (fu/prune-facet-response (search-and-return-v2-facets) [:title])))))))

(def sk-all (dc/science-keyword {:category "Earth science"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"}))

(def sk-same-vl1 (dc/science-keyword {:category "Earth science"
                                      :topic "Topic1"
                                      :term "Term2"
                                      :variable-level-1 "Level1-1"
                                      :variable-level-2 "Level2-2"
                                      :variable-level-3 "Level2-3"}))

(def sk-diff-vl1 (dc/science-keyword {:category "Earth science"
                                      :topic "Topic1"
                                      :term "Term1"
                                      :variable-level-1 "Another Level"}))

(deftest link-traversal-test
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all))
  (testing (str "Traversing a single hierarchical keyword returns the same index for all subfields "
                "in the remove links")
    (is (= #{"0"} (->> (search-and-return-v2-facets)
                       fu/traverse-hierarchical-links-in-order
                       fu/get-all-links
                       (mapcat fu/get-science-keyword-indexes-in-link)
                       set))))
  (testing (str "Selecting a field with the same name in another hierarchical field will result in "
                "only the correct hierarchical field from being applied in the facets.")
    (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
    (let [expected {:title "Browse Collections"
                    :children
                    [{:title "Keywords" :applied true
                      :children
                      [{:title "Topic1" :applied true
                        :children
                        [{:title "Term1" :applied true
                          :children
                          [{:title "Level1-1" :applied true
                            :children [{:title "Level1-2", :applied false}]}]}
                         {:title "Term2" :applied false}]}]}]}
          actual (-> (search-and-return-v2-facets)
                     (fu/traverse-links ["Keywords" "Topic1" "Term1" "Level1-1"])
                     (fu/prune-facet-response [:title :applied]))]
      (is (= expected actual))))
  (testing (str "When there are multiple fields selected at the same level of the hierarchy they "
                "each have different indexes")
    (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1 sk-diff-vl1))
    (let [expected #{0 1 2 3} ;; Went from 3 to 4 values - Not sure why 2 is not included TODO add unit tests for has-siblings?
          actual (->> (search-and-return-v2-facets)
                      fu/traverse-hierarchical-links-in-order
                      fu/get-all-links
                      (mapcat fu/get-science-keyword-indexes-in-link)
                      (map #(Integer/parseInt %))
                      set)]
      (is (= expected actual))))

  (testing (str "Scenario: Traverse links so that there are multiple children with different "
                "indexes such that the one with the different index from the parent is a term that "
                "shows up in two different hierarchies. Stay with me... Then remove the other term "
                "(the one with the same index as parent) and verify that only the correct "
                "hierarchical term is applied.")
    (let [expected {:title "Browse Collections"
                    :children
                    [{:title "Keywords" :applied true
                      :children
                      [{:title "Topic1" :applied true
                        :children
                        [{:title "Term1" :applied true
                          :children
                          [{:title "Another Level" :applied false}
                           {:title "Level1-1" :applied true
                            :children [{:title "Level1-2" :applied false}]}]}
                         {:title "Term2" :applied false}]}]}]}

          actual (-> (search-and-return-v2-facets)
                     (fu/traverse-links ["Keywords" "Topic1" "Term1" "Another Level"])
                     (fu/traverse-links ["Keywords" "Topic1" "Term1" "Level1-1"])
                     ;; Click the remove link to remove "Another Level" leaving "Level1-1"
                     (fu/click-link ["Keywords" "Topic1" "Term1" "Another Level"])
                     (fu/prune-facet-response [:title :applied]))]
      (is (= expected actual)))))

(deftest invalid-facets-v2-response-formats
  (testing "invalid xml response formats"
    (are [resp-format]
         (= {:status 400 :errors ["V2 facets are only supported in the JSON format."]}
            (search/get-search-failure-xml-data
              (search/find-concepts-in-format resp-format :collection {:include-facets "v2"})))
         mt/echo10
         mt/dif
         mt/dif10
         mt/xml
         mt/kml
         mt/atom
         mt/iso19115))

  (testing "invalid json response formats"
     (are [resp-format]
         (= {:status 400 :errors ["V2 facets are only supported in the JSON format."]}
            (search/get-search-failure-data
              (search/find-concepts-in-format resp-format :collection {:include-facets "v2"})))
         mt/umm-json
         mt/opendata)))

(comment
 ;; Good for manually testing applying links
 (do
   (cmr.system-int-test.utils.ingest-util/create-provider {:provider-id "PROV1" :provider-guid "prov1guid"})
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1 sk-diff-vl1))))

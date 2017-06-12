(ns cmr.system-int-test.search.facets.collection-facets-v2-search-test
  "This tests retrieving v2 facets when searching for collections"
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as frf2]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.search.facets.facet-responses :as fr]
   [cmr.system-int-test.search.facets.facets-util :as fu]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture
                       variable-util/grant-all-variable-fixture]))

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
  (let [token (e/login (s/context) "user1")
        coll1 (fu/make-coll 1 "PROV1"
                            (fu/science-keywords sk1 sk2)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/processing-level-id "PL1")
                            {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
        coll2 (fu/make-coll 2 "PROV1"
                            (fu/science-keywords sk1 sk3)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/processing-level-id "PL1")
                            {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})]
    (index/wait-until-indexed)
    ;; create variables
    (variable-util/create-variable-with-attrs token
                                              {:Name "Variable1"
                                               :LongName "Measurement1"})
    (variable-util/create-variable-with-attrs token
                                              {:Name "Variable2"
                                               :LongName "Measurement2"})
    ;; create variable associations
    (variable-util/associate-by-concept-ids token
                                            "variable1"
                                            [{:concept-id (:concept-id coll1)}
                                             {:concept-id (:concept-id coll2)}])
    (variable-util/associate-by-concept-ids token
                                            "variable2"
                                            [{:concept-id (:concept-id coll2)}]))
  (index/wait-until-indexed)
  (testing "No fields applied for facets"
    (is (= fr/expected-v2-facets-apply-links (search-and-return-v2-facets))))
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
                       :data-center-h "DOI/USGS/CMG/WHSC"
                       :variables-h {:0 {:measurement "Measurement1"
                                         :variable "Variable1"}}}]
    (testing "All fields applied for facets"
      (is (= fr/expected-v2-facets-remove-links (search-and-return-v2-facets search-params))))
    (testing "Some fields not applied for facets"
      (let [response (search-and-return-v2-facets
                      (dissoc search-params :platform-h :project-h :data-center-h))]
        (is (not (fu/applied? response :platform-h)))
        (is (not (fu/applied? response :project-h)))
        (is (not (fu/applied? response :data-center-h)))
        (is (fu/applied? response :science-keywords-h))
        (is (fu/applied? response :variables-h))
        (is (fu/applied? response :instrument-h))
        (is (fu/applied? response :processing-level-id-h))))))

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
  ;; (fu/platforms "ASTER" 1) will create platform with ShortName ASTER-p0, not ASTER
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
    (is (= #{0} (->> (search-and-return-v2-facets)
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
    (let [expected #{0 1 2 3}
          actual (->> (search-and-return-v2-facets)
                      fu/traverse-hierarchical-links-in-order
                      fu/get-all-links
                      (mapcat fu/get-science-keyword-indexes-in-link)
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

(defn- get-facet-field
  "Returns the facet in result that matches the given facet field and value"
  [facets-result field value]
  (let [field-facet (first (get (group-by :title (:children facets-result)) field))]
    (some #(when (= value (:title %)) %) (:children field-facet))))

(defn- assert-facet-field-not-exist
  "Assert the given facet field with name does not exist in the facets result"
  [facets-result field value]
  (let [field-match-value (get-facet-field facets-result field value)]
    (is (nil? field-match-value))))

(defn- assert-facet-field
  "Assert the given facet field with name and count matches the facets result"
  [facets-result field value count]
  (let [field-match-value (get-facet-field facets-result field value)]
    (is (= count (:count field-match-value)))))

(deftest platform-facets-v2-test
  (let [coll1 (d/ingest "PROV1" (dc/collection
                                {:entry-title "coll1"
                                 :short-name "S1"
                                 :version-id "V1"
                                 :platforms (dc/platforms "P1")}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll2"
                                  :short-name "S2"
                                  :version-id "V2"
                                  :platforms (dc/platforms "P1" "P2")}))
        coll3 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll3"
                                  :short-name "S3"
                                  :version-id "V3"
                                  :platforms [(dc/platform
                                               {:short-name "P2"
                                                :instruments [(dc/instrument {:short-name "I3"})]})]
                                  :projects (dc/projects "proj3")}))
        coll4 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll4"
                                  :short-name "S4"
                                  :version-id "V4"
                                  :platforms [(dc/platform
                                               {:short-name "P4"
                                                :instruments [(dc/instrument {:short-name "I4"})]})]
                                  :projects (dc/projects "proj4")}))]
    (testing "search by platform parameter filters the other facets, but not platforms facets"
      (let [facets-result (search-and-return-v2-facets {:platform-h ["P4"]})]
        (assert-facet-field facets-result "Platforms" "P1" 2)
        (assert-facet-field facets-result "Platforms" "P2" 2)
        (assert-facet-field facets-result "Platforms" "P4" 1)
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))

    (testing "search by multiple platforms"
      (let [facets-result (search-and-return-v2-facets {:platform-h ["P1" "P2"]})]
        (assert-facet-field facets-result "Platforms" "P1" 2)
        (assert-facet-field facets-result "Platforms" "P2" 2)
        (assert-facet-field facets-result "Platforms" "P4" 1)
        (assert-facet-field facets-result "Instruments" "I3" 1)
        (assert-facet-field facets-result "Projects" "proj3" 1)
        (assert-facet-field-not-exist facets-result "Instruments" "I4")
        (assert-facet-field-not-exist facets-result "Projects" "proj4")))

    (testing "search by params other than platform"
      (let [facets-result (search-and-return-v2-facets {:instrument-h ["I4"]})]
        (assert-facet-field facets-result "Platforms" "P4" 1)
        (assert-facet-field facets-result "Instruments" "I3" 1)
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P1")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))

    (testing "search by both facet field param and regular param"
      (let [facets-result (search-and-return-v2-facets {:short-name "S1"
                                                        :platform-h ["P4"]})]
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field facets-result "Platforms" "P4" 0)
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Instruments" "I4")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")
        (assert-facet-field-not-exist facets-result "Projects" "proj4")))

    (testing "search by more than one facet field params"
      (let [facets-result (search-and-return-v2-facets {:platform-h ["P4"]
                                                        :instrument-h ["I4"]})]
        (assert-facet-field facets-result "Platforms" "P4" 1)
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P1")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))))

(deftest science-keywords-facets-v2-test
  (let [coll1 (d/ingest "PROV1" (dc/collection
                                {:entry-title "coll1"
                                 :short-name "S1"
                                 :version-id "V1"
                                 :platforms (dc/platforms "P1")
                                 :science-keywords [(dc/science-keyword
                                                     {:category "Earth Science"
                                                      :topic "Topic1"
                                                      :term "Term1"
                                                      :variable-level-1 "Level1-1"
                                                      :variable-level-2 "Level1-2"
                                                      :variable-level-3 "Level1-3"
                                                      :detailed-variable "Detail1"})]}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll2"
                                  :short-name "S2"
                                  :version-id "V2"
                                  :platforms (dc/platforms "P2")
                                  :science-keywords [(dc/science-keyword
                                                      {:category "Earth Science"
                                                       :topic "Topic2"
                                                       :term "Term2"
                                                       :variable-level-1 "Level2-1"
                                                       :variable-level-2 "Level2-2"})]}))
        coll3 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll3"
                                  :short-name "S3"
                                  :version-id "V3"
                                  :platforms (dc/platforms "P3")
                                  :science-keywords [(dc/science-keyword
                                                      {:category "Earth Science"
                                                       :topic "Topic1"
                                                       :term "Term3"})
                                                     (dc/science-keyword
                                                      {:category "Earth Science"
                                                       :topic "Topic2"
                                                       :term "Term3"})]}))]
    ;; We only check the topic level science keywords facet for convenience since the whole
    ;; hierarchical structure of science keywords facet has been covered in all facets test.
    (testing "search by science-keywords param filters the other facets, but not science-keywords facets"
      (let [facets-result (search-and-return-v2-facets
                           {:science-keywords-h {:0 {:topic "Topic1"
                                                     :term "Term1"
                                                     :variable-level-1 "Level1-1"
                                                     :variable-level-2 "Level1-2"
                                                     :variable-level-3 "Level1-3"}}})]
        (assert-facet-field facets-result "Keywords" "Topic1" 2)
        (assert-facet-field facets-result "Keywords" "Topic2" 2)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))

    (testing "search by both science-keywords param and regular param"
      (let [facets-result (search-and-return-v2-facets
                           {:short-name "S1"
                            :science-keywords-h {:0 {:topic "Topic1"}}})]
        (assert-facet-field facets-result "Keywords" "Topic1" 1)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field-not-exist facets-result "Keywords" "Topic2")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))
    (testing "search by both science-keywords param and a facet field param, with science keywords match a collection"
      (let [facets-result (search-and-return-v2-facets
                           {:platform-h "P1"
                            :science-keywords-h {:0 {:topic "Topic1"
                                                     :term "Term1"}}})]
        (assert-facet-field facets-result "Keywords" "Topic1" 1)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field-not-exist facets-result "Keywords" "Topic2")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))
    (testing "search by both science-keywords param and a facet field param, with science keywords match two collections"
      (let [facets-result (search-and-return-v2-facets
                           {:platform-h "P3"
                            :science-keywords-h {:0 {:topic "Topic1"}}})]
        (assert-facet-field facets-result "Keywords" "Topic1" 1)
        (assert-facet-field facets-result "Keywords" "Topic2" 1)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field facets-result "Platforms" "P3" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P2")))))

(deftest organization-facets-v2-test
  (let [org1 (dc/org :archive-center "DOI/USGS/CMG/WHSC")
        org2 (dc/org :processing-center "LPDAAC")
        org3 (dc/org :archive-center "NSIDC")
        coll1 (d/ingest "PROV1" (dc/collection
                                {:entry-title "coll1"
                                 :short-name "S1"
                                 :version-id "V1"
                                 :organizations [org1]}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll2"
                                  :short-name "S2"
                                  :version-id "V2"
                                  :organizations [org2]}))
        coll3 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll3"
                                  :short-name "S3"
                                  :version-id "V3"
                                  :organizations [org1 org2]
                                  :projects (dc/projects "proj3")}))
        coll4 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll4"
                                  :short-name "S4"
                                  :version-id "V4"
                                  :organizations [org3]
                                  :projects (dc/projects "proj4")}))]
    (testing "search by data-center parameter filters the other facets, but not Organizations facets"
      (let [facets-result (search-and-return-v2-facets
                           {:data-center-h ["NSIDC"]})]
        (assert-facet-field facets-result "Organizations" "DOI/USGS/CMG/WHSC" 2)
        (assert-facet-field facets-result "Organizations" "LPDAAC" 2)
        (assert-facet-field facets-result "Organizations" "NSIDC" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))

    (testing "search by multiple data-centers"
      (let [facets-result (search-and-return-v2-facets
                           {:data-center-h ["LPDAAC" "NSIDC"]})]
        (assert-facet-field facets-result "Organizations" "DOI/USGS/CMG/WHSC" 2)
        (assert-facet-field facets-result "Organizations" "LPDAAC" 2)
        (assert-facet-field facets-result "Organizations" "NSIDC" 1)
        (assert-facet-field facets-result "Projects" "proj3" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)))

    (testing "search by params other than data-center"
      (let [facets-result (search-and-return-v2-facets
                           {:project-h ["proj4"]})]
        (assert-facet-field facets-result "Organizations" "NSIDC" 1)
        (assert-facet-field facets-result "Projects" "proj3" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Organizations" "DOI/USGS/CMG/WHSC")
        (assert-facet-field-not-exist facets-result "Organizations" "LPDAAC")))

    (testing "search by both data-center facet field param and regular param"
      (let [facets-result (search-and-return-v2-facets
                           {:short-name "S1"
                            :data-center-h ["NSIDC"]})]
        (assert-facet-field facets-result "Organizations" "DOI/USGS/CMG/WHSC" 1)
        (assert-facet-field facets-result "Organizations" "NSIDC" 0)
        (assert-facet-field-not-exist facets-result "Organizations" "LPDAAC")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")
        (assert-facet-field-not-exist facets-result "Projects" "proj4")))

    (testing "search by more than one facet field params"
      (let [facets-result (search-and-return-v2-facets {:data-center-h ["NSIDC"]
                                                        :project-h ["proj4"]})]
        (assert-facet-field facets-result "Organizations" "NSIDC" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Organizations" "DOI/USGS/CMG/WHSC")
        (assert-facet-field-not-exist facets-result "Organizations" "LPDAAC")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))))

(deftest dummy-service-facets
  (testing "Service facets are not returned by default"
    (let [facets-result (search-and-return-v2-facets {})]
      (are [facet-group facet-value]
        (assert-facet-field-not-exist facets-result facet-group facet-value)

        "Output File Formats" "ASCII"
        "Output File Formats" "GeoTIFF"
        "Output File Formats" "NetCDF4-CF"
        "Reprojections" "Geographic"
        "Reprojections" "Lambert Conic Conformal"
        "Reprojections" "Universal Transverse Mercator")))

  (testing "Service facets returned when configured on"
    (dev-sys-util/eval-in-dev-sys
     `(cmr.search.services.query-execution.facets.facets-v2-results-feature/set-include-service-facets!
       true))
    (let [facets-result (search-and-return-v2-facets {})]
      (are [facet-group facet-value facet-count]
        (assert-facet-field facets-result facet-group facet-value facet-count)

        "Output File Formats" "ASCII" 4
        "Output File Formats" "GeoTIFF" 15
        "Output File Formats" "NetCDF4-CF" 8
        "Reprojections" "Geographic" 14
        "Reprojections" "Lambert Conic Conformal" 1
        "Reprojections" "Universal Transverse Mercator" 3))
    (dev-sys-util/eval-in-dev-sys
     `(cmr.search.services.query-execution.facets.facets-v2-results-feature/set-include-service-facets!
       false))))

(deftest variables-facets-v2-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll1"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :platforms (dc/platforms "P1")}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll2"
                                  :short-name "S2"
                                  :version-id "V2"
                                  :platforms (dc/platforms "P2")}))
        coll3 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll3"
                                  :short-name "S3"
                                  :version-id "V3"
                                  :platforms (dc/platforms "P3")}))
        coll4 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll4"
                                  :short-name "S4"
                                  :version-id "V4"}))]

    ;; index the collections so that they can be found during variable association
    (index/wait-until-indexed)
    ;; create variables
    (variable-util/create-variable-with-attrs token
                                              {:Name "Variable1"
                                               :LongName "Measurement1"})
    (variable-util/create-variable-with-attrs token
                                              {:Name "Variable2"
                                               :LongName "Measurement2"})
    (variable-util/create-variable-with-attrs token
                                              {:Name "SomeVariable"
                                               :LongName "Measurement2"})
    ;; create variable associations
    ;; variable1 is associated with coll1 and coll2
    ;; variable2 is associated with coll2 and coll3
    ;; somevariable is associated with coll4
    (variable-util/associate-by-concept-ids token
                                            "variable1"
                                            [{:concept-id (:concept-id coll1)}
                                             {:concept-id (:concept-id coll2)}])
    (variable-util/associate-by-concept-ids token
                                            "variable2"
                                            [{:concept-id (:concept-id coll2)}
                                             {:concept-id (:concept-id coll3)}])
    (variable-util/associate-by-concept-ids token
                                            "somevariable"
                                            [{:concept-id (:concept-id coll4)}])
    (index/wait-until-indexed)

    ;; We only check the top level variables facet for convenience since the whole
    ;; hierarchical structure of variables facet has been covered in all facets test.
    (testing "search by variables param filters the other facets, but not variables facets"
      (let [facets-result (search-and-return-v2-facets
                           {:variables-h {:0 {:variable "Variable1"}}})]
        (assert-facet-field facets-result "Measurements" "Measurement1" 2)
        (assert-facet-field facets-result "Measurements" "Measurement2" 3)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field facets-result "Platforms" "P2" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))

    (testing "search by both variables param and regular param"
      (let [facets-result (search-and-return-v2-facets
                           {:short-name "S1"
                            :variables-h {:0 {:variable "Variable1"}}})]
        (assert-facet-field facets-result "Measurements" "Measurement1" 1)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field-not-exist facets-result "Measurements" "Measurement2")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))

    (testing "search by both variables param and another facet field param"
      (let [facets-result (search-and-return-v2-facets
                           {:platform-h "P1"
                            :variables-h {:0 {:variable "Variable1"}}})]
        (assert-facet-field facets-result "Measurements" "Measurement1" 1)
        (assert-facet-field facets-result "Platforms" "P1" 1)
        (assert-facet-field facets-result "Platforms" "P2" 1)
        (assert-facet-field-not-exist facets-result "Measurements" "Measurement2")
        (assert-facet-field-not-exist facets-result "Platforms" "P3")))))

(comment
 ;; Good for manually testing applying links
 (do
   (cmr.system-int-test.utils.ingest-util/create-provider {:provider-id "PROV1" :provider-guid "prov1guid"})
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1 sk-diff-vl1))))

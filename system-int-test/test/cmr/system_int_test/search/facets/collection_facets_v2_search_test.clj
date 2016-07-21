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
            [clj-http.client :as client]
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
   [{:title "Keywords", :applied true,
     :children
     [{:title "Topic1", :applied true,
       :children
       [{:title "Term1", :applied true,
         :children
         [{:title "Level1-1", :applied true,
           :children
           [{:title "Level1-2", :applied true,
             :children
             [{:title "Level1-3", :applied true}]}]}]}]}]}]})

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


(def partial-science-keywords-applied
  "Facet response with just the title, applied, and children fields. Used to verify that when
  searching for a nested field (a value of TERM1 for term) all of the fields above term have
  applied set to true and any fields below have applied set to false. Also only one level below
  the last applied term is returned. In the case of searching for a term, only variable-level-1
  should be returned. Both variable-level-2 and variable-level-3 should be omitted from the
  response."
  {:title "Browse Collections",
   :children
   [{:title "Keywords", :applied true,
     :children
     [{:title "Topic1", :applied true,
       :children
       [{:title "Term1", :applied true,
         :children
         [{:title "Level1-1", :applied false}]}]}]}]})

(deftest hierarchical-applied-test
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk1))
  (testing "Children science keywords applied causes parent fields to be marked as applied"
    (are3 [search-params expected-response]
      (is (= expected-response (fu/prune-facet-response (search-and-return-v2-facets search-params)
                                                        [:title :applied])))

      "Lowest level field causes all fields above to be applied."
      {:science-keywords-h {:0 {:variable-level-3 "Level1-3"}}}
      science-keywords-all-applied

      "Middle level field causes all fields above to be applied, but not fields below."
      {:science-keywords-h {:0 {:term "Term1"}}}
      partial-science-keywords-applied)))

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

(defn- get-lowest-hierarchical-depth
  "Returns the lowest hierachical depth within the facet response for any hierarchical fields."
  ([facet]
   (get-lowest-hierarchical-depth facet -1))
  ([facet current-depth]
   (apply max
          current-depth
          (map #(get-lowest-hierarchical-depth % (inc current-depth))
              (:children facet)))))

(deftest appropriate-hierarchical-depth
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk1 sk2))
  (testing "Default to one level without any search parameters"
    (is (= 1 (get-lowest-hierarchical-depth (search-and-return-v2-facets {})))))
  (are [sk-param expected-depth]
    (= expected-depth (get-lowest-hierarchical-depth (search-and-return-v2-facets
                                                      {:science-keywords-h {:0 sk-param}})))

    {:category "Earth Science"} 1
    {:topic "Topic1"} 2
    {:topic "Topic1" :category "Earth Science"} 2
    {:term "Term1"} 3
    {:term "Term1" :category "Earth Science"} 3
    {:term "Term1" :category "Earth Science" :topic "Topic1"} 3
    {:variable-level-1 "Level1-1"} 4
    {:variable-level-1 "Level1-1" :term "Term1" :category "Earth Science" :topic "Topic1"} 4
    {:variable-level-2 "Level1-2" :term "Term1" :category "Earth Science" :topic "Topic1"
     :variable-level-1 "Level1-1"} 5
    {:variable-level-3 "Level1-3"} 5
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

(defn- find-first-apply-link
  "Takes a facet response and recursively finds the first apply link starting at the top node."
  [facet-response]
  (if-let [apply-link (get-in facet-response [:links :apply])]
    apply-link
    (when-let [first-apply-link (some find-first-apply-link (:children facet-response))]
      first-apply-link)))

(defn- traverse-hierarchical-links-in-order
  "Takes a facet response and recursively clicks on the first apply link in the hierarchy until
   every link has been applied."
  [facet-response]
  (if-let [apply-link (find-first-apply-link facet-response)]
    (let [response (get-in (client/get apply-link {:as :json}) [:body :feed :facets])]
      (traverse-hierarchical-links-in-order response))
    ;; All links have been applied
    facet-response))

(comment
 (get-science-keyword-indexes-in-link "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1&science_keywords_h%5B1%5D%5Bterm%5D=Term1&science_keywords_h%5B2%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B3%5D%5Bvariable_level_2%5D=Level1-2")
 (get-science-keyword-indexes-in-link "http://localhost:3003/collections.json?page_size=0&include_facets=v2"))

(defn- get-science-keyword-indexes-in-link
  "Returns a sequence of all of the science keyword indexes in link or nil if no science keywords
  are in the link."
  [link]
  (let [index-regex #"science_keywords_h%5B(\d+)%5D"
        matcher (re-matcher index-regex link)]
    (loop [matches (re-find matcher)
           all-indexes nil]
      (if-not matches
        all-indexes
        (recur (re-find matcher) (conj all-indexes (second matches)))))))

(defn- get-all-links
  "Returns all of the links in a facet response."
  ([facet-response]
   (get-all-links facet-response nil))
  ([facet-response links]
   (let [link (first (vals (:links facet-response)))
         sublinks (mapcat #(get-all-links % links) (:children facet-response))]
     (if link
       (conj sublinks link)
       sublinks))))

(defn- traverse-hierarchy
  "Takes a collection of title strings and follows the apply links for each title in order.
  Example: [\"Keywords\" \"Agriculture\" \"Agricultural Aquatic Sciences\" \"Aquaculture\"]"
  [facet-response titles]
  ; (println "Called with " facet-response "and titles" titles)
  (let [child-facet (first (filter #(= (first titles) (:title %)) (:children facet-response)))]
        ; _ (println "Child facet is" child-facet)]
    (if-let [link (get-in child-facet [:links :apply])]
      ;; Need to apply the link and try again
      (let [facet-response (get-in (client/get link {:as :json}) [:body :feed :facets])]
        (traverse-hierarchy facet-response titles))
      ;; Ok good the node has been applied or it is a group node - move to the next title
      (loop [remaining-titles (rest titles)
             child-facet (first (filter #(= (first remaining-titles) (:title %))
                                        (:children child-facet)))]
        ; (println "CDD: I am in the loop with " child-facet "and titles" remaining-titles)
        (if (seq remaining-titles)
          ;; Check to see if any links need to be applied
          (if-let [link (get-in child-facet [:links :apply])]
            ;; Need to apply the link and try again
            (let [facet-response (get-in (client/get link {:as :json}) [:body :feed :facets])]
              (traverse-hierarchy facet-response titles))
            ;; Else try to traverse the next title
            (let [remaining-titles (rest remaining-titles)]
                  ; _ (println "Comparing" (first remaining-titles) "to" ())]
              (recur remaining-titles
                     (first (filter #(= (first remaining-titles) (:title %))
                                    (:children child-facet))))))
          ;; We are done return the facet response
          facet-response)))))

(deftest link-traversal-test
  (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all))
  (testing (str "Traversing a single hierarchical keyword returns the same index for all subfields "
                "in the remove links"))
    ; (is (= #{"0"} (->> (search-and-return-v2-facets)
    ;                    traverse-hierarchical-links-in-order
    ;                    get-all-links
    ;                    (mapcat get-science-keyword-indexes-in-link)
    ;                    set))))
  (testing (str "Selecting a field with the same name in another hierarchical field will result in "
                "only the correct hierarchical field from being applied in the facets.")
    (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
    (let [expected {:title "Browse Collections",
                    :children
                    [{:title "Keywords",
                      :applied true,
                      :children
                      [{:title "Topic1",
                        :applied true,
                        :children
                        [{:title "Term1",
                          :applied true,
                          :children
                          [{:title "Level1-1",
                            :applied true,
                            :children [{:title "Level1-2", :applied false}]}]}]}]}]}
          actual (-> (search-and-return-v2-facets)
                     (traverse-hierarchy ["Keywords" "Topic1" "Term1" "Level1-1"])
                     (fu/prune-facet-response [:title :applied]))])))
      ; (is (= expected actual)))))

  ;; Traversing so that there are multiple children with different indexes and then removing the
  ;; one with the same index might potentially cause a problem.

; (deftest science-keywords-connected-test
;   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
;   (are3 [search-params expected-response]
;     (is (= expected-response (search-and-return-v2-facets search-params)))
;
;     (str "Remove links are generated correctly when the same lower level field is selected "
;          "in two different hierarchical keywords")
;     {:science-keywords-h {:0 {:category "Earth Science"
;                               :topic "Topic1"
;                               :term "Term1"
;                               :variable-level-1 "Level1-1"}}}
;     nil
;
;     (str "When there are multiple fields selected at the same level of the hierarchy they "
;          "each have different indexes with exactly one matching the parent index")

;; NOTE: This can only be done by manually manipulating the URLS (not by following links)
;     (str "When a subfield exists in two different hierarchies, is selected, but no direct "
;          "parent is selected, the field is marked as applied in both hierarchies")))

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

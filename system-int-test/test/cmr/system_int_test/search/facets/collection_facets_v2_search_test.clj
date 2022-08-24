(ns cmr.system-int-test.search.facets.collection-facets-v2-search-test
  "This tests retrieving v2 facets when searching for collections"
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.services.query-execution.facets.collection-v2-facets :as frf2]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.data2.umm-spec-common :as umm-spec-common]
   [cmr.system-int-test.search.facets.facet-responses :as fr]
   [cmr.system-int-test.search.facets.facets-util :as fu]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
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

(defn- platform-map
  "Create a platform map using the supplied values. Values are an array"
  [values]
  (zipmap [:basis :category :sub-category :short-name :long-name] values))

; organized by a common name followed by a map
(def sample-platforms
  {:diadem (platform-map ["Space-based Platforms"
                          "Earth Observation Satellites"
                          "DIADEM"
                          "DIADEM-1D"
                          "Dee Iee Aee Dee Eee Mee Dash One Dee"])
   :diadem-lower (platform-map ["Space-based Platforms"
                                "Earth Observation Satellites"
                                "DIADEM"
                                "diadem-1D"])
   :dmsp (platform-map ["Space-based Platforms"
                        "Earth Observation Satellites"
                        "Defense Meteorological Satellite Program(DMSP)"
                        "DMSP 5B/F3"
                        "Defense Meteorological Satellite Program-F3"])
   :smap (platform-map ["Space-based Platforms"
                        "Earth Observation Satellites"
                        "SMAP-like"
                        "SMAP"
                        "Soil Moisture Active and Passive Observatory"])
   :non-existent (platform-map ["Space-based Platforms"
                                "Earth Observation Satellites"
                                "DIADEM"
                                "Non-Exist"])})

(defn- sample-platform-full
  "Build a platform map which can be used in search queries for the platforms-s
  property. Supply the 'name' from one of the values in sample-platforms"
  [name]
  {:basis (get-in sample-platforms [name :basis])
   :category (get-in sample-platforms [name :category])
   :sub-category (get-in sample-platforms [name :sub-category])
   :short-name (get-in sample-platforms [name :short-name])})

(defn- find-facet-field
  "Recursively search facet-results looking for a title. When found, that title
  will either be reported back as existing or have it's count value checked based
  on how many parameters are passed in."

  ;; report back if the field in question exists
  ([facet-result field]
   (find-facet-field facet-result field nil (fn [_] true)))

  ;; report back if the field exists and has a count maching value
  ([facet-result field value]
   (find-facet-field facet-result field value (fn [x] (= (:count facet-result) x))))

  ;; performs a field chacke looking for a value that matches the checker function
  ([facet-result field value checker]
   (if (= field (:title facet-result))
     (checker value)
     (if (contains? facet-result :children)
       (some? (get (group-by #(find-facet-field % field value) (:children facet-result)) true))
       false))))


(def sk1 (umm-spec-common/science-keyword {:Category "Earth science"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level1-1"
                                           :VariableLevel2 "Level1-2"
                                           :VariableLevel3 "Level1-3"
                                           :DetailedVariable "Detail1"}))

(def sk2 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Extreme"
                                           :VariableLevel1 "Level2-1"
                                           :VariableLevel2 "Level2-2"
                                           :VariableLevel3 "Level2-3"
                                           :DetailedVariable "UNIVERSAL"}))

(def sk3 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "UNIVERSAL"}))

(def sk4 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Alpha"}))

(def sk5 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Beta"}))

(def sk6 (umm-spec-common/science-keyword {:Category "EARTH SCIENCE"
                                           :Topic "Popular"
                                           :Term "Omega"}))
(def gdf1 {:FileDistributionInformation
           [{:FormatType "Binary"
             :AverageFileSize 50
             :AverageFileSizeUnit "MB"
             :Fees "None currently"
             :Format "NetCDF-3"}]})
(def facets-size-error-msg
  "Collection parameter facets_size needs to be passed in like facets_size[platform]=n1&facets_size[instrument]=n2 with n1 and n2 being a positive integer, which will be translated into a map with positive integer string values like {:platform \"1\" :instrument \"2\"}")

(defn- search-and-return-v2-facets
  "Returns the facets returned by a search requesting v2 facets."
  ([]
   (search-and-return-v2-facets {}))
  ([search-params]
   (index/wait-until-indexed)
   (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
     (get-in (search/find-concepts-json :collection query-params) [:results :facets]))))

(defn- search-and-return-v2-facets-errors
  "Returns the facets returned by a search requesting v2 facets."
  ([]
   (search-and-return-v2-facets {}))
  ([search-params]
   (let [query-params (merge search-params {:page-size 0 :include-facets "v2"})]
     (get-in (search/find-concepts-json :collection query-params) [:errors]))))

(deftest all-facets-v2-test
  (dev-sys-util/eval-in-dev-sys
   `(cmr.search.services.query-execution.facets.collection-v2-facets/set-include-variable-facets!
     true))
  (let [token (e/login (s/context) "user1")
        coll1 (fu/make-coll 1 "PROV1"
                            (fu/science-keywords sk1 sk2)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "DOI/USGS/CMG/WHSC"})]
                             :ArchiveAndDistributionInformation gdf1
                             :TilingIdentificationSystems (data-umm-spec/tiling-identification-systems "MISR" "CALYPSO")})
        coll2 (fu/make-coll 2 "PROV1"
                            (fu/science-keywords sk1 sk3)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "DOI/USGS/CMG/WHSC"})]})
        _ (index/wait-until-indexed)
        ;; create variables
        var1-concept (variable-util/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"}
                      {:coll-concept-id (:concept-id coll1)})
        var2-concept (variable-util/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"}
                      {:coll-concept-id (:concept-id coll2)})
        {variable1-concept-id :concept-id} (variable-util/ingest-variable-with-association var1-concept)
        {variable2-concept-id :concept-id} (variable-util/ingest-variable-with-association var2-concept)])
  (index/wait-until-indexed)
  (testing "No fields applied for facets"
    (is (= fr/expected-v2-facets-apply-links (search-and-return-v2-facets))))
  (testing "Facets size applied for facets"
    (is (= fr/expected-v2-facets-apply-links-with-facets-size
           (search-and-return-v2-facets {:facets-size {:platforms 1}}))))
  (testing "Facets size applied for facets, with selecting facet that exists, but outside of the facets size range."
    (is (= fr/expected-v2-facets-apply-links-with-selecting-facet-outside-of-facets-size
           (search-and-return-v2-facets {:facets-size {:platforms 1}
                                         :platforms-h {:0 (sample-platform-full :diadem-lower)}}))))
  (testing "Facets size applied for facets, with selecting facet that exists, without specifying facets size."
    (is (= fr/expected-v2-facets-apply-links-with-selecting-facet-without-facets-size
           (search-and-return-v2-facets {:platforms-h {:0 (sample-platform-full :diadem-lower)}}))))
  (testing "Facets size applied for facets, with selecting facet that doesn't exist."
    (is (= fr/expected-v2-facets-apply-links-with-facets-size-and-non-existing-selecting-facet
           (search-and-return-v2-facets {:facets-size {:platforms 1}
                                         :platforms-h {:0 (sample-platform-full :non-existent)}}))))
  (testing "Empty facets size applied for facets"
    (is (= [(str facets-size-error-msg " but was [{:instrument \"\"}].")]
           (search-and-return-v2-facets-errors {:facets-size {:instrument ""}}))))
  (testing "Negative facets size applied for facets"
    (is (= [(str facets-size-error-msg " but was [{:instrument \"-1\"}].")]
           (search-and-return-v2-facets-errors {:facets-size {:instrument -1}}))))
  (testing "Invalid facets size applied for facets"
    (is (= [(str facets-size-error-msg " but was [a].")]
           (search-and-return-v2-facets-errors {:facets-size "a"}))))
  (let [search-params {:science-keywords-h {:0 {:category "Earth Science"
                                                :topic "Topic1"
                                                :term "Term1"
                                                :variable-level-1 "Level1-1"
                                                :variable-level-2 "Level1-2"
                                                :variable-level-3 "Level1-3"}}
                       :project-h ["proj1"]
                       :platforms-h {:0 (sample-platform-full :diadem)}
                       :instrument-h ["ATM"]
                       :processing-level-id-h ["PL1"]
                       :data-center-h "DOI/USGS/CMG/WHSC"
                       :variables-h {:0 {:measurement "Measurement1"
                                         :variable "Variable1"}}}]
    (testing "All fields applied for facets"
      (is (= fr/expected-v2-facets-remove-links (search-and-return-v2-facets search-params))))
    (testing "Some fields not applied for facets"
      (let [response (search-and-return-v2-facets
                      (dissoc search-params :platforms-h :project-h :data-center-h :granule-data-format-h :coordinate-system))]
        (is (not (fu/applied? response :platforms-h)))
        (is (not (fu/applied? response :project-h)))
        (is (not (fu/applied? response :data-center-h)))
        (is (fu/applied? response :science-keywords-h))
        (is (fu/applied? response :variables-h))
        (is (fu/applied? response :instrument-h))
        (is (fu/applied? response :processing-level-id-h)))))
  (dev-sys-util/eval-in-dev-sys
   `(cmr.search.services.query-execution.facets.collection-v2-facets/set-include-variable-facets!
     false)))

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
  (fu/make-coll 3 "PROV1" {:Platforms [(data-umm-spec/platform {:ShortName "Terra"})]}
                (fu/science-keywords sk2 sk5))
  ;; 55 platforms with a count of 1, none of which should appear
  (fu/make-coll 4 "PROV1" (fu/platforms "low" 55) (fu/science-keywords sk2 sk5))

  (testing "Platform sorting behavior"

    (testing "Platforms are sorted alphabetically"
      (let [response (search-and-return-v2-facets
                      {:platforms-h {:0 {:short-name "Terra"}}})
            platforms (-> (:children response) first :children)]
        (verify-nested-facets-ordered-alphabetically platforms)))

    (testing "Science keywords are sorted alphabetically"
      (let [response (search-and-return-v2-facets
                      {:science-keywords-h {:0 {:topic "Popular"}}})
            science-keywords (-> (:children response) first :children)]
        (verify-nested-facets-ordered-alphabetically science-keywords)))))

(deftest remove-facets-without-collections
  (fu/make-coll 1
                "PROV1"
                (fu/science-keywords sk1)
                {:Platforms [{:Instruments [{:ShortName "ATM"}]
                              :ShortName "SMAP",
                              :LongName "Soil Moisture Active and Passive Observatory"}]})
  (fu/make-coll 1
                "PROV1"
                (fu/science-keywords sk1)
                {:Platforms [{:Instruments []
                              :ShortName "DMSP 5B/F3"
                              :LongName "Defense Meteorological Satellite Program-F3"}]})
  (fu/make-coll 1
                "PROV1"
                (fu/science-keywords sk1)
                {:Platforms [{:Instruments []
                              :ShortName "Aqua"
                              :LongName "Earth Observing System, Aqua"}]})
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
                         :platforms-h {:0 {:basis "Space-based Platforms"
                                           :category "Earth Observation Satellites"
                                           :sub-category "DIADEM"
                                           }}
                         :instrument-h ["ATM"]
                         :processing-level-id-h ["PL1"]
                         :data-center-h "DOI/USGS/CMG/WHSC"
                         :keyword "MODIS"}
          response (search-and-return-v2-facets search-params)]
      (is (= fr/expected-facets-with-no-matching-collections response))))
  (testing "Facets with multiple facets applied, some with matching collections, some without"
    (is (= fr/expected-facets-modis-and-aster-no-results-found
           (search-and-return-v2-facets {:platforms-h {:0 {:basis "Space-based Platforms"
                                                           :category "Earth Observation Satellites"
                                                           :sub-category "fake"
                                                           :short-name "moDIS-p0"}
                                                       :1 {:basis "Space-based Platforms"
                                                           :category "Earth Observation Satellites"
                                                           :sub-category "SMAP-like"
                                                           :short-name "SMAP"}}
                                         :keyword "DMSP 5B/F3"}))))
  (testing "Facets with multiple facets applied, some with matching collections, some without part 2"
    (is (= fr/expected-facets-when-aqua-search-results-found
           (search-and-return-v2-facets {:platforms-h {:0 {:basis "Space-based Platforms"
                                                           :category "Earth Observation Satellites"
                                                           :sub-category "fake"
                                                           :short-name "moDIS-p0"}
                                                       :1 {:basis "Space-based Platforms"
                                                           :category "Earth Observation Satellites"
                                                           :sub-category "SMAP-like"
                                                           :short-name "SMAP"}
                                                       :2 {:basis "Space-based Platforms"
                                                           :category "Earth Observation Satellites"
                                                           :short-name "Aqua"}}
                                         :keyword "Aqua"})))))

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
     :category "Earth Science" :topic "Topic1" :variable-level-1 "Level1-1"} 6))

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
  (let [non-earth-science-keyword (umm-spec-common/science-keyword {:Category "Cat1"
                                                                    :Topic "OtherTopic"
                                                                    :Term "OtherTerm"})]
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

(def sk-all (umm-spec-common/science-keyword {:Category "Earth science"
                                              :Topic "Topic1"
                                              :Term "Term1"
                                              :VariableLevel1 "Level1-1"
                                              :VariableLevel2 "Level1-2"
                                              :VariableLevel3 "Level1-3"}))

(def sk-same-vl1 (umm-spec-common/science-keyword {:Category "Earth science"
                                                   :Topic "Topic1"
                                                   :Term "Term2"
                                                   :VariableLevel1 "Level1-1"
                                                   :VariableLevel2 "Level2-2"
                                                   :VariableLevel3 "Level2-3"}))

(def sk-diff-vl1 (umm-spec-common/science-keyword {:Category "Earth science"
                                                   :Topic "Topic1"
                                                   :Term "Term1"
                                                   :VariableLevel1 "Another Level"}))

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
  [facets-result field value facet-count]
  (let [field-match-value (get-facet-field facets-result field value)]
    (testing value
     (is (= facet-count (:count field-match-value))))))

(defn- assert-latency-facet-field
   "Assert the latency facet field with count and link match the facets result"
   [facets-result value facet-count link]
   (let [field-match-value (get-facet-field facets-result "Latency" value)]
     (testing value
      (is (= facet-count (:count field-match-value)))
      (is (= link (:links field-match-value))))))

(defn- assert-field-in-hierarchy
  [facets field value facet-count]
  (for [facet facets]
    (let [finding (when (= value (:title facet)) facet)]
      (if finding
        finding
        (when (:children facet)
          (assert-field-in-hierarchy (:children facet) field value facet-count))))))

(defn- assert-facet-field-in-hierarchy
  [facets-result field value facet-count]
  (let [field-facet (first (get (group-by :title (:children facets-result)) field))]
    (first
      (flatten
        (assert-field-in-hierarchy (:children field-facet) field value facet-count)))))


(deftest platform-facets-v2-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :Platforms (data-umm-spec/platforms
                                                                  (get-in sample-platforms [:diadem :short-name]))}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V2"
                                                      :Platforms (data-umm-spec/platforms
                                                                  (get-in sample-platforms [:diadem :short-name])
                                                                  (get-in sample-platforms [:dmsp :short-name]))}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V3"
                                                      :Platforms [(data-umm-spec/platform
                                                                   {:ShortName (get-in sample-platforms [:dmsp :short-name])
                                                                    :Instruments [(data-umm-spec/instrument {:ShortName "I3"})]})]
                                                      :Projects (umm-spec-common/projects "proj3")}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll4"
                                                      :ShortName "S4"
                                                      :VersionId "V4"
                                                      :Platforms [(data-umm-spec/platform
                                                                   {:ShortName (get-in sample-platforms [:smap :short-name])
                                                                    :Instruments [(data-umm-spec/instrument {:ShortName "I4"})]})]
                                                      :Projects (umm-spec-common/projects "proj4")}))]
    (testing "search by platform parameter filters the other facets, but not platforms facets"
      (let [facets-result (search-and-return-v2-facets
                           {:platforms-h {:0 (sample-platform-full :smap)}})
            tester (partial find-facet-field facets-result)]
        (is (tester (get-in sample-platforms [:smap :basis]) 4))
        (is (tester (get-in sample-platforms [:smap :category]) 4))
        (is (tester (get-in sample-platforms [:smap :sub-category]) 1))
        (is (tester (get-in sample-platforms [:smap :short-name]) 1))
        (is (tester (get-in sample-platforms [:dmsp :sub-category]) 2))
        (is (tester (get-in sample-platforms [:diadem :sub-category]) 2))
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))

    (testing "search by multiple platforms"
      (let [facets-result (search-and-return-v2-facets {:platforms-h {:0 (sample-platform-full :smap)
                                                                      :1 (sample-platform-full :dmsp)}})
            tester (partial find-facet-field facets-result)]
        (is (tester (get-in sample-platforms [:diadem :sub-category]) 2))
        (is (tester (get-in sample-platforms [:dmsp :short-name]) 2))
        (is (tester (get-in sample-platforms [:smap :short-name]) 1))
        (assert-facet-field-not-exist facets-result "Instruments" "I4")
        (assert-facet-field-not-exist facets-result "Projects" "proj4")))

    (testing "search by params other than platform"
      (let [facets-result (search-and-return-v2-facets {:instrument-h ["I4"]})]
        (assert-facet-field facets-result "Platforms" "Space-based Platforms" 1)
        (assert-facet-field facets-result "Instruments" "I3" 1)
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (assert-facet-field-not-exist facets-result "Platforms" "P1")
        (assert-facet-field-not-exist facets-result "Platforms" "P2")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))

    (testing "search by both facet field param and regular param"
      (let [facets-result (search-and-return-v2-facets {:short-name "S1"
                                                        :platforms-h {:0 (sample-platform-full :smap)}})
            tester (partial find-facet-field facets-result)]
        (is (tester (get-in sample-platforms [:diadem :sub-category]) 1))
        (is (tester (get-in sample-platforms [:smap :short-name]) 0))
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Instruments" "I4")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")
        (assert-facet-field-not-exist facets-result "Projects" "proj4")))

    (testing "search by more than one facet field params"
      (let [facets-result (search-and-return-v2-facets {:platforms-h {:0 (sample-platform-full :smap)}
                                                        :instrument-h ["I4"]})
            tester (partial find-facet-field facets-result)]
        (is (tester (get-in sample-platforms [:smap :short-name]) 1))
        (assert-facet-field facets-result "Instruments" "I4" 1)
        (assert-facet-field facets-result "Projects" "proj4" 1)
        (is (not (tester (get-in sample-platforms [:diadem :short-name]))))
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))
        (assert-facet-field-not-exist facets-result "Instruments" "I3")
        (assert-facet-field-not-exist facets-result "Projects" "proj3")))))

(deftest hierarchy-facets-v2-test-more-complex-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :Platforms [(data-umm-spec/platform
                                                                  {:ShortName "AEROS-1"})
                                                                 (data-umm-spec/platform
                                                                  {:ShortName "Aqua"})
                                                                 (data-umm-spec/platform
                                                                  {:ShortName "AM-1"})]
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                         {:Category "Earth Science"
                                                                          :Topic "Topic1"
                                                                          :Term "Term1"
                                                                          :DetailedVariable "Detail1"})
                                                                        (umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic1"
                                                                           :Term "Term1"
                                                                           :DetailedVariable "Detail2"})
                                                                         (umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic1"
                                                                           :Term "Term1"
                                                                           :VariableLevel1 "Level3-1"
                                                                           :DetailedVariable "Detail3"})]}))]

    (testing "Testing that a sub-category and two short name platform facets exist"
      (let [facets-result (search-and-return-v2-facets {:platforms-h
                                                        {:0
                                                         {:basis "Space-based Platforms"
                                                          :category "Earth Observation Satellites"}}})]
        (assert-facet-field-in-hierarchy facets-result "Platforms" "Terra" 1)
        (assert-facet-field-in-hierarchy facets-result "Platforms" "Aqua" 1)
        (assert-facet-field-in-hierarchy facets-result "Platforms" "Aeros" 1)))

    (testing "Testing that a sub-category and two short name platform facets exist"
      (let [facets-result (search-and-return-v2-facets {:science-keywords-h
                                                        {:0
                                                         {:topic "Topic1"
                                                          :term "Term1"}}})]
        (assert-facet-field-in-hierarchy facets-result "Keywords" "Detail1" 1)
        (assert-facet-field-in-hierarchy facets-result "Keywords" "Detail2" 1)
        (assert-facet-field-in-hierarchy facets-result "Keywords" "Level3-1" 1)))))


(deftest science-keywords-facets-v2-test-simple
  "Tests for keys that are missing in the hierarchy. If science keywords are used, DetailedVariable
  can exist after Term, VariableLevel1, VariableLevel2, or VariableLevel3.  For Platform keywords
  short-name can exist after category or sub-category."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic1"
                                                                           :Term "Term1"
                                                                           :VariableLevel1 "Level1-1"
                                                                           :VariableLevel2 "Level1-2"
                                                                           :VariableLevel3 "Level1-3"
                                                                           :DetailedVariable "Detail1"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V1"
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                                {:Category "Earth Science"
                                                                                 :Topic "Topic2"
                                                                                 :Term "Term2"
                                                                                 :VariableLevel1 "Level2-1"
                                                                                 :VariableLevel2 "Level2-2"
                                                                                 :DetailedVariable "Detail2"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V1"
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                                {:Category "Earth Science"
                                                                                 :Topic "Topic3"
                                                                                 :Term "Term3"
                                                                                 :VariableLevel1 "Level3-1"
                                                                                 :DetailedVariable "Detail3"})]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll4"
                                                      :ShortName "S4"
                                                      :VersionId "V1"
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                                {:Category "Earth Science"
                                                                                 :Topic "Topic4"
                                                                                 :Term "Term4"
                                                                                 :DetailedVariable "Detail4"})]}))]
    (are3
     [query detailed-variable]
     (let [facets-result (search-and-return-v2-facets query)]
       (assert-facet-field-in-hierarchy facets-result "Keywords" detailed-variable 1))

     "Test that a full hierarchy can be found."
     {:science-keywords-h {:0 {:topic "Topic1"
                               :term "Term1"
                               :variable-level-1 "Level1-1"
                               :variable-level-2 "Level1-2"
                               :variable-level-3 "Level1-3"
                               :detailed-variable "Detail1"}}}
     "Detail1"

     "Test that the last facet is missing, but detailed-variable can be found."
     {:science-keywords-h {:0 {:topic "Topic2"
                               :term "Term2"
                               :variable-level-1 "Level2-1"
                               :variable-level-2 "Level2-2"
                               :detailed-variable "Detail2"}}}
     "Detail2"

     "Test that the last 2 facets are missing, but detailed-variable can be found."
     {:science-keywords-h {:0 {:topic "Topic3"
                               :term "Term3"
                               :variable-level-1 "Level3-1"
                               :detailed-variable "Detail3"}}}
     "Detail3"

     "Test that the last 3 facets are missing, but detailed-variable can be found."
     {:science-keywords-h {:0 {:topic "Topic4"
                               :term "Term4"
                               :detailed-variable "Detail4"}}}
     "Detail4")))

(deftest platforms2-facets-v2-test-simple
  "Tests for keys that are missing in the hierarchy. For Platform keywords
  short-name can exist after category or sub-category."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :Platforms [(umm-spec-common/platform
                                                                   {:ShortName "NASA S-3B VIKING"
                                                                    :LongName "NASA S-3B VIKING"
                                                                    :Type "Jet"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V1"
                                                      :Platforms [(umm-spec-common/platform
                                                                   {:ShortName "AEROS-1"
                                                                    :LongName "AEROS-1"
                                                                    :Type "Earth Observation Satellites"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V1"
                                                      :Platforms [(umm-spec-common/platform
                                                                   {:ShortName "Aqua"
                                                                    :LongName "Earth Observing System, Aqua"
                                                                    :Type "Earth Observation Satellites"})]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll4"
                                                      :ShortName "S4"
                                                      :VersionId "V1"
                                                      :Platforms [(umm-spec-common/platform
                                                                   {:ShortName "ISS"
                                                                    :LongName "International Space Station"
                                                                    :Type "Space Stations/Crewed Spacecraft"})]}))]

    (are3
     [query short-name]
     (let [facets-result (search-and-return-v2-facets query)]
       (assert-facet-field-in-hierarchy facets-result "Platforms" short-name 1))

     "Test that a full hierarchy can be found where sub-category doesn't exist."
     {:platforms-h {:0 {:basis "Air-based Platforms"
                        :category "Jet"
                        :short-name "NASA S-3B VIKING"}}}
     "NASA S-3B VIKING"

     "Test that the full hierarchy can be found."
     {:platforms-h {:0 {:basis "Space-based Platforms"
                        :category "Earth Observation Satellites"
                        :sub-category "Aeros"
                        :short-name "AEROS-1"}}}
     "AEROS-1"

     "Test that the sub-category is missing, but short-name can be found."
     {:platforms-h {:0 {:basis "Space-based Platforms"
                        :category "Earth Observation Satellites"
                        :short-name "Aqua"}}}
     "Aqua"

     "Test that the hierarchy can be found when humanized value is not in KMS but the original is."
     {:platforms-h {:0 {:basis "Space-based Platforms"
                        :category "Space Stations/Crewed Spacecraft"
                        :short-name "International Space Station"}}}
     "International Space Station")))

(deftest science-keywords-facets-v2-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :Platforms (data-umm-spec/platforms
                                                                  (get-in sample-platforms [:diadem :short-name]))
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic1"
                                                                           :Term "Term1"
                                                                           :VariableLevel1 "Level1-1"
                                                                           :VariableLevel2 "Level1-2"
                                                                           :VariableLevel3 "Level1-3"
                                                                           :DetailedVariable "Detail1"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V2"
                                                      :Platforms (data-umm-spec/platforms
                                                                  (get-in sample-platforms [:dmsp :short-name]))
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic2"
                                                                           :Term "Term2"
                                                                           :VariableLevel1 "Level2-1"
                                                                           :VariableLevel2 "Level2-2"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V3"
                                                      :Platforms (data-umm-spec/platforms
                                                                  (get-in sample-platforms [:smap :short-name]))
                                                      :ScienceKeywords [(umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic1"
                                                                           :Term "Term3"})
                                                                         (umm-spec-common/science-keyword
                                                                          {:Category "Earth Science"
                                                                           :Topic "Topic2"
                                                                           :Term "Term3"})]}))]
    ;; We only check the topic level science keywords facet for convenience since the whole
    ;; hierarchical structure of science keywords facet has been covered in all facets test.
    (testing "search by science-keywords param filters the other facets, but not science-keywords facets"
      (let [facets-result (search-and-return-v2-facets
                           {:science-keywords-h {:0 {:topic "Topic1"
                                                     :term "Term1"
                                                     :variable-level-1 "Level1-1"
                                                     :variable-level-2 "Level1-2"
                                                     :variable-level-3 "Level1-3"}}})
            tester (partial find-facet-field facets-result)]
        (assert-facet-field facets-result "Keywords" "Topic1" 2)
        (assert-facet-field facets-result "Keywords" "Topic2" 2)
        (is (tester (get-in sample-platforms [:diadem :basis]) 1))
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))
        (is (not (tester (get-in sample-platforms [:smap :short-name]))))))

    (testing "search by both science-keywords param and regular param"
      (let [facets-result (search-and-return-v2-facets
                           {:short-name "S1"
                            :science-keywords-h {:0 {:topic "Topic1"}}})
            tester (partial find-facet-field facets-result)]
        (assert-facet-field facets-result "Keywords" "Topic1" 1)
        (assert-facet-field facets-result "Platforms" (get-in sample-platforms [:diadem :basis]) 1)
        (assert-facet-field-not-exist facets-result "Keywords" "Topic2")
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))
        (is (not (tester (get-in sample-platforms [:smap :short-name]))))))
    (testing "search by both science-keywords param and a facet field param, with science keywords match a collection"
      (let [facets-result (search-and-return-v2-facets
                           {:platforms-h {:0 (sample-platform-full :diadem)}
                            :science-keywords-h {:0 {:topic "Topic1"
                                                     :term "Term1"}}})
            tester (partial find-facet-field facets-result)]
        (is (tester "Topic1" 1))
        (is (tester (get-in sample-platforms [:diadem :basis]) 1))
        (is (not (tester "Topic2")))
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))
        (is (not (tester (get-in sample-platforms [:smap :short-name]))))))
    (testing "search by both science-keywords param and a facet field param, with science keywords match two collections"
      (let [facets-result (search-and-return-v2-facets
                           {:platforms-h {:0 (sample-platform-full :smap)}
                            :science-keywords-h {:0 {:topic "Topic1"}}})
            tester (partial find-facet-field facets-result)]
        (is (tester "Topic1" 1))
        (is (tester "Topic2" 1))
        (is (tester (get-in sample-platforms [:diadem :basis]) 2))
        (is (tester (get-in sample-platforms [:diadem :sub-category]) 1))
        (is (tester (get-in sample-platforms [:smap :short-name]) 1))
        (is (not (tester (get-in sample-platforms [:dmsp :short-name]))))))))

(deftest organization-facets-v2-test
  (let [org1 (data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "DOI/USGS/CMG/WHSC"})
        org2 (data-umm-spec/data-center {:Roles ["PROCESSOR"] :ShortName "LPDAAC"})
        org3 (data-umm-spec/data-center {:Roles ["ARCHIVER"] :ShortName "NSIDC"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :DataCenters [org1]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V2"
                                                      :DataCenters [org2]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V3"
                                                      :DataCenters [org1 org2]
                                                      :Projects (data-umm-spec/projects "proj3")}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll4"
                                                      :ShortName "S4"
                                                      :VersionId "V4"
                                                      :DataCenters [org3]
                                                      :Projects (data-umm-spec/projects "proj4")}))]
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

(deftest variables-facets-v2-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :VersionId "V1"
                                                      :Platforms (data-umm-spec/platforms "P1")}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :VersionId "V2"
                                                      :Platforms (data-umm-spec/platforms "P2")}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll3"
                                                      :ShortName "S3"
                                                      :VersionId "V3"
                                                      :Platforms (data-umm-spec/platforms "P3")}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll4"
                                                      :ShortName "S4"
                                                      :VersionId "V4"}))
        ;; index the collections so that they can be found during variable association
        _ (index/wait-until-indexed)
        ;; create variables
        var1-concept (variable-util/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"}
                      {:coll-concept-id (:concept-id coll1)})
        var2-concept (variable-util/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"}
                      {:coll-concept-id (:concept-id coll2)})
        var3-concept (variable-util/make-variable-concept
                      {:Name "SomeVariable"
                       :LongName "Measurement2"}
                      {:coll-concept-id (:concept-id coll4)})
        {variable1-concept-id :concept-id} (variable-util/ingest-variable-with-association var1-concept)
        {variable2-concept-id :concept-id} (variable-util/ingest-variable-with-association var2-concept)
        {variable3-concept-id :concept-id} (variable-util/ingest-variable-with-association var3-concept)]
    (index/wait-until-indexed)
    (testing "variable facets are disabled by default"
      (let [facets-result (search-and-return-v2-facets
                           {:variables-h {:0 {:variable "Variable1"}}})]
        (assert-facet-field-not-exist facets-result "Measurements" "Measurement1")
        (assert-facet-field-not-exist facets-result "Measurements" "Measurement2")))

    (testing "variable facets enabled"
      (dev-sys-util/eval-in-dev-sys
       `(cmr.search.services.query-execution.facets.collection-v2-facets/set-include-variable-facets!
         true))
      ;; We only check the top level variables facet for convenience since the whole
      ;; hierarchical structure of variables facet has been covered in all facets test.
      (testing "search by variables param filters the other facets, but not variables facets"
        (let [facets-result (search-and-return-v2-facets
                             {:variables-h {:0 {:variable "Variable1"}}})]
          (assert-facet-field facets-result "Measurements" "Measurement1" 1)
          (assert-facet-field facets-result "Measurements" "Measurement2" 2)
          (assert-facet-field facets-result "Platforms" "P1" nil)
          (assert-facet-field facets-result "Platforms" "P2" nil)
          (assert-facet-field-not-exist facets-result "Platforms" "P3")))

      (testing "search by both variables param and regular param"
        (let [facets-result (search-and-return-v2-facets
                             {:short-name "S1"
                              :variables-h {:0 {:variable "Variable1"}}})]
          (assert-facet-field facets-result "Measurements" "Measurement1" 1)
          (assert-facet-field facets-result "Platforms" "P1" nil)
          (assert-facet-field-not-exist facets-result "Measurements" "Measurement2")
          (assert-facet-field-not-exist facets-result "Platforms" "P2")
          (assert-facet-field-not-exist facets-result "Platforms" "P3")))

      (testing "search by both variables param and another facet field param"
        (let [facets-result (search-and-return-v2-facets
                             {:platform-h "P1"
                              :variables-h {:0 {:variable "Variable1"}}})]
          (assert-facet-field facets-result "Measurements" "Measurement1" 1)
          (assert-facet-field facets-result "Platforms" "P1" nil)
          (assert-facet-field facets-result "Platforms" "P2" nil)
          (assert-facet-field-not-exist facets-result "Measurements" "Measurement2")
          (assert-facet-field-not-exist facets-result "Platforms" "P3")))
      (dev-sys-util/eval-in-dev-sys
       `(cmr.search.services.query-execution.facets.collection-v2-facets/set-include-variable-facets!
         false)))))

(def spatial
  "This is spatial data to test the horizontal data resolutions range facets."
  {:GranuleSpatialRepresentation "CARTESIAN"
   :HorizontalSpatialDomain
     {:Geometry {:CoordinateSystem "CARTESIAN"
                 :Points [{:Longitude 0
                           :Latitude 0}]}
      :ResolutionAndCoordinateSystem
        {:HorizontalDataResolution
           {:VariesResolution "Varies"
            :PointResolution "Point"
            :NonGriddedResolutions [{:XDimension 1000
                                     :YDimension 1000
                                     :Unit "Meters"
                                     :ViewingAngleType "At Nadir"
                                     :ScanDirection "Cross Track"}
                                    {:XDimension 0.007
                                     :YDimension 0.008
                                     :Unit "Kilometers"
                                     :ViewingAngleType "At Nadir"
                                     :ScanDirection "Cross Track"}]
            :NonGriddedRangeResolutions [{:MinimumXDimension 0.2
                                          :MinimumYDimension 0.2
                                          :MaximumXDimension 0.9
                                          :MaximumYDimension 0.9
                                          :Unit "Meters"
                                          :ViewingAngleType "At Nadir"
                                          :ScanDirection "Cross Track"}]
            :GriddedResolutions [{:XDimension 2
                                  :YDimension 2
                                  :Unit "Nautical Miles"}
                                 {:XDimension 2.007
                                  :YDimension 2.008
                                  :Unit "Statute Miles"}]
            :GriddedRangeResolutions [{:MinimumXDimension 2
                                       :MinimumYDimension 2
                                       :MaximumXDimension 4
                                       :MaximumYDimension 4
                                       :Unit "Meters"}]
            :GenericResolutions [{:XDimension 20
                                  :YDimension 20
                                  :Unit "Nautical Miles"}
                                 {:XDimension 0.007
                                  :YDimension 0.008
                                  :Unit "Statute Miles"}]}}}})

(deftest latency-facet-v2-test
   "Test the latency facets ingest, indexing, and search."
   (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                      {:EntryTitle "coll1"
                                                       :ShortName "S1"
                                                       :SpatialExtent spatial
                                                       :CollectionDataType "NEAR_REAL_TIME"
                                                       :Projects [{:ShortName "Proj1"
                                                                   :LongName "Proj1 Long Name"}]}))
         coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                      {:EntryTitle "coll2"
                                                       :ShortName "S2"
                                                       :CollectionDataType "LOW_LATENCY"
                                                       :Projects [{:ShortName "Proj2"
                                                                   :LongName "Proj2 Long Name"}]}))
         coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                      {:EntryTitle "coll3"
                                                       :ShortName "S3"
                                                       :CollectionDataType "EXPEDITED"
                                                       :Projects [{:ShortName "Proj3"
                                                                   :LongName "Proj3 Long Name"}]}))]

     (testing "Latency v2 facets"
       (let [;; search for collections without latency parameter.
             facets-result1 (search-and-return-v2-facets {})
             ;; search for collections with one of the valid latency values.
             facets-result2 (search-and-return-v2-facets {:latency "1 to 4 days"})
             ;; search for collections with an invalid latency value.
             facets-result3 (search-and-return-v2-facets {:latency "1 to 5 days"})
             ;; search for collections with all three valid latency values
             facets-result4 (search-and-return-v2-facets {:latency ["1 to 4 days" "3 to 24 hours" "1 to 3 hours"]})]
         ;; Verify all 3 latency facets show up with the right links.
         (assert-latency-facet-field facets-result1
          "1 to 3 hours"
          1
          {:apply "http://localhost:3003/collections.json?page_size=0&include_facets=v2&latency%5B%5D=1+to+3+hours"})
         (assert-latency-facet-field facets-result1
          "3 to 24 hours"
          1
          {:apply "http://localhost:3003/collections.json?page_size=0&include_facets=v2&latency%5B%5D=3+to+24+hours"})
         (assert-latency-facet-field facets-result1
          "1 to 4 days"
          1
          {:apply "http://localhost:3003/collections.json?page_size=0&include_facets=v2&latency%5B%5D=1+to+4+days"})

         ;; Verify all 3 latency facets show up with "1 to 4 days" links being marked as "remove". And the other two links
         ;; have the "1 to 4 days" appended to the query.
         (assert-latency-facet-field facets-result2
          "1 to 3 hours"
          1
          {:apply "http://localhost:3003/collections.json?latency=1+to+4+days&page_size=0&include_facets=v2&latency%5B%5D=1+to+3+hours"})
         (assert-latency-facet-field facets-result2
          "3 to 24 hours"
          1
          {:apply "http://localhost:3003/collections.json?latency=1+to+4+days&page_size=0&include_facets=v2&latency%5B%5D=3+to+24+hours"})
         (assert-latency-facet-field facets-result2
          "1 to 4 days"
          1
          {:remove "http://localhost:3003/collections.json?page_size=0&include_facets=v2"})


          ;; Verify all 3 latency facets show up and all have the "1 to 5 days" appended to their queries.
          (assert-latency-facet-field facets-result3
          "1 to 3 hours"
          1
          {:apply "http://localhost:3003/collections.json?latency=1+to+5+days&page_size=0&include_facets=v2&latency%5B%5D=1+to+3+hours"})
         (assert-latency-facet-field facets-result3
          "3 to 24 hours"
          1
          {:apply "http://localhost:3003/collections.json?latency=1+to+5+days&page_size=0&include_facets=v2&latency%5B%5D=3+to+24+hours"})
         (assert-latency-facet-field facets-result3
          "1 to 4 days"
          1
          {:apply "http://localhost:3003/collections.json?latency=1+to+5+days&page_size=0&include_facets=v2&latency%5B%5D=1+to+4+days"})

         ;; Verify all 3 latency facets show up with the links marked as "remove".
         (assert-latency-facet-field facets-result4
          "1 to 3 hours"
          1
          {:remove "http://localhost:3003/collections.json?latency=1+to+4+days&latency=3+to+24+hours&page_size=0&include_facets=v2"})
         (assert-latency-facet-field facets-result4
          "3 to 24 hours"
          1
          {:remove "http://localhost:3003/collections.json?latency=1+to+4+days&latency=1+to+3+hours&page_size=0&include_facets=v2"})
         (assert-latency-facet-field facets-result4
          "1 to 4 days"
          1
          {:remove "http://localhost:3003/collections.json?latency=3+to+24+hours&latency=1+to+3+hours&page_size=0&include_facets=v2"})))))

(deftest horizontal-data-resolution-range-facet-v2-test
  "Test the horizontal data resolution facets ingest, indexing, and search."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll1"
                                                      :ShortName "S1"
                                                      :SpatialExtent spatial
                                                      :Projects [{:ShortName "Proj4"
                                                                  :LongName "Proj4 Long Name"}]})
                                                    {:format :umm-json})
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-spec/collection
                                                     {:EntryTitle "coll2"
                                                      :ShortName "S2"
                                                      :Projects [{:ShortName "Proj3"
                                                                  :LongName "Proj3 Long Name"}]})
                                                    {:format :umm-json})]

    (testing "search by horizontal data resolution. Parameter filters the other facets, but not
              this facet. This also tests the edge case where the record contains the value of
              1000, which goes into 2 ranges: 500 to 1000 meters and 1 to 10 km."
      (let [facets-result (search-and-return-v2-facets {:horizontal-data-resolution-range ["1 to 10 km"]})]
        (assert-facet-field facets-result "Horizontal Data Resolution" "0 to 1 meter" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 30 meters" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "30 to 100 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 meters")
        (assert-facet-field facets-result "Horizontal Data Resolution" "500 to 1000 meters" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 10 km" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "10 to 50 km" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "50 to 100 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "500 to 1000 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "1000 km & above")
        (assert-facet-field-not-exist facets-result "Projects" "Proj3")
        (assert-facet-field facets-result "Projects" "Proj4" 1)))

    (testing "search by multiple horizontal data resolutions"
      (let [facets-result (search-and-return-v2-facets {:horizontal-data-resolution-range ["500 to 1000 meters" "1 to 10 km"]})]
        (assert-facet-field facets-result "Horizontal Data Resolution" "0 to 1 meter" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 30 meters" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "30 to 100 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 meters")
        (assert-facet-field facets-result "Horizontal Data Resolution" "500 to 1000 meters" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 10 km" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "10 to 50 km" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "50 to 100 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "500 to 1000 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "1000 km & above")
        (assert-facet-field-not-exist facets-result "Projects" "Proj3")
        (assert-facet-field facets-result "Projects" "Proj4" 1)))

    (testing "search by params other than horizontal data resolution"
      (let [facets-result (search-and-return-v2-facets {:project-h ["Proj3"]})]
        (assert-facet-field facets-result "Projects" "Proj3" 1)
        (assert-facet-field facets-result "Projects" "Proj4" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "0 to 1 meter")))

    (testing "search by both facet field param and regular param"
      (let [facets-result (search-and-return-v2-facets {:short-name "S1"
                                                        :horizontal-data-resolution-range ["1 to 10 km"]})]
        (assert-facet-field facets-result "Horizontal Data Resolution" "0 to 1 meter" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 30 meters" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "30 to 100 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 meters")
        (assert-facet-field facets-result "Horizontal Data Resolution" "500 to 1000 meters" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 10 km" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "10 to 50 km" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "50 to 100 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "500 to 1000 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "1000 km & above")
        (assert-facet-field-not-exist facets-result "Projects" "Proj3")
        (assert-facet-field facets-result "Projects" "Proj4" 1)))

    (testing "search by more than one facet field params"
      (let [facets-result (search-and-return-v2-facets {:project-h ["Proj4"]
                                                        :horizontal-data-resolution-range ["1 to 10 km"]})]
        (assert-facet-field facets-result "Horizontal Data Resolution" "0 to 1 meter" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 30 meters" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "30 to 100 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 meters")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 meters")
        (assert-facet-field facets-result "Horizontal Data Resolution" "500 to 1000 meters" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "1 to 10 km" 1)
        (assert-facet-field facets-result "Horizontal Data Resolution" "10 to 50 km" 1)
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "50 to 100 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "100 to 250 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "250 to 500 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "500 to 1000 km")
        (assert-facet-field-not-exist facets-result "Horizontal Data Resolution" "1000 km & above")
        (assert-facet-field-not-exist facets-result "Projects" "Proj3")
        (assert-facet-field facets-result "Projects" "Proj4" 1)))))

(comment
 ;; Good for manually testing applying links
 (do
   (cmr.system-int-test.utils.ingest-util/create-provider {:provider-id "PROV1" :provider-guid "prov1guid"})
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1))
   (fu/make-coll 1 "PROV1" (fu/science-keywords sk-all sk-same-vl1 sk-diff-vl1))))

(defn subset-matches?
  [obj matcher]
  (every? true? (map #(= (get obj %) (get matcher %)) (keys matcher))))

(deftest hierarchical-query-responses
  (are3 [query response]
    (is (subset-matches? (search/find-concepts-json :collection query)
                         response))

    "platforms_h missing index and sub-key"
    {:platforms-h "Suomi-NPP"}
    {:status 400
     :errors ["Parameter [:platforms-h] must include an index and nested key, platforms_h[n][...]=value."]}

    "platforms_h missing index"
    {:platforms-h "Suomi-NPP"}
    {:status 400
     :errors ["Parameter [:platforms-h] must include an index and nested key, platforms_h[n][...]=value."]}

    "platforms_h missing sub-keys"
    {:platforms-h {:0 nil}}
    {:status 400
     :errors ["Parameter [platforms_h[0]] must include a nested key, platforms_h[0][...]=value."]}

    "platforms_h with valid index and subkey"
    {:platforms-h {:0 {:basis "Space-based+Platforms"
                       :category "Earth+Observation+Satellites"}}}
    {:status 200
     :hits 0}))

(ns cmr.system-int-test.search.variable.variable-search-test
  "This tests searching variables."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variables]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                variables/grant-all-variable-fixture]))

(deftest search-for-variables-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400
            :errors ["Parameter [foo] was not recognized."]}
           (variables/search {:foo "bar"}))))

  (testing "Unsupported sort key"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting variables."]}
           (variables/search {:sort-key "concept_id"}))))

  (testing "Unsupported options"
    (are [field option]
      (= {:status 400
          :errors [(format "Option [%s] is not supported for param [%s]" option (name field))]}
         (variables/search {field "foo" (format "options[%s][%s]" (name field) option) true}))
      :variable_name "and"
      :measurement "and"))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:status 400
            :errors ["Concept-id [V*] is not valid."
                     "Option [pattern] is not supported for param [concept_id]"]}
           (variables/search {:concept-id "V*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:status 400
            :errors ["Option [ignore_case] is not supported for param [concept_id]"]}
           (variables/search {:concept-id "V1000-PROV1" "options[concept-id][ignore-case]" true}))))

  (testing "Default variable search result format is XML"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :variable {})]
      (is (= 200 status))
      (is (= "application/xml; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format in headers"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for variables."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :atom+xml :variable {})))))

  (testing "Unsuported result format in url extension"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for variables."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :variable {} {:url-extension "atom"}))))))

(deftest search-for-variables-test
  (let [token (e/login (s/context) "user1")
        [coll1 coll2 coll3] (doall (for [n (range 1 4)]
                                     (d/ingest-umm-spec-collection
                                      "PROV1"
                                      (data-umm-c/collection n {})
                                      {:token token})))
        [coll4 coll5 coll6] (doall (for [n (range 1 4)]
                                     (d/ingest-umm-spec-collection
                                      "PROV2"
                                      (data-umm-c/collection n {})
                                      {:token token})))
        _ (index/wait-until-indexed)
        var1-concept (variables/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"
                       :Sets [{:Name "TESTSETNAME"
                               :Type "Science"
                               :Size 2
                               :Index 2}]}
                      {:native-id "VAR1"
                       :coll-concept-id (:concept-id coll1)})
        var2-concept (variables/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll2)})
        var3-concept (variables/make-variable-concept
                      {:Name "a subsitute for variable2"
                       :LongName "variable1"
                       :Sets [{:Name "TESTSETNAME"
                               :Type "Science"
                               :Size 2
                               :Index 2}]}
                      {:native-id "var3"
                       :coll-concept-id (:concept-id coll4)})
        var4-concept (variables/make-variable-concept
                      {:Name "v.other"
                       :LongName "m.other"}
                      {:native-id "special-variable"
                       :coll-concept-id (:concept-id coll5)})
        variable1 (variables/ingest-variable-with-association var1-concept)
        variable2 (variables/ingest-variable-with-association var2-concept)
        variable3 (variables/ingest-variable-with-association var3-concept)
        variable4 (variables/ingest-variable-with-association var4-concept)

        prov1-variables [variable1 variable2]
        prov2-variables [variable3 variable4]
        all-variables (concat prov1-variables prov2-variables)]

    (index/wait-until-indexed)

    (are3 [expected-variables query]
      (d/refs-match? expected-variables (search/find-refs :variable query))

      "Find all"
      all-variables {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match"
      [variable1]
      {:name "Variable1"}

      "By name case sensitive, default ignore-case true"
      [variable1]
      {:name "variable1"}

      "By name ignore case false"
      []
      {:name "variable1" "options[name][ignore-case]" false}

      "By name ignore case true"
      [variable1]
      {:name "variable1" "options[name][ignore-case]" true}

      "By name Pattern, default false"
      []
      {:name "*other"}

      "By name Pattern true"
      [variable4]
      {:name "*other" "options[name][pattern]" true}

      "By name Pattern false"
      []
      {:name "*other" "options[name][pattern]" false}

      "By multiple names"
      [variable1 variable2]
      {:name ["Variable1" "variable2"]}

      "By multiple names with options"
      [variable1 variable4]
      {:name ["Variable1" "*other"] "options[name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; variable-name Param
      "By variable-name case sensitive - exact match"
      [variable1]
      {:variable-name "Variable1"}

      "By variable-name case sensitive, default ignore-case true"
      [variable1]
      {:variable-name "variable1"}

      "By variable-name ignore case false"
      []
      {:variable-name "variable1" "options[variable-name][ignore-case]" false}

      "By variable-name ignore case true"
      [variable1]
      {:variable-name "variable1" "options[variable-name][ignore-case]" true}

      "By variable-name Pattern, default false"
      []
      {:variable-name "*other"}

      "By variable-name Pattern true"
      [variable4]
      {:variable-name "*other" "options[variable-name][pattern]" true}

      "By variable-name Pattern false"
      []
      {:variable-name "*other" "options[variable-name][pattern]" false}

      "By multiple variable-names"
      [variable1 variable2]
      {:variable-name ["Variable1" "variable2"]}

      "By multiple variable-names with options"
      [variable1 variable4]
      {:variable-name ["Variable1" "*other"] "options[variable-name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; provider Param
      "By provider - exact match"
      prov1-variables
      {:provider "PROV1"}

      "By provider, default ignore-case true"
      prov1-variables
      {:provider "prov1"}

      "By provider ignore case false"
      []
      {:provider "prov1" "options[provider][ignore-case]" false}

      "By provider ignore case true"
      prov1-variables
      {:provider "prov1" "options[provider][ignore-case]" true}

      "By provider Pattern, default false"
      []
      {:provider "PROV?"}

      "By provider Pattern true"
      all-variables
      {:provider "PROV?" "options[provider][pattern]" true}

      "By provider Pattern false"
      []
      {:provider "PROV?" "options[provider][pattern]" false}

      "By multiple providers"
      prov2-variables
      {:provider ["PROV2" "PROV3"]}

      "By multiple providers with options"
      all-variables
      {:provider ["PROV1" "*2"] "options[provider][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; native-id Param
      "By native-id case sensitive - exact match"
      [variable1]
      {:native-id "VAR1"}

      "By native-id case sensitive, default ignore-case true"
      [variable1]
      {:native-id "var1"}

      "By native-id ignore case false"
      []
      {:native-id "var1" "options[native-id][ignore-case]" false}

      "By native-id ignore case true"
      [variable1]
      {:native-id "var1" "options[native-id][ignore-case]" true}

      "By native-id Pattern, default false"
      []
      {:native-id "var*"}

      "By native-id Pattern true"
      [variable1 variable2 variable3]
      {:native-id "var*" "options[native-id][pattern]" true}

      "By native-id Pattern false"
      []
      {:native-id "var*" "options[native-id][pattern]" false}

      "By multiple native-ids"
      [variable1 variable2]
      {:native-id ["VAR1" "var2"]}

      "By multiple native-ids with options"
      [variable1 variable4]
      {:native-id ["VAR1" "special*"] "options[native-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; concept-id Param
      "By concept-id - single"
      [variable1]
      {:concept-id (:concept-id variable1)}

      "By concept-id - multiple"
      [variable1 variable2]
      {:concept-id [(:concept-id variable1) (:concept-id variable2)]}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; keyword Param
      "By keyword case insensitive - exact match"
      [variable1]
      {:keyword "Measurement1"}

      "By keyword case insensitive, mixed case"
      [variable1]
      {:keyword "measuREment1"}

      "By keyword match both variable_name and measurement"
      [variable1 variable3]
      {:keyword "variable1"}

      "By keyword match associated collection"
      [variable1]
      {:keyword (:concept-id coll1)}

      "By keyword match associated collection, no match"
      []
      {:keyword (:concept-id coll3)}

      "By keyword match set name"
      [variable1 variable3]
      {:keyword "TESTSETNAME"}

      "By keyword match set name, no match"
      []
      {:keyword "WRONGSETNAME"}

      "By keyword match tokenized variable_name and measurement"
      [variable2 variable3]
      {:keyword "variable2"}

      "By keyword match explict string"
      [variable3]
      {:keyword "a subsitute for variable2"}

      "By keyword match partial string"
      [variable3]
      {:keyword "subsitute variable2"}

      "By keyword match wildcard *"
      [variable1 variable2]
      {:keyword "meas*"}

      "By keyword match wildcard *, also apply to tokenized string"
      [variable1 variable2 variable3]
      {:keyword "var*"}

      "By keyword match wildcard ?, no match"
      []
      {:keyword "meas?"}

      "By keyword match wildcard ?, match"
      [variable1 variable2]
      {:keyword "measurement?"}

      "By keyword multiple wildcards"
      [variable3]
      {:keyword "sub* variable?"})))

(deftest search-variable-science-keywords-test
  (let [sk1 (data-umm-cmn/science-keyword {:Category "Cat1"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level1-1"
                                           :VariableLevel2 "Level1-2"
                                           :VariableLevel3 "Level1-3"
                                           :DetailedVariable "SUPER DETAILED!"})
        sk2 (data-umm-cmn/science-keyword {:Category "Hurricane"
                                           :Topic "Laser spoonA"
                                           :Term "Extreme"
                                           :VariableLevel1 "Level2-1"
                                           :VariableLevel2 "Level2-2"
                                           :VariableLevel3 "Level2-3"})
        sk3 (data-umm-cmn/science-keyword {:Category "Cat2"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level3-1"
                                           :VariableLevel2 "Level3-2"
                                           :VariableLevel3 "Level3-3"
                                           :DetailedVariable "S@PER"})
        token (e/login (s/context) "user1")
        [coll1 coll2 coll3] (doall (for [n (range 1 4)]
                                     (d/ingest-umm-spec-collection
                                      "PROV1"
                                      (data-umm-c/collection n {})
                                      {:token token})))
        _ (index/wait-until-indexed)
        var1-concept (variables/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"
                       :ScienceKeywords [sk1 sk2]}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        var2-concept (variables/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"
                       :ScienceKeywords [sk3]}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll2)})
        var3-concept (variables/make-variable-concept
                      {:Name "a subsitute for variable2"
                       :LongName "variable1"}
                      {:native-id "var3"
                       :coll-concept-id (:concept-id coll3)})
        variable1 (variables/ingest-variable-with-association var1-concept)
        variable2 (variables/ingest-variable-with-association var2-concept)
        variable3 (variables/ingest-variable-with-association var3-concept)]
    (index/wait-until-indexed)

    (are3 [expected-variables keyword]
      (variables/assert-variable-search expected-variables (variables/search {:keyword keyword}))

      ;; Science keywords
      "Category"
      [variable1]
      "Cat1"

      "Topic"
      [variable1 variable2]
      "Topic1"

      "Term"
      [variable1 variable2]
      "Term1"

      "Variable level 1"
      [variable1]
      "Level2-1"

      "Variable level 2"
      [variable1]
      "Level2-2"

      "Variable level 3"
      [variable1]
      "Level2-3"

      "Detailed variable"
      [variable1]
      "SUPER"

      "Combination of keywords"
      [variable1]
      "Hurricane Laser"

      "Combination of keywords - different order, case insensitive"
      [variable1]
      "laser hurricane"

      "Wildcards"
      [variable1 variable2]
      "s?per term*")))

(deftest deleted-variables-not-found-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection 1 {})
                                            {:token token})
        _ (index/wait-until-indexed)
        var1-concept (variables/make-variable-concept
                      {:Name "Variable1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        variable1 (variables/ingest-variable-with-association var1-concept {:token token})
        var2-concept (variables/make-variable-concept
                      {:Name "Variable2"}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll1)})
        variable2 (variables/ingest-variable-with-association var2-concept {:token token})
        all-variables [variable1 variable2]]
    (index/wait-until-indexed)

    ;; Now I should find the all variables when searching
    (variables/assert-variable-search all-variables (variables/search {}))

    ;; Delete variable1
    (ingest/delete-concept var1-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variable
    (variables/assert-variable-search [variable2] (variables/search {}))

    ;; Delete variable2
    (ingest/delete-concept var2-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variables
    (variables/assert-variable-search [] (variables/search {}))))

(deftest variable-search-in-umm-json-format-test
  (testing "variable search result in UMM JSON format has associated collections"
    (let [token (e/login (s/context) "user1")
          [coll1 coll2 coll3] (doall (for [n (range 1 4)]
                                       (d/ingest-umm-spec-collection
                                        "PROV1"
                                        (data-umm-c/collection n {})
                                        {:token token})))
          ;; index the collections so that they can be found during variable association
          _ (index/wait-until-indexed)
          ;; create variables
          var1-concept (variables/make-variable-concept
                        {:Name "Variable1"}
                        {:native-id "var1"
                         :coll-concept-id (:concept-id coll1)})
          var2-concept (variables/make-variable-concept
                        {:Name "Variable2"}
                        {:native-id "var2"
                         :coll-concept-id (:concept-id coll2)
                         :coll-revision-id 1})
          var3-concept (variables/make-variable-concept
                        {:Name "Variable3"}
                        {:native-id "var3"
                         :coll-concept-id (:concept-id coll3)})
          variable1 (variables/ingest-variable-with-association var1-concept)
          variable2 (variables/ingest-variable-with-association var2-concept)
          variable3 (variables/ingest-variable-with-association var3-concept)

          associations1 {:associations {:collections [(:concept-id coll1)]}
                         :association-details {:collections [{:concept-id (:concept-id coll1)}]}}
          associations2 {:associations {:collections [(:concept-id coll2)]}
                         :association-details {:collections [{:concept-id (:concept-id coll2)
                                                              :revision-id 1}]}}
          associations3 {:associations {:collections [(:concept-id coll3)]}
                         :association-details {:collections [{:concept-id (:concept-id coll3)}]}}
          variable1 (merge variable1 associations1)
          variable2 (merge variable2 associations2)
          variable3 (merge variable3 associations3)

          variable1-assoc-colls [{:concept-id (:concept-id coll1)}]
          variable2-assoc-colls [{:concept-id (:concept-id coll2)
                                  :revision-id 1}]
          variable3-assoc-colls [{:concept-id (:concept-id coll3)}]

          ;; Add the variable info to variable concepts for comparision with UMM JSON result
          expected-variable1 (merge var1-concept
                                    variable1
                                    {:associated-collections variable1-assoc-colls})
          expected-variable2 (merge var2-concept
                                    variable2
                                    {:associated-collections variable2-assoc-colls})
          expected-variable3 (merge var3-concept
                                    variable3
                                    {:associated-collections variable3-assoc-colls})]
      (index/wait-until-indexed)

      ;; verify variable search UMM JSON response has associated collections
      (du/assert-variable-umm-jsons-match
       umm-version/current-variable-version [expected-variable1 expected-variable2 expected-variable3]
       (search/find-concepts-umm-json :variable {}))

      (testing "update variable not affect the associated collections in search result"
        (let [updated-long-name "Variable1234"]
          ;; sanity check that no variable is found with the about to be updated long name
          (du/assert-variable-umm-jsons-match
           umm-version/current-variable-version []
           (search/find-concepts-umm-json :variable {:keyword updated-long-name}))

          (let [updated-variable1-concept (variables/make-variable-concept
                                           {:Name "Variable1"
                                            :LongName updated-long-name}
                                           {:native-id "var1"
                                            :coll-concept-id (:concept-id coll1)})
                updated-variable1 (variables/ingest-variable-with-association updated-variable1-concept)
                updated-variable1 (merge updated-variable1 associations1)
                expected-variable1 (merge updated-variable1-concept
                                          updated-variable1
                                          {:associated-collections variable1-assoc-colls})]
            (index/wait-until-indexed)

            (du/assert-variable-umm-jsons-match
             umm-version/current-variable-version [expected-variable1]
             (search/find-concepts-umm-json :variable {:keyword updated-long-name}))

            (testing "delete collection affect the associated collections in search result"
              (let [coll1-concept (mdb/get-concept (:concept-id coll1))
                    _ (ingest/delete-concept coll1-concept)]
                (index/wait-until-indexed)

                ;; Now the variable is deleted through collection deletion.
                (is (= 0 (get-in (search/find-concepts-umm-json
                                   :variable {:keyword updated-long-name})
                                 [:results :hits])))))))))))

(deftest variable-search-sort
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection
               "PROV2"
               (data-umm-c/collection {:EntryTitle "E1"
                                       :ShortName "S1"})
               {:token token})
        [coll2 coll3 coll4] (doall (for [n (range 1 4)]
                                     (d/ingest-umm-spec-collection
                                      "PROV1"
                                      (data-umm-c/collection n {})
                                      {:token token})))
        _ (index/wait-until-indexed)
        var1-concept (variables/make-variable-concept
                      {:Name "variable"
                       :LongName "Measurement1"
                       :provider-id "PROV2"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        var2-concept (variables/make-variable-concept
                      {:Name "Variable 2"
                       :LongName "Measurement2"}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll2)})
        var3-concept (variables/make-variable-concept
                      {:Name "a variable"
                       :LongName "Long name"}
                      {:native-id "var3"
                       :coll-concept-id (:concept-id coll3)})
        var4-concept (variables/make-variable-concept
                      {:Name "variable"
                       :LongName "m.other"}
                      {:native-id "var4"
                       :coll-concept-id (:concept-id coll4)})
        variable1 (variables/ingest-variable-with-association var1-concept)
        variable2 (variables/ingest-variable-with-association var2-concept)
        variable3 (variables/ingest-variable-with-association var3-concept)
        variable4 (variables/ingest-variable-with-association var4-concept)]
    (index/wait-until-indexed)

    (are3 [sort-key expected-variables]
      (variables/assert-variable-search-order
       expected-variables
       (variables/search (if sort-key
                           {:sort-key sort-key}
                           {})))

      "Default sort"
      nil
      [variable3 variable4 variable1 variable2]

      "Sort by name"
      "name"
      [variable3 variable4 variable1 variable2]

      "Sort by name descending order"
      "-name"
      [variable2 variable4 variable1 variable3]

      "Sort by provider id"
      "provider"
      [variable2 variable3 variable4 variable1]

      "Sort by provider id descending order"
      "-provider"
      [variable1 variable2 variable3 variable4]

      "Sort by revision-date"
      "revision_date"
      [variable1 variable2 variable3 variable4]

      "Sort by revision-date descending order"
      "-revision_date"
      [variable4 variable3 variable2 variable1]

      "Sort by long name"
      "long-name"
      [variable3 variable4 variable1 variable2]

      "Sort by long name descending order"
      "-long-name"
      [variable2 variable1 variable4 variable3]

      "Sort by name ascending then provider id ascending explicitly"
      ["name" "provider"]
      [variable3 variable4 variable1 variable2]

      "Sort by name ascending then provider id descending order"
      ["name" "-provider"]
      [variable3 variable1 variable4 variable2]

      "Sort by name then provider id descending order"
      ["-name" "-provider"]
      [variable2 variable1 variable4 variable3])))

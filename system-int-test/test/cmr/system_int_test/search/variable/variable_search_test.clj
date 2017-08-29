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
    (is (= {:status 400, :errors ["Parameter [foo] was not recognized."]}
           (variables/search {:foo "bar"}))))
  (testing "Unsupported parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting variables."]}
           (variables/search {:sort-key "concept_id"})))
    (is (= {:status 400, :errors ["Parameter [entry_title] was not recognized."]}
           (variables/search {:entry_title "foo"}))))

  (testing "Unsupported options"
    (are [field option]
      (= {:status 400
          :errors [(format "Option [%s] is not supported for param [%s]" option (name field))]}
         (variables/search {field "foo" (format "options[%s][%s]" (name field) option) true}))
      :variable_name "and"
      :measurement "and"))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:errors
            ["Concept-id [V*] is not valid."
             "Option [pattern] is not supported for param [concept_id]"],
            :status 400}
           (variables/search {:concept-id "V*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:errors
            ["Concept-id [V*] is not valid."
             "Option [ignore_case] is not supported for param [concept_id]"],
            :status 400}
           (variables/search {:concept-id "V*" "options[concept-id][ignore-case]" true}))))

  (testing "Default variable search result format is JSON"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :variable {})]
      (is (= 200 status))
      (is (= "application/json; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format"
    (is (= {:errors ["The mime type [application/xml] is not supported for variables."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :xml :variable {})))))

  (testing "Unsuported result format"
    (is (= {:errors ["The mime type [application/atom+xml] is not supported for variables."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :variable {} {:url-extension "atom"}))))))

(deftest search-for-variables-test
  (let [variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "Variable1"
                                                         :LongName "Measurement1"
                                                         :provider-id "PROV1"})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable2"
                                                         :LongName "Measurement2"
                                                         :provider-id "PROV1"})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a subsitute for variable2"
                                                         :LongName "variable1"
                                                         :provider-id "PROV2"})
        variable4 (variables/ingest-variable-with-attrs {:native-id "var4"
                                                         :Name "v.other"
                                                         :LongName "m.other"
                                                         :provider-id "PROV2"})
        prov1-variables [variable1 variable2]
        prov2-variables [variable3 variable4]
        all-variables (concat prov1-variables prov2-variables)]
    (index/wait-until-indexed)

    (are3 [expected-variables query]
      (variables/assert-variable-search expected-variables (variables/search query))

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

(deftest search-variable-by-science-keywords-keyword-test
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
        variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "Variable1"
                                                         :LongName "Measurement1"
                                                         :ScienceKeywords [sk1 sk2]})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable2"
                                                         :LongName "Measurement2"
                                                         :ScienceKeywords [sk3]})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a subsitute for variable2"
                                                         :LongName "variable1"})]
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
        var1-concept (variables/make-variable-concept {:native-id "var1"
                                                       :Name "Variable1"})
        variable1 (variables/ingest-variable var1-concept {:token token})
        var2-concept (variables/make-variable-concept {:native-id "var2"
                                                       :Name "Variable2"})
        variable2 (variables/ingest-variable var2-concept {:token token})
        all-variables [variable1 variable2]]
    (index/wait-until-indexed)

    ;; Now I should find the all variables when searching
    (variables/assert-variable-search all-variables (variables/search {}))

    ;; Delete variable1
    (ingest/delete-concept var1-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variable
    (variables/assert-variable-search [variable2] (variables/search {}))

    ;; Now verify that after we delete a variable that has variable association,
    ;; we can't find it through search
    ;; create variable associations on variable2
    (variables/associate-by-concept-ids token
                                        (:concept-id variable2)
                                        [{:concept-id (:concept-id coll1)}])
    ;; Delete variable2
    (ingest/delete-concept var2-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variables
    (variables/assert-variable-search [] (variables/search {}))))

(deftest variable-search-in-umm-json-format-test
  (testing "variable search result in UMM JSON format has associated collections"
    (let [token (e/login (s/context) "user1")
          [coll1 coll2 coll3] (for [n (range 1 4)]
                                (d/ingest-umm-spec-collection
                                 "PROV1"
                                 (data-umm-c/collection n {})
                                 {:token token}))
          ;; create variables
          variable1-concept (variables/make-variable-concept {:native-id "var1"
                                                              :Name "Variable1"})
          variable1 (variables/ingest-variable variable1-concept)
          variable2-concept (variables/make-variable-concept {:native-id "var2"
                                                              :Name "Variable2"})
          variable2 (variables/ingest-variable variable2-concept)
          variable3-concept (variables/make-variable-concept {:native-id "var3"
                                                              :Name "Variable3"})
          variable3 (variables/ingest-variable variable3-concept)
          variable1-concept-id (:concept-id variable1)
          variable2-concept-id (:concept-id variable2)
          variable1-assoc-colls [{:concept-id (:concept-id coll1)}
                                 {:concept-id (:concept-id coll2)}]
          variable2-assoc-colls [{:concept-id (:concept-id coll3)
                                  :revision-id 1}]
          ;; Add the variable info to variable concepts for comparision with UMM JSON result
          expected-variable1 (merge variable1-concept
                                    variable1
                                    {:associated-collections variable1-assoc-colls})
          expected-variable2 (merge variable2-concept
                                    variable2
                                    {:associated-collections variable2-assoc-colls})
          expected-variable3 (merge variable3-concept variable3)]
      ;; index the collections so that they can be found during variable association
      (index/wait-until-indexed)
      ;; create variable associations
      (variables/associate-by-concept-ids token variable1-concept-id variable1-assoc-colls)
      (variables/associate-by-concept-ids token variable2-concept-id variable2-assoc-colls)
      (index/wait-until-indexed)

      ;; verify variable search UMM JSON response has associated collections
      (du/assert-variable-umm-jsons-match
       umm-version/current-variable-version [expected-variable1 expected-variable2 expected-variable3]
       (search/find-concepts-umm-json :variable {}))

      (testing "update variable not affect the associated collections in search result"
        (let [updated-variable1-name "Variable1234"
              updated-variable1-concept (variables/make-variable-concept
                                         {:native-id "var1"
                                          :Name updated-variable1-name})
              updated-variable1 (variables/ingest-variable updated-variable1-concept)
              expected-variable1 (merge updated-variable1-concept
                                        updated-variable1
                                        {:associated-collections variable1-assoc-colls})]
          (index/wait-until-indexed)

          (du/assert-variable-umm-jsons-match
           umm-version/current-variable-version [expected-variable1]
           (search/find-concepts-umm-json :variable {:variable_name updated-variable1-name}))

          (testing "delete collection affect the associated collections in search result"
            (let [coll1-concept (mdb/get-concept (:concept-id coll1))
                  _ (ingest/delete-concept coll1-concept)
                  ;; Now variable1 is only associated to coll2, as coll1 is deleted
                  expected-variable1 (assoc expected-variable1
                                            :associated-collections
                                            [{:concept-id (:concept-id coll2)}])]
              (index/wait-until-indexed)

              (du/assert-variable-umm-jsons-match
               umm-version/current-variable-version [expected-variable1]
               (search/find-concepts-umm-json
                :variable {:variable_name updated-variable1-name})))))))))

(deftest variable-search-sort
  (let [variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "variable"
                                                         :LongName "Measurement1"
                                                         :provider-id "PROV2"})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable 2"
                                                         :LongName "Measurement2"
                                                         :provider-id "PROV1"})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a variable"
                                                         :LongName "Long name"
                                                         :provider-id "PROV1"})
        variable4 (variables/ingest-variable-with-attrs {:native-id "var4"
                                                         :Name "variable"
                                                         :LongName "m.other"
                                                         :provider-id "PROV1"})]
    (index/wait-until-indexed)

    (are3 [sort-key expected-variables]
      (do
       (variables/assert-variable-search-order
        expected-variables
        (variables/search (if sort-key
                            {:sort-key sort-key}
                            {}))))

      "Default sort"
      nil
      [variable3 variable4 variable1 variable2]

      "Sort by name"
      "name"
      [variable3 variable4 variable1 variable2]

      "Sort by provider id"
      "provider_id"
      [variable2 variable3 variable4 variable1]

      "Sort by revision-date"
      "revision_date"
      [variable1 variable2 variable3 variable4]

      "Sort by long name"
      "long-name"
      [variable3 variable4 variable1 variable2]

      "Sort by name then long name"
      ["name" "long_name"]
      [variable3 variable4 variable1 variable2])))

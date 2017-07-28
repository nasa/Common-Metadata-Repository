(ns cmr.system-int-test.search.variable.variable-search-test
  "This tests searching variables."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.variable-util :as variables]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

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
         ;; variable-key and originator-id do not support ignore case because they are always case insensitive
         :variable_name "and"
         :measurement "and")))

(deftest search-for-variables-test
  (let [variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "Variable1"
                                                         :LongName "Measurement1"})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable2"
                                                         :LongName "Measurement2"})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a subsitute for variable2"
                                                         :LongName "variable1"})
        variable4 (variables/ingest-variable-with-attrs {:native-id "var4"
                                                         :Name "v.other"
                                                         :LongName "m.other"})
        all-variables [variable1 variable2 variable3 variable4]]
    (index/wait-until-indexed)

    (are3 [expected-variables query]
      (variables/assert-variable-search expected-variables (variables/search query))

      "Find all"
      all-variables {}

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
      ;; measurement Param
      "By measurement case sensitive - exact match"
      [variable1]
      {:measurement "Measurement1"}

      "By measurement case sensitive, default ignore-case true"
      [variable1]
      {:measurement "measurement1"}

      "By measurement ignore case false"
      []
      {:measurement "measurement1" "options[measurement][ignore-case]" false}

      "By measurement ignore case true"
      [variable1]
      {:measurement "measurement1" "options[measurement][ignore-case]" true}

      "By measurement Pattern, default false"
      []
      {:measurement "*other"}

      "By measurement Pattern true"
      [variable4]
      {:measurement "*other" "options[measurement][pattern]" true}

      "By measurement Pattern false"
      []
      {:measurement "*other" "options[measurement][pattern]" false}

      "By multiple measurements"
      [variable1 variable2]
      {:measurement ["Measurement1" "measurement2"]}

      "By multiple measurements with options"
      [variable1 variable4]
      {:measurement ["measurement1" "*other"] "options[measurement][pattern]" true}

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
      {:keyword "a* variable?"})))

(deftest deleted-variables-not-found-test
  (let [{token :token} (variables/setup-update-acl (s/context) "PROV1")
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

    ;; Now searching variables finds nothing
    (variables/assert-variable-search [variable2] (variables/search {}))

    ;; resave the variable
    (let [variable1-3 (variables/ingest-variable var1-concept {:token token})]

      (index/wait-until-indexed)

      ;; Now I should find the variable when searching
      (variables/assert-variable-search [variable1-3 variable2] (variables/search {})))))

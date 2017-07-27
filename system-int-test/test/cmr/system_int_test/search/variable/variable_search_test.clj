(ns cmr.system-int-test.search.variable.variable-search-test
  "This tests searching variables."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2]]
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
                                                         :Name "variable3"
                                                         :LongName "Measurement3"})
        variable4 (variables/ingest-variable-with-attrs {:native-id "var4"
                                                         :Name "v.other"
                                                         :LongName "OtherMeasurement"})
        all-variables [variable1 variable2 variable3 variable4]]
    (index/wait-until-indexed)

    (are2 [expected-variables query]
      (variables/assert-variable-search expected-variables (variables/search query))

      "Find all"
      all-variables {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; variable-name Param
      "By variable-name case sensitive - exact match"
      [variable1] {:variable-name "Variable1"}

      "By variable-name case sensitive, default ignore-case true"
      [variable1] {:variable-name "variable1"}

      "By variable-name ignore case false"
      [] {:variable-name "variable1" "options[variable-name][ignore-case]" false}

      "By variable-name ignore case true"
      [variable1] {:variable-name "variable1" "options[variable-name][ignore-case]" true}

      "By variable-name Pattern, default false"
      [] {:variable-name "*other"}

      "By variable-name Pattern true"
      [variable4] {:variable-name "*other" "options[variable-name][pattern]" true}

      "By variable-name Pattern false"
      [] {:variable-name "*other" "options[variable-name][pattern]" false})))

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

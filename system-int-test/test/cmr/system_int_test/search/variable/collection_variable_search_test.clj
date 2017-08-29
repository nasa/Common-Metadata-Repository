(ns cmr.system-int-test.search.variable.collection-variable-search-test
  "Tests searching for collections by associated variables"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as vu]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                vu/grant-all-variable-fixture]))

(deftest collection-variable-search-test
  (let [token (e/login (s/context) "user1")
        [coll1 coll2 coll3 coll4 coll5] (doall (for [n (range 1 6)]
                                                (d/ingest-umm-spec-collection
                                                 "PROV1"
                                                 (data-umm-c/collection n {})
                                                 {:token token})))
        ;; index the collections so that they can be found during variable association
        _ (index/wait-until-indexed)
        ;; create variables
        {variable1-concept-id :concept-id} (vu/ingest-variable-with-attrs
                                            {:native-id "var1"
                                             :Name "Variable1"
                                             :LongName "Measurement1"})
        {variable2-concept-id :concept-id} (vu/ingest-variable-with-attrs
                                            {:native-id "var2"
                                             :Name "Variable2"
                                             :LongName "Measurement2"})
        {variable3-concept-id :concept-id} (vu/ingest-variable-with-attrs
                                            {:native-id "somevar"
                                             :Name "SomeVariable"
                                             :LongName "Measurement2"})]

    ;; create variable associations
    ;; Variable1 is associated with coll1 and coll2
    (vu/associate-by-concept-ids token variable1-concept-id [{:concept-id (:concept-id coll1)}
                                                             {:concept-id (:concept-id coll2)}])
    ;; Variable2 is associated with coll2 and coll3
    (vu/associate-by-concept-ids token variable2-concept-id [{:concept-id (:concept-id coll2)}
                                                             {:concept-id (:concept-id coll3)}])
    ;; SomeVariable is associated with coll4
    (vu/associate-by-concept-ids token variable3-concept-id [{:concept-id (:concept-id coll4)}])
    (index/wait-until-indexed)

    (testing "search collections by variables"
      (are3 [items variable options]
        (let [params (merge {:variable_name variable}
                            (when options
                              {"options[variable_name]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single variable search"
        [coll1 coll2] "Variable1" {}

        "no matching variable"
        [] "Variable3" {}

        "multiple variables"
        [coll1 coll2 coll3] ["Variable1" "Variable2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["Variable1" "Variable2"] {:and false}

        "AND option true"
        [coll2] ["Variable1" "Variable2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3] "Var*" {:pattern true}

        "pattern false"
        [] "Var*" {:pattern false}

        "default pattern is false"
        [] "Var*" {}

        "ignore-case true"
        [coll1 coll2] "variable1" {:ignore-case true}

        "ignore-case false"
        [] "variable1" {:ignore-case false}))

    (testing "search collections by variable native-ids"
      (are3 [items variable options]
        (let [params (merge {:variable_native_id variable}
                            (when options
                              {"options[variable_native_id]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single variable search"
        [coll1 coll2] "var1" {}

        "no matching variable"
        [] "var3" {}

        "multiple variables"
        [coll1 coll2 coll3] ["var1" "var2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["var1" "var2"] {:and false}

        "AND option true"
        [coll2] ["var1" "var2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3] "var*" {:pattern true}

        "pattern false"
        [] "var*" {:pattern false}

        "default pattern is false"
        [] "var*" {}

        "ignore-case true"
        [coll1 coll2] "VAR1" {:ignore-case true}

        "ignore-case false"
        [] "VAR1" {:ignore-case false}))

    (testing "search collections by measurements"
      (are3 [items measurement options]
        (let [params (merge {:measurement measurement}
                            (when options
                              {"options[measurement]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single measurement search"
        [coll1 coll2] "Measurement1" {}

        "no matching measurement"
        [] "Measurement3" {}

        "multiple measurements"
        [coll1 coll2 coll3 coll4] ["Measurement1" "Measurement2"] {}

        "AND option false"
        [coll1 coll2 coll3 coll4] ["Measurement1" "Measurement2"] {:and false}

        "AND option true"
        [coll2] ["Measurement1" "Measurement2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3 coll4] "M*" {:pattern true}

        "pattern false"
        [] "M*" {:pattern false}

        "default pattern is false"
        [] "M*" {}

        "ignore-case true"
        [coll1 coll2] "measurement1" {:ignore-case true}

        "ignore-case false"
        [] "measurement1" {:ignore-case false}))


    (testing "search collections by variables-h"
      (are3 [items params options]
        (let [search-params (merge {:variables-h params}
                                   (when options
                                     {"options[variables-h]" options}))]
          (d/refs-match? items (search/find-refs :collection search-params)))

        "single measurement search"
        [coll1 coll2]
        {:0 {:measurement "Measurement1"}}
        {}

        "no matching measurement"
        []
        {:0 {:measurement "Measurement3"}}
        {}

        "multiple measurements"
        [coll2]
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {}

        "multiple measurements option OR true"
        [coll1 coll2 coll3 coll4]
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {:or true}

        "multiple measurements option OR false"
        [coll2]
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {:or false}

        "single variable search"
        [coll1 coll2]
        {:0 {:variable "Variable1"}}
        {}

        "no matching variable"
        []
        {:0 {:variable "Variable3"}}
        {}

        "multiple variables"
        [coll2]
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {}

        "multiple variables option OR true"
        [coll1 coll2 coll3]
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {:or true}

        "multiple variables option OR false"
        [coll2]
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {:or false}

        "pattern true"
        [coll1 coll2 coll3]
        {:0 {:variable "V*"}}
        {:pattern true}

        "pattern true"
        []
        {:0 {:variable "V*"}}
        {:pattern false}

        "default pattern is false"
        []
        {:0 {:variable "V*"}}
        {}

        "ignore-case true"
        [coll1 coll2]
        {:0 {:measurement "measurement1"}}
        {:ignore-case true}

        "ignore-case false"
        []
        {:0 {:measurement "measurement1"}}
        {:ignore-case false}

        "default ignore-case is true"
        [coll1 coll2]
        {:0 {:measurement "measurement1"}}
        {}

        "not combined variable fields"
        [coll2 coll3 coll4]
        {:0 {:variable "Variable2"}
         :1 {:measurement "Measurement2"}}
        {:or true}

        "combined variable fields"
        [coll2 coll3]
        {:0 {:variable "Variable2"
             :measurement "Measurement2"}}
        {:or true}))))

(deftest collection-variables-search-error-scenarios
  (testing "search by invalid format."
    (let [{:keys [status errors]} (search/find-refs :collection
                                                    {:variables {:0 {:and "true"}}})]
      (is (= 400 status))
      (is (re-find #"Parameter \[variables\] was not recognized." (first errors)))))
  (testing "search by invalid format."
    (let [{:keys [status errors]} (search/find-refs :collection
                                                    {:variables-h {:0 {:and "true"}}})]
      (is (= 400 status))
      (is (re-find #"parameter \[and\] is not a valid variable search term." (first errors))))))

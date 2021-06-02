(ns cmr.system-int-test.search.variable.collection-variable-search-test
  "Tests searching for collections by associated variables"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as atom]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service-util]
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
        var1-concept (vu/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        var2-concept (vu/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll2)})
        var3-concept (vu/make-variable-concept
                      {:Name "SomeVariable"
                       :LongName "Measurement2"}
                      {:native-id "somevar"
                       :coll-concept-id (:concept-id coll3)})
        var4-concept (vu/make-variable-concept
                      {:Name "Name4"
                       :LongName "Measurement4"}
                      {:native-id "v4"
                       :coll-concept-id (:concept-id coll4)})
        {variable1-concept-id :concept-id} (vu/ingest-variable-with-association var1-concept)
        {variable2-concept-id :concept-id} (vu/ingest-variable-with-association var2-concept)
        {variable3-concept-id :concept-id} (vu/ingest-variable-with-association var3-concept)
        {variable4-concept-id :concept-id} (vu/ingest-variable-with-association var4-concept)]

    (index/wait-until-indexed)

    (testing "search collections by variables"
      (are3 [items variable options]
        (let [params (merge {:variable_name variable}
                            (when options
                              {"options[variable_name]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single variable search"
        [coll1] "Variable1" {}

        "no matching variable"
        [] "Variable3" {}

        "multiple variables"
        [coll1 coll2] ["Variable1" "Variable2"] {}

        "AND option false"
        [coll1 coll2] ["Variable1" "Variable2"] {:and false}

        "AND option true"
        [] ["Variable1" "Variable2"] {:and true}

        "pattern true"
        [coll1 coll2] "Var*" {:pattern true}

        "pattern false"
        [] "Var*" {:pattern false}

        "default pattern is false"
        [] "Var*" {}

        "ignore-case true"
        [coll1] "variable1" {:ignore-case true}

        "ignore-case false"
        [] "variable1" {:ignore-case false}))

    (testing "search collections by variable concept-ids"
      (are3 [items variable options]
        (let [params (merge {:variable_concept_id variable}
                            (when options
                              {"options[variable_concept_id]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single variable search"
        [coll1] variable1-concept-id {}

        "variable concept id search is case sensitive"
        [] (string/lower-case variable1-concept-id) {}

        "another single variable search because every variable has to be associated."
        [coll4] variable4-concept-id {}

        "multiple variables"
        [coll1 coll2] [variable1-concept-id variable2-concept-id] {}

        "AND option false"
        [coll1 coll2] [variable1-concept-id variable2-concept-id] {:and false}

        "AND option true"
        [] [variable1-concept-id variable2-concept-id] {:and true}))

    (testing "search collections by variable native-ids"
      (are3 [items variable options]
        (let [params (merge {:variable_native_id variable}
                            (when options
                              {"options[variable_native_id]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single variable search"
        [coll1] "var1" {}

        "no matching variable"
        [] "var3" {}

        "multiple variables"
        [coll1 coll2] ["var1" "var2"] {}

        "AND option false"
        [coll1 coll2] ["var1" "var2"] {:and false}

        "AND option true"
        [] ["var1" "var2"] {:and true}

        "pattern true"
        [coll1 coll2] "var*" {:pattern true}

        "pattern false"
        [] "var*" {:pattern false}

        "default pattern is false"
        [] "var*" {}

        "ignore-case true"
        [coll1] "VAR1" {:ignore-case true}

        "ignore-case false"
        [] "VAR1" {:ignore-case false}))

    (testing "search collections by measurements"
      (are3 [items measurement options]
        (let [params (merge {:measurement measurement}
                            (when options
                              {"options[measurement]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single measurement search"
        [coll1] "Measurement1" {}

        "no matching measurement"
        [] "Measurement3" {}

        "multiple measurements"
        [coll1 coll2 coll3] ["Measurement1" "Measurement2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["Measurement1" "Measurement2"] {:and false}

        "AND option true"
        [] ["Measurement1" "Measurement2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3 coll4] "M*" {:pattern true}

        "pattern false"
        [] "M*" {:pattern false}

        "default pattern is false"
        [] "M*" {}

        "ignore-case true"
        [coll1] "measurement1" {:ignore-case true}

        "ignore-case false"
        [] "measurement1" {:ignore-case false}))


    (testing "search collections by variables-h"
      (are3 [items params options]
        (let [search-params (merge {:variables-h params}
                                   (when options
                                     {"options[variables-h]" options}))]
          (d/refs-match? items (search/find-refs :collection search-params)))

        "single measurement search"
        [coll1]
        {:0 {:measurement "Measurement1"}}
        {}

        "no matching measurement"
        []
        {:0 {:measurement "Measurement3"}}
        {}

        "multiple measurements"
        []
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {}

        "multiple measurements option OR true"
        [coll1 coll2 coll3]
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {:or true}

        "multiple measurements option OR false"
        []
        {:0 {:measurement "Measurement1"}
         :1 {:measurement "Measurement2"}}
        {:or false}

        "single variable search"
        [coll1]
        {:0 {:variable "Variable1"}}
        {}

        "no matching variable"
        []
        {:0 {:variable "Variable3"}}
        {}

        "multiple variables"
        []
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {}

        "multiple variables option OR true"
        [coll1 coll2]
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {:or true}

        "multiple variables option OR false"
        []
        {:0 {:variable "Variable1"}
         :1 {:variable "Variable2"}}
        {:or false}

        "pattern true"
        [coll1 coll2]
        {:0 {:variable "V*"}}
        {:pattern true}

        "pattern false"
        []
        {:0 {:variable "V*"}}
        {:pattern false}

        "default pattern is false"
        []
        {:0 {:variable "V*"}}
        {}

        "ignore-case true"
        [coll1]
        {:0 {:measurement "measurement1"}}
        {:ignore-case true}

        "ignore-case false"
        []
        {:0 {:measurement "measurement1"}}
        {:ignore-case false}

        "default ignore-case is true"
        [coll1]
        {:0 {:measurement "measurement1"}}
        {}

        "not combined variable fields"
        [coll2 coll3]
        {:0 {:variable "Variable2"}
         :1 {:measurement "Measurement2"}}
        {:or true}

        "combined variable fields"
        [coll2]
        {:0 {:variable "Variable2"
             :measurement "Measurement2"}}
        {:or true}))))

(deftest collection-variables-search-error-scenarios
  (testing "search by invalid param"
    (let [{:keys [status errors]} (search/find-refs :collection
                                                    {:variables {:0 {:and "true"}}})]
      (is (= 400 status))
      (is (re-find #"Parameter \[variables\] was not recognized." (first errors)))))
  (testing "search by invalid parameter field"
    (let [{:keys [status errors]} (search/find-refs :collection
                                                    {:variables-h {:0 {:and "true"}}})]
      (is (= 400 status))
      (is (re-find #"Parameter \[and\] is not a valid \[variables_h\] search term." (first errors)))))
  (testing "search variable concept id does not support ignore-case options"
    (let [{:keys [status errors]} (search/find-refs
                                   :collection
                                   {:variable_concept_id "V123456-PROV1"
                                    "options[variable_concept_id]" {:ignore-case true}})]
      (is (= 400 status))
      (is (re-find #"Option \[ignore_case\] is not supported for param \[variable_concept_id\]"
                   (first errors)))))
  (testing "search variable concept id does not support pattern options"
    (let [{:keys [status errors]} (search/find-refs
                                   :collection
                                   {:variable_concept_id "V123456-PROV1"
                                    "options[variable_concept_id]" {:pattern true}})]
      (is (= 400 status))
      (is (re-find #"Option \[pattern\] is not supported for param \[variable_concept_id\]"
                   (first errors))))))

(deftest collection-variable-search-atom-json-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "ET3"
                                                :short-name "S3"
                                                :version-id "V3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "ET4"
                                                :short-name "S4"
                                                :version-id "V4"}))
        ;; index the collections so that they can be found during variable association
        _ (index/wait-until-indexed)

        ;; create variable
        var1-concept (vu/make-variable-concept
                      {:Name "Variable1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll1)})
        {variable1-concept-id :concept-id} (vu/ingest-variable-with-association var1-concept)
        ;; service with SubsetType of Variable, when associated with collection
        ;; will turn the has-variables flag on the collection to true
        {serv1-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv1"
                                         :Name "service1"
                                         :ServiceOptions {:Subset {:VariableSubset {:AllowMultipleValues true}}}})
        {serv2-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv2"
                                         :Name "service2"
                                         :ServiceOptions {:Subset {:TemporalSubset {:AllowMultipleValues true}}}})]
    ;; index the collections so that they can be found during variable association
    (index/wait-until-indexed)

    ;; associate coll3 with a service that has SubsetType that is Variable
    (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll3)}])
    ;; associate coll4 with a service that has SubsetType that is Temporal
    ;; has-varialbes will be false, has-temporal-subsetting will be true.
    (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll4)}])
    (index/wait-until-indexed)

    (testing "search collections in ATOM format has-variables field"
      (are3 [coll expected-fields]
        (let [coll-with-extra-fields (merge coll expected-fields)
              {:keys [entry-title]} coll
              coll-atom (atom/collections->expected-atom
                         [coll-with-extra-fields]
                         (format "collections.atom?entry_title=%s" entry-title))
              {:keys [status results]} (search/find-concepts-atom
                                        :collection {:entry-title entry-title})]

          (is (= [200 coll-atom]
                 [status results])))

        "has-variables true"
        coll1 {:has-variables true}

        "has-variables false"
        coll2 {:has-variables false}

        "has-variables true through service association"
        coll3 {:has-variables true}

        "has-variables false, has-temporal-subsetting true through service association"
        coll4 {:has-variables false :has-temporal-subsetting true}))

    (testing "search collections in JSON format has-variables field"
      (are3 [coll expected-fields]
        (let [coll-with-extra-fields (merge coll expected-fields)
              {:keys [entry-title]} coll
              coll-json (atom/collections->expected-json
                         [coll-with-extra-fields]
                         (format "collections.json?entry_title=%s" entry-title))
              {:keys [status results]} (search/find-concepts-json
                                        :collection {:entry-title entry-title})]

          (is (= [200 coll-json]
                 [status results])))

        "has-variables true and associations exist in JSON format"
        coll1 {:has-variables true :variables [variable1-concept-id]}

        "has-variables false"
        coll2 {:has-variables false}))

    (testing "delete variable affect collection search has-variables field"
      (let [{:keys [entry-title]} coll1
            expected-json (atom/collections->expected-json
                           [(assoc coll1 :has-variables true :variables [variable1-concept-id])]
                           (format "collections.json?entry_title=%s" entry-title))
            {:keys [results]} (search/find-concepts-json
                               :collection {:entry-title entry-title})]
        ;; verify has-variables is true and associations exist before the variable is deleted
        (is (= expected-json results))

        ;; Delete variable1
        (ingest/delete-concept var1-concept {:token token})
        (index/wait-until-indexed)
        (let [{:keys [entry-title]} coll1
              expected-json (atom/collections->expected-json
                             [(assoc coll1 :has-variables false)]
                             (format "collections.json?entry_title=%s" entry-title))
              {:keys [results]} (search/find-concepts-json
                                 :collection {:entry-title entry-title})]
          ;; verify has-variables is false after the variable is deleted
          (is (= expected-json results)))))))

(ns cmr.system-int-test.search.variable.collection-measurement-search-test
  "Tests searching for collections by associated variables and measurements"
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

(deftest collection-variable-measurement-search-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (d/ingest-umm-spec-collection
                                     "PROV1"
                                     (data-umm-c/collection n {})))
        ;; index the collections so that they can be found during variable association
        _ (index/wait-until-indexed)
        variable1 (vu/make-variable {:Name "Variable1"
                                     :LongName "Measurement1"})
        variable2 (vu/make-variable {:Name "Variable2"
                                     :LongName "Measurement2"})
        token (e/login (s/context) "user1")]

    ;; create variables
    (vu/create-variable token variable1)
    (vu/create-variable token variable2)

    ;; create variable associations
    ;; variable1 is associated with coll1 and coll2
    ;; variable2 is associated with coll2 and coll3
    (vu/associate-by-concept-ids token "variable1" [{:concept-id (:concept-id coll1)}
                                                    {:concept-id (:concept-id coll2)}])
    (vu/associate-by-concept-ids token "variable2" [{:concept-id (:concept-id coll2)}
                                                    {:concept-id (:concept-id coll3)}])
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
        [coll1 coll2 coll3] ["Measurement1" "Measurement2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["Measurement1" "Measurement2"] {:and false}

        "AND option true"
        [coll2] ["Measurement1" "Measurement2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3] "M*" {:pattern true}

        "pattern false"
        [] "M*" {:pattern false}

        "default pattern is false"
        [] "M*" {}

        "ignore-case true"
        [coll1 coll2] "measurement1" {:ignore-case true}

        "ignore-case false"
        [] "measurement1" {:ignore-case false}))))

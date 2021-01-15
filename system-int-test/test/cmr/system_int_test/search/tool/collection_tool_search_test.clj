(ns cmr.system-int-test.search.tool.collection-tool-search-test
  "Tests searching for collections with associated tools"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as data2-collection]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool-util]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                tool-util/grant-all-tool-fixture]))

(deftest collection-tool-search-test
  (let [token (e/login (s/context) "user1")
        [coll1 coll2 coll3 coll4 coll5] (doall (for [n (range 1 6)]
                                                 (d/ingest-umm-spec-collection
                                                  "PROV1"
                                                  (data-umm-c/collection n {})
                                                  {:token token})))
        ;; index the collections so that they can be found during tool association
        _ (index/wait-until-indexed)
        ;; create tools
        {tool1-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                        {:native-id "tool1"
                                         :Name "Tool1"})
        {tool2-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                        {:native-id "tool2"
                                         :Name "Tool2"})
        {tool3-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                        {:native-id "someTool"
                                         :Name "SomeTool"
                                         :Type "Model"})
        {tool4-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                        {:native-id "S4"
                                         :Name "Name4"})]

    ;; create tool associations
    ;; tool1 is associated with coll1 and coll2
    (au/associate-by-concept-ids token tool1-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    ;; tool2 is associated with coll2 and coll3
    (au/associate-by-concept-ids token tool2-concept-id [{:concept-id (:concept-id coll2)}
                                                         {:concept-id (:concept-id coll3)}])
    ;; SomeTool is associated with coll4
    (au/associate-by-concept-ids token tool3-concept-id [{:concept-id (:concept-id coll4)}])
    (index/wait-until-indexed)

    (testing "search collections by tool names"
      (are3 [items tool options]
        (let [params (merge {:tool_name tool}
                            (when options
                              {"options[tool_name]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single tool search"
        [coll1 coll2] "tool1" {}

        "no matching tool"
        [] "tool3" {}

        "multiple tools"
        [coll1 coll2 coll3] ["tool1" "tool2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["tool1" "tool2"] {:and false}

        "AND option true"
        [coll2] ["tool1" "tool2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3] "Tool*" {:pattern true}

        "pattern false"
        [] "Tool*" {:pattern false}

        "default pattern is false"
        [] "Tool*" {}

        "ignore-case true"
        [coll1 coll2] "tool1" {:ignore-case true}

        "ignore-case false"
        [] "tool1" {:ignore-case false}))

    (testing "search collections by tool concept-ids"
      (are3 [items tool options]
        (let [params (merge {:tool_concept_id tool}
                            (when options
                              {"options[tool_concept_id]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single tool search"
        [coll1 coll2] tool1-concept-id {}

        "tool concept id search is case sensitive"
        [] (string/lower-case tool1-concept-id) {}

        "no matching tool"
        [] tool4-concept-id {}

        "multiple tools"
        [coll1 coll2 coll3] [tool1-concept-id tool2-concept-id] {}

        "AND option false"
        [coll1 coll2 coll3] [tool1-concept-id tool2-concept-id] {:and false}

        "AND option true"
        [coll2] [tool1-concept-id tool2-concept-id] {:and true}))

    (testing "search collections by tool types"
      (are3 [items tool]
        (let [params (merge {:tool_type tool})]
          (d/refs-match? items (search/find-refs :collection params)))

        "single tool search"
        [coll4] "Model"

        "different single tool search"
        [coll1 coll2 coll3] "Downloadable Tool"

        "multiple search search"
        [coll1 coll2 coll3 coll4] ["Model" "Downloadable Tool"]))))

(deftest collection-tool-search-result-fields-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest "PROV1" (data2-collection/collection {:entry-title "ET1"
                                                              :short-name "S1"
                                                              :version-id "V1"}))

        coll2 (d/ingest "PROV1" (data2-collection/collection {:entry-title "ET2"
                                                              :short-name "S2"
                                                              :version-id "V2"}))
        ;; create tool
        {tool1-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                         {:native-id "tl1"
                                          :Name "tool1"
                                          :SupportedOutputFormats ["TIFF" "JPEG"]
                                          :SupportedInputFormats ["TIFF"]})

        {tool2-concept-id :concept-id} (tool-util/ingest-tool-with-attrs
                                         {:native-id "tl2"
                                          :Name "tool2"
                                          :SupportedOutputFormats ["TIFF"]
                                          :SupportedInputFormats ["TIFF"]})]


    ;; index the collections so that they can be found during service association
    (index/wait-until-indexed)
    ;; associate tool1 and tool2 to coll1
    (au/associate-by-concept-ids token tool1-concept-id [{:concept-id (:concept-id coll1)}])
    (au/associate-by-concept-ids token tool2-concept-id [{:concept-id (:concept-id coll1)}])

    ;; associate tool2 to coll2
    (au/associate-by-concept-ids token tool2-concept-id [{:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify search result on coll1 contains associations with both tool1 and tool2, and has-formats being false
    ;; because tools have no effect on the has-formats flag
    (tool-util/assert-collection-search-result
     coll1 {:has-formats false} [tool1-concept-id tool2-concept-id])

    ;; verify search result on coll1 contains associations with tool2, and has-formats being false
    ;; because tools have no effect on has-formats flag.
    (tool-util/assert-collection-search-result
     coll2 {:has-formats false} [tool2-concept-id])))

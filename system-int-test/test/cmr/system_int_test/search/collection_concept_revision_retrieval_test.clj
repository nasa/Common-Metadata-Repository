(ns cmr.system-int-test.search.collection-concept-revision-retrieval-test
  "Integration test for collection concept map retrieval with params"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.collection :as c]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [cmr.common.util :refer [are2]]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"} false))

(comment
  (dev-sys-util/reset)
  (ingest/create-provider "provguid1" "PROV1")
  (ingest/create-provider "provguid2" "PROV2")

  (def user1-token (e/login (s/context) "user1"))

  (search/get-concept-by-concept-id "C1200000000-PROV1"
                                    {:query-params {:token user1-token}})

  (get-in user/system [:apps :search :caches :acls])

)

(deftest retrieve-collection-concept-revisions-by-params

  ;; Users with admin privs have access to all collections
  (e/grant-group-admin (s/context) "group1")

  ;; Ingest 2 early versions of coll1
  (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                    :projects (dc/projects "ESI_1")}))
  (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                    :projects (dc/projects "ESI_2")}))


  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-id "entry-1"
                                                :entry-title "et1"
                                                :version-id "v1"
                                                :short-name "s1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-id "entry-2"
                                                :entry-title "et2"
                                                :version-id "v1"
                                                :short-name "s2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-id "entry-3"
                                                :entry-title "et3"
                                                :version-id "v3"
                                                :short-name "s3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-id "entry-1"
                                                :entry-title "et1"
                                                :version-id "v3"
                                                :short-name "s4"}))

        ;; tokens
        user1-token (e/login (s/context) "user1" "group1")]
    (index/wait-until-indexed)

    (testing "find-with-parameters"
      (testing "latest concepts"
        (are2 [collections params]
              (= (set collections)
                 (set (-> (search/find-concept-revisions :collection params)
                          :concepts)))
              "provider-id"
              [coll1 coll2] {:provider-id "PROV1"})))



    #_(testing "retrieval by params"
      (let [response (search/get-concept-by-concept-id (:concept-id coll1)
                                                       {:query-params {:token user1-token}})]
        (is (= 404 (:status response)))
        (is (re-find #"Concept with concept-id: .*? could not be found" (:body response)))))
    ))


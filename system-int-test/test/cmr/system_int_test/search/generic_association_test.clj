(ns cmr.system-int-test.search.generic-association-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as association-util]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.system-int-test.utils.tool-util :as tool-util]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.umm-spec.models.umm-variable-models :as umm-v])
  (:import
   [java.util UUID]))

(defn grant-all-generic-permission-fixture
  "A test fixture that grants all users the ability to create and modify collection, generic documents and generic associations."
  [f]
  (echo-util/grant-system-ingest-management (system/context) [:read :update] [:read :update])
  (f))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    grant-all-generic-permission-fixture
                                    tags/grant-all-tag-fixture]))

(defn search-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type-ext params]
   (-> {:method :get
        :url (format "%s%s?%s" (url-helper/search-root) concept-type-ext params)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false}
       (client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Test that generic associations can be made between generic documents and tools.
;; Also test the collection-tool associations through the old association api and verify
;; the associations are shown in the right place in the search result.
(deftest test-tool-and-generic-association
  (let [token (echo-util/login (system/context) "user1")
        ;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;;Then ingest two collections and two tools 
        coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll1"
                                                      :EntryTitle "entry-title1"})
                                                    {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll2"
                                                      :EntryTitle "entry-title2"})
                                                    {:token "mock-echo-system-token"})
        coll1-concept-id (:concept-id coll1)
        coll1-revision-id (:revision-id coll1)
        coll2-concept-id (:concept-id coll2)
        coll2-revision-id (:revision-id coll2)
        _ (index/wait-until-indexed)
        tl1 (tool-util/ingest-tool-with-attrs {:native-id "tl1" :Name "tool1"}) 
        tl2 (tool-util/ingest-tool-with-attrs {:native-id "tl2" :Name "tool2"}) 
        tl1-concept-id (:concept-id tl1)
        tl1-revision-id (:revision-id tl1)
        tl2-concept-id (:concept-id tl2)
        tl2-revision-id (:revision-id tl2)]
    (index/wait-until-indexed)
    (testing "Associate collection with tool by concept-id and revision-ids"
      (let [response1 (association-util/associate-by-concept-ids
                       token tl1-concept-id [{:concept-id coll1-concept-id}
                                             {:concept-id coll2-concept-id}])
            _ (index/wait-until-indexed)
            ;;Search for the tool tl1, it should return the association
            tl1-search-result (search-request "tools.umm_json" "native_id=tl1")
            tl1-search-body (json/parse-string (:body tl1-search-result) true)
            tl1-search-generic-associations (get-in (first (:items tl1-search-body)) [:meta :associations :collections])
           
            ;;Search for the collection coll1, it should return the association
            coll1-search-result (search-request "collections.umm-json" "entry_title=entry-title1")
            coll1-search-body (json/parse-string (:body coll1-search-result) true)
            coll1-search-generic-associations (get-in (first (:items coll1-search-body)) [:meta :associations :tools])

            ;;Search for the collection coll2, it should return the association
            coll2-search-result (search-request "collections.umm-json" "entry_title=entry-title2")
            coll2-search-body (json/parse-string (:body coll2-search-result) true)
            coll2-search-generic-associations (get-in (first (:items coll2-search-body)) [:meta :associations :tools])]
        (is (= 200 (:status response1)))

        ;; Search for the tool tl1 returns the coll1 and coll2 as association
        (is (= (set [coll2-concept-id coll1-concept-id])
               (set tl1-search-generic-associations)))

        ;; Search for the collection coll1 returns the tl1 as association
        (is (= [tl1-concept-id]
               coll1-search-generic-associations))

        ;; Search for the collection coll2 returns the tl1 as association
        (is (= [tl1-concept-id]
               coll2-search-generic-associations))))
             
    (testing "Associate tool with tool by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl2-concept-id tl2-revision-id [{:concept-id tl1-concept-id  :revision-id tl1-revision-id}])
            ;;Switch the position of tl1 and tl2 should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id tl2-concept-id :revision-id tl2-revision-id}])
            ;;Try to associate tl1 with tl2 by concept-id only. This shouldn't be allowed.
            ;;Try to associate tl2 with tl1 by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl2-concept-id nil [{:concept-id tl1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the tool tl1, it should return the association
            tl1-search-result (search-request "tools.umm_json" "native_id=tl1")
            tl1-search-body (json/parse-string (:body tl1-search-result) true)
            tl1-search-generic-associations (get-in (first (:items tl1-search-body)) [:meta :associations :tools])

            ;;Search for the tool tl2, it should return the association
            tl2-search-result (search-request "tools.umm_json" "native_id=tl2")
            tl2-search-body (json/parse-string (:body tl2-search-result) true)
            tl2-search-generic-associations (get-in (first (:items tl2-search-body)) [:meta :associations :tools])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id tl2-concept-id :revision-id tl2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the tool tl1 again, it should NOT return the association
            tl1-search-result1 (search-request "tools.umm_json" "native_id=tl1")
            tl1-search-body1 (json/parse-string (:body tl1-search-result1) true)
            tl1-search-generic-associations1 (get-in (first (:items tl1-search-body1)) [:meta :associations :tools])

            ;;Search for the tool tl2 again, it should NOT return the association
            tl2-search-result1 (search-request "tools.umm_json" "native_id=tl2")
            tl2-search-body1 (json/parse-string (:body tl2-search-result1) true)
            tl2-search-generic-associations1 (get-in (first (:items tl2-search-body1)) [:meta :associations :tools])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[TL\d*-PROV1\] and concept id \[TL\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the tool tl1 returns the tl2 as generic association
        (is (= [tl2-concept-id]
               tl1-search-generic-associations))

        ;; Search for the tool tl2 returns the tl1 as generic association
        (is (= [tl1-concept-id]
               tl2-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the tool tl1 again doesn't return the tl2 as generic association
        (is (= nil tl1-search-generic-associations1))

        ;; Search for the tool tl2 again doesn't return the tl1 as generic association
        (is (= nil tl2-search-generic-associations1))))
    (testing "Associate grid with tool by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id grid-revision-id [{:concept-id tl1-concept-id  :revision-id tl1-revision-id}])
            ;;Switch the position of grid and tool should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;;Try to associate grid with tool by concept-id only. This shouldn't be allowed.
            ;;Try to associate tool with grid by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id tl1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid, it should return the association
            grid-search-result (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body (json/parse-string (:body grid-search-result) true)
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:associations :tools])

            ;;Search for the tool, it should return the association
            tl1-search-result (search-request "tools.umm_json" "native_id=tl1")
            tl1-search-body (json/parse-string (:body tl1-search-result) true)
            tl1-search-generic-associations (get-in (first (:items tl1-search-body)) [:meta :associations :grids])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:associations :tools])

            ;;Search for the tool again, it should NOT return the association
            tl1-search-result1 (search-request "tools.umm_json" "native_id=tl1")
            tl1-search-body1 (json/parse-string (:body tl1-search-result1) true)
            tl1-search-generic-associations1 (get-in (first (:items tl1-search-body1)) [:meta :associations :grids])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[GRD\d*-PROV1\] and concept id \[TL\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the grid returns the tl1 as generic association
        (is (= [tl1-concept-id]
               grid-search-generic-associations))

        ;; Search for the tool returns the grid as generic association
        (is (= [grid-concept-id]
               tl1-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the tl1 as generic association
        (is (= nil grid-search-generic-associations1))

        ;; Search for the tool again doesn't return the grid as generic association
        (is (= nil  tl1-search-generic-associations1))))))

;; Test that generic associations can be made between generic documents and services.
;; Also test the collection-service associations through the old association api and verify
;; the associations are shown in the right place in the search result.
(deftest test-service-and-generic-association
  (let [token (echo-util/login (system/context) "user1")
        ;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;;Then ingest two collections and two services 
        coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll1"
                                                      :EntryTitle "entry-title1"})
                                                    {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll2"
                                                      :EntryTitle "entry-title2"})
                                                    {:token "mock-echo-system-token"})
        coll1-concept-id (:concept-id coll1)
        coll1-revision-id (:revision-id coll1)
        coll2-concept-id (:concept-id coll2)
        coll2-revision-id (:revision-id coll2)
        _ (index/wait-until-indexed)
        sv1 (service-util/ingest-service-with-attrs {:native-id "sv1"
                                                     :Name "service1"}) 
        sv2 (service-util/ingest-service-with-attrs {:native-id "sv2"
                                                     :Name "service2"}) 
        sv1-concept-id (:concept-id sv1)
        sv1-revision-id (:revision-id sv1)
        sv2-concept-id (:concept-id sv2)
        sv2-revision-id (:revision-id sv2)]
    (index/wait-until-indexed)
    (testing "Associate collection with service by concept-id and revision-ids"
      (let [response1 (association-util/associate-by-concept-ids
                       token sv1-concept-id [{:concept-id coll1-concept-id}
                                             {:concept-id coll2-concept-id}])
            _ (index/wait-until-indexed)
            ;;Search for the service sv1, it should return the association
            sv1-search-result (search-request "services.umm_json" "native_id=sv1")
            sv1-search-body (json/parse-string (:body sv1-search-result) true)
            sv1-search-generic-associations (get-in (first (:items sv1-search-body)) [:meta :associations :collections])
           
            ;;Search for the collection coll1, it should return the association
            coll1-search-result (search-request "collections.umm-json" "entry_title=entry-title1")
            coll1-search-body (json/parse-string (:body coll1-search-result) true)
            coll1-search-generic-associations (get-in (first (:items coll1-search-body)) [:meta :associations :services])

            ;;Search for the collection coll2, it should return the association
            coll2-search-result (search-request "collections.umm-json" "entry_title=entry-title2")
            coll2-search-body (json/parse-string (:body coll2-search-result) true)
            coll2-search-generic-associations (get-in (first (:items coll2-search-body)) [:meta :associations :services])]
        (is (= 200 (:status response1)))

        ;; Search for the service sv1 returns the coll1 as association
        (is (= (set [coll2-concept-id coll1-concept-id])
               (set sv1-search-generic-associations)))

        ;; Search for the collection coll1 returns the sv1 as association
        (is (= [sv1-concept-id]
               coll1-search-generic-associations))

        ;; Search for the collection coll2 returns the sv1 as association
        (is (= [sv1-concept-id]
               coll2-search-generic-associations))))
             
    (testing "Associate service with service by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv2-concept-id sv2-revision-id [{:concept-id sv1-concept-id  :revision-id sv1-revision-id}])
            ;;Switch the position of sv1 and sv2 should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id sv2-concept-id :revision-id sv2-revision-id}])
            ;;Try to associate sv1 with sv2 by concept-id only. This shouldn't be allowed.
            ;;Try to associate sv2 with sv1 by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv2-concept-id nil [{:concept-id sv1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the service sv1, it should return the association
            sv1-search-result (search-request "services.umm_json" "native_id=sv1")
            sv1-search-body (json/parse-string (:body sv1-search-result) true)
            sv1-search-generic-associations (get-in (first (:items sv1-search-body)) [:meta :associations :services])

            ;;Search for the service sv2, it should return the association
            sv2-search-result (search-request "services.umm_json" "native_id=sv2")
            sv2-search-body (json/parse-string (:body sv2-search-result) true)
            sv2-search-generic-associations (get-in (first (:items sv2-search-body)) [:meta :associations :services])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id sv2-concept-id :revision-id sv2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the service sv1 again, it should NOT return the association
            sv1-search-result1 (search-request "services.umm_json" "native_id=sv1")
            sv1-search-body1 (json/parse-string (:body sv1-search-result1) true)
            sv1-search-generic-associations1 (get-in (first (:items sv1-search-body1)) [:meta :associations :services])

            ;;Search for the service sv2 again, it should NOT return the association
            sv2-search-result1 (search-request "services.umm_json" "native_id=sv2")
            sv2-search-body1 (json/parse-string (:body sv2-search-result1) true)
            sv2-search-generic-associations1 (get-in (first (:items sv2-search-body1)) [:meta :associations :services])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[S\d*-PROV1\] and concept id \[S\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the service sv1 returns the sv2 as generic association
        (is (= [sv2-concept-id]
               sv1-search-generic-associations))

        ;; Search for the service sv2 returns the sv1 as generic association
        (is (= [sv1-concept-id]
               sv2-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the service sv1 again doesn't return the sv2 as generic association
        (is (= nil sv1-search-generic-associations1))

        ;; Search for the service sv2 again doesn't return the sv1 as generic association
        (is (= nil sv2-search-generic-associations1))))
    (testing "Associate grid with service by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id grid-revision-id [{:concept-id sv1-concept-id  :revision-id sv1-revision-id}])
            ;;Switch the position of grid and service should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;;Try to associate grid with service by concept-id only. This shouldn't be allowed.
            ;;Try to associate service with grid by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id sv1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid, it should return the association
            grid-search-result (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body (json/parse-string (:body grid-search-result) true)
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:associations :services])

            ;;Search for the service, it should return the association
            sv1-search-result (search-request "services.umm_json" "native_id=sv1")
            sv1-search-body (json/parse-string (:body sv1-search-result) true)
            sv1-search-generic-associations (get-in (first (:items sv1-search-body)) [:meta :associations :grids])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:associations :services])

            ;;Search for the service again, it should NOT return the association
            sv1-search-result1 (search-request "services.umm_json" "native_id=sv1")
            sv1-search-body1 (json/parse-string (:body sv1-search-result1) true)
            sv1-search-generic-associations1 (get-in (first (:items sv1-search-body1)) [:meta :associations :grids])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[GRD\d*-PROV1\] and concept id \[S\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the grid returns the sv1 as generic association
        (is (= [sv1-concept-id]
               grid-search-generic-associations))

        ;; Search for the service returns the grid as generic association
        (is (= [grid-concept-id]
               sv1-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the sv1 as generic association
        (is (= nil grid-search-generic-associations1))

        ;; Search for the service again doesn't return the grid as generic association
        (is (= nil  sv1-search-generic-associations1))))))

;; Test that generic associations can be made between generic documents and variables.
(deftest test-variable-and-generic-association
  (let [token (echo-util/login (system/context) "user1")
        ;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;;Then ingest two collections and two variables
        coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll1"
                                                      :EntryTitle "entry-title1"})
                                                    {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll2"
                                                      :EntryTitle "entry-title2"})
                                                    {:token "mock-echo-system-token"})
        coll1-concept-id (:concept-id coll1)
        coll1-revision-id (:revision-id coll1)
        coll2-concept-id (:concept-id coll2)
        coll2-revision-id (:revision-id coll2)
        _ (index/wait-until-indexed)
        concept1 (variable-util/make-variable-concept
                  {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                           :Size 3
                                                           :Type "OTHER"})]}
                  {:native-id "var1"
                   :coll-concept-id (:concept-id coll1)})
        var1 (variable-util/ingest-variable-with-association
              concept1
              (variable-util/token-opts token))
        concept2 (variable-util/make-variable-concept
                  {:Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land"
                                                           :Size 3
                                                           :Type "OTHER"})]}
                  {:native-id "var2"
                   :coll-concept-id (:concept-id coll2)})
        var2 (variable-util/ingest-variable-with-association
              concept2
              (variable-util/token-opts token))
        var1-concept-id (:concept-id var1)
        var1-revision-id (:revision-id var1)
        var2-concept-id (:concept-id var2)
        var2-revision-id (:revision-id var2)]
    (index/wait-until-indexed)
    (testing "Associate variable with variable by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token var2-concept-id var2-revision-id [{:concept-id var1-concept-id  :revision-id var1-revision-id}])
            ;;Switch the position of grid and variable should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id var2-concept-id :revision-id var2-revision-id}])
            ;;Try to associate var1 with var2 by concept-id only. This shouldn't be allowed.
            ;;Try to associate var2 with var1 by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token var2-concept-id nil [{:concept-id var1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the variable var1, it should return the association
            var1-search-result (search-request "variables.umm_json" "native_id=var1")
            var1-search-body (json/parse-string (:body var1-search-result) true)
            var1-search-generic-associations (get-in (first (:items var1-search-body)) [:associations :variables])
            var1-search-generic-associations-in-meta (get-in (first (:items var1-search-body)) [:meta :associations :variables])
            var1-search-generic-association-details-in-meta (get-in (first (:items var1-search-body)) [:meta :association-details :variables])

            ;;Search for the variable var2, it should return the association
            var2-search-result (search-request "variables.umm_json" "native_id=var2")
            var2-search-body (json/parse-string (:body var2-search-result) true)
            var2-search-generic-associations (get-in (first (:items var2-search-body)) [:associations :variables])
            var2-search-generic-associations-in-meta (get-in (first (:items var2-search-body)) [:meta :associations :variables])
            var2-search-generic-association-details-in-meta (get-in (first (:items var2-search-body)) [:meta :association-details :variables])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id var2-concept-id :revision-id var2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the variable var1 again, it should NOT return the association
            var1-search-result1 (search-request "variables.umm_json" "native_id=var1")
            var1-search-body1 (json/parse-string (:body var1-search-result1) true)
            var1-search-generic-associations1 (get-in (first (:items var1-search-body1)) [:associations :variables])

            ;;Search for the variable var2 again, it should NOT return the association
            var2-search-result1 (search-request "variables.umm_json" "native_id=var2")
            var2-search-body1 (json/parse-string (:body var2-search-result1) true)
            var2-search-generic-associations1 (get-in (first (:items var2-search-body1)) [:associations :variables])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[V\d*-PROV1\] and concept id \[V\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the variable var1 returns the var2 as generic association
        (is (= [{:concept-id var2-concept-id :revision-id var2-revision-id}]
               var1-search-generic-associations))
        (is (= [{:concept-id var2-concept-id :revision-id var2-revision-id}]
               var1-search-generic-association-details-in-meta))
        (is (= [var2-concept-id]
               var1-search-generic-associations-in-meta))

        ;; Search for the variable var2 returns the var1 as generic association
        (is (= [{:concept-id var1-concept-id :revision-id var1-revision-id}]
               var2-search-generic-associations))
        (is (= [{:concept-id var1-concept-id :revision-id var1-revision-id}]
               var2-search-generic-association-details-in-meta))
        (is (= [var1-concept-id]
               var2-search-generic-associations-in-meta))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the variable var1 again doesn't return the var2 as generic association
        (is (= nil var1-search-generic-associations1))

        ;; Search for the variable var2 again doesn't return the var1 as generic association
        (is (= nil
               var2-search-generic-associations1))))
    (testing "Associate grid with variable by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id grid-revision-id [{:concept-id var1-concept-id  :revision-id var1-revision-id}])
            ;;Switch the position of grid and variable should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;;Try to associate grid with variable by concept-id only. This shouldn't be allowed.
            ;;Try to associate variable with grid by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id var1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid, it should return the association
            grid-search-result (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body (json/parse-string (:body grid-search-result) true)
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:associations])

            ;;Search for the variable, it should return the association
            var1-search-result (search-request "variables.umm_json" "native_id=var1")
            var1-search-body (json/parse-string (:body var1-search-result) true)
            var1-search-generic-associations (get-in (first (:items var1-search-body)) [:associations :grids])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:associations])

            ;;Search for the variable again, it should NOT return the association
            var1-search-result1 (search-request "variables.umm_json" "native_id=var1")
            var1-search-body1 (json/parse-string (:body var1-search-result1) true)
            var1-search-generic-associations1 (get-in (first (:items var1-search-body1)) [:associations :grids])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[GRD\d*-PROV1\] and concept id \[V\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the grid returns the var1 as generic association
        (is (= [var1-concept-id]
               (:variables grid-search-generic-associations)))

        ;; Search for the variable returns the grid as generic association
        (is (= [{:concept-id grid-concept-id :revision-id grid-revision-id}]
               var1-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the var1 as generic association
        (is (= nil grid-search-generic-associations1))

        ;; Search for the variable again doesn't return the grid as generic association
        (is (= nil  var1-search-generic-associations1))))
    
    (testing "Associate collection with collection by concept-id and revision-ids"
      ;;since we ingested two collections in this test, it's better to do the collection-to-collection association test here.
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll2-concept-id coll2-revision-id [{:concept-id coll1-concept-id  :revision-id coll1-revision-id}])
            ;;Switch the position of grid and variable should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll1-concept-id coll1-revision-id [{:concept-id coll2-concept-id :revision-id coll2-revision-id}])
            ;;Try to associate coll1 with coll2 by concept-id only. This shouldn't be allowed.
            ;;Try to associate coll2 with coll1 by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll2-concept-id nil [{:concept-id coll1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the collection coll1, it should return the association
            coll1-search-result (search-request "collections.umm-json" "entry_title=entry-title1")
            coll1-search-body (json/parse-string (:body coll1-search-result) true)
            coll1-search-generic-associations (get-in (first (:items coll1-search-body)) [:meta :associations :collections])

            ;;Search for the collection coll2, it should return the association
            coll2-search-result (search-request "collections.umm-json" "entry_title=entry-title2")
            coll2-search-body (json/parse-string (:body coll2-search-result) true)
            coll2-search-generic-associations (get-in (first (:items coll2-search-body)) [:meta :associations :collections])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token coll1-concept-id coll1-revision-id [{:concept-id coll2-concept-id :revision-id coll2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the collection coll1 again, it should NOT return the association
            coll1-search-result1 (search-request "collections.umm-json" "entry_title=entry-title1")
            coll1-search-body1 (json/parse-string (:body coll1-search-result1) true)
            coll1-search-generic-associations1 (get-in (first (:items coll1-search-body1)) [:meta :associations :collections])

            ;;Search for the collection coll2 again, it should NOT return the association
            coll2-search-result1 (search-request "collections.umm-json" "entry_title=entry-title2")
            coll2-search-body1 (json/parse-string (:body coll2-search-result1) true)
            coll2-search-generic-associations1 (get-in (first (:items coll2-search-body1)) [:meta :associations :collections])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[C\d*-PROV1\] and concept id \[C\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the collection coll1 returns the coll2 as generic association
        (is (= [coll2-concept-id]
               coll1-search-generic-associations))

        ;; Search for the collection coll2 returns the coll1 as generic association
        (is (= [coll1-concept-id]
               coll2-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the collection coll1 again doesn't return the coll2 as generic association
        (is (= nil
               coll1-search-generic-associations1))

        ;; Search for the collection coll2 again doesn't return the coll1 as generic association
        (is (= nil
               coll2-search-generic-associations1))))))

;; Test that generic associations can be made between generic documents and collections. 
(deftest test-collection-and-generic-association
  (let [;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;;Then ingest a collection
        coll (data-core/ingest-umm-spec-collection "PROV1"
              (data-umm-c/collection
               {:ShortName "coll1"
                :EntryTitle "entry-title1"})
              {:token "mock-echo-system-token"})
        coll-concept-id (:concept-id coll)
        coll-revision-id (:revision-id coll)
        token (echo-util/login (system/context) "user1")]
    (index/wait-until-indexed)

    (testing "Generic associations can not be made between collection and service,tool and variables."
      (let [ response (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll-concept-id nil [{:concept-id "SE1234-PROV1"} {:concept-id "V1234-PROV1"} {:concept-id "S1234-PROV1"} {:concept-id "TL1234-PROV1"}])]
        (is (= 422 (:status response)))
        (is (some? (re-find #"The following concept ids \[\(\"V1234-PROV1\" \"S1234-PROV1\" \"TL1234-PROV1\"\)\] can not be associated with concept id \[C\d*-PROV1\] because collection/\[service\|tool\|variable\] associations are not supported by the new generic association api."
                            (first (:errors response))))
            "error message did not match")))

    (testing "Associate grid with collection by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id grid-revision-id [{:concept-id coll-concept-id :revision-id coll-revision-id}])
            ;;Switch the position of grid and collection should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll-concept-id coll-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;;Try to associate grid with collection by concept-id only. This shouldn't be allowed.
            ;;Try to associate collection with grid by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id coll-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid, it should return the association
            grid-search-result (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body (json/parse-string (:body grid-search-result) true)
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:associations])

            ;;Search for the collection, it should return the association
            coll-search-result (search-request "collections.umm-json" "entry_title=entry-title1")
            coll-search-body (json/parse-string (:body coll-search-result) true)
            coll-search-generic-associations (get-in (first (:items coll-search-body)) [:meta :associations :grids])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token coll-concept-id coll-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:associations])

            ;;Search for the collection again, it should NOT return the association
            coll-search-result1 (search-request "collections.umm-json" "entry_title=entry-title1")
            coll-search-body1 (json/parse-string (:body coll-search-result1) true)
            coll-search-generic-associations1 (get-in (first (:items coll-search-body1)) [:meta :associations :grids])]

        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The first and second associations contain different associated-item because associated-item
        ;; should always be related to of the items in the list that's associated to a particular concept.
        (is (= {:concept-id coll-concept-id  :revision-id coll-revision-id}
               (get-in (first (:body response1)) [:associated-item])))
        (is (= {:concept-id grid-concept-id  :revision-id grid-revision-id}
               (get-in (first (:body response2)) [:associated-item])))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (some? (re-find #"There are already generic associations between concept id \[GRD\d*-PROV1\] and concept id \[C\d*-PROV1\] revision ids \[\d*\], cannot create generic association on the same concept without revision id."
                            (-> response3
                                (:body)
                                (first)
                                (:errors)
                                (first)))))

        ;; Search for the grid returns the collection as generic association
        (is (= {:collections [coll-concept-id]}
               grid-search-generic-associations))

        ;; Search for the collection returns the grid as generic association
        (is (= [grid-concept-id]
               coll-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)) "Dissociation of the association failed.")

        ;; Search for the grid again doesn't return the collection as generic association
        (is (= nil
               grid-search-generic-associations1))

        ;; Search for the collection again doesn't return the grid as generic association
        (is (= nil
               coll-search-generic-associations1))))))

(deftest test-all-associations
  (let [;; ingest two collections - the first for the test, and the second to put more assocation
        ;; data into the cmr_associations table.
        coll (data-core/ingest-umm-spec-collection "PROV1"
                                                   (data-umm-c/collection
                                                    {:ShortName "coll1"
                                                     :EntryTitle "entry-title1"}))
        coll-concept-id (:concept-id coll)
        coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll2"
                                                      :EntryTitle "entry-title2"}))
        coll-concept-id2 (:concept-id coll2)
        ;; ingest a service
        service-concept (service-util/ingest-service-with-attrs {:Name "service1"})
        service-concept-id (:concept-id service-concept)
        ;; ingest a tool
        tool-concept (tool-util/ingest-tool-with-attrs {:Name "tool1"})
        tool-concept-id (:concept-id tool-concept)
        ;; ingest two variables.
        var1-concept (variable-util/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll)})
        var2-concept (variable-util/make-variable-concept
                      {:Name "Variable2"
                       :LongName "Measurement2"}
                      {:native-id "var2"
                       :coll-concept-id (:concept-id coll)})
        ;; create a tag.
        tag (tags/make-tag)
        tag-key (:tag-key tag)
        ;; create a token
        token (echo-util/login (system/context) "user1")
        _ (tags/create-tag token tag)
        ;; create a data quality summary record
        dqs (gen-util/ingest-generic-document
             nil "PROV1" "dataqualitysummary-1" :dataqualitysummary gen-util/data-quality-summary :post)
        ;; create a order option record
        oo (gen-util/ingest-generic-document
            nil "PROV1" "orderoption-1" :orderoption gen-util/order-option :post)
        oo2 (gen-util/ingest-generic-document
            nil "PROV1" "orderoption-2" :orderoption gen-util/order-option :post)]
    (index/wait-until-indexed)

    ;; Associate the concepts.
    (association-util/associate-by-concept-ids
     token service-concept-id [{:concept-id coll-concept-id, :data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"}}
                               {:concept-id coll-concept-id2}])
    (association-util/associate-by-concept-ids
     token tool-concept-id [{:concept-id coll-concept-id}
                            {:concept-id coll-concept-id2}])
    (variable-util/ingest-variable-with-association var1-concept)
    (variable-util/ingest-variable-with-association var2-concept)
    (tags/associate-by-query token tag-key {:provider "PROV1"})
    (association-util/generic-associate-by-concept-ids-revision-ids
     token (:concept-id dqs) nil [{:concept-id coll-concept-id}])
    (association-util/generic-associate-by-concept-ids-revision-ids
     token (:concept-id oo) (:revision-id oo) [{:concept-id (:concept-id dqs)}])
    (association-util/generic-associate-by-concept-ids-revision-ids
     token coll-concept-id nil [{:concept-id (:concept-id oo2)}])
    (index/wait-until-indexed)

    (let [coll-search-resp (search-request "collections.json" "entry-title=entry-title1")
          body (json/parse-string (get coll-search-resp :body) true)
          entry (first (:entry (:feed body)))
          assoc (:associations entry)
          assoc-details (:association-details entry)]
      (is (= (:status coll-search-resp) 200))
      (is (= (count (:variables assoc)) 2))
      (is (= (count (:services assoc)) 1))
      (is (= (count (:orderoptions assoc)) 1))
      (is (= (count (:variables assoc-details)) 2))
      (is (= (count (:services assoc-details)) 1))
      (is (some? (:data (first (:services assoc-details))))))))

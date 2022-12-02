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

(defn get-associations-and-details
  "Get the search result and extract associations and association-details."
  [concept-type-ext params concept-type-plural meta?]
  (let [search-result (search-request concept-type-ext params)
        search-body (json/parse-string (:body search-result) true)
        associations (if meta?
                       (get-in (first (:items search-body)) [:meta :associations concept-type-plural])
                       (get-in (first (:items search-body)) [:associations concept-type-plural]))
        association-details (if meta?
                              (get-in (first (:items search-body)) [:meta :association-details concept-type-plural])
                              (get-in (first (:items search-body)) [:association-details concept-type-plural]))]
    {:associations associations
     :association-details association-details}))

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
                       token tl1-concept-id [{:concept-id coll1-concept-id :revision-id coll1-revision-id}
                                             {:concept-id coll2-concept-id :data "some data"}])
            _ (index/wait-until-indexed)
            ;;Search for the tool tl1, it should return the association
            tl1-search-result (get-associations-and-details "tools.umm_json" "native_id=tl1" :collections true)

            ;;Search for the collection coll1, it should return the association
            coll1-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :tools true)

            ;;Search for the collection coll2, it should return the association
            coll2-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title2" :tools true)]
        (is (= 200 (:status response1)))

        ;; Search for the tool tl1 returns the coll1 and coll2 as association
        (is (= (set [coll2-concept-id coll1-concept-id])
               (set (:associations tl1-search-result))))
        (is (= (set [{:concept-id coll1-concept-id, :revision-id coll1-revision-id}
                     {:data "some data", :concept-id coll2-concept-id}])
               (set (:association-details tl1-search-result))))

        ;; Search for the collection coll1 returns the tl1 as association
        (is (= [tl1-concept-id]
               (:associations coll1-search-result)))
        (is (= [{:concept-id tl1-concept-id}]
               (:association-details coll1-search-result)))

        ;; Search for the collection coll2 returns the tl1 as association
        (is (= [tl1-concept-id]
               (:associations coll2-search-result)))))
             
    (testing "Associate tool with tool by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl2-concept-id tl2-revision-id [{:concept-id tl1-concept-id :revision-id tl1-revision-id}])
            ;; Switch the position of tl1 and tl2 should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id tl2-concept-id :revision-id tl2-revision-id}])
            ;;Try to associate tl1 with tl2 by concept-id only. This shouldn't be allowed.
            ;;Try to associate tl2 with tl1 by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl2-concept-id nil [{:concept-id tl1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the tool tl1, it should return the association
            tl1-search-result (get-associations-and-details "tools.umm_json" "native_id=tl1" :tools true)

            ;;Search for the tool tl2, it should return the association
            tl2-search-result (get-associations-and-details "tools.umm_json" "native_id=tl2" :tools true)

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id tl2-concept-id :revision-id tl2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the tool tl1 again, it should NOT return the association
            tl1-search-result1 (get-associations-and-details "tools.umm_json" "native_id=tl1" :tools true)

            ;;Search for the tool tl2 again, it should NOT return the association
            tl2-search-result1 (get-associations-and-details "tools.umm_json" "native_id=tl2" :tools true)]
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
               (:associations tl1-search-result)))

        ;; Search for the tool tl2 returns the tl1 as generic association
        (is (= [tl1-concept-id]
               (:associations tl2-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the tool tl1 again doesn't return the tl2 as generic association
        (is (= nil (:associations tl1-search-result1)))

        ;; Search for the tool tl2 again doesn't return the tl1 as generic association
        (is (= nil (:associations tl2-search-result1)))))
    (testing "Associate grid with tool by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id grid-revision-id [{:concept-id tl1-concept-id  :revision-id tl1-revision-id}])
            ;;Switch the position of grid and tool should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;; Try to associate grid with tool by concept-id only. This shouldn't be allowed.
            ;; Try to associate tool with grid by conept-id only,  This shouldn't be allowed.
            ;; The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id tl1-concept-id}])
            _ (index/wait-until-indexed)

            ;; Search for the grid, it should return the association
            grid-search-result (get-associations-and-details "grids.json" "name=Grid-A7-v1" :tools false)

            ;; Search for the tool, it should return the association
            tl1-search-result (get-associations-and-details "tools.umm_json" "native_id=tl1" :grids true)

            ;;Update the tool and the grid, search for the grid and the tool, it should return the association.
            _ (gen-util/ingest-generic-document nil "PROV1" native-id :grid grid-doc :post)
            _ (tool-util/ingest-tool-with-attrs {:native-id "tl1" :Name "tool1"})
            grid-search-result-update (get-associations-and-details "grids.json" "name=Grid-A7-v1" :tools false)

            tl1-search-result-update (get-associations-and-details "tools.umm_json" "native_id=tl1" :grids true)

            ;; Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token tl1-concept-id tl1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;; Search for the grid again, it should NOT return the association
            grid-search-result1 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :tools false)

            ;; Search for the tool again, it should NOT return the association
            tl1-search-result1 (get-associations-and-details "tools.umm_json" "native_id=tl1" :grids true)]
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
               (:associations grid-search-result)
               (:associations grid-search-result-update)))
        (is (= [{:concept-id tl1-concept-id :revision-id tl1-revision-id}]
               (:association-details grid-search-result)
               (:association-details grid-search-result-update)))

        ;; Search for the tool returns the grid as generic association
        (is (= [grid-concept-id]
               (:associations tl1-search-result)
               (:associations tl1-search-result-update)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the tl1 as generic association
        (is (= nil (:associations grid-search-result1)))

        ;; Search for the tool again doesn't return the grid as generic association
        (is (= nil  (:associations tl1-search-result1)))))))

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

        ;; Then ingest two collections and two services
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
                       token sv1-concept-id [{:concept-id coll1-concept-id :revision-id coll1-revision-id}
                                             {:concept-id coll2-concept-id :data "some data"}])
            _ (index/wait-until-indexed)
            ;; Search for the service sv1, it should return the association
            sv1-search-result (get-associations-and-details "services.umm_json" "native_id=sv1" :collections true)

            ;; Search for the collection coll1, it should return the association
            coll1-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :services true)

            ;; Search for the collection coll2, it should return the association
            coll2-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title2" :services true)]
        (is (= 200 (:status response1)))

        ;; Search for the service sv1 returns the coll1 as association
        (is (= (set [coll2-concept-id coll1-concept-id])
               (set (:associations sv1-search-result))))
        (is (= (set [{:concept-id coll1-concept-id, :revision-id coll1-revision-id}
                     {:data "some data", :concept-id coll2-concept-id}])
               (set (:association-details sv1-search-result))))

        ;; Search for the collection coll1 returns the sv1 as association
        (is (= [sv1-concept-id]
               (:associations coll1-search-result)))

        ;; Search for the collection coll2 returns the sv1 as association
        (is (= [sv1-concept-id]
               (:associations coll2-search-result)))))
             
    (testing "Associate service with service by concept-id and revision-ids"
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv2-concept-id sv2-revision-id [{:concept-id sv1-concept-id  :revision-id sv1-revision-id}])
            ;; Switch the position of sv1 and sv2 should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id sv2-concept-id :revision-id sv2-revision-id}])
            ;; Try to associate sv1 with sv2 by concept-id only. This shouldn't be allowed.
            ;; Try to associate sv2 with sv1 by conept-id only,  This shouldn't be allowed.
            ;; The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token sv2-concept-id nil [{:concept-id sv1-concept-id}])
            _ (index/wait-until-indexed)

            ;; Search for the service sv1, it should return the association
            sv1-search-result (get-associations-and-details "services.umm_json" "native_id=sv1" :services true)

            ;; Search for the service sv2, it should return the association
            sv2-search-result (get-associations-and-details "services.umm_json" "native_id=sv2" :services true)

            ;; Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id sv2-concept-id :revision-id sv2-revision-id}])
            _ (index/wait-until-indexed)

            ;; Search for the service sv1 again, it should NOT return the association
            sv1-search-result1 (get-associations-and-details "services.umm_json" "native_id=sv1" :services true)

            ;; Search for the service sv2 again, it should NOT return the association
            sv2-search-result1 (get-associations-and-details "services.umm_json" "native_id=sv2" :services true)]
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
               (:associations sv1-search-result)))

        ;; Search for the service sv2 returns the sv1 as generic association
        (is (= [sv1-concept-id]
               (:associations sv2-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the service sv1 again doesn't return the sv2 as generic association
        (is (= nil (:associations sv1-search-result1)))

        ;; Search for the service sv2 again doesn't return the sv1 as generic association
        (is (= nil (:associations sv2-search-result1)))))
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
            grid-search-result (get-associations-and-details "grids.json" "name=Grid-A7-v1" :services false)

            ;;Search for the service, it should return the association
            sv1-search-result (get-associations-and-details "services.umm_json" "native_id=sv1" :grids true)

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token sv1-concept-id sv1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :services false)

            ;;Search for the service again, it should NOT return the association
            sv1-search-result1 (get-associations-and-details "services.umm_json" "native_id=sv1" :grids true)]
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
               (:associations grid-search-result)))

        ;; Search for the service returns the grid as generic association
        (is (= [grid-concept-id]
               (:associations sv1-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the sv1 as generic association
        (is (= nil (:associations grid-search-result1)))

        ;; Search for the service again doesn't return the grid as generic association
        (is (= nil  (:associations sv1-search-result1)))))))

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
            var1-search-result (get-associations-and-details "variables.umm_json" "native_id=var1" :variables false)
            var1-search-result-in-meta (get-associations-and-details "variables.umm_json" "native_id=var1" :variables true)

            ;;Search for the variable var2, it should return the association
            var2-search-result (get-associations-and-details "variables.umm_json" "native_id=var2" :variables false)
            var2-search-result-in-meta (get-associations-and-details "variables.umm_json" "native_id=var2" :variables true)

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id var2-concept-id :revision-id var2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the variable var1 again, it should NOT return the association
            var1-search-result1 (get-associations-and-details "variables.umm_json" "native_id=var1" :variables false)

            ;;Search for the variable var2 again, it should NOT return the association
            var2-search-result1 (get-associations-and-details "variables.umm_json" "native_id=var2" :variables false)]
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
               (:associations var1-search-result)))
        (is (= [{:concept-id var2-concept-id :revision-id var2-revision-id}]
               (:association-details var1-search-result-in-meta)))
        (is (= [var2-concept-id]
               (:associations var1-search-result-in-meta)))

        ;; Search for the variable var2 returns the var1 as generic association
        (is (= [{:concept-id var1-concept-id :revision-id var1-revision-id}]
               (:associations var2-search-result)))
        (is (= [{:concept-id var1-concept-id :revision-id var1-revision-id}]
               (:association-details var2-search-result-in-meta)))
        (is (= [var1-concept-id]
               (:associations var2-search-result-in-meta)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the variable var1 again doesn't return the var2 as generic association
        (is (= nil (:associations var1-search-result1)))

        ;; Search for the variable var2 again doesn't return the var1 as generic association
        (is (= nil
               (:associations var2-search-result1)))))
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
            grid-search-result (get-associations-and-details "grids.json" "name=Grid-A7-v1" :variables false)

            ;;Search for the variable, it should return the association
            var1-search-result (get-associations-and-details "variables.umm_json" "native_id=var1" :grids false)

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :variables false)

            ;;Search for the variable again, it should NOT return the association
            var1-search-result1 (get-associations-and-details "variables.umm_json" "native_id=var1" :grids false)]
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
               (:associations grid-search-result)))

        ;; Search for the variable returns the grid as generic association
        (is (= [{:concept-id grid-concept-id :revision-id grid-revision-id}]
               (:associations var1-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the grid again doesn't return the var1 as generic association
        (is (= nil (:associations grid-search-result1)))

        ;; Search for the variable again doesn't return the grid as generic association
        (is (= nil  (:associations var1-search-result1)))))
    
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

            ;; Search for the collection coll1, it should return the association
            coll1-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :collections true)

            ;; Search for the collection coll2, it should return the association
            coll2-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title2" :collections true)

            ;; Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token coll1-concept-id coll1-revision-id [{:concept-id coll2-concept-id :revision-id coll2-revision-id}])
            _ (index/wait-until-indexed)

            ;; Search for the collection coll1 again, it should NOT return the association
            coll1-search-result1 (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :collections true)

            ;; Search for the collection coll2 again, it should NOT return the association
            coll2-search-result1 (get-associations-and-details "collections.umm-json" "entry_title=entry-title2" :collections true)]
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
               (:associations coll1-search-result)))

        ;; Search for the collection coll2 returns the coll1 as generic association
        (is (= [coll1-concept-id]
               (:associations coll2-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)))

        ;; Search for the collection coll1 again doesn't return the coll2 as generic association
        (is (= nil
               (:associations coll1-search-result1)))

        ;; Search for the collection coll2 again doesn't return the coll1 as generic association
        (is (= nil
               (:associations coll2-search-result1)))))))

;; Test that generic associations can be made between generic documents and collections. 
(deftest test-collection-and-generic-association
  (let [;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;; Then ingest a collection
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
            ;; Switch the position of grid and collection should return the same concept-id and revision-id is increased by 1.
            response2 (association-util/generic-associate-by-concept-ids-revision-ids
                       token coll-concept-id coll-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            ;; Try to associate grid with collection by concept-id only. This shouldn't be allowed.
            ;; Try to associate collection with grid by conept-id only,  This shouldn't be allowed.
            ;; The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid-concept-id nil [{:concept-id coll-concept-id}])
            _ (index/wait-until-indexed)

            ;; Search for the grid, it should return the association
            grid-search-result (get-associations-and-details "grids.json" "name=Grid-A7-v1" :collections false)

            ;; Search for the collection, it should return the association
            coll-search-result (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :grids true)

            ;; Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token coll-concept-id coll-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;; Search for the grid again, it should NOT return the association
            grid-search-result1 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :collections false)

            ;; Search for the collection again, it should NOT return the association
            coll-search-result1 (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :grids true)]

        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The first and second associations contain different associated-item because associated-item
        ;; should always be related to one of the items in the list that's associated to a particular concept.
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
        (is (= [coll-concept-id]
               (:associations grid-search-result)))

        ;; Search for the collection returns the grid as generic association
        (is (= [grid-concept-id]
               (:associations coll-search-result)))

        ;; Dissociation of the association is successful
        (is (= 200 (:status response4)) "Dissociation of the association failed.")

        ;; Search for the grid again doesn't return the collection as generic association
        (is (= nil
               (:associations grid-search-result1)))

        ;; Search for the collection again doesn't return the grid as generic association
        (is (= nil
               (:associations coll-search-result1)))))))

;; Test that generic associations are removed from the search result when one of the
;; associated concept is deleted - specifically when collection or generic doc is deleted.
(deftest test-cascading-association-deletion-for-generic-doc
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

        ;; Ingest a service
        sv (service-util/ingest-service-with-attrs {:native-id "sv" :Name "service"})
        sv-concept-id (:concept-id sv)

        ;;Then ingest a tool
        tl (tool-util/ingest-tool-with-attrs {:native-id "tl" :Name "tool"})
        tl-concept-id (:concept-id tl)
        token (echo-util/login (system/context) "user1")
        _ (index/wait-until-indexed)
        ;; associate grid with collection,service and tool.
        _ (association-util/generic-associate-by-concept-ids-revision-ids
           token grid-concept-id grid-revision-id [{:concept-id coll-concept-id :revision-id coll-revision-id}
                                                   {:concept-id sv-concept-id}
                                                   {:concept-id tl-concept-id}])
        ;; search for the collection, service and tool, the association should be there.
        coll-search-result1 (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :grids true)
        sv-search-result1 (get-associations-and-details "services.umm_json" "native_id=sv" :grids true)
        tl-search-result1 (get-associations-and-details "tools.umm_json" "native_id=tl" :grids true)

        ;; delete the grid
        _ (gen-util/ingest-generic-document nil "PROV1" native-id :grid grid-doc :delete)
        _ (index/wait-until-indexed)

        ;; search for the collection,service and tool the association shouldn't be there.
        coll-search-result2 (get-associations-and-details "collections.umm-json" "entry_title=entry-title1" :grids true)
        sv-search-result2 (get-associations-and-details "services.umm-json" "native_id=sv" :grids true)
        tl-search-result2 (get-associations-and-details "tools.umm-json" "native_id=tl" :grids true)]
    (is (= [grid-concept-id]
           (:associations coll-search-result1)
           (:associations sv-search-result1)
           (:associations tl-search-result1)))
    (is (= nil
           (:associations coll-search-result2)
           (:associations sv-search-result2)
           (:associations tl-search-result2)))))

(deftest test-cascading-association-deletion-for-collection
  (let [;; Ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        grid-doc gen-util/grid-good
        grid (gen-util/ingest-generic-document
              nil "PROV1" native-id :grid grid-doc :post)
        grid-concept-id (:concept-id grid)
        grid-revision-id (:revision-id grid)

        ;;Ingest a collection
        coll (data-core/ingest-umm-spec-collection "PROV1"
              (data-umm-c/collection
               {:ShortName "coll1"
                :EntryTitle "entry-title1"})
              {:token "mock-echo-system-token"})
        coll-concept-id (:concept-id coll)
        coll-revision-id (:revision-id coll)

        ;; Ingest a service
        sv (service-util/ingest-service-with-attrs {:native-id "sv" :Name "service"})
        sv-concept-id (:concept-id sv)

        ;;Then ingest a tool
        tl (tool-util/ingest-tool-with-attrs {:native-id "tl" :Name "tool"})
        tl-concept-id (:concept-id tl) 
        token (echo-util/login (system/context) "user1")
        _ (index/wait-until-indexed)
        
        ;; associate grid and collection
        _ (association-util/generic-associate-by-concept-ids-revision-ids
           token grid-concept-id grid-revision-id [{:concept-id coll-concept-id :revision-id coll-revision-id}])

        ;; associate service and collection using the old association api (not allowed in the new api)
        _ (association-util/associate-by-concept-ids
           token sv-concept-id [{:concept-id coll-concept-id :revision-id coll-revision-id}])

        ;; associate tool and collection using the old association api (not allowed in the new api)
        _ (association-util/associate-by-concept-ids
           token tl-concept-id [{:concept-id coll-concept-id :revision-id coll-revision-id}])
        
        ;; search for the grid,service and tool, the association should be there.
        grid-search-result1 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :collections false)
        sv-search-result1 (get-associations-and-details "services.umm_json" "native_id=sv" :collections true)
        tl-search-result1 (get-associations-and-details "tools.umm_json" "native_id=tl" :collections true)

        ;; delete the collection
        _ (ingest/delete-concept (data-core/umm-c-collection->concept coll :echo10) {:accept-format :json :raw? true}) 
        _ (index/wait-until-indexed)

        ;; search for the grid,service and tool, the association shouldn't be there.
        grid-search-result2 (get-associations-and-details "grids.json" "name=Grid-A7-v1" :collections false)
        sv-search-result2 (get-associations-and-details "services.umm_json" "native_id=sv" :collections false)
        tl-search-result2 (get-associations-and-details "tools.umm_json" "native_id=tl" :collections false)]
    (is (= [coll-concept-id]
           (:associations grid-search-result1)
           (:associations sv-search-result1)
           (:associations tl-search-result1)))
    (is (= nil
           (:associations grid-search-result2)
           (:associations sv-search-result2)
           (:associations tl-search-result2)))))

(deftest test-all-associations
  (let [;; ingest two collections - the first for the test, and the second to put more association
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
             nil "PROV1" "data-quality-summary-1" :data-quality-summary gen-util/data-quality-summary :post)
        ;; create a order option record
        oo (gen-util/ingest-generic-document
            nil "PROV1" "order-option-1" :order-option gen-util/order-option :post)
        oo2 (gen-util/ingest-generic-document
            nil "PROV1" "order-option-2" :order-option gen-util/order-option :post)]
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
      (is (= (count (:order-options assoc)) 1))
      (is (= (count (:variables assoc-details)) 2))
      (is (= (count (:services assoc-details)) 1))
      (is (some? (:data (first (:services assoc-details))))))))

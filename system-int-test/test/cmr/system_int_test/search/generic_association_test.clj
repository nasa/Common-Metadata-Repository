(ns cmr.system-int-test.search.generic-association-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as association-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.generic-util :as gen-util])
  (:import
   [java.util UUID]))

(defn grant-all-generic-permission-fixture
  "A test fixture that grants all users the ability to create and modify collection, generic documents and generic associations."
  [f]
  (echo-util/grant-system-ingest-management (system/context) [:read :update] [:read :update])
  (f))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    grant-all-generic-permission-fixture]))

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

;; Test that generic associations can be made between generic documents and collection 
(deftest test-generic-association

  (let [;;First ingest a Grid concept
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        generic-requester (partial gen-util/generic-request nil "PROV1" native-id "grid")
        good-generic-requester (partial generic-requester gen-util/grid-good)
        post-results (good-generic-requester :post)
        body (json/parse-string (:body post-results) true)
        grid-concept-id (:concept-id body)
        grid-revision-id (:revision-id body)
        
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
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:generic-associations])

            ;;Search for the collection, it should return the association
            coll-search-result (search-request "collections.umm-json" "entry_title=entry-title1")
            coll-search-body (json/parse-string (:body coll-search-result) true)
            coll-search-generic-associations (get-in (first (:items coll-search-body)) [:meta :associations :generics])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token coll-concept-id coll-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:generic-associations])

            ;;Search for the collection again, it should NOT return the association
            coll-search-result1 (search-request "collections.umm-json" "entry_title=entry-title1")
            coll-search-body1 (json/parse-string (:body coll-search-result1) true)
            coll-search-generic-associations1 (get-in (first (:items coll-search-body1)) [:meta :associations :generics])]
        
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))
        
        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))
        
        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (= ["There are already generic associations between concept id [GRD1200000006-PROV1] and concept id [C1200000007-PROV1] revision ids [1], cannot create generic association on the same concept without revision id.; There are already generic associations between concept id [C1200000007-PROV1] and concept id [GRD1200000006-PROV1] revision ids [1], cannot create generic association on the same concept without revision id."]
               (get-in (first (:body response3)) [:errors])))

        ;; Search for the grid returns the collection as generic association
        (is (= [coll-concept-id]
               grid-search-generic-associations))

        ;; Search for the collection returns the grid as generic association
        (is (= [grid-concept-id]
               coll-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= {:status 200, :body [{:generic-association {:concept-id "GA1200000008-CMR", :revision-id 3}, :associated-item {:concept-id "GRD1200000006-PROV1", :revision-id 1}}]}
               response4))

        ;; Search for the grid again doesn't return the collection as generic association
        (is (= nil 
               grid-search-generic-associations1))

        ;; Search for the collection again doesn't return the grid as generic association
        (is (= nil 
               coll-search-generic-associations1)) 

        ))))

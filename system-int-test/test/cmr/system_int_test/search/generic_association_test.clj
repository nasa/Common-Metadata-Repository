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
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
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
            ;;Try to associate grid with variable by concept-id only. This shouldn't be allowed.
            ;;Try to associate variable with grid by conept-id only,  This shouldn't be allowed.
            ;;The following association is trying to do both of the above.
            response3 (association-util/generic-associate-by-concept-ids-revision-ids
                       token var2-concept-id nil [{:concept-id var1-concept-id}])
            _ (index/wait-until-indexed)

            ;;Search for the variable var1, it should return the association
            var1-search-result (search-request "variables.umm_json" "native_id=var1")
            var1-search-body (json/parse-string (:body var1-search-result) true)
            var1-search-generic-associations (get-in (first (:items var1-search-body)) [:associations :generics])

            ;;Search for the variable var2, it should return the association
            var2-search-result (search-request "variables.umm_json" "native_id=var2")
            var2-search-body (json/parse-string (:body var2-search-result) true)
            var2-search-generic-associations (get-in (first (:items var2-search-body)) [:associations :generics])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id var2-concept-id :revision-id var2-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the variable var1 again, it should NOT return the association
            var1-search-result1 (search-request "variables.umm_json" "native_id=var1")
            var1-search-body1 (json/parse-string (:body var1-search-result1) true)
            var1-search-generic-associations1 (get-in (first (:items var1-search-body1)) [:associations :generics])

            ;;Search for the variable var2 again, it should NOT return the association
            var2-search-result1 (search-request "variables.umm_json" "native_id=var2")
            var2-search-body1 (json/parse-string (:body var2-search-result1) true)
            var2-search-generic-associations1 (get-in (first (:items var2-search-body1)) [:associations :generics])] 
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (= ["There are already generic associations between concept id [V1200000011-PROV1] and concept id [V1200000009-PROV1] revision ids [1], cannot create generic association on the same concept without revision id.; There are already generic associations between concept id [V1200000009-PROV1] and concept id [V1200000011-PROV1] revision ids [1], cannot create generic association on the same concept without revision id."]
               (get-in (first (:body response3)) [:errors])))

        ;; Search for the variable var1 returns the var2 as generic association
        (is (= [{:concept-id var2-concept-id :revision-id var2-revision-id}]
               var1-search-generic-associations))

        ;; Search for the variable var2 returns the var1 as generic association
        (is (= [{:concept-id var1-concept-id :revision-id var1-revision-id}]
               var2-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= {:status 200, :body [{:generic-association {:concept-id "GA1200000013-CMR", :revision-id 3}, :associated-item {:concept-id "V1200000011-PROV1", :revision-id 1}}]}
               response4))

        ;; Search for the variable var1 again doesn't return the var2 as generic association
        (is (= nil
               var1-search-generic-associations1))

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
            grid-search-generic-associations (get-in (first (:items grid-search-body)) [:generic-associations])

            ;;Search for the variable, it should return the association
            var1-search-result (search-request "variables.umm_json" "native_id=var1")
            var1-search-body (json/parse-string (:body var1-search-result) true)
            var1-search-generic-associations (get-in (first (:items var1-search-body)) [:associations :generics])

            ;;Dissociate the association
            response4 (association-util/generic-dissociate-by-concept-ids-revision-ids
                       token var1-concept-id var1-revision-id [{:concept-id grid-concept-id :revision-id grid-revision-id}])
            _ (index/wait-until-indexed)

            ;;Search for the grid again, it should NOT return the association
            grid-search-result1 (search-request "grids.json" "name=Grid-A7-v1")
            grid-search-body1 (json/parse-string (:body grid-search-result1) true)
            grid-search-generic-associations1 (get-in (first (:items grid-search-body1)) [:generic-associations])

            ;;Search for the variable again, it should NOT return the association
            var1-search-result1 (search-request "variables.umm_json" "native_id=var1")
            var1-search-body1 (json/parse-string (:body var1-search-result1) true)
            var1-search-generic-associations1 (get-in (first (:items var1-search-body1)) [:associations :generics])]
        ;; The first and second associations are successful
        (is (= 200 (:status response1) (:status response2)))

        ;; The first and second associations are creating and updating the same association
        (is (= (get-in (first (:body response1)) [:generic-association :concept-id])
               (get-in (first (:body response2)) [:generic-association :concept-id])))
        (is (= (get-in (first (:body response2)) [:generic-association :revision-id])
               (+ 1 (get-in (first (:body response1)) [:generic-association :revision-id]))))

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (= ["There are already generic associations between concept id [GRD1200000006-PROV1] and concept id [V1200000009-PROV1] revision ids [1], cannot create generic association on the same concept without revision id.; There are already generic associations between concept id [V1200000009-PROV1] and concept id [GRD1200000006-PROV1] revision ids [1], cannot create generic association on the same concept without revision id."]
               (get-in (first (:body response3)) [:errors])))

        ;; Search for the grid returns the var1 as generic association
        (is (= [var1-concept-id]
               grid-search-generic-associations))

        ;; Search for the variable returns the grid as generic association
        (is (= [{:concept-id grid-concept-id :revision-id grid-revision-id}]
               var1-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= {:status 200, :body [{:generic-association {:concept-id "GA1200000014-CMR", :revision-id 3}, :associated-item {:concept-id "V1200000009-PROV1", :revision-id "1"}}]}
               response4))

        ;; Search for the grid again doesn't return the var1 as generic association
        (is (= nil
               grid-search-generic-associations1))

        ;; Search for the variable again doesn't return the grid as generic association
        (is (= nil
               var1-search-generic-associations1))))))

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
        (is (= ["The following concept ids [(\"V1234-PROV1\" \"S1234-PROV1\" \"TL1234-PROV1\")] can not be associated with concept id [C1200000007-PROV1] because collection/[service|tool|variable] associations are not supported by the new generic association api."]
               (:errors response)))))
 
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
               coll-search-generic-associations1))))))

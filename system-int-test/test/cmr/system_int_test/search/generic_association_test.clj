(ns cmr.system-int-test.search.generic-association-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.edn :as edn]
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
   [cmr.system-int-test.utils.variable-util :as vu]
   [cmr.transmit.tag :as transmit-tag])
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

;; Test that generic associations can be made between generic documents and collection
(deftest test-generic-association

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
        (is (= ["The following concept ids [(\"V1234-PROV1\" \"S1234-PROV1\" \"TL1234-PROV1\")] can not be associated with concept id [C1200000008-PROV1] because collection/[service|tool|variable] associations are not supported by the new generic association api."]
               (:errors response))
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

        ;; The third association is not allowed.
        (is (= 400 (:status response3)))
        (is (= ["There are already generic associations between concept id [GRD1200000007-PROV1] and concept id [C1200000008-PROV1] revision ids [1], cannot create generic association on the same concept without revision id.; There are already generic associations between concept id [C1200000008-PROV1] and concept id [GRD1200000007-PROV1] revision ids [1], cannot create generic association on the same concept without revision id."]
               (get-in (first (:body response3)) [:errors])))

        ;; Search for the grid returns the collection as generic association
        (is (= {:collections [coll-concept-id]}
               grid-search-generic-associations))

        ;; Search for the collection returns the grid as generic association
        (is (= [grid-concept-id]
               coll-search-generic-associations))

        ;; Dissociation of the association is successful
        (is (= {:status 200, :body [{:generic-association {:concept-id "GA1200000009-CMR", :revision-id 3}, :associated-item {:concept-id "GRD1200000007-PROV1", :revision-id 1}}]}
               response4) "Dissociation of the association failed.")

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
        var1-concept (vu/make-variable-concept
                      {:Name "Variable1"
                       :LongName "Measurement1"}
                      {:native-id "var1"
                       :coll-concept-id (:concept-id coll)})
        var2-concept (vu/make-variable-concept
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
    (vu/ingest-variable-with-association var1-concept)
    (vu/ingest-variable-with-association var2-concept)
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
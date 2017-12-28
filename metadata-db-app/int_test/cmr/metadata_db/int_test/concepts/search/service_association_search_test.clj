(ns cmr.metadata-db.int-test.concepts.search.service-association-search-test
  "Contains integration tests for searching service associations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-service-associations
  (let [coll1 (concepts/create-and-save-concept
               :collection "REG_PROV" 1 1 {:extra-fields {:entry-id "entry-1"
                                                          :entry-title "et1"
                                                          :version-id "v1"
                                                          :short-name "s1"}})
        coll2 (concepts/create-and-save-concept
               :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                          :entry-title "et2"
                                                          :version-id "v1"
                                                          :short-name "s2"}})
        associated-service (concepts/create-and-save-concept :service "REG_PROV" 1)
        serv-association1 (concepts/create-and-save-concept :service-association coll1 associated-service 1 3)
        serv-association2 (concepts/create-and-save-concept :service-association coll2 associated-service 2 2)]

    (testing "find latest revisions"
      (are3 [service-associations params]
        (is (= (set service-associations)
               (set (->> (util/find-latest-concepts :service-association params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id))))))

        "by associated-concept-id"
        [serv-association1]
        {:associated-concept-id (:concept-id coll1)}

        "by associated-concept-id, multiple"
        [serv-association1 serv-association2]
        {:associated-concept-id [(:concept-id coll1) (:concept-id coll2)]}

        "by service-concept-id"
        [serv-association1 serv-association2]
        {:service-concept-id (:concept-id associated-service)}

        "by native-id"
        [serv-association1]
        {:native-id (:native-id serv-association1)}

        "by service-concept-id not found"
        []
        {:service-concept-id "S100-REG_PROV"}

        "search for all, with metadata"
        [serv-association1 serv-association2]
        {}

        "exclude metadata"
        [(dissoc serv-association1 :metadata) (dissoc serv-association2 :metadata)]
        {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-service-associations (-> (util/find-concepts :service-association {})
                                            :concepts
                                            count)]
        (is (= 5 num-of-service-associations))))))

(deftest find-service-associations-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [service-association] with parameters [provider-id] is not supported."]}
           (util/find-concepts :service-association {:provider-id "REG_PROV"})))))

(ns cmr.metadata-db.int-test.concepts.search.service-search-test
  "Contains integration tests for searching services."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-services
  (let [;; create two services with the same native-id on different providers with multiple revisions
        serv1 (concepts/create-and-save-concept :service "REG_PROV" 1 3)
        serv2 (concepts/create-and-save-concept :service "SMAL_PROV1" 1 2)]
    (testing "find latest revisions"
      (are3 [servs params]
        (let [found-servs (->> (util/find-latest-concepts :service params)
                               :concepts
                               util/concepts-for-comparison)]
          ;; this verifies the number of services found is correct,
          ;; we had bug where the same service shows up multiple times in the results
          (is (= (count servs)
                 (count found-servs)))
          ;; this verifies the content of the services found is correct
          (is (= (set servs)
                 (set found-servs))))

        "with metadata search by provider-id"
        [serv1] {:provider-id "REG_PROV"}

        "with metadata search by concept-id"
        [serv1] {:concept-id (:concept-id serv1)}

        "with metadata search by native-id"
        [serv1 serv2] {:native-id (:native-id serv2)}

        "with metadata search by provider-id and native-id"
        [serv2] {:provider-id "SMAL_PROV1"
                 :native-id (:native-id serv2)}

        "exclude metadata search by concept-id"
        [(dissoc serv1 :metadata)] {:concept-id (:concept-id serv1)
                                    :exclude-metadata true}

        "exclude metadata search by provider-id and native-id"
        [(dissoc serv2 :metadata)] {:provider-id "SMAL_PROV1"
                                    :native-id (:native-id serv2)
                                    :exclude-metadata true}

        "no match by native id"
        [] {:native-id "foo"}

        "no match by concept id"
        [] {:concept-id "S99-REG_PROV"}))

    (testing "find all revisions"
      (let [num-of-servs (-> (util/find-concepts :service {})
                             :concepts
                             count)]
        (is (= 5 num-of-servs))))))

(deftest find-services-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [service] with parameters [short-name] is not supported."]}
           (util/find-concepts :service {:short-name "SN1"})))))

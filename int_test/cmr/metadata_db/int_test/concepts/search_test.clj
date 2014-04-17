(ns cmr.metadata-db.int-test.concepts.search-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.data]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]))

(use-fixtures :each (util/reset-database-fixture "PROV1" "PROV2"))

(deftest search-by-concept-revision-id-tuples
  (let [coll1 (util/create-and-save-collection "PROV1" 1 3)
        coll2 (util/create-and-save-collection "PROV1" 2 3)
        coll3 (util/create-and-save-collection "PROV2" 3 3)
        gran1 (util/create-and-save-granule "PROV1" (:concept-id coll1) 1 2)
        gran2 (util/create-and-save-granule "PROV1" (:concept-id coll2) 2 2)]
    (are [item-revision-tuples]
         (let [tuples (map #(update-in % [0] :concept-id) item-revision-tuples)
               {:keys [status concepts]} (util/get-concepts tuples)
               expected-concepts (map (fn [[item revision]]
                                        (assoc item :revision-id revision))
                                      item-revision-tuples)]
           (and (= 200 status)
                (= expected-concepts concepts)))
         ; one collection
         [[coll1 0]]
         ;; two collections
         [[coll1 1] [coll2 0]]
         ;; multiple versions of same collection
         [[coll1 1] [coll1 0]]
         ; granules and collections
         [[gran1 1] [gran1 0] [gran2 1] [coll3 2] [coll1 1]])))

(deftest get-concepts-with-one-invalid-revision-id-test
  (let [coll1 (util/create-and-save-collection "PROV1" 1)
        tuples [[(:concept-id coll1) 0]
                [(:concept-id coll1) 1]
                ["C2-PROV1" 0] ]
        {:keys [status errors]} (util/get-concepts tuples)]
    (is (= 404 status ))
    (is (= #{(msg/concept-with-concept-id-and-rev-id-does-not-exist (:concept-id coll1) 1)
             (msg/concept-with-concept-id-and-rev-id-does-not-exist "C2-PROV1" 0)}
           (set errors)))))

(deftest find-collections
  (let [coll1 (util/create-and-save-collection "PROV1" 1)
        coll2 (util/create-and-save-collection "PROV1" 2)
        coll3 (util/create-and-save-collection "PROV2" 3)
        colls [coll1 coll2 coll3]
        [short1 short2 short3] (map #(get-in % [:extra-fields :short-name]) colls)
        [vid1 vid2 vid3] (map #(get-in % [:extra-fields :version-id]) colls)
        [et1 et2 et3] (map #(get-in % [:extra-fields :entry-title]) colls)]
    (testing "find by provider-id, short-name, version id"
      (testing "find one"
        (is (= {:status 200
                :concepts [coll1]}
               (util/find-concepts :collection {:provider-id "PROV1"
                                                :short-name short1
                                                :version-id vid1}))))
      (testing "find none"
        (are [provider-id sn vid] (= {:status 200 :concepts []}
                                     (util/find-concepts
                                       :collection
                                       {:provider-id provider-id :short-name sn :version-id vid}))
             "PROV1" "none" vid1
             "PROV1" short1 "none"
             "PROV2" short1 vid1
             ;; Searching with an unknown provider id should just find nothing
             "PROVNONE" short1 vid1)))
    (testing "find by provider-id, entry-title"
      (testing "find one"
        (is (= {:status 200
                :concepts [coll2]}
               (util/find-concepts :collection {:provider-id "PROV1"
                                                :entry-title et2}))))
      (testing "find none"
        (are [provider-id et] (= {:status 200 :concepts []}
                                     (util/find-concepts
                                       :collection
                                       {:provider-id provider-id :entry-title et}))
             "PROV1" "none"
             "PROV2" et1
             ;; Searching with an unknown provider id should just find nothing
             "PROVNONE" et1)))))


(deftest find-collections-with-invalid-parameters
  (testing "missing parameters"
    (are [params] (= {:status 400
                      :errors [(msg/find-not-supported :collection params)]}
                     (util/find-concepts :collection (reduce #(assoc %1 %2 1) {} params)))
         [:provider-id :short-name]
         [:provider-id :version-id]
         [:short-name :version-id]
         []))
  (testing "extra parameters"
    (= {:status 400
        :errors [(msg/find-not-supported :collection [:provider-id :short-name :version-id :foo])]}
       (util/find-concepts :collection {:provider-id "PROV1"
                                        :short-name "f"
                                        :version-id "v"
                                        :foo "foo"}))))

(deftest find-granules-is-not-supported
  (= {:status 400
      :errors [(msg/find-not-supported :granule [])]}
     (util/find-concepts :granule {})))




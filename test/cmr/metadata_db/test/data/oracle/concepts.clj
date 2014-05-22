(ns cmr.metadata-db.test.data.oracle.concepts
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.oracle.connection :as oracle]
            [clojure.java.jdbc :as j]
            [clj-time.format :as f]
            [clj-time.coerce :as cr]
            [clj-time.core :as t])
  (:import javax.sql.rowset.serial.SerialBlob
           java.util.zip.GZIPInputStream
           java.io.ByteArrayInputStream
           oracle.sql.TIMESTAMPTZ))

(defn mock-blob
  "Create a mock blob"
  [value]
  (SerialBlob. (c/string->gzip-bytes value)))

(defn gzip-bytes->string
  "Convert compressed byte array to string"
  [input]
  (-> input ByteArrayInputStream. GZIPInputStream. slurp))

(defn fix-result
  [result]
  (let [vector-result (apply vector (map #(apply vector %) result))]
    (update-in vector-result [1 2] #(gzip-bytes->string %))))

(deftest find-params->sql-clause-test
  (testing "only allows valid param names to prevent sql-injection"
    (are [keystring] (c/find-params->sql-clause {(keyword keystring) 1})
         "a" "a1" "A" "A1" "A_1" "b123__dkA" "a-b")
    (are [keystring] (thrown? Exception (c/find-params->sql-clause {(keyword keystring) 1}))
         "a;b" "a&b" "a!b" ))
  (testing "converting single parameter"
    (is (= `(= :a 5)
           (c/find-params->sql-clause {:a 5}))))
  (testing "converting multiple parameters"
    (is (= `(and (= :b "bravo")
                 (= :a 5))
           (c/find-params->sql-clause {:a 5 :b "bravo"})))))


(deftest db-result->concept-map-test
  (j/with-db-transaction
    [db (oracle/create-db oracle/db-spec)]
    (let [revision-time (t/date-time 1986 10 14 4 3 27 456)
          oracle-timestamp (TIMESTAMPTZ. ^java.sql.Connection (c/db->oracle-conn db)
                                         ^java.sql.Timestamp (cr/to-sql-time revision-time))]
      (testing "collection results"
        (let [result {:native_id "foo"
                      :concept_id "C5-PROV1"
                      :metadata (mock-blob "<foo>")
                      :format "xml"
                      :revision_id 2
                      :revision_date oracle-timestamp
                      :deleted 0
                      :short_name "short"
                      :version_id "v1"
                      :entry_title "entry"}]
          (is (= {:concept-type :collection
                  :native-id "foo"
                  :concept-id "C5-PROV1"
                  :provider-id "PROV1"
                  :metadata "<foo>"
                  :format "xml"
                  :revision-id 2
                  :revision-date "1986-10-14T04:03:27.456Z"
                  :deleted false
                  :extra-fields {:short-name "short"
                                 :version-id "v1"
                                 :entry-title "entry"}}
                 (c/db-result->concept-map :collection db "PROV1" result)))))
      (testing "granule results"
        (let [result {:native_id "foo"
                      :concept_id "G7-PROV1"
                      :metadata (mock-blob "<foo>")
                      :format "xml"
                      :revision_date oracle-timestamp
                      :revision_id 2
                      :deleted 0
                      :parent_collection_id "C5-PROV1"}]
          (is (= {:concept-type :granule
                  :native-id "foo"
                  :concept-id "G7-PROV1"
                  :provider-id "PROV1"
                  :metadata "<foo>"
                  :format "xml"
                  :revision-id 2
                  :revision-date "1986-10-14T04:03:27.456Z"
                  :deleted false
                  :extra-fields {:parent-collection-id "C5-PROV1"}}
                 (c/db-result->concept-map :granule db "PROV1" result))))))))


(deftest concept->insert-args-test
  (testing "collection insert-args"
    (let [concept {:concept-type :collection
                   :native-id "foo"
                   :concept-id "C5-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "xml"
                   :revision-id 2
                   :deleted false
                   :extra-fields {:short-name "short"
                                  :version-id "v1"
                                  :entry-title "entry"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"
               "short_name" "version_id" "entry_title"]
              ["foo" "C5-PROV1" "<foo>" "xml" 2 false "short" "v1" "entry"]]
             (fix-result (c/concept->insert-args concept))))))
  (testing "granule insert-args"
    (let [concept {:concept-type :granule
                   :native-id "foo"
                   :concept-id "G7-PROV1"
                   :provider-id "PROV1"
                   :metadata "<foo>"
                   :format "xml"
                   :revision-id 2
                   :deleted false
                   :extra-fields {:parent-collection-id "C5-PROV1"}}]
      (is (= [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted" "parent_collection_id"]
              ["foo" "G7-PROV1" "<foo>" "xml" 2 false "C5-PROV1"]]
             (fix-result (c/concept->insert-args concept)))))))
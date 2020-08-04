(in-ns 'cmr.indexer.test.services.index-fields)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Test Data   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- es-doc-coll
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "C1234-PROV1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        version-id "1"
        project-short-names ["ESI" "EPI" "EVI"]]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title-lowercase (string/lower-case entry-title)
     :provider-id provider-id
     :provider-id-lowercase (string/lower-case provider-id)
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :version-id version-id
     :version-id-lowercase (string/lower-case version-id)
     :project-sn project-short-names
     :project-sn-lowercase (map string/lower-case project-short-names)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- save-collection
  "Save a collection Elasticsearch document."
  ([es-doc concept-id revision-id]
   (save-collection es-doc concept-id revision-id {}))
  ([es-doc concept-id revision-id options]
   (save-document-in-elastic
    "collection" es-doc concept-id revision-id options)))

(defn- delete-collection
  "Delete a collection Elasticsearch document."
  ([concept-id revision-id]
   (delete-collection concept-id revision-id {}))
  ([concept-id revision-id options]
   (delete-document-in-elastic "collection" concept-id revision-id options)))

(defn- assert-version-coll
  "Assert the retrieved document for the given id is of the given version"
  [concept-id version]
  (assert-same "collection" concept-id :_version version))

(defn- assert-delete-coll
  "Assert the document with the given concept-id is deleted"
  [concept-id]
  (assert-delete "collection" concept-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-collection-with-increment-versions-test
  (testing "Save with increment versions"
    (save-collection (es-doc-coll) "C1234-PROV1" 1)
    (assert-version-coll "C1234-PROV1" 1)
    (save-collection (es-doc-coll) "C1234-PROV1" 2)
    (assert-version-coll "C1234-PROV1" 2)
    (save-collection (es-doc-coll) "C1234-PROV1" 10)
    (assert-version-coll "C1234-PROV1" 10)))

(deftest save-collection-with-equal-versions-test
  (testing "Save with equal versions"
    (save-collection (es-doc-coll) "C1234-PROV1" 1)
    (assert-version-coll "C1234-PROV1" 1)
    (save-collection (es-doc-coll) "C1234-PROV1" 1)
    (assert-version-coll "C1234-PROV1" 1)))

(deftest save-collection-with-earlier-versions-test
  (testing "Save with earlier versions with ignore-conflict false"
    (save-collection (es-doc-coll) "C1234-PROV1" 3)
    (assert-version-coll "C1234-PROV1" 3)
    (try
      (save-collection (es-doc-coll) "C1234-PROV1" 2)
      (catch clojure.lang.ExceptionInfo e
        (let [type (:type (ex-data e))
              err-msg (first (:errors (ex-data e)))]
          (is (= :conflict type))
          (is (re-find #"version conflict, current version \[3\] is higher than the one provided \[2\]" err-msg))))))
  (testing "Save with earlier versions with ignore-conflict true"
    (save-collection (es-doc-coll) "C1234-PROV1" 3
                              {:ignore-conflict? true})
    (assert-version-coll "C1234-PROV1" 3)
    (save-collection (es-doc-coll) "C1234-PROV1" 2
                              {:ignore-conflict? true})
    (assert-version-coll "C1234-PROV1" 3)))

(deftest delete-collection-with-increment-versions-test
  (testing "Delete with increment versions"
    (save-collection (es-doc-coll) "C1234-PROV1" 1)
    (delete-collection "C1234-PROV1" 2)
    (assert-delete-coll "C1234-PROV1")
    (delete-collection "C1234-PROV1" 8)
    (assert-delete-coll "C1234-PROV1")))

(deftest delete-collection-with-equal-versions-test
  (testing "Delete with equal versions"
    (save-collection (es-doc-coll) "C1234-PROV1" 1)
    (delete-collection "C1234-PROV1" 1)
    (assert-delete-coll "C1234-PROV1")))

(deftest delete-collection-with-earlier-versions-test
  (testing "Delete with earlier versions ignore-conflict false"
    (save-collection (es-doc-coll) "C1234-PROV1" 2)
    (try
      (delete-collection "C1234-PROV1" 1)
      (catch java.lang.Exception e
        (is (re-find #"version conflict, current version \[2\] is higher than the one provided \[1\]" (.getMessage e))))))
  (testing "Delete with earlier versions ignore-conflict true"
    (save-collection (es-doc-coll) "C1234-PROV1" 2 {:ignore-conflict? true})
    (delete-collection "C1234-PROV1" 1 {:ignore-conflict? true})
    (assert-version-coll "C1234-PROV1" 2)))

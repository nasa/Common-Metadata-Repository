(in-ns 'cmr.indexer.test.services.index-fields)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Test Data   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- es-doc-svc-rev-1
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "S1234-PROV1"
        native-id "native-id-1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        service-name "DummyShort"]
    {:concept-id concept-id
     :native-id native-id
     :entry-title entry-title
     :entry-title-lowercase (string/lower-case entry-title)
     :provider-id provider-id
     :provider-id-lowercase (string/lower-case provider-id)
     :service-name service-name
     :service-name-lowercase (string/lower-case service-name)
     :keyword "kw1 kw2 kw3"}))

(defn- es-doc-svc-rev-2
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "S1234-PROV1"
        native-id "native-id-1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        service-name "DummyShort"]
    {:concept-id concept-id
     :native-id native-id
     :entry-title entry-title
     :entry-title-lowercase (string/lower-case entry-title)
     :provider-id provider-id
     :provider-id-lowercase (string/lower-case provider-id)
     :service-name service-name
     :service-name-lowercase (string/lower-case service-name)
     :keyword "keyword1 keyword2 keyword3"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- save-service
  "Save a service Elasticsearch document."
  ([es-doc concept-id revision-id]
   (save-service es-doc concept-id revision-id {}))
  ([es-doc concept-id revision-id options]
   (save-document-in-elastic "service" es-doc concept-id revision-id options)))

(defn- delete-service
  "Delete a service Elasticsearch document."
  ([concept-id revision-id]
   (delete-service concept-id revision-id {}))
  ([concept-id revision-id options]
   (delete-document-in-elastic "service" concept-id revision-id options)))

(defn- assert-match-in-svc
  "Assert that value associated with the nested fields for the retrieved
  document with the given concept-id contains the given substring."
  [concept-id fields substr]
  (assert-field-match-in "service" concept-id fields substr))

(defn- assert-not-match-in-svc
  "Assert that value associated with the nested fields for the retrieved
  document with the given concept-id does not contain the given substring."
  [concept-id fields substr]
  (assert-not-field-match-in "service" concept-id fields substr))

(defn- assert-delete-svc
  "Assert the document with the given id is deleted."
  [concept-id]
  (assert-delete "service" concept-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest save-service-test
  (save-service (es-doc-svc-rev-1) "S1234-PROV1" 1)
  (assert-match-in-svc "S1234-PROV1" [:keyword] "kw")
  (assert-match-in-svc "S1234-PROV1" [:keyword] "kw3")
  (delete-service "S1234-PROV1" 1)
  (assert-delete-svc "S1234-PROV1")
  (assert-not-match-in-svc "S1234-PROV1" :keyword "kw"))

(deftest save-service-all-revisions-test
  (save-service (es-doc-svc-rev-1) "S1234-PROV1" 1 {:all-revisions-index? true})
  (save-service (es-doc-svc-rev-2) "S1234-PROV1" 2 {:all-revisions-index? true})
  (assert-match-in-svc "S1234-PROV1,1" [:keyword] "kw")
  (assert-match-in-svc "S1234-PROV1,1" [:keyword] "kw3")
  (assert-not-match-in-svc "S1234-PROV1,1" [:keyword] "keyword")
  (assert-match-in-svc "S1234-PROV1,2" [:keyword] "keyword")
  (assert-match-in-svc "S1234-PROV1,2" [:keyword] "keyword3")
  (assert-not-match-in-svc "S1234-PROV1,2" [:keyword] "kw")
  (delete-service "S1234-PROV1" 1 {:all-revisions-index? true})
  (assert-delete-svc "S1234-PROV1")
  (assert-not-match-in-svc "S1234-PROV1,1" [:keyword] "kw")
  (delete-service "S1234-PROV1" 2 {:all-revisions-index? true})
  (assert-delete-svc "S1234-PROV1")
  (assert-not-match-in-svc "S1234-PROV1,2" [:keyword] "keyword"))

(deftest save-service-all-revisions-false-test
  (save-service (es-doc-svc-rev-1) "S1234-PROV1" 1 {:all-revisions-index? false})
  (assert-match-in-svc "S1234-PROV1" [:keyword] "kw")
  (assert-match-in-svc "S1234-PROV1" [:keyword] "kw3")
  (assert-not-match-in-svc "S1234-PROV1" [:keyword] "keyword")
  (save-service (es-doc-svc-rev-2) "S1234-PROV1" 2 {:all-revisions-index? false})
  (assert-match-in-svc "S1234-PROV1" [:keyword] "keyword")
  (assert-match-in-svc "S1234-PROV1" [:keyword] "keyword3")
  (assert-not-match-in-svc "S1234-PROV1" [:keyword] "kw")
  (delete-service "S1234-PROV1" 2 {:all-revisions-index? false})
  (assert-delete-svc "S1234-PROV1")
  (assert-not-match-in-svc "S1234-PROV1" [:keyword] "keyword")
  (assert-not-match-in-svc "S1234-PROV1" [:keyword] "kw"))

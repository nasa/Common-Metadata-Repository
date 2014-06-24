(ns cmr.system-int-test.transformer.collection
  "Integration test for CMR transformer for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.transformer :as t]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(def valid-collection-xml
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
  </Collection>")


(deftest transform-collection-echo10
  (let [{:keys [concept-id, revision-id] :as umm} (d/ingest "PROV1" (dc/collection {:short-name "MINIMAL"
                                                :long-name "A minimal valid collection"
                                                :version-id 1}))
        resp (t/transform-concepts [[concept-id revision-id]] "application/echo10+xml")]
    (println umm)
    (println resp)))
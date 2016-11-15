(ns cmr.system-int-test.search.collection-usage-relevancy
  "This tests the CMR Search API's community usage relevancy scoring and ranking
  capabilities"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def sample-usage-csv
  (str "Product,Version,Hosts\n"
       "AMSR-L1A,3,4\n"))

(defn- ingest-community-usage-metrics
 "Ingest sample metrics to use in tests"
 []
 (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
 (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])]
   (hu/update-community-usage-metrics admin-update-token sample-usage-csv)
   (index/wait-until-indexed)))

(deftest community-usage-relevancy-scoring
  (ingest-community-usage-metrics)
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "AMSR-L1A"
                                                :version-id "3"}))]
    (def coll1 coll1)
    (index/wait-until-indexed)))

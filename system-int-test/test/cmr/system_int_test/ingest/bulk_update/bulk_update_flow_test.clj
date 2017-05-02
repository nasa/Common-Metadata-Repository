(ns cmr.system-int-test.ingest.bulk-update.bulk-update-flow-test
  "CMR bulk update queueing flow integration tests"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest bulk-update
  (let [concept-ids (for [x (range 3)]
                      (:concept-id (ingest/ingest-concept
                                     (data-umm-c/collection-concept
                                       (data-umm-c/collection x {})))))
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value "X"}
        response (ingest/parse-bulk-update-body :json
                   (ingest/bulk-update-collections "PROV1" bulk-update-body
                     {:accept-format :json :raw? true}))]))
    ;(proto-repl.saved-values/save 1)))

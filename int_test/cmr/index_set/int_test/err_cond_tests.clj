(ns cmr.index-set.int-test.err-cond-tests
  "Contains integration tests to verify index-set app behavior under abnormal conditions."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.index-set.int-test.utility :as util]
            [cmr.index-set.services.messages :as messages]))


;;; Manually execed following steps to verify rollback - applicable when running on single node
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1/ set number_of_replicas > 0 in
;;    cmr.index-set.config.elasticsearch-config/idx-cfg-for-index-sets
;; 2/ init elastic
;;     curl  -i -H "Authorization: echo:xxxx" -H "Confirm-delete-action: true" -XDELETE 'http://localhost:9200/'
;; 3/ re-start index-set app (creates index-sets index with new settings)
;; 4/ in repl and in ns cmr.index-set.int-test.utility exec
;;    (submit-create-index-set-req sample-index-set)
;; 5/ exec and watch 'curl http://localhost:9200/_aliases?pretty=1' on console every few secs
;;    to see create and delete of indices listed in index-set
;; 6/ set number_of_replicas = 0


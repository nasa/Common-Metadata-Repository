(ns cmr.system-int-test.bootstrap.bulk-index.channels-test
  "Integration tests for bootstrap channels"
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :as a :refer [chan go <! >! <!! close! timeout]]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.system :as sys]
    [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.umm.echo10.echo10-core :as echo10]))

(deftest collections-index-channel-test
  (testing "channels closed does not trigger infinite worker loop"
    (let [status-tracker (atom :running)
          umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (mdb/save-concept {:concept-type :collection
                                   :format "application/echo10+xml"
                                   :metadata xml1
                                   :extra-fields {:short-name "coll1"
                                                  :entry-title "coll1"
                                                  :entry-id "coll1"
                                                  :version-id "v1"}
                                   :provider-id "PROV1"
                                   :native-id "coll1"
                                   :short-name "coll1"})
          umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))
          valid-coll-id (:concept-id umm1)
          _ (dotimes [n 20]
              (bootstrap/bulk-index-collection
                "PROV1" valid-coll-id))
          ;; Stop the system
          stopped-sys (sys/stop)]

      (print "stopped system = " stopped-sys)

      ;; Poll for the side effect
      (let [finished? (loop [retries 0]
                        (cond
                          (nil? stopped-sys) true
                          (> retries 10) false
                          :else (do (Thread/sleep 100)
                                    (recur (inc retries)))))]
        (is finished? "The internal workers did not shut down!")))))
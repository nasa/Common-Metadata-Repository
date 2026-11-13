(ns cmr.ingest.test.api.core
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]))

(deftest known-user-id-does-not-resolve-test
  (let [resolved? (atom false)
        context (util/lazy-assoc {:token (apply str (repeat 120 "t"))}
                                 :user-id (do (reset! resolved? true) "user1"))]
    (is (nil? (#'api-core/known-user-id context)))
    (is (false? @resolved?))
    (util/lazy-get context :user-id)
    (is (= "user1" (#'api-core/known-user-id context)))))

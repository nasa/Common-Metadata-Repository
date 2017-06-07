(ns cmr.common-app.test.api.request-context-user-augmenter
  "Tests to verify functionality in cmr.search.api.request-context-user-augmenter namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common.cache :as cache]
   [cmr.common.util :as util]))

(deftest token-and-user-id-context-cache
 (let [token-sid-cache (context-augmenter/create-token-sid-cache)
       user-id-cache (context-augmenter/create-token-user-id-cache)]
   (cache/set-value token-sid-cache "ABC-1" ["sid-1" "sid-2"])
   (cache/set-value user-id-cache "ABC-1" "user-id-1")

   (testing "Retrieve values from cache with token"
    (let [context {:system {:caches {context-augmenter/token-sid-cache-name token-sid-cache
                                     context-augmenter/token-user-id-cache-name user-id-cache}}
                   :token "ABC-1"}]
     (testing "sids"
      (is (= ["sid-1" "sid-2"] (#'context-augmenter/context->sids context))))

     (testing "user-id"
      (is (= "user-id-1" (#'context-augmenter/context->user-id context))))))

   (testing "Token required message"
    (let [context {:system {:caches {context-augmenter/token-sid-cache-name token-sid-cache
                                     context-augmenter/token-user-id-cache-name user-id-cache}}}]
     (try
      (#'context-augmenter/context->user-id context "Token required")
      (catch clojure.lang.ExceptionInfo e
       (is (= "Token required" (first (:errors (ex-data e)))))))))))

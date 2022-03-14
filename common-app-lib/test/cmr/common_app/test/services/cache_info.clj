(ns cmr.common-app.test.services.cache-info
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.util :refer [are3]]
   [cmr.common.log :as log]
   [cmr.common-app.services.cache-info :as cache-info]))

(def log-content (atom []))

(defn mock-log
  [& body]
  (swap! log-content concat body))

(defn reset-log
  [f]
  (reset! log-content [])
  (f))

(use-fixtures :each reset-log)

(deftest log-cache-sizes-test
  (testing "Given system context with populated caches"
    (let [imc1 (mem-cache/create-in-memory-cache)
          _ (cache/set-value imc1 :foo "bar")

          imc2 (mem-cache/create-in-memory-cache)
          _ (cache/set-value imc2 :cmr "rocks")

          ctx {:system {:caches {:in-mem-a imc1 :in-mem-b imc2}}}]
      (testing "When the cache sizes job runs"
        (is (nil? (cache-info/log-cache-sizes (cache/cache-sizes ctx)))
            "Then the operation complete successfully and does not throw.")))))

(comment
  ;; with-redefs-fn is not overwriting bindings unless the NS is recompiled
  ;; and is causing tests to fail. These tests can be run locally if the associated
  ;; source file is reloaded through the REPL after the repl comes up
  (deftest log-cache-sizes--very-large-values--no-exceptions
    (with-redefs-fn {#'log/info mock-log}
      #(are3 [cache-size-map output]
         (do
           (reset! log-content [])
           (cache-info/log-cache-sizes cache-size-map)
           (is (= output @log-content)))

         "bytes"
         {:in-mem 1023}
         ["in-memory-cache [:in-mem] [1023 bytes] [1023 bytes]"
          "Total in-memory-cache usage [1023 bytes] [1023 bytes]"]

         "kilobytes"
         {:in-mem (* 1024 6)}
         ["in-memory-cache [:in-mem] [6.00 KB] [6144 bytes]"
          "Total in-memory-cache usage [6.00 KB] [6144 bytes]"]

         "megabytes"
         {:in-mem (inc (* 1024 1024 42))}
         ["in-memory-cache [:in-mem] [42.00 MB] [44040193 bytes]"
          "Total in-memory-cache usage [42.00 MB] [44040193 bytes]"]

         "gigabytes"
         {:in-mem (inc (* 1024 1024 1024 2))}
         ["in-memory-cache [:in-mem] [2.00 GB] [2147483649 bytes]"
          "Total in-memory-cache usage [2.00 GB] [2147483649 bytes]"])))

  (deftest log-cache-sizes--exceeds-long-size--exception-handled
    (with-redefs-fn {#'log/info mock-log
                     #'log/error mock-log}
      #(let [cache-size-map {:in-mem-1 java.lang.Long/MAX_VALUE
                             :in-mem-2 java.lang.Long/MAX_VALUE}]
         (cache-info/log-cache-sizes cache-size-map)
         (is (= ["in-memory-cache [:in-mem-1] [8589934592.00 GB] [9223372036854775807 bytes]"
                 "in-memory-cache [:in-mem-2] [8589934592.00 GB] [9223372036854775807 bytes]"
                 "In-memory-cache size calculation experienced a problem: integer overflow"]
                @log-content)))))

  (deftest log-cache-sizes-job--basics--output-is-correct
    (with-redefs-fn {#'log/info mock-log}
      #(let [imc-basic (mem-cache/create-in-memory-cache)
             _ (cache/set-value imc-basic :foo "bar")
             ctx {:system {:caches {:in-mem-basic imc-basic}}}]
         (cache-info/log-cache-sizes (cache/cache-sizes ctx))
         (is (= ["in-memory-cache [:in-mem-basic] [3 bytes] [3 bytes]"
                 "Total in-memory-cache usage [3 bytes] [3 bytes]"]
                @log-content)))))
  
  (deftest log-cache-sizes-job--multiple-caches--output-is-correct
    (with-redefs [cmr.common.log/info mock-log]
      (let [imc1 (mem-cache/create-in-memory-cache)
            _ (cache/set-value imc1 :foo "bar")
            imc2 (mem-cache/create-in-memory-cache)
            _ (cache/set-value imc2 :foo "bar")
            ctx {:system {:caches {:in-mem-a imc1 :in-mem-b imc2}}}]

        (cache-info/log-cache-sizes (cache/cache-sizes ctx))
        (is (= ["in-memory-cache [:in-mem-a] [3 bytes] [3 bytes]"
                "in-memory-cache [:in-mem-b] [3 bytes] [3 bytes]"
                "Total in-memory-cache usage [6 bytes] [6 bytes]"]
               @log-content)))))
  )

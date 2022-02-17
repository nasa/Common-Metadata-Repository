(ns cmr.common-app.test.services.cache-info
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :as log]
   [cmr.common.util :refer [are3]]
   [cmr.common-app.services.cache-info :as cache-info]))

(deftest log-cache-sizes--very-large-values--no-exceptions
  (let [stdout (atom [])]
    (are3 [cache-size-map output]
      (do
        (reset! stdout [])
        (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))}
          #(do
             (cache-info/log-cache-sizes cache-size-map)
             (is (= output @stdout)))))

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
  (let [stdout (atom [])
        cache-size-map {:in-mem-1 java.lang.Long/MAX_VALUE
                        :in-mem-2 java.lang.Long/MAX_VALUE}]
    (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))
                     #'log/warn (fn [s] (swap! stdout conj s))}
      #(do
         (cache-info/log-cache-sizes cache-size-map)
         (is (= ["in-memory-cache [:in-mem-1] [8589934592.00 GB] [9223372036854775807 bytes]"
                 "in-memory-cache [:in-mem-2] [8589934592.00 GB] [9223372036854775807 bytes]"
                 "In-memory-cache size calculation experienced a problem: integer overflow"]
                @stdout))))))

(deftest log-cache-sizes-job--basics--output-is-correct
  (let [imc-basic (mem-cache/create-in-memory-cache)
        _ (cache/set-value imc-basic :foo "bar")
        ctx {:system {:caches {:in-mem-basic imc-basic}}}
        stdout (atom [])]
    (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))}
      #(do
         (cache-info/log-cache-sizes (cache/cache-sizes ctx))
         (is (= ["in-memory-cache [:in-mem-basic] [3 bytes] [3 bytes]"
                 "Total in-memory-cache usage [3 bytes] [3 bytes]"]
                @stdout))))))

(deftest log-cache-sizes-job--multiple-caches--output-is-correct
  (let [imc1 (mem-cache/create-in-memory-cache)
        _ (cache/set-value imc1 :foo "bar")
        imc2 (mem-cache/create-in-memory-cache)
        _ (cache/set-value imc2 :foo "bar")
        ctx {:system {:caches {:in-mem-a imc1 :in-mem-b imc2}}}
        stdout (atom [])]
    (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))}
      #(do
         (cache-info/log-cache-sizes (cache/cache-sizes ctx))
         (is (= ["in-memory-cache [:in-mem-a] [3 bytes] [3 bytes]"
                 "in-memory-cache [:in-mem-b] [3 bytes] [3 bytes]"
                 "Total in-memory-cache usage [6 bytes] [6 bytes]"]
                @stdout))))))

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
    (are3 [cache-size-map unit output]
      (do
        (reset! stdout [])
        (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))}
          #(do
             (cache-info/log-cache-sizes cache-size-map unit)
             (is (= output @stdout)))))

      "no unit specified"
      {:in-mem java.lang.Long/MAX_VALUE} nil
      ["in-memory-cache [:in-mem] [9223372036854776000.00 bytes]"
       "Total in-memory-cache usage [9223372036854776000.00 bytes]"]

      "bytes"
      {:in-mem java.lang.Long/MAX_VALUE} :bytes
      ["in-memory-cache [:in-mem] [9223372036854776000.00 bytes]"
       "Total in-memory-cache usage [9223372036854776000.00 bytes]"]

      "megabytes"
      {:in-mem java.lang.Long/MAX_VALUE} :mb
      ["in-memory-cache [:in-mem] [9007199254740992.00 MB]"
       "Total in-memory-cache usage [9007199254740992.00 MB]"]

      "gigabytes"
      {:in-mem java.lang.Long/MAX_VALUE} :gb
      ["in-memory-cache [:in-mem] [8796093022208.00 GB]"
       "Total in-memory-cache usage [8796093022208.00 GB]"])))

(deftest log-cache-sizes--exceeds-long-size--exception-handled
  (let [stdout (atom [])
        cache-size-map {:in-mem-1 java.lang.Long/MAX_VALUE
                        :in-mem-2 java.lang.Long/MAX_VALUE}]
    (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))
                     #'log/warn (fn [s] (swap! stdout conj s))}
      #(do
         (cache-info/log-cache-sizes cache-size-map)
         (is (= ["in-memory-cache [:in-mem-1] [9223372036854776000.00 bytes]"
                 "in-memory-cache [:in-mem-2] [9223372036854776000.00 bytes]"
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
         (is (= ["in-memory-cache [:in-mem-basic] [3.00 bytes]"
                 "Total in-memory-cache usage [3.00 bytes]"]
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
         (is (= ["in-memory-cache [:in-mem-a] [3.00 bytes]"
                 "in-memory-cache [:in-mem-b] [3.00 bytes]"
                 "Total in-memory-cache usage [6.00 bytes]"]
                @stdout))))))

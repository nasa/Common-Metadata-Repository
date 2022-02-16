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
      ["in-memory-cache [:in-mem] [9223372036854775807 bytes]"
       "Total in-memory-cache usage [9223372036854775807 bytes]"]

      "bytes"
      {:in-mem java.lang.Long/MAX_VALUE} :bytes
      ["in-memory-cache [:in-mem] [9223372036854775807 bytes]"
       "Total in-memory-cache usage [9223372036854775807 bytes]"]

      "megabytes"
      {:in-mem java.lang.Long/MAX_VALUE} :mb
      ["in-memory-cache [:in-mem] [9007199254740991 MB]"
       "Total in-memory-cache usage [9007199254740991 MB]"]

      "gigabytes"
      {:in-mem java.lang.Long/MAX_VALUE} :gb
      ["in-memory-cache [:in-mem] [8796093022207 GB]"
       "Total in-memory-cache usage [8796093022207 GB]"])))

(deftest log-cache-sizes-job--basics--output-is-correct
  (let [imc-basic (mem-cache/create-in-memory-cache)
        _ (cache/set-value imc-basic :foo "bar")
        ctx {:system {:caches {:in-mem-basic imc-basic}}}
        stdout (atom [])]
    (with-redefs-fn {#'log/info (fn [s] (swap! stdout conj s))}
      #(do
         (cache-info/log-cache-sizes (cache/cache-sizes ctx))
         (is (= ["in-memory-cache [:in-mem-basic] [3 bytes]"
                 "Total in-memory-cache usage [3 bytes]"]
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
         (is (= ["in-memory-cache [:in-mem-a] [3 bytes]"
                 "in-memory-cache [:in-mem-b] [3 bytes]"
                 "Total in-memory-cache usage [6 bytes]"]
                @stdout))))))

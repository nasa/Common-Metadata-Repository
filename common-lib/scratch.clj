(ns scratch
  (require [cmr.common.util :as u]
           [clojure.java.io :as io]
           [clojure.string :as str]
           [clojure.core.reducers :as r]))

(def example-echo10-dir
  "/Users/jgilman/Desktop/example_echo10")

(def echo10-files
  (->> example-echo10-dir
       io/file
       .listFiles
       (map #(.getAbsolutePath ^java.io.File %))
       (filter #(re-find #"\.xml$" %))
       (map io/file)))

(defn echo10-file->echo10-xmls
  [ef]
  (let [contents (slurp ef)]
    (for [match (drop 1 (str/split contents #"(?ms)<result "))]
      (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))))

(def gzipped-echo10s
  (mapv u/string->gzip-bytes (mapcat echo10-file->echo10-xmls echo10-files)))

;; 63.312862 megabytes
(reduce + (map count gzipped-echo10s))


(def echo10s-by-size
  (mapv #(vector (count %) %) gzipped-echo10s))

;; Get the biggest xml and print it
(->> echo10s-by-size
     (sort-by first)
     reverse
     first
     second
     u/gzip-bytes->string
     println)

(def top-n-gzipped-xmls
  (->> echo10s-by-size
       (sort-by first)
       reverse
       (take 2000)
       (mapv second)))

(def bottom-n-gzipped-xmls
  (->> echo10s-by-size
       (sort-by first)
       (take 2000)
       (mapv second)))

(require '[criterium.core :as c])

;; 515 ms for mapv unzipping 2000 biggest collections
;; 117 ms for pmap unzipping 2000 biggest collections

;; 109ms for mapv of smallest collections
;; 27ms for the pmap of smallest collections
;; 34ms for r/fold
;; 26ms for r/fold size of 25
;; 25ms for r/fold size of 50
;; 28ms for r/fold size of 100
;; 28ms for r/fold size of 200

;; all collecions
;; pmap - 1.8s
;; pmapv - 1.4

(c/quick-bench
 (doall (pmap u/gzip-bytes->string gzipped-echo10s)))

(c/quick-bench
 (u/pmapv u/gzip-bytes->string gzipped-echo10s))

(c/quick-bench
  (r/fold (fn
            ([] [])
            ([results value]
             (into results value)))
          (fn
            ([] [])
            ([results value]
             (conj results value)))
          (r/map u/gzip-bytes->string bottom-n-gzipped-xmls)))

(c/quick-bench
 (persistent!
  (r/fold 200
          (fn
            ([] (transient []))
            ([results values]
             (reduce conj! results (persistent! values))))
          ; (into results values)))
          (fn
            ([] (transient []))
            ([results value]
             (conj! results (u/gzip-bytes->string value))))
          bottom-n-gzipped-xmls)))

(count result)

(count bottom-n-gzipped-xmls)

(r/fold (fn
          ([] [])
          ([results value]
           (proto/save 3)
           (into results value)))
        (fn
          ([] [])
          ([results value]
           (proto/save 2)
           (conj results value)))
        (r/map inc (vec (range 1000))))


(c/quick-bench
 (into [] (r/map u/gzip-bytes->string bottom-n-gzipped-xmls)))


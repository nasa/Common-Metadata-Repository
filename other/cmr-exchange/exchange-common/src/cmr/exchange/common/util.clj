(ns cmr.exchange.common.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [taoensso.timbre :as log])
  (:import
   (java.util UUID)
   (java.util Random)
   (clojure.lang Symbol)))

(defn get-uuid
  "Creates Java UUID while avoiding expensive call to SecureRandom."
  []
  (let [random (Random.)]
    (str (UUID. (.nextLong random) (.nextLong random)))))

(defn newline?
  ""
  [byte]
  (= (char byte) \newline))

(defn bool?
  [arg]
  (if (contains? #{true :true "true" "TRUE" "t" "T" 1} arg)
    true
    false))

(def bool bool?)

(defn remove-empty
  [coll]
  (remove #(or (nil? %) (empty? %)) coll))

(defn deep-merge
  "Merge maps recursively."
  [& maps]
  (if (every? #(or (map? %) (nil? %)) maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn now
  []
  (/ (System/currentTimeMillis) 1000))

(defn timed
  [start]
  (float (- (now) start)))

(defn most-frequent
  "This identifies the most frequently occuring data in a collection
  and returns it."
  [data]
  (->> data
       frequencies
       ;; the 'frequencies' function puts data first; let's swap the order
       (map (fn [[k v]] [v k]))
       ;; sort in reverse order to get the highest counts first
       (sort (comp - compare))
       ;; just get the highest
       first
       ;; the first element is the count, the second is the bounding data
       second))

(defn promise?
  [p]
  (isa? (class p) clojure.lang.IPending))

(defn bytes->ascii
  ""
  [bytes]
  (.trim (new String bytes "US-ASCII")))

(defn bytes->utf8
  ""
  [bytes]
  (.trim (new String bytes "UTF-8")))

(defn bytes->int
  ""
  [bytes]
  (-> bytes
      (bytes->ascii)
      (Integer/parseInt)))

(defn str->bytes
  ""
  [str]
  (.getBytes str))

(defn str->stream
  ""
  [str]
  (io/input-stream (str->bytes str)))

(defn resolve-fully-qualified-fn
  [^Symbol fqfn]
  (when fqfn
    (try
      (let [[name-sp fun] (mapv symbol (string/split (str fqfn) #"/"))]
        (require name-sp)
        (var-get (ns-resolve name-sp fun)))
      (catch  Exception _
        (log/warn "Couldn't resolve one or more of" fqfn)))))

(defn data-type->bytes
  [data-type]
  (case (keyword (string/lower-case data-type))
    :byte 1
    :float 4
    :float32 4
    :float64 8
    :double 8
    :ubyte 1
    :ushort 2
    :uint 4
    :uchar 1
    :string 2
    :char8 1
    :uchar8 1
    :short 2
    :long 4
    :int 4
    :int8 1
    :int16 2
    :int32 4
    :int64 8
    :uint8 1
    :uint16 2
    :uint32 4
    :uint64 8))

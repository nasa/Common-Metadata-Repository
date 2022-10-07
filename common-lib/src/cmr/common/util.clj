(ns cmr.common.util
  "Utility functions that might be useful throughout the CMR."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint print-table]]
   [clojure.reflect :refer [reflect]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.template :as template]
   [clojure.test :as test]
   [clojure.walk :as w]
   [cmr.common.config :as cfg]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors])
  (:import
   (java.text DecimalFormat)
   (java.util.zip GZIPInputStream GZIPOutputStream)
   (java.io ByteArrayOutputStream ByteArrayInputStream)
   (java.sql Blob)
   (java.util Arrays)
   (net.jpountz.lz4 LZ4Compressor LZ4SafeDecompressor LZ4FastDecompressor LZ4Factory)))

(defmacro are2
  "DEPRECATED. Use are3 instead.

   Based on the are macro from clojure.test. Checks multiple assertions with a
   template expression. Wraps each tested expression in a testing block to
   identify what's being tested. See clojure.template/do-template for an
   explanation of templates.

   Example: (are2 [x y] (= x y)
                 \"The most basic case with 1\"
                 2 (+ 1 1)
                 \"A more complicated test\"
                 4 (* 2 2))
   Expands to:
            (do
               (testing \"The most basic case with 1\"
                 (is (= 2 (+ 1 1))))
               (testing \"A more complicated test\"
                 (is (= 4 (* 2 2)))))

   Note: This breaks some reporting features, such as line numbers."
  [argv expr & args]
  (if (or
        ;; (are2 [] true) is meaningless but ok
        (and (empty? argv) (empty? args))
        ;; Catch wrong number of args
        (and (pos? (count argv))
             (pos? (count args))
             (zero? (mod (count args) (inc (count argv))))))
    (let [testing-var (gensym "testing-msg")
          argv (vec (cons testing-var argv))]
      `(template/do-template
         ~argv (test/testing ~testing-var (test/is ~expr)) ~@args))
    (throw
      (IllegalArgumentException.
        (str "The number of args doesn't match are2's argv or testing doc "
             "string may be missing.")))))

(defmacro are3
  "Similar to the are2 macro with the exception that it expects that your
  assertion expressions will be explicitly wrapped in is calls. This gives
  better error messages in the case of failures than if ANDing them together.

  Example: (are3 [x y]
             (do
               (is (= x y))
               (is (= y x)))
             \"The most basic case with 1\"
             2 (+ 1 1)
             \"A more complicated test\"
             4 (* 2 2))
  Expands to:
           (do
             (testing \"The most basic case with 1\"
               (do
                 (is (= 2 (+ 1 1)))
                 (is (= (+ 1 1) 2))))
             (testing \"A more complicated test\"
               (do
                 (is (= 4 (* 2 2)))
                 (is (= (* 2 2) 4)))))

  Note: This breaks some reporting features, such as line numbers."
  [argv expr & args]
  (if (or
        ;; (are3 [] true) is meaningless but ok
        (and (empty? argv) (empty? args))
        ;; Catch wrong number of args
        (and (pos? (count argv))
             (pos? (count args))
             (zero? (mod (count args) (inc (count argv))))))
    (let [testing-var (gensym "testing-msg")
          argv (vec (cons testing-var argv))]
      `(template/do-template ~argv (test/testing ~testing-var ~expr) ~@args))
    (throw
      (IllegalArgumentException.
        (str "The number of args doesn't match are3's argv or testing doc "
             "string may be missing.")))))

(defn trunc
  "Returns the given string truncated to n characters."
  [s n]
  (when s
    (subs s 0 (min (count s) n))))

(defn safe-lowercase
  "Returns the given string in lower case safely."
  [v]
  (when (some? v) (string/lower-case v)))

(defn safe-uppercase
  "Returns the given string in upper case safely."
  [v]
  (when (some? v) (string/upper-case v)))

(defn match-enum-case
  "Given a string and a collection of valid enum values, return the proper-cased
   value from the enum. The values will not differ, but this ensures that the
   one returned is the proper case, even if that is a crazy mix"
  [value enum-values]
  (->> enum-values
       (filter #(re-matches (re-pattern (str "(?i)" value)) %))
       seq
       first))

(defn sequence->fn
  [vals]
  "Creates a stateful function that returns individual values from the
  sequence. It returns the first value when called the first time, the second
  value on the second call and so on until the sequence is exhausted of
  values. Returns nil forever after that.

      user=> (def my-ints (sequence->fn [1 2 3]))
      user=> (my-ints)
      1
      user=> (my-ints)
      2
      user=> (my-ints)
      3
      user=> (my-ints)
      nil"
  (let [vals-atom (atom {:curr-val nil :next-vals (seq vals)})]
    (fn []
      (:curr-val (swap! vals-atom
                        (fn [{:keys [next-vals]}]
                          {:curr-val (first next-vals)
                           :next-vals (rest next-vals)}))))))

(defmacro future-with-logging
  "Creates a future that will log when a task starts and completes or if
  exceptions occur."
  [taskname & body]
  `(future
    (info "Starting " ~taskname)
    (try
      (let [result# (do ~@body)]
        (info ~taskname " completed without exception")
        result#)
      (catch Throwable e#
        (error e# "Exception in " ~taskname)
        (throw e#))
      (finally
        (info ~taskname " complete.")))))

(defmacro time-execution
  "Times the execution of the body and returns a tuple of time it took and the
  results."
  [& body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)]
     [(- (System/currentTimeMillis) start#) result#]))

(defmacro defn-timed
  "Creates a function that logs how long it took to execute the body. It
  supports multiarity functions but only times how long the last listed arity
  version takes. This means it should be used with multiarity functions where
  it calls itself with the extra arguments."
  [fn-name & fn-tail]
  (let [fn-name-str (name fn-name)
        ns-str (str *ns*)
        ;; Extract the doc string from the function if present
        [doc-string fn-tail] (if (string? (first fn-tail))
                               [(first fn-tail) (next fn-tail)]
                               [nil fn-tail])
        ;; Wrap single arity functions in a list
        fn-tail (if (vector? (first fn-tail))
                  (list fn-tail)
                  fn-tail)
        ;; extract other arities defined in the function which will not be
        ;; timed.
        other-arities (drop-last fn-tail)
        ;; extract the last arity definitions bindings and body
        [timed-arity-bindings & timed-arity-body] (last fn-tail)]
    `(defn ~fn-name
       ~@(when doc-string [doc-string])
       ~@other-arities
       (~timed-arity-bindings
         (let [start# (System/currentTimeMillis)]
           (try
             ~@timed-arity-body
             (finally
               (let [elapsed# (- (System/currentTimeMillis) start#)]
                 ;; CMR-3792. making defn-timed debug messages configurable
                 (when (= true (cfg/defn-timed-debug))
                   (info
                     (format
                       "Timed function %s/%s took %d ms."
                       ~ns-str ~fn-name-str elapsed#)))))))))))

(defn build-validator
  "Creates a function that will call f with it's arguments. If f returns any
  errors then it will throw a service error of the type given.

  DEPRECATED: we should use the validations namespace."
  [error-type f]
  (fn [& args]
    (when-let [errors (apply f args)]
      (when (seq errors)
        (errors/throw-service-errors error-type errors)))))

(defn apply-validations
  "Given a list of validation functions, applies the arguments to each
  validation, concatenating all errors and returning them. As such, validation
  functions are expected to only return a list; if the list is empty, it is
  understood that no errors occurred.

  DEPRECATED: we should use the validations namespace."
  [validations & args]
  (reduce (fn [errors validation]
            (if-let [new-errors (apply validation args)]
              (concat errors new-errors)
              errors))
          []
          validations))

(defn compose-validations
  "Creates a function that will compose together a list of validation functions
  into a single function that will perform all validations together.

  DEPRECATED: we should use the validations namespace."
  [validation-fns]
  (partial apply-validations validation-fns))

(defmacro record-fields
  "Returns the set of fields in a record type as keywords. The record type
  passed in must be a java class. Uses the getBasis function on record classes
  which returns a list of symbols of the fields of the record."
  [record-type]
  `(map keyword  ( ~(symbol (str record-type "/getBasis")))))

(defn remove-map-keys
  "Removes all keys from a map where the provided function returns true for the
  value of that key. The supplied function must take a single value as an
  argument."
  [f m]
  (apply dissoc m (for [[k v] m
                        :when (f v)]
                    k)))

(defn remove-nil-keys
  "Removes keys mapping to nil values in a map."
  [m]
  (reduce (fn [m kv]
            (if (nil? (val kv))
              (dissoc m (key kv))
              m))
          m
          m))

(defn inflate-nil-keys
  "Occupy nil values with a given default value."
  [m filler]
  (w/postwalk-replace {nil filler} m))

(defn nil-if-value
  "Treat value as nil if matches key."
  [key value]
  (when-not (= key value)
    value))

(defn remove-empty-maps
  "Recursively removes maps with only nil values."
  [x]
  (cond
    (map? x)
    (let [clean-map (remove-nil-keys
                      (zipmap (keys x) (map remove-empty-maps (vals x))))]
      (when (seq clean-map)
        clean-map))
    (sequential? x)
    (keep remove-empty-maps x)
    :else
    x))

(defn remove-nils-empty-maps-seqs
  "Recursively removes nils, maps with nil values, empty maps, empty vectors,
   and empty sequences from the passed in form."
  [x]
  (cond
    (map? x) (let [clean-map (remove-nil-keys
                               (zipmap (keys x) (map remove-nils-empty-maps-seqs (vals x))))]
               (when (seq clean-map)
                 clean-map))
    (vector? x) (when (seq x)
                  (into [] (keep remove-nils-empty-maps-seqs x)))
    (sequential? x) (when (seq x)
                      (keep remove-nils-empty-maps-seqs x))
    :else x))

(defn map-keys
  "Maps f over the keys in map m and updates all keys with the result of f.
  This is a recommended function from the Camel Snake Kebab library."
  [f m]
  (when m
    (letfn [(handle-value [v]
                          (cond
                            (map? v) (map-keys f v)
                            (vector? v) (mapv handle-value v)
                            (seq? v) (map handle-value v)
                            :else v))
            (mapper [[k v]]
                    [(f k) (handle-value v)])]
      (into {} (map mapper m)))))

(defn map-values
  "Maps f over all the values in m returning a new map with the updated
  values."
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn map-keys->snake_case
  "Converts map keys to snake_case."
  [m]
  (map-keys csk/->snake_case_keyword m))

(defn map-keys->kebab-case
  "Converts map keys to kebab-case"
  [m]
  (map-keys csk/->kebab-case-keyword m))

(defn mapcatv
  "An eager version of mapcat that returns a vector of the results."
  [f sequence]
  (reduce (fn [v i]
            (into v (f i)))
          []
          sequence))

(defn any-true?
  "Returns true if predicate f returns a truthy value against any of the items.
  This is very similar to some but it's faster through it's use of reduce."
  [f items]
  (reduce (fn [_ i]
            (if (f i)
              (reduced true) ;; short circuit
              false))
          false
          items))

(defn map-n
  "Calls f with every step count elements from items. Equivalent to:

      (map f (partition n step items))

  but faster. Note that it drops partitions at the end that would be less than
  a length of n."
  ([f n items]
   (map-n f n n items))
  ([f ^long n ^long step items]
   (let [items (vec items)
         size (count items)]
     (loop [index 0 results (transient [])]
       (let [subvec-end (+ index n)]
         (if (or (>= index size) (> subvec-end size))
           (persistent! results)
           (let [sub (subvec items index subvec-end)]
             (recur (+ index step) (conj! results (f sub))))))))))

(defn map-n-all
  "Calls f with every step count elements from items. Equivalent to:

      (map f (partition-all n step items))

  but faster. Includes sets at the end that could be less than a lenght of n."
  ([f n items]
   (map-n-all f n n items))
  ([f ^long n ^long step items]
   (let [items (vec items)
         size (count items)]
     (loop [index 0 results (transient [])]
       (let [subvec-end (min (+ index n) size)]
         (if (>= index size)
           (persistent! results)
           (let [sub (subvec items index subvec-end)]
             (recur (+ index step) (conj! results (f sub))))))))))

(defn pmap-n-all
  "Splits work up n ways across futures and executes it in parallel. Calls the
  function with a set of n or fewer at the end. Not lazy - Items will be
  evaluated fully.

  Note that n is _not_ the number of threads that will be used. It's the number
  of items that will be processed in each parallel batch."
  [f n items]
  (let [build-future (fn [subset]
                       (future
                         (try
                           (f subset)
                           (catch Throwable t
                             (error t (.getMessage t))
                             (throw t)))))
        futures (map-n-all build-future n items)]
    (mapv deref futures)))

(defn fast-map
  "Eager version of pmap. Does error handling to throw the original error
  thrown in f. In pmap the  error thrown will be
  java.util.concurrent.ExecutionException which will mask the original
  exception. This version will throw the original exception so it can be
  handled appropriately."
  [f values]
  (let [errors (atom nil)
        result (doall (pmap
                        (fn [val]
                         (try
                           (f val)
                           (catch Exception e
                             (swap! errors conj e)
                             nil)))
                        values))]
    (if @errors
      (throw (first @errors))
      result)))

(defn doall-recursive
  "Recursively forces evaluation of any nested sequences. The regular doall
  will force evaluation of a sequence but if elements of those sequences are
  things like maps which also contain lazy sequences they would not be
  realized. This function will force all of them to be realized."
  [v]
  (w/postwalk identity v))

(defmacro while-let
  "A macro that's similar to when let. It will continually evaluate the
  bindings and execute the body until the binding results in a nil value."
  [bindings & body]
  `(loop []
     (when-let ~bindings
       ~@body
       (recur))))

(defn double->string
  "Converts a double to string without using exponential notation or loss of
  accuracy."
  [d]
  (when d (.format (DecimalFormat. "#.#####################") d)))

(defn numeric-string?
  "Returns true if the string can be converted to a double. False otherwise."
  [val]
  (try
    (Double. ^String val)
    true
    (catch NumberFormatException _
      false)))

(defn rename-keys-with [m kmap merge-fn]
  "Returns the map with the keys in kmap renamed to the vals in kmap. Values of
  renamed keys for which there is already existing value will be merged using
  the merge-fn. merge-fn will be called with the original keys value and the
  renamed keys value."
  (let [rename-subset (select-keys m (keys kmap))
        renamed-subsets  (map (fn [[k v]]
                                (set/rename-keys {k v} kmap))
                              rename-subset)
        m-without-renamed (apply dissoc m (keys kmap))]
    (reduce #(merge-with merge-fn %1 %2) m-without-renamed renamed-subsets)))

(defn binary-search
  "Does a binary search between minv and maxv searching for an acceptable
  value. middle-fn should be a function taking two values and finding the
  midpoint. matches-fn should be a function taking a value along with the
  current recursion depth. matches-fn should return a keyword of :less-than,
  :greater-than, or :matches to indicate if the current value is an acceptable
  response."
  [minv maxv middle-fn matches-fn]
  (loop [minv minv
         maxv maxv
         depth 0]
    (let [current (middle-fn minv maxv)
          matches-result (matches-fn current minv maxv depth)]
      (case matches-result
        :less-than (recur current maxv (inc depth))
        :greater-than (recur minv current (inc depth))
        matches-result))))

(defn- compare-results-match?
  "Returns true if the given values are in order based on the given matches-fn,
  otherwise returns false."
  [matches-fn values]
  (->> (partition 2 1 values)
       (map #(apply compare %))
       (every? matches-fn)))

(defn greater-than?
  "Returns true if the given values are in descending order. This is similar to
  core/> except it uses compare function underneath and applies to other types
  other than just java.lang.Number."
  [& values]
  (compare-results-match? pos? values))

(defn less-than?
  "Returns true if the given values are in ascending order. This is similar to
  core/< except it uses compare function underneath and applies to other types
  other than just java.lang.Number."
  [& values]
  (compare-results-match? neg? values))

(defn get-keys-in
  "Returns a set of all of the keys in the given nested map or collection."
  ([m]
   (get-keys-in m #{}))
  ([m key-set]
   (cond
     (map? m)
     (-> key-set
         (into (keys m))
         (into (get-keys-in (vals m))))

     (sequential? m)
     (reduce #(into %1 (get-keys-in %2)) key-set m))))

(defn unbatch
  "Returns a lazy seq of individual results from a lazy sequence of
  sequences. For example: return a lazy sequence of each item in a
  sequence of batches of items in search results."
  [coll]
  (mapcat seq coll))

(defn delete-recursively
  "Recursively delete the directory or file by the given name. Does nothing if
  the file does not exist."
  [fname]
  (when (.exists (io/file fname))
    (letfn [(delete-recursive
              [^java.io.File file]
              (when (.isDirectory file)
                (dorun (map delete-recursive (.listFiles file))))
              (io/delete-file file))]
      (delete-recursive (io/file fname)))))

(defn string->lz4-bytes
  "Compresses the string using LZ4 compression. Returns a map containing the
  compressed byte array and the length of the original string in bytes. The
  decompression length is used during decompression. LZ4 compression is
  faster than GZIP compression but uses more space."

  [^String s]
  (let [data (.getBytes s "UTF-8")
        decompressed-length (count data)
        ^LZ4Factory factory (LZ4Factory/fastestInstance)
        ^LZ4Compressor compressor (.highCompressor factory)
        max-compressed-length (.maxCompressedLength
                                compressor decompressed-length)
        compressed (byte-array max-compressed-length)
        compressed-length (.compress compressor
                                     ;; Data to compress and size
                                     data 0 decompressed-length
                                     ;; Target byte array and size
                                     compressed 0 max-compressed-length)]
    {:decompressed-length decompressed-length
     :compressed (Arrays/copyOf compressed compressed-length)}))

(defn lz4-bytes->string
  "Takes a map as returned by string->lz4-bytes and decompresses it back to the
  original string."
  [lz4-info]
  (let [{:keys [^long decompressed-length
                ^bytes compressed]} lz4-info
        ^LZ4Factory factory (LZ4Factory/fastestInstance)
        ^LZ4FastDecompressor decompressor (.fastDecompressor factory)
        restored (byte-array decompressed-length)]
    (.decompress decompressor
                 compressed 0
                 restored 0 decompressed-length)
    (String. restored 0 decompressed-length)))

(defn gzip-blob->string
  "Convert a gzipped BLOB to a string"
  [^Blob blob]
  (-> blob .getBinaryStream GZIPInputStream. slurp))

(defn gzip-bytes->string
  "Convert a byte array of gzipped data into a string."
  [^bytes bytes]
  (-> bytes ByteArrayInputStream. GZIPInputStream. slurp))

(defn string->gzip-bytes
  "Convert a string to an array of compressed bytes"
  [input]
  (let [output (ByteArrayOutputStream.)
        gzip (GZIPOutputStream. output)]
    (io/copy input gzip)
    (.finish gzip)
    (.toByteArray output)))

(defn string->gzip-base64
  "Converts a string to another string that is the base64 encoded bytes
  obtained by gzip compressing the bytes of the original string."
  [input]
  (let [^bytes b64-bytes (-> input string->gzip-bytes b64/encode)]
   (String. b64-bytes (java.nio.charset.Charset/forName "UTF-8"))))

(defn gzip-base64->string
  "Converts a base64 encoded gzipped string back to the original string."
  [^String input]
  (-> input
      .getBytes
      b64/decode
      ByteArrayInputStream.
      GZIPInputStream.
      slurp))

(defn map->path-values
  "Takes a map and returns a map of a sequence of paths through the map to
  values contained in that map. A path is a sequence of keys to a value in the
  map like that taken by the get-in function.

  Example:

  (map->path-values {:a 1
  :b {:c 2}})
  =>
  {
    [:a] 1
    [:b] 2
  }"
  [matching-map]
  (into {}
        (mapcatv
          (fn [[k v]]
            (if (map? v)
              (mapv (fn [[path value]]
                      [(vec (cons k path)) value])
                    (map->path-values v))
              [[[k] v]]))
          matching-map)))

(defn map-matches-path-values?
  "Returns true if the map matches the given path values. Path values are
  described in the map->path-values function documentation.  When a value is sequential,
  any map which matches at least one of the contained values will be considered to match
  (i.e. the values are ORed)"
  [path-values m]
  (every? (fn [[path value]]
            (some #(= (get-in m path) %) (if (sequential? value) value [value])))
          path-values))

(defn filter-matching-maps
  "Keeps all the maps which match the given matching map. The matching map is a
  set of nested maps with keys and values. A map matches it if the matching
  map is a subset of the map."
  [matching-map maps]
  (let [path-values (map->path-values matching-map)]
    (filter #(map-matches-path-values? path-values %) maps)))

(defn update-in-each
  "Like update-in but applied to each value in seq at path."
  [m path f & args]
  (update-in m path (fn [xs]
                      (when xs
                        (map (fn [x]
                               (apply f x args))
                             xs)))))

(defn update-in-each-vector
  "Like update-in-each but applied to each value in seq at path
   and returns a vector."
  [m path f & args]
  (update-in m path (fn [xs]
                      (when xs
                        (mapv (fn [x]
                                (apply f x args))
                              xs)))))

(defn update-in-all
  "For nested maps, this is identical to clojure.core/update-in. If it
  encounters a sequential structure at one of the keys, though, it applies
  the update to each value in the sequence. If it encounters nil at a parent
  key, it does nothing."
  [m [k & ks] f & args]
  (let [v (get m k)]
    (if (nil? v)
      m
      (if (sequential? v)
        (if ks
          (assoc m k (mapv #(apply update-in-all %1 ks f args) v))
          (assoc m k (mapv #(apply f %1 args) v)))
        (if ks
          (assoc m k (apply update-in-all v ks f args))
          (assoc m k (apply f v args)))))))

(defn get-in-all
  "Similar to clojure.core/get-in, but iterates over sequence values found
  along the key path and returns an array of all matching values.

  This function handles the following conditions:
   * Return empty results if the path can't be followed
   * Return just the value if we're at the end of the path
   * Iterate and recurse through sequences
   * Recurse on ordinary keys (assumed to be maps)"
  [m [k & ks]]
  (let [v (get m k)]
    (cond
      (nil? v) []
      (nil? ks) [v]
      (sequential? v) (mapcat #(get-in-all %1 ks) v)
      :else (get-in-all v ks))))

(defn- key->delay-name
  "Returns the key that the delay is stored in for a lazy value"
  [k]
  {:pre [(keyword? k)]}
  (keyword (str "cmr.common.util/" (name k) "-delay")))

(defmacro lazy-assoc
  "Associates a value in a map in a way that the expression isn't evaluated
  until the value is retrieved from the map. The value must be retrieved using
  lazy-get. A different key is built using the one specified so that only
  lazy-get can be used to retrieve the value. It also allows a map to contain
  either the original value with the same key and a lazily determined value."
  [m k value-expression]
  (let [delay-name (key->delay-name k)]
    `(assoc ~m ~delay-name (delay ~value-expression))))

(defn lazy-get
  "Realizes and retrieves a value stored via lazy-assoc."
  [m k]
  (some-> m (get (key->delay-name k)) deref))

(defn get-real-or-lazy
  "Retrieves the value directly from the map with the key k or if not set looks
  for a lazily associated value."
  [m k]
  (or (get m k) (lazy-get m k)))

(defn extract-between-strings
  "Extracts a substring from s that begins with start and ends with end."
  ([^String s ^String start ^String end]
   (extract-between-strings s start end true))
  ([^String s ^String start ^String end include-start-and-end?]
   (let [start-index (.indexOf s start)]
     (when (not= start-index -1)
       (let [end-index (.indexOf s end (+ start-index (count start)))]
         (when (not= end-index -1)
           (if include-start-and-end?
             (.substring s start-index (+ end-index (count end)))
             (let [substr (.substring s (+ start-index (count start)) end-index)]
               ;; Return nil if there's no data between the two
               (when (not= 0 (count substr))
                 substr)))))))))

(defn map-by
  "Like group-by but assumes that all the keys returned by f will be unique per
  item."
  [f items]
  (into {} (for [item items] [(f item) item])))

(defn truncate-nils
  "Truncates the nil elements from the end of the given sequence, returns the
  truncated sequence. Example:

    (truncate-nils [1 2 nil 3 nil nil]) => '(1 2 nil 3)"
  [coll]
  (reverse (drop-while nil? (reverse coll))))

(defn map-longest
  "Similar to map function, but applies the function to the longest of the
  sequences, use the given default to pad the shorter sequences.

  See http://stackoverflow.com/questions/18940629/using-map-with-different-sized-collections-in-clojure"
  [f default & colls]
  (lazy-seq
    (when (some seq colls)
      (cons
        (apply f (map #(if (seq %) (first %) default) colls))
        (apply map-longest f default (map rest colls))))))

(defn key-sorted-map
  "Creates an empty map whose keys are sorted by the order given. Keys not in
  the set will appear after the specified keys. Keys must all be of the same
  type."
  [key-order]
  ;; Create a map of the keys to a numeric number indicating their position.
  (let [key-order-map (zipmap key-order (range))]
    (sorted-map-by
      (fn [k1 k2]
        (let [k1-order (key-order-map k1)
              k2-order (key-order-map k2)]
          (cond
            ;; k1 and k2 are both in the key-order-map.
            (and k1-order k2-order) (compare k1-order k2-order)

            ;; k1 is in the map but not k2. k1 should appear earlier than k2
            k1-order -1

            ;; k2 is in the map but not k1. k1 should appear after k2
            k2-order 1

            ;; Neither is in the map so compare them directly
            :else (compare k1 k2)))))))

;; Copied from clojure.core.incubator. We were having issues referring to this after updating to Clojure 1.7.
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn seqv
  "Returns (vec coll) when (seq coll) is not nil."
  [coll]
  (when (seq coll)
    (vec coll)))

(defn seqify
  "When x is non-nil, returns x if it is sequential, or else returns a
  sequential collection containing only x."
  [x]
  (when (some? x)
    (if (sequential? x)
      x
      [x])))

(defn- decompose-natural-string
  "Given a string, returns a vector containing the alternating sequences of
  digit and non-digit subsequences. Digits are returned as integers and the
  vector is guaranteed to start with a (potentially empty) string."
  [s]
  (let [lower-s (string/lower-case s)
        result (map #(if (re-matches #"\d+" %)
                       (Integer/parseInt % 10)
                       %)
                    (re-seq #"(?:\d+|[^\d]+)" lower-s))]
    (if (number? (first result))
      (into [""] result)
      (vec result))))

(defn compare-vectors
  "Compares two vectors of potentially unequal size. The default comparator
   for vectors considers length first, regardless of contents. compare-vectors
   compares in a manner similar to strings, where contents are considered
   first.

   > (compare [1 2 3] [2 1]) => 1
   > (compare-vectors [1 2 3] [2 1]) => -1"
  [v0 v1]
  (let [len (min (count v0) (count v1))
        cmp (compare (subvec v0 0 len) (subvec v1 0 len))]
    (if (or (zero? cmp) (= (count v0) (count v1)))
      (compare v0 v1)
      cmp)))

(defn compare-natural-strings
  "A comparator function for two strings that does not consider case and
  interprets numbers within the strings, so that ab1c < ab2c < AB10C"
  [s0 s1]
  (compare-vectors (decompose-natural-string s0)
                   (decompose-natural-string s1)))

(defn xor
  "Returns true if only one of xs is truthy."
  [& xs]
  (= 1 (count (filter identity xs))))

(defn max-compare
  "Returns the maximum of the objects which can be compared using compare."
  ([] nil)
  ([x] x)
  ([x y]
   (if (pos? (compare x y)) x y))
  ([x y & more]
   (reduce max-compare (max-compare x y) more)))

(defn snake-case-data
  "Returns the given data with keys converted to snake case."
  [data]
  (cond
    (sequential? data) (map map-keys->snake_case data)
    (map? data) (map-keys->snake_case data)
    :else data))

(defn kebab-case-data
  "Returns the data with keys converted to kebab case.

  Alternatively, you can provide a function that takes the data as an argument,
  run before the mapping takes place. This is useful for tests when you want to
  perform assertion checks upon the raw data, before transformation."
  ([data]
   (cond
     (sequential? data) (map map-keys->kebab-case data)
     (map? data) (map-keys->kebab-case data)
     :else data))
  ([data fun]
   (fun data)
   (kebab-case-data data)))

(defn show-methods
  "Display a Java object's public methods."
  [obj]
  (print-table
    (sort-by :name
      (filter (fn [x]
                (contains? (:flags x) :public))
              (:members (reflect obj))))))

(defn show-env
  "Show the system environment currently available to Clojure.

  Example usage:
  ```
  (show-env)
  (show-env keyword)
  (show-env (comp keyword string/lower-case))
  ```"
  ([]
   (show-env identity))
  ([key-fn]
   (show-env key-fn (constantly true)))
  ([key-fn filter-fn]
   (show-env key-fn filter-fn (System/getenv)))
  ([key-fn filter-fn data]
   (->> data
        (filter filter-fn)
        (map (fn [[k v]] [(key-fn k) v]))
        (into (sorted-map))
        (pprint))
   :ok))

(defn show-cmr-env
  "Show just the system environment variables with the `CMR_` prefix."
  []
  (show-env
    (comp keyword
          string/lower-case
          #(string/replace % "_" "-")
          #(string/replace % #"^CMR_" ""))
    #(string/starts-with? %1 "CMR_")))

(defn scrub-token
  "Scrub token:
  1. When at least 25 chars long keep the first 10 chars and the last 5 chars.
  2. When at least 15 chars long keep the first and the last 5 chars.
  3. When at least 5 and no more than 14 chars long, remove last 5.
  4. When less than 5 chars long, remove all chars.
  5. Replace what's removed with XXX."
  [token]
  (let [token-length (count token)]
    (cond
      (>= token-length 25) (str (subs token 0 10)
                                "XXX"
                                (subs token (- token-length 5) token-length))
      (>= token-length 15) (str (subs token 0 5)
                                "XXX"
                                (subs token (- token-length 5) token-length))
      (and (> token-length 5)
           (<= token-length 14)) (str (subs token 0 (- token-length 5)) "XXX")
      :else "XXX")))

(defn is-jwt-token?
  "Check if a token matches the JWT pattern (Base64.Base64.Base64) and if it
   does, try to look inside the header section and verify that the token is JWT
   and it came from EarthDataLogin (EDL). Tokens may start with Bearer and end
   with with a client-id section.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [raw-token]
  (let [BEARER "Bearer "
        token (if (string/starts-with? raw-token BEARER)
                (subs raw-token (count BEARER))
                raw-token)]
    (if (some? (re-find #"[A-Za-z0-9=_-]+\.[A-Za-z0-9=_-]+\.[:A-Za-z0-9=_-]+" token))
      (let [token-parts (string/split token #"\.")
            token-header (first token-parts)
            header-raw (String. (.decode (java.util.Base64/getDecoder) token-header))]
        ;; don't parse the data unless it is really needed to prevent unnecessary
        ;; processing. Check first to see if the data looks like JSON
        (if (and (string/starts-with? header-raw "{")
                 (string/ends-with? header-raw "}"))
          (try
            (if-let [header-data (json/parse-string header-raw true)]
              (and (= "JWT" (:typ header-data))
                   (= "Earthdata Login" (:origin header-data)))
              false)
            (catch com.fasterxml.jackson.core.JsonParseException e false))
          false))
      false)))

(defn human-join
  "Given a vector of strings, return a string joining the elements of the collection with 'separator', except for
  the last two which are joined with \"'separator' 'final-separator' \".
  Example: (fancy-join [\"One\" \"Two\" \"Three\"] \",\" \"or\") => \"One, Two, or Three\""
  [coll separator final-separator]
  (let [spaced-sep (str separator " ")]
    (if (< (count coll) 3)
      (string/join (format " %s " final-separator) coll)
      (let [front (string/join spaced-sep (subvec coll 0 (- (count coll) 1)))
            back (format "%s %s %s" separator final-separator (last coll))]
        (str front back)))))

;; The next set of declarations and a function is to convert numbers of specific units to meters.
;; It is used to convert
(def decimal-degress->meters-conversion-factor
  "We are using the great circle algorithm https://en.wikipedia.org/wiki/Great-circle_distance
   as the conversion from Decimal Degrees to meters. (2*pi*r)/360 where r=6371009 meters - the mean
   radius of the earth using the WGS84 ellipsoid. This conversion is adequate for horizontal data
   resolution range facets for which this conversion is being used. 2*3.14159*6371009/360=111195
   meters/degree"
  111195)

(def kilometers->meters-conversion-factor
  "Conversion factor from kilometers to meters"
  1000)

(def statute-miles->meters-conversion-factor
  "Conversion factor from statue miles to meters"
  1609.344)

(def nautical-miles->meters-conversion-factor
  "Conversion factor from nautical miles to meters"
  1852.001)

(defn convert-to-meters
  "Converts the value of the specific unit to meters. If the value is nil then return nil."
  [value unit]
  (cond
    (nil? value) nil
    (= "Decimal Degrees" unit) (* value decimal-degress->meters-conversion-factor)
    (= "Kilometers" unit) (* value kilometers->meters-conversion-factor)
    (= "Statute Miles" unit) (* value statute-miles->meters-conversion-factor)
    (= "Nautical Miles" unit) (* value nautical-miles->meters-conversion-factor)
    :else value))

(defn remove-nil-tail
  "Remove trailing nils from a list or vector."
  [coll]
  (loop [x coll]
    (if (or (empty? x) (last x))
      x
      (recur (drop-last x)))))

(defn safe-read-string
  "If s is a string, call read-string, otherwise returns s."
  [s]
  (if (string? s)
    (read-string s)
    s))

(ns cmr.common.joda-time
  "Adds some features for Joda Times to make them easier to work with.
  - Adds ability to convert Joda Times to JSON.

  - Implements the clojure multimethod print-dup for Joda Time. This allows instances of the Joda
  time classes to be written and parsed from strings. This feature can be made available simply by
  requiring this namespace."
  (:require
   [cheshire.generate :as json-gen])
  (:import
   (com.fasterxml.jackson.core JsonGenerator)
   (org.joda.time DateTime DateTimeZone)
   (org.joda.time.base AbstractInstant)
   (java.io Writer)))

;; Adds the ability for Joda DateTimes to be converted to JSON.
(json-gen/add-encoder
  DateTime
  (fn [c ^JsonGenerator jsonGenerator]
    (.writeString jsonGenerator (str c))))

;; Based on code from
;; https://github.com/michaelklishin/monger/blob/master/src/clojure/monger/joda_time.clj
;; See https://github.com/clj-time/clj-time/issues/80

(defn date-time
  "Constructor for date time instances in a specific time zone. This will be referenced from the
  printed instant."
  [^long instant ^String zone-id]
  ; (println "I'm turning this into a date-time - instant" instant "zone-id" zone-id)
  (DateTime. instant (DateTimeZone/forID zone-id)))

(defn date-time-instant->construction-code
  "Converts a date time instant into the code that can be used to construct a new instant."
  [^AbstractInstant d]
  (let [zone-id (.. d getZone getID)
        millis (.. d toInstant getMillis)]
    `(cmr.common.joda-time/date-time ~millis ~zone-id)))

(defmethod print-dup AbstractInstant
  [^AbstractInstant d ^Writer out]
  (.write
    out
    (str "#=" (date-time-instant->construction-code d))))

(defmethod print-method AbstractInstant
  [^AbstractInstant d ^Writer writer]
  (.write
    writer
    (str "#=" (date-time-instant->construction-code d))))

;; Need to override some methods defined by clj-time in 0.15.1 that do not handle timezones
;; correctly for DateTime
(defmethod print-dup DateTime
  [^DateTime d ^Writer out]
  (.write
    out
    (str "#=" (date-time-instant->construction-code d))))

(defmethod print-method DateTime
  [^DateTime d ^Writer writer]
  (.write
    writer
    (str "#=" (date-time-instant->construction-code d))))

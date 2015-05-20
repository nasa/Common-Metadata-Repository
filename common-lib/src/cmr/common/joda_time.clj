(ns cmr.common.joda-time
  "Implements the clojure multimethod print-dup for Joda Time. This allows instances of the Joda
  time classes to be written and parsed from strings. This feature can be made available simply by
  requiring this namespace.")

;; Based on code from
;; https://github.com/michaelklishin/monger/blob/master/src/clojure/monger/joda_time.clj
;; See https://github.com/clj-time/clj-time/issues/80

(defn date-time
  "Constructor for date time instances in a specific time zone. This will be referenced from the
  printed instant."
  [^long instant ^String zone-id]
  (org.joda.time.DateTime. instant (org.joda.time.DateTimeZone/forID zone-id)))

(defn date-time-instant->construction-code
  "Converts a date time instant into the code that can be used to construct a new instant."
  [^org.joda.time.base.AbstractInstant d]
  (let [zone-id (.. d getZone getID)
        millis (.. d toInstant getMillis)]
    `(cmr.common.joda-time/date-time ~millis ~zone-id)))

(defmethod print-dup org.joda.time.base.AbstractInstant
  [^org.joda.time.base.AbstractInstant d ^java.io.Writer out]
  (.write
    out
    (str "#=" (date-time-instant->construction-code d))))

(defmethod print-method org.joda.time.base.AbstractInstant
  [d writer]
  (.write
    writer
    (str "#=" (date-time-instant->construction-code d))))


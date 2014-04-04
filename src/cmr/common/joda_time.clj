(ns cmr.common.joda-time
  "Implements the clojure multimethod print-dup for Joda Time. This allows instances of the Joda
  time classes to be written and parsed from EDN. This feature can be made available simply by
  requiring this namespace in the namespace that is writing the EDN.")

;; Based on code from
;; https://github.com/michaelklishin/monger/blob/master/src/clojure/monger/joda_time.clj
;; See https://github.com/clj-time/clj-time/issues/80

(defmethod print-dup org.joda.time.base.AbstractInstant
  [^org.joda.time.base.AbstractInstant d ^java.io.Writer out]
  (.write
    out
    (str "#=" `(org.joda.time.DateTime. ~(.. d toInstant getMillis)))))

(defmethod print-method org.joda.time.base.AbstractInstant
  [d writer]
  (.write
    writer
    (str "#=" `(org.joda.time.DateTime. ~(.. d toInstant getMillis)))))

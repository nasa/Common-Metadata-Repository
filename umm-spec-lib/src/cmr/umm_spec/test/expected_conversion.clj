(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format")

(defn- expected-echo10
  "This manipulates the expected parsed UMM record based on lossy conversion in ECHO10."
  [expected]
  ;; ECHO10 returns entry id as a combination of short name and version. It generates short name
  ;; from entry id. So the expected entry id when going from umm->echo10->umm is the original
  ;; entry id concatenated with the version id.
  (update-in expected [:EntryId :Id] #(str % "_"
                                           ;; put version here once it's added to UMM.
                                           "V1"
                                           )))

(def ^:private formats->expected-conversion-fns
  "A map of metadata formats to expected conversion functions"
  {:echo10 expected-echo10})

(defn metadata-format->expected-conversion
  "Takes a metadata format and returns the function that can convert the UMM record used as input
  into the expected parsed UMM."
  [metadata-format]
  ;; identity is used if no conversion is needed.
  (get formats->expected-conversion-fns metadata-format identity))

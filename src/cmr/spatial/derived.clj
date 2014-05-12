(ns cmr.spatial.derived
  "This namespace defines a protocol for converting from a type with core fields defined to
  the type with core fields + derived data. Many spatial shapes follow the pattern of having
  several core fields along with derived fields. We don't always want to derive those fields. This
  protocol allows those derived fields to be calculated at a later time.")


(defprotocol DerivedCalculator
  (calculate-derived
    [record]
    "Takes a record with core fields and returns it with additional derived fields calculated."))
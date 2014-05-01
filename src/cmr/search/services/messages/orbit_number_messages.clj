(ns cmr.search.services.messages.orbit-number-messages
  "Contains messages for reporting responses to the user")

(defn invalid-orbit-number-msg
  []
  "orbit_number must be an number or a range in the form 'number,number'.")
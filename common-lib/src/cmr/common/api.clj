(ns cmr.common.api
  "Defines common helper functions for use when creating an API.")

(defn pretty-request?
  "Returns true if the request indicates the response should be returned in a human readable
  fashion. This can be specified either through a pretty=true in the URL query parameters or through
  a Cmr-Pretty HTTP header."
  ([params headers]
   (pretty-request? {:query-params params :headers headers}))
  ([request]
   (let [{:keys [headers query-params]} request]
     (or (= "true" (get query-params "pretty"))
         (= "true" (get headers "cmr-pretty"))))))


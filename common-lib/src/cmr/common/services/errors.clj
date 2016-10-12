(ns cmr.common.services.errors
  "Contains functions that will throw errors that when caught in the API will return the
  correct error code. Errors can be a list of strings or a list of path errors. A path error is an error
  within a structured piece of data that has a path. A path would consist of a set of clojure keywords
  and numbers (as indexes within a list) to the item that had the error.

  For example if the following clojure data structure had an error with the second address city

    {:first-name \"James\"
     :last-name \"Blonde\"
     :addresses [{:street \"123 Main St.\"
                  :city \"Annapolis\"}
                 {:street \"123 Main St.\"
                  :city \"\"}]}
  A sample error would be

  {:path [:addresses 1 :city]
   :errors [\"City is required.\"]}")

(defrecord PathErrors
  [
   ;; A sequence of keywords and indices to the location of an error within a structured piece of data.
   path
   ;; A list of error messages at this path.
   errors])

(defn throw-service-error
  "Throws an instance of clojure.lang.ExceptionInfo that will contain a map with the type of
  error and a message. See http://stackoverflow.com/a/16159584."
  ([type msg]
   (throw (ex-info msg {:type type :errors [msg]})))
  ([type msg cause]
   (throw (ex-info msg {:type type :errors [msg]} cause))))

(defmulti errors->message
  "Returns an error message to include as the message in a thrown exception."
  (fn [errors]
    (type (first errors))))

(defmethod errors->message String
  [errors]
  (first errors))

(defmethod errors->message cmr.common.services.errors.PathErrors
  [errors]
  (-> errors first :errors first))

(defn throw-service-errors
  "Throws an instance of clojure.lang.ExceptionInfo that will contain a map with the type of
  error and errors. See http://stackoverflow.com/a/16159584."
  ([type errors]
   (throw (ex-info (errors->message errors) {:type type :errors errors})))
  ([type errors cause]
   (throw (ex-info (errors->message errors) {:type type :errors errors} cause))))

(defn internal-error!
  "Throws an Exception with the given message and error, if given, to indicate an internal error in the system."
  ([^String msg]
   (internal-error! msg nil))
  ([^String msg ^Throwable cause]
   (if cause
     (throw (Exception. msg cause))
     (throw (Exception. msg)))))

(defn handle-service-errors
  "A helper for catching and handling service errors. Takes one function that may generate a service
  error. The other function handles the service error. It will be passed three arguments: the error
  type, the list of errors, and the actual exception."
  [f error-handler]
  (try
    (f)
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (error-handler type errors e)
          (throw e))))))

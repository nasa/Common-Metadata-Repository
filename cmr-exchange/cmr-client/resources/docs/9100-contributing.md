#  • Contributing


## Reporting Bugs / Requesting Features

For questions, reporting bugs, or asking for new features to be put on the
development roadmap, you can submit your request by
[creating a new ticket][create-issue] on Github.


## Assisting with Implementations

If you would like to contribute code in an effort to bring the client API into
feature parity with the CMR services API, here are the steps you need to
follow:

* Identify the API call you'd like to implement
* Update the protocol, e.g., `cljc/cmr/client/search/protocol.cljc`
  * Add the new protocol method to the `import-vars` in the Clojure client,
    e.g., `clj/cmr/client/search.clj`
  * Add the new protocol method to the `import-vars` in the ClojureScript
    client, e.g., `cljs/cmr/client/search.clj`
* Create an implementation of the new function, e.g.,
  `cljc/cmr/client/search/impl.cljc` (note that Clojure and ClojureScript CMR
  clients share implementations, so you'll have to write code that's compatible
  with both)
* Update the `extend-type` in the ClojureScript client, e.g.,
  `cljs/cmr/client/search.clj` (this step isn't needed for the Clojure client;
  it's needed in ClojureScript because it doesn't support the `extend` macro)
* Try out the new function in the Clojure REPL
* Try it out in the ClojureScript (figwheel) REPL
* Try it out in the dev console at [http://localhost:3449/dev.html](local-web-repl)
* For any support functions you've created, add some unit tests
* Add integration tests for the new function
* Make sure you've included docstrings for all your additions


<!-- Named page links below: /-->

[create-issue]: https://github.com/cmr-exchange/cmr-client/issues/new

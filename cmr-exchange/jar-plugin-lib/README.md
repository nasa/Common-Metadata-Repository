# cmr-jar-plugin

*A library for creating JAR-based plugins*

[![Build Status][travis-badge]][travis]
[![Security Scan][security-scan-badge]][travis]
[![Dependencies Status][deps-badge]][travis]
[![Open Pull Requests][prs-badge]][prs]

[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]

[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Setup](#setup-)
* [Usage](#usage-)
* [License](#license-)


## About [&#x219F;](#contents)

This project offers two key pieces of fucntionality:

1. An easy means of treating regular JAR files as application-specific
   plugins, and
1. Using the [Component](https://github.com/stuartsierra/component)
   library to register these in a running system.

**Question**: At a high-level, how do I use this? How do a plan a project to
take advantage of this?

**Answer**: TBD!


## Dependencies [&#x219F;](#contents)

* Java
* `lein`


## Setup [&#x219F;](#contents)

Before using, one must do two things:

* Create one or more plugin JAR files (requires adding custom `MANIFEST.mf`
  headers), and
* Add (or update) a configuration file in your project's resource path.


### Creating a Plugin Jar

The `lein` tool provides an
[easy and intuitive way](https://github.com/technomancy/leiningen/blob/master/sample.project.clj)
of adding custom entries to a JAR file's manifest file:

```clj
  :manifest {"CMR-Plugin" "service-bridge-routes"}
```

Here we're annotating a project as a `CMR-Plugin` providing a plugin type of
`service-bridge-routes`.


### Creating a Configuration File

Next, you need to create a configuration file in EDN format. Here is the
default config file:

```clj
{:plugin {
   :jarfiles {
     ;; The reducer factory function must take two args: plugin name and plugin
     ;; type -- both of type ^String.
     :reducer-factory cmr.plugin.jar.core/create-regex-plugin-reducer}
   :registry {
     :default {
       :plugin-name ".*"
       :plugin-type ".*"
       ;; The fowllowing is an in-JAR path to the config file
       :config-file "config/cmr-opendap/config.edn"}
     :web {
       :route-keys [:web :route-fns]
       :api-route-key :api
       :site-route-key :site}}}}
```

You can put this function anywhere in your project that will be included in
the JAR file.


Note that the `:config-file` that is referneced in the configuration is where
the JVM will look in the plugin JAR for your _plugin's_ configuration file.
It is (sanely) expected that all of your plugins will share a common path and
file name.

While this project was originally designed for use by the NASA/Earthdata's
CMR suite of services, it can be used anywhere with any name. Names can be
either strings or (Clojure-style) regular expressions. The same is true for the
plugin type. Note, however, that the closer a match you provide, the quicker
the plugins will be processed (too library a regular expression will let
through all the JAR files in your project's classpath, and each of those will
be searched for the plugin config file.)

`cmr-jar-plugin` comes with a handful of reducer-creating functions, the most
flexible of which is the one in the default configuration. These functions
take as arguments a plugin name and a plugin type (e.g., the key and value that
are used in a manifest file). They return a reducer function (a function
whose first arg is an accumulator and whose second argument is the item being
processed in any given step). You can create your own if you so desire, and
reference it in your configuration file.

Once the reducer has created a collection of JAR files that match what you
specified in your configuration, they will then read each plugin's configuration
file (stored in the JAR file at the location you indicated with
`:config-file`).

By default, a web/routes plugin expects to find your routes configuration at
`[:web :route-fns]`:

```clj
{:web {
   :route-fns {
     :api my.plugin1.app.api.routes/all
     :site my.plugin1.app.site.routes/all}}}
```

If your web plugin's routes are configured elsewhere, just update the
`:route-keys` entry in your confiuration. Also, the keynames used for API and
site routes can be changed.

Finally, all that's left' to be done is `lein install` or `lein deploy clojars`
(depending upon where you want your plugin dep to live).


## Usage [&#x219F;](#contents)

### Library

Note that the results in the following example usages were captured on a
system where sample plugin JARs were added as dependencies to a sample project.

```clj
(require '[cmr.plugin.jar.core :as jar-plugin]
         '[cmr.plugin.jar.jarfile :as jarfile])
```

Get a list of JAR files that declare themselves as plugins with a given name:

```clj
(def plugins (jar-plugin/jarfiles "CMR-Plugin"
                                  "service-bridge-app"
                                  jar-plugin/create-has-plugin-name-reducer))
plugins
```
```clj
(#object[java.util.jar.JarFile 0x7f2c21ed "java.util.jar.JarFile@7f2c21ed"]
 #object[java.util.jar.JarFile 0x1fddbdfb "java.util.jar.JarFile@1fddbdfb"]
 #object[java.util.jar.JarFile 0x5f6c7f61 "java.util.jar.JarFile@5f6c7f61"])
```

You can see the actual JAR file names with this:
```clj
(map jarfile/name plugins)
```
```clj
("/Users/oubiwann/.m2/repository/me/delete/plugin-c/0.1.0-SNAPSHOT/plugin-c-0.1.0-SNAPSHOT.jar"
 "/Users/oubiwann/.m2/repository/me/delete/plugin-a/0.1.0-SNAPSHOT/plugin-a-0.1.0-SNAPSHOT.jar"
 "/Users/oubiwann/.m2/repository/me/delete/plugin-b/0.1.0-SNAPSHOT/plugin-b-0.1.0-SNAPSHOT.jar")
```

And, with even more fine-grained detail, you can see the plugin types with
this:

```clj
(map #(jarfile/manifest-value % "CMR-Plugin") plugins)
```
```clj
("concept" "service-bridge-routes" "service-bridge-routes")
```

As you can see, there are different plugin types for plugins with the same
name. You can filter by type, if you so desire:

```clj
(def plugins (jar-plugin/jarfiles "CMR-Plugin"
                                  "service-bridge-routes"
                                  jar-plugin/create-has-plugin-type-reducer))
plugins
```
```clj
(#object[java.util.jar.JarFile 0x2a1d7585 "java.util.jar.JarFile@2a1d7585"]
 #object[java.util.jar.JarFile 0x65815455 "java.util.jar.JarFile@65815455"])
```



### Registry

Note that the results in the following example usages were captured on a
system where sample plugin JARs were added as dependencies to a sample project.

The plugin registry is just a specialized
[Component](https://github.com/stuartsierra/component) in a running system.
Once the system is brought up, you can use the functions that have been written
for the plugin regsitry component:

```clj
(require '[cmr.plugin.jar.components.registry :as registry])
(registry/jars system)
```
```clj
[{:file "/Users/oubiwann/.m2/repository/me/delete/plugin-c/0.1.0-SNAPSHOT/plugin-c-0.1.0-SNAPSHOT.jar"
  :object #object[java.util.jar.JarFile 0x7797053f "java.util.jar.JarFile@7797053f"]
  :plugin-name "CMR-Plugin"
  :plugin-type "concept"}
 {:file "/Users/oubiwann/.m2/repository/me/delete/plugin-a/0.1.0-SNAPSHOT/plugin-a-0.1.0-SNAPSHOT.jar"
  :object #object[java.util.jar.JarFile 0x2ec9c57a "java.util.jar.JarFile@2ec9c57a"]
  :plugin-name "CMR-Plugin"
  :plugin-type "service-bridge-routes"}
 {:file "/Users/oubiwann/.m2/repository/me/delete/plugin-b/0.1.0-SNAPSHOT/plugin-b-0.1.0-SNAPSHOT.jar"
  :object #object[java.util.jar.JarFile 0x2de0f074 "java.util.jar.JarFile@2de0f074"]
  :plugin-name "CMR-Plugin"
  :plugin-type "service-bridge-routes"}]
```

Then, for route plugins:

```clj
(registry/routes system)
```
```clj
{:api [plugin-c.core/foo plugin-a.core/foo plugin-b.core/foo]
 :site [plugin-c.core/foo plugin-a.core/foo plugin-b.core/foo]}
```

```clj
(registry/assembled-routes system)
```
```clj
{:api (["/c/1" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/c/2" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/c/3" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/a/1" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/a/2" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/a/3" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/b/1" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/b/2" {:get #<Fn@62977afb clojure.core/identity>}]
       ["/b/3" {:get #<Fn@62977afb clojure.core/identity>}])
 :site (["/c/1" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/c/2" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/c/3" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/a/1" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/a/2" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/a/3" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/b/1" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/b/2" {:get #<Fn@62977afb clojure.core/identity>}]
        ["/b/3" {:get #<Fn@62977afb clojure.core/identity>}])}
```

Note that calling the last two functions for a system that has plugins, but not
route plguins, will result in `nil` values.


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[travis]: https://travis-ci.org/cmr-exchange/cmr-jar-plugin
[travis-badge]: https://travis-ci.org/cmr-exchange/cmr-jar-plugin.png?branch=master
[deps-badge]: https://img.shields.io/badge/deps%20check-passing-brightgreen.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/cmr-jar-plugin.svg
[tag]: https://github.com/cmr-exchange/cmr-jar-plugin/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.9.0-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-jar-plugin
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-jar-plugin.svg
[security-scan-badge]: https://img.shields.io/badge/dependency%20check%20security%20scan-passing-brightgreen.svg
[prs]: https://github.com/pulls?utf8=%E2%9C%93&q=is%3Aopen+is%3Apr+org%3Acmr-exchange+archived%3Afalse+
[prs-badge]: https://img.shields.io/badge/Open%20PRs-org-yellow.svg

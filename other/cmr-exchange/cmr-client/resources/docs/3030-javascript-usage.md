#  • JavaScript Usage

You can use the compiled ClojureScript client in the browser by including the
library `cmr_client.js` file in a `<script>` tag in a web page or JavaScript-
based desktop/mobile application.

If you have a local copy of the library, you can use this declaration:

```html
<script src="js/cmr_client.js" type="text/javascript"></script>
```

Since the CMR client is published in [npm][npm], you can use the
[npm CDN][npm-cdn]:

```html
<script src="//unpkg.com/@nasa-earthdata/cmr@latest"
        type="text/javascript"></script>
```

Then, from the console, in the page, or in a `.js` file, you can do:

```js
var client = cmr.client.ingest.create_client({"return-body?": true});
var channel = cmr.client.ingest.get_providers(client);
cmr.client.common.util.with_callback(channel, function(data) {
  var formatted_output = JSON.stringify(data, null, 2);
  document.getElementById("data").innerHTML = formatted_output;
});
```
Then you'll the page get updated with the following content:
```clj
[{:provider-id LARC_ASDC, :short-name LARC_ASDC ...}
```

See the [example html page][example-html-page] if you'd like to try this out.


<!-- Named page links below: /-->

[npm]: https://www.npmjs.com/package/@nasa-earthdata/cmr
[npm-cdn]: https://unpkg.com/
[example-html-page]: https://cmr-exchange.github.io/cmr-client/cdn.html

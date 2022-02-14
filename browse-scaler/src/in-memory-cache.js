const InMemoryCache = (function(){
    const _data = {};

    function getInstance() {
        return _data;
    }

    return {
      getInstance: getInstance
    };
  }());

exports.setValue = (key, item) => { InMemoryCache.getInstance() [key] = item;}

exports.getValue = (id) => { return InMemoryCache.getInstance() [id]}

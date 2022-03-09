const InMemoryCache = (function(){
    const _data = {};

    function getInstance() {
        return _data;
    }

    return {
      getInstance: getInstance
    };
  }());

exports.setValue = (key, value) => { InMemoryCache.getInstance() [key] = value;}

exports.getValue = (key) => { return InMemoryCache.getInstance() [key]}

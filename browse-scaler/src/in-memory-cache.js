const data = {};

// const InMemoryCache = (function() {
//   const _data = {};

//   function getInstance() {
//     return _data;
//   }

//   return {
//     getInstance
//   };
// })();

const getInstance = () => {
  return data;
};

export const setValue = (key, value) => {
  getInstance()[key] = value;
};

export const getValue = key => {
  return getInstance()[key];
};

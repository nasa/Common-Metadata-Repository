const data = {};

const getInstance = () => {
  return data;
};

export const setValue = (key, value) => {
  getInstance()[key] = value;
};

export const getValue = key => {
  return getInstance()[key];
};

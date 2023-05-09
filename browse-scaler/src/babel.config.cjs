module.exports = {
  presets: [
    [
      '@babel/preset-env',
      {
        targets: {
          esmodules: true
        }
      }
    ]
  ],
  plugins: ['@babel/plugin-syntax-import-assertions']
};

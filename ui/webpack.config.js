const MomentLocalesPlugin = require('moment-locales-webpack-plugin');
const path = require('path');

module.exports = {  
  mode: "production",
  entry: path.resolve(__dirname, "src", "app.ts"),
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
    ],
  },
  resolve: {
    extensions: ['.ts']
  },
  output: {
    path: path.resolve(__dirname, "public"),
    filename: "app.js"
  },
  plugins: [
    new MomentLocalesPlugin()
  ]
}

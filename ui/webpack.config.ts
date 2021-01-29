const MomentLocalesPlugin = require('moment-locales-webpack-plugin');
import { PreRenderPlugin } from './prerender/plugin'
import { resolve } from 'path'

module.exports = {  
  mode: "production",
  entry: resolve(__dirname, "src", "boot.ts"),
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader?configFile=tsconfig.app.json',
        exclude: /node_modules/,
      },
    ],
  },
  resolve: {
    extensions: ['.ts', '.js', '.tsx']
  },
  output: {
    path: resolve(__dirname, "public"),
    filename: "app.js"
  },
  plugins: [
    new MomentLocalesPlugin(),
    new PreRenderPlugin()
  ]
}

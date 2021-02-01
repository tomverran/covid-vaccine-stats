const MomentLocalesPlugin = require('moment-locales-webpack-plugin');
const HtmlMinimizerPlugin = require("html-minimizer-webpack-plugin");
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
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
      {
        test: /\.s?[ac]ss$/i,
        use: [MiniCssExtractPlugin.loader, "css-loader", "sass-loader"]
      },
      {
        test: /\.svg/,
        type: 'asset/inline'
      }
    ],
  },
  resolve: {
    extensions: ['.ts', '.js', '.tsx', '.scss', ".css"],
    alias: {
      react: "preact/compat",
      "react-dom": "preact/compat"
    }
  },
  output: {
    path: resolve(__dirname, "public"),
    filename: "app.js"
  },
  externals: {
    moment: 'moment'
  },
  plugins: [
    new MiniCssExtractPlugin(),
    new MomentLocalesPlugin(),
    new PreRenderPlugin(),
    new HtmlMinimizerPlugin(),
  ]
}

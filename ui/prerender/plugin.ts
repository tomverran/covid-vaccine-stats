import { JSDOM } from 'jsdom';
import { renderToString } from 'react-dom/server';
import { createElement } from 'react'

import { readFileSync } from 'fs';
import { resolve } from 'path'
import { Compiler } from 'webpack';
import { OriginalSource } from 'webpack-sources';

export class PreRenderPlugin {
  apply(compiler: Compiler) {
    compiler.hooks.thisCompilation.tap('PreRenderPlugin', compilation => {
      const { App } = require("../src/app");
      const renderedComponents: string = renderToString(createElement(App));
      const dom: JSDOM = new JSDOM(readFileSync(resolve(__dirname, "index.html"), 'utf8'));
      (dom.window.document.querySelector('body div') as HTMLElement).innerHTML = renderedComponents;
      const toEmit: any = new OriginalSource(dom.serialize(), 'index.html');
      compilation.emitAsset('index.html', toEmit);
    });
  }
}

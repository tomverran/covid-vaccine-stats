import { JSDOM } from 'jsdom';
import { App } from '../src/app'
import { renderToString } from 'react-dom/server';
import { createElement } from 'react'

import { readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path'
import { Compiler } from 'webpack';

export class PreRenderPlugin {
  apply(compiler: Compiler) {
    compiler.hooks.afterEmit.tap('PreRenderPlugin', compilation => {
      const renderedComponents: string = renderToString(createElement(App));
      const dom: JSDOM = new JSDOM(readFileSync(resolve(__dirname, "index.html"), 'utf8'));
      (dom.window.document.querySelector('body div') as HTMLElement).innerHTML = renderedComponents;
      writeFileSync(resolve(compilation.outputOptions.path ?? '../', 'index.html'), dom.serialize());
    });
  }
}
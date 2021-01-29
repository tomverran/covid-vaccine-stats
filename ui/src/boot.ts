import { App } from './app'
import ReactDOM from 'react-dom';
import React from 'react';

ReactDOM.hydrate(React.createElement(App), document.querySelector('body div'))
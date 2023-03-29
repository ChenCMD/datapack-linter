/* eslint-disable */

'use strict';
const path = require('path');

const targetFileName = 'datapack-linter-fastopt';

module.exports = {
    target: 'node',
    entry: {
        [targetFileName]: path.resolve(__dirname, `${targetFileName}.js`),
        // server: './node_modules/@spgoding/datapack-language-server/lib/server.js'
    },
    output: {
        path: path.resolve(__dirname, '../../../../dist'),
        filename: '[name]-bundle.js'
    },
    // devtool: 'source-map',
    resolve: {
        extensions: ['.ts', '.js', '.json']
    },
    mode: "development",
    module: {
        rules: [
            {
                test: /\.ts$/,
                exclude: /node_modules/,
                use: [
                    {
                        loader: 'ts-loader'
                    }
                ]
            },
            {
                test: /\.d\.ts$/,
                type: 'asset/source'
            }
        ]
    }
};
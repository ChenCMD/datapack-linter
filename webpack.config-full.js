/* eslint-disable */

'use strict';
const path = require('path');

const targetFileName = 'datapack-linter-opt';

module.exports = {
    target: 'node',
    entry: {
        [targetFileName]: path.resolve(__dirname, `${targetFileName}.js`),
        // server: './node_modules/@spgoding/datapack-language-server/lib/server.js'
    },
    output: {
        path: path.resolve(__dirname, '../../../../dist'),
        filename: '[name]-bundle.js',
        hashFunction: 'xxhash64' // https://github.com/webpack/webpack/issues/14532
    },
    // devtool: 'source-map',
    resolve: {
        extensions: ['.ts', '.js', '.json']
    },
    mode: "production",
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
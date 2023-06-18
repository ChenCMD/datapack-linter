/* eslint-disable */

'use strict';
const path = require('path');

module.exports = {
    target: 'node',
    entry: {
        index: './scripts/index.ts',
        server: './node_modules/@spgoding/datapack-language-server/lib/server.js'
    },
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: '[name].js'
    },
    // devtool: 'source-map',
    resolve: {
        extensions: ['.ts', '.js', '.json']
    },
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
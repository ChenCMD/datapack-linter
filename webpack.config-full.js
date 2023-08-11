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
                test: /VanillaData.js$/g,
                loader: 'string-replace-loader',
                options: {
                    search: 'return `https:\/\/raw.githubusercontent.com\/\$\{maintainer\}\/\$\{name\}\/\$\{path\}`;',
                    replace: [
                        'if (name === "mc-nbtdoc") {',
                        '   if (path.startsWith("1.19.4-gen")) { path = path.replace("1.19.4-gen", "afba4f597e6bab050bcb8e2d945081f06f96af71"); }',
                        '   if (path.startsWith("1.20.0-gen")) { return "https://gist.githubusercontent.com/ChenCMD/58e317ac04d78eb4dce846867130aa44/raw/6d2ded8fa2ac8a65ca63efe5faea763dedc87cc5/1.20.0-gen.json"; }',
                        '   if (path.startsWith("1.20.1-gen")) { return "https://gist.githubusercontent.com/ChenCMD/58e317ac04d78eb4dce846867130aa44/raw/6d2ded8fa2ac8a65ca63efe5faea763dedc87cc5/1.20.1-gen.json"; }',
                        '}',
                        'return `https://raw.githubusercontent.com/${maintainer}/${name}/${path}`;'
                    ].join('\n'),
                    strict: true
                }
            },
            {
                test: /DocCommentPlugin.js$/g,
                loader: 'string-replace-loader',
                options: {
                    search: '__1.escapeIdentityPattern(anno[index].raw)',
                    replace: "__1.escapeIdentityPattern(anno[index].raw).replace(/\\?/g,'[^:/]').replace(/\\*\\*\\//g,'.{0,}').replace(/\\*\\*/g,'.{0,}').replace(/\\*/g,'[^:/]{0,}')",
                    strict: true
                }
            },
            {
                test: /ClientCache.js$/g,
                loader: 'string-replace-loader',
                options: {
                    multiple: [
                        { search: ':**', replace: ':.*' },
                        { search: ':**', replace: ':.*' },
                        { search: "'**'", replace: "'.*'" },
                        { search: ".replace(/\\?/g, '[^:/]')", replace: "" },
                        { search: ".replace(/\\*\\*\\//g, '.{0,}')", replace: "" },
                        { search: ".replace(/\\*\\*/g, '.{0,}')", replace: "" },
                        { search: ".replace(/\\*/g, '[^:/]{0,}')", replace: "" }
                    ],
                    strict: true
                }
            },
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
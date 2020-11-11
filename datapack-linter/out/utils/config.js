"use strict";
/* eslint-disable @typescript-eslint/no-explicit-any */
Object.defineProperty(exports, "__esModule", { value: true });
exports.getConfiguration = void 0;
const types_1 = require("@spgoding/datapack-language-server/lib/types");
const datapack_language_server_1 = require("@spgoding/datapack-language-server");
async function getConfiguration(configPath) {
    const json = await datapack_language_server_1.readFile(configPath);
    const result = {};
    const obj = JSON.parse(json);
    Object.entries(obj).forEach(([path, value]) => {
        const walk = (key, [head, ...tail]) => {
            if (key[head] === undefined)
                key[head] = {};
            if (tail.length > 0)
                walk(key[head], tail);
            else
                key[head] = value;
        };
        return walk(result, path.split('.'));
    });
    return types_1.constructConfig(result.datapack);
}
exports.getConfiguration = getConfiguration;
